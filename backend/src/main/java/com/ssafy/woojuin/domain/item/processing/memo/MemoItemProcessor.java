package com.ssafy.woojuin.domain.item.processing.memo;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.ai.AiSourceType;
import com.ssafy.woojuin.domain.ai.CategoryCandidate;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.event.ItemDoneEvent;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import com.ssafy.woojuin.global.common.ItemStatus;
import com.ssafy.woojuin.global.common.TransactionRunner;
import com.ssafy.woojuin.global.sse.WorkspaceChangedEvent;
import com.ssafy.woojuin.global.sse.WorkspaceEventType;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 메모 아이템 가공 오케스트레이터 (묶음 D).
 *
 * <p>MEMO는 {@code ItemService.validate()}에서 이미 content가 필수로 검증돼 저장
 * 시점에 본문이 확보돼 있다. URL의 트랙 B(본문 확보)에 항상 해당하는 상태로
 * 태어나는 셈이라 별도의 성공/실패 분기가 없다 — AI 보강 결과와 무관하게 항상
 * DONE으로 확정한다.
 *
 * <p><b>트랜잭션 경계</b>: process()에 @Transactional을 걸지 않는다 — LLM 호출(수십 초)
 * 내내 커넥션을 점유해 풀이 마르기 때문이다({@link TransactionRunner} javadoc의 사고 기록).
 * 읽기(스냅숏·후보 조회)는 리포지토리의 짧은 자체 트랜잭션으로, 외부 호출은 트랜잭션
 * 없이, DB 반영만 마지막에 {@code tx.write()}로 짧게 감싼다. 외부 호출 동안 아이템이
 * 삭제·처리될 수 있으므로 반영 트랜잭션 안에서 상태 가드를 다시 확인한다.
 */
@Slf4j
@Component
public class MemoItemProcessor implements ItemProcessor {

    private final ItemRepository itemRepository;
    private final AiAnalyzer aiAnalyzer;
    private final CategoryAssignmentService categoryAssignmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionRunner tx;

    public MemoItemProcessor(ItemRepository itemRepository, AiAnalyzer aiAnalyzer,
            CategoryAssignmentService categoryAssignmentService, ApplicationEventPublisher eventPublisher,
            TransactionRunner tx) {
        this.itemRepository = itemRepository;
        this.aiAnalyzer = aiAnalyzer;
        this.categoryAssignmentService = categoryAssignmentService;
        this.eventPublisher = eventPublisher;
        this.tx = tx;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.MEMO;
    }

    @Override
    public void process(ItemProcessingMessage message) {
        // 1단계(짧은 읽기): 스냅숏 확보 + 가드. 이 엔티티는 트랜잭션 밖에서 읽기 전용으로만 쓴다.
        Item snapshot = loadProcessable(message.itemId());
        if (snapshot == null) {
            return;
        }

        // 2단계(트랜잭션 없음): LLM 호출. 실패는 흡수한다 — AI 보강이 사용자 메모를 무효화하면 안 된다.
        AiAnalysis analysis = analyzeSafely(snapshot);

        // 3단계(짧은 쓰기): 다시 로드해 가드를 재확인하고 반영·확정한다.
        tx.write(() -> {
            Item item = itemRepository.findById(message.itemId()).orElse(null);
            if (item == null) {
                log.warn("반영할 아이템이 없음(외부 호출 중 삭제됨?): itemId={}", message.itemId());
                return;
            }
            if (item.getStatus() != ItemStatus.PROCESSING) {
                log.info("이미 처리된 아이템, 반영 스킵: itemId={}, status={}", item.getId(), item.getStatus());
                return;
            }
            if (analysis != null) {
                item.update(analysis.title(), null);   // AI가 다듬은 제목(null이면 기존 유지)
                item.applySummary(analysis.summary());
                categoryAssignmentService.assign(item.getId(), item.getWorkspaceId(), analysis.categories());
            }
            item.markDone();
            eventPublisher.publishEvent(new ItemDoneEvent(item.getId()));
            eventPublisher.publishEvent(WorkspaceChangedEvent.of(item.getWorkspaceId(), WorkspaceEventType.ITEM));
            log.info("메모 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
        });
    }

    /** 가공 대상 아이템을 읽는다. 없거나 이미 처리됐으면 null — AI를 부르기 전에 거른다. */
    private Item loadProcessable(Long itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", itemId);
            return null;
        }
        if (item.getStatus() != ItemStatus.PROCESSING) {
            // at-least-once 큐 특성상 이미 끝난 메시지가 재배달될 수 있다.
            // AI를 또 호출하지 않도록 여기서 막는다.
            log.info("이미 처리된 아이템, 재처리 스킵: itemId={}, status={}", item.getId(), item.getStatus());
            return null;
        }
        return item;
    }

    /**
     * AI 요약·분류를 수행한다. 어떤 실패도 이미 저장된 사용자 메모를 무효화하면
     * 안 되므로 조용히 흡수하고 null을 돌려준다(UrlItemProcessor.analyzeSafely와 동일 패턴).
     */
    private AiAnalysis analyzeSafely(Item snapshot) {
        try {
            List<CategoryCandidate> candidates = categoryAssignmentService.candidates(snapshot.getWorkspaceId());
            return aiAnalyzer.analyze(new AiAnalysisRequest(
                    AiSourceType.MEMO, snapshot.getTitle(), snapshot.getContent(), candidates));
        } catch (Exception e) {
            // 스택트레이스 포함 — 래퍼 예외에 묻힌 근본 원인(DB 커넥션 등)이 보여야 한다.
            log.warn("AI 보강 실패(무시): itemId={}", snapshot.getId(), e);
            return null;
        }
    }
}

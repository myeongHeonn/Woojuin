package com.ssafy.woojuin.domain.item.processing.memo;

import com.ssafy.woojuin.domain.ai.AiAnalysis;
import com.ssafy.woojuin.domain.ai.AiAnalysisRequest;
import com.ssafy.woojuin.domain.ai.AiAnalyzer;
import com.ssafy.woojuin.domain.category.service.CategoryAssignmentService;
import com.ssafy.woojuin.domain.item.entity.Item;
import com.ssafy.woojuin.domain.item.entity.ItemType;
import com.ssafy.woojuin.domain.item.processing.ItemProcessingMessage;
import com.ssafy.woojuin.domain.item.processing.ItemProcessor;
import com.ssafy.woojuin.domain.item.repository.ItemRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메모 아이템 가공 오케스트레이터 (묶음 D).
 *
 * <p>MEMO는 {@code ItemService.validate()}에서 이미 content가 필수로 검증돼 저장
 * 시점에 본문이 확보돼 있다. URL의 트랙 B(본문 확보)에 항상 해당하는 상태로
 * 태어나는 셈이라 별도의 성공/실패 분기가 없다 — AI 보강 결과와 무관하게 항상
 * DONE으로 확정한다.
 */
@Slf4j
@Component
public class MemoItemProcessor implements ItemProcessor {

    private final ItemRepository itemRepository;
    private final AiAnalyzer aiAnalyzer;
    private final CategoryAssignmentService categoryAssignmentService;

    public MemoItemProcessor(ItemRepository itemRepository, AiAnalyzer aiAnalyzer,
            CategoryAssignmentService categoryAssignmentService) {
        this.itemRepository = itemRepository;
        this.aiAnalyzer = aiAnalyzer;
        this.categoryAssignmentService = categoryAssignmentService;
    }

    @Override
    public boolean supports(ItemType type) {
        return type == ItemType.MEMO;
    }

    @Override
    @Transactional
    public void process(ItemProcessingMessage message) {
        Item item = itemRepository.findById(message.itemId()).orElse(null);
        if (item == null) {
            log.warn("가공할 아이템이 없음(삭제됨?): itemId={}", message.itemId());
            return;
        }

        enrichWithAi(item);
        item.markDone();
        log.info("메모 가공 완료: itemId={}, status={}", item.getId(), item.getStatus());
    }

    /**
     * AI 요약·분류를 반영한다. 어떤 실패도 이미 저장된 사용자 메모를 무효화하면
     * 안 되므로 조용히 흡수한다(UrlItemProcessor.enrichWithAi와 동일 패턴).
     */
    private void enrichWithAi(Item item) {
        try {
            List<String> candidates = categoryAssignmentService.candidateNames(item.getWorkspaceId());
            AiAnalysis analysis = aiAnalyzer.analyze(
                    new AiAnalysisRequest(item.getTitle(), item.getContent(), candidates));
            item.applySummary(analysis.summary());
            categoryAssignmentService.assign(item.getId(), item.getWorkspaceId(), analysis.categories());
        } catch (Exception e) {
            log.warn("AI 보강 실패(무시): itemId={}, cause={}", item.getId(), e.toString());
        }
    }
}

package com.ssafy.woojuin.domain.category.service;

import com.ssafy.woojuin.domain.ai.AiMixClient;
import com.ssafy.woojuin.domain.category.event.CategoryDescriptionNeededEvent;
import com.ssafy.woojuin.domain.category.repository.CategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 카테고리의 AI 분류용 설명을 백그라운드에서 생성한다. 카테고리 생성/이름 변경은
 * 사용자 앞의 동기 API라 LLM 호출(수 초)을 그 안에 넣지 않는다 — 커밋 후(@Async) 생성해
 * 채우고, 그 사이 분류 요청이 오면 이름 폴백으로 동작한다(AiMixClient.classify 참고).
 *
 * <p>실패는 로그만 남기고 버린다 — 설명은 분류 정확도를 올리는 부가 정보지 필수가 아니고,
 * 이름 폴백이 항상 있다. ai-mix가 꺼진 환경(클라이언트 빈 없음)에서는 조용히 건너뛴다.
 */
@Slf4j
@Component
public class CategoryDescriptionGenerator {

    private final CategoryRepository categoryRepository;
    private final ObjectProvider<AiMixClient> aiMixClientProvider;

    public CategoryDescriptionGenerator(CategoryRepository categoryRepository,
            ObjectProvider<AiMixClient> aiMixClientProvider) {
        this.categoryRepository = categoryRepository;
        this.aiMixClientProvider = aiMixClientProvider;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    // AFTER_COMMIT 리스너는 원 트랜잭션이 이미 끝난 뒤라 REQUIRES_NEW로 새로 연다
    // (Spring이 기본 전파를 금지한다 — 커밋된 트랜잭션에 참여하는 척하는 사고 방지).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generate(CategoryDescriptionNeededEvent event) {
        AiMixClient client = aiMixClientProvider.getIfAvailable();
        if (client == null) {
            return;
        }
        try {
            String description = client.createCategoryDescription(event.categoryId(), event.name());
            if (description == null) {
                return;
            }
            // 커밋 후 비동기 실행이라 그 사이 삭제·이름 재변경이 있었을 수 있다 — 다시 읽어
            // 이름이 그대로일 때만 반영한다(다른 이름의 설명을 덮어쓰는 사고 방지).
            categoryRepository.findById(event.categoryId())
                    .filter(category -> event.name().equals(category.getName()))
                    .ifPresent(category -> category.applyDescription(truncate(description)));
            log.info("카테고리 설명 생성 완료: categoryId={}", event.categoryId());
        } catch (Exception e) {
            log.warn("카테고리 설명 생성 실패(무시, 분류는 이름 폴백): categoryId={}", event.categoryId(), e);
        }
    }

    private String truncate(String value) {
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}

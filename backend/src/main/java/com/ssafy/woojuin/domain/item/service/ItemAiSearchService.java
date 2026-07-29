package com.ssafy.woojuin.domain.item.service;

import com.ssafy.woojuin.domain.ai.query.AiQueryPlan;
import com.ssafy.woojuin.domain.ai.query.AiQueryPlanner;
import com.ssafy.woojuin.domain.item.dto.ItemAiSearchResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * AI 모드 검색 (프론트 토글 ON). 자연어 질문을 검색어로 바꾼 뒤 <b>기존 키워드 검색을 그대로
 * 재사용</b>한다 — 검색 엔진을 하나 더 만드는 게 아니라, 사람 말과 키워드 사이의 번역만
 * AI에 맡기는 구조다.
 *
 * <p>덕분에 오타("울지로"→"을지로")와 표현 차이("이탈리안"→"파스타")를 pgvector 임베딩 없이
 * 해결할 수 있다. 임베딩 기반 의미 검색은 나중에 키워드 검색의 0건 폴백으로 붙는다
 * ({@link ItemSearchService} 주석 참고).
 */
@Service
public class ItemAiSearchService {

    private final AiQueryPlanner aiQueryPlanner;
    private final ItemSearchService itemSearchService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public ItemAiSearchService(AiQueryPlanner aiQueryPlanner, ItemSearchService itemSearchService,
            WorkspaceMemberRepository workspaceMemberRepository) {
        this.aiQueryPlanner = aiQueryPlanner;
        this.itemSearchService = itemSearchService;
        this.workspaceMemberRepository = workspaceMemberRepository;
    }

    /**
     * 트랜잭션을 걸지 않는다 — LLM 호출(수 초)이 트랜잭션 안에 들어가면 그동안 DB 커넥션을
     * 붙잡고 있어 풀이 마른다. 실제 조회는 ItemSearchService가 자기 트랜잭션으로 처리한다.
     * (S3 업로드를 트랜잭션 밖에서 먼저 끝내는 ItemService.createFromImage와 같은 이유.)
     */
    public ItemAiSearchResponse search(Long workspaceId, Long userId, String question, int page, int size) {
        // LLM을 부르기 전에 먼저 막는다. 뒤에 있는 ItemSearchService도 같은 검증을 하지만,
        // 거기까지 가면 남의 워크스페이스 id를 넣고 호출하는 것만으로 AI 토큰이 소모된다.
        verifyMembership(workspaceId, userId);

        AiQueryPlan plan = aiQueryPlanner.plan(question);

        if (plan.isEmpty()) {
            // 질문이 비었거나 걸러낼 게 전부여서 검색어가 안 남은 경우. 워크스페이스 전체를
            // 긁지 않도록 여기서 끊는다(키워드 검색과 같은 정책).
            PageRequest pageable = PageRequest.of(page, size);
            return ItemAiSearchResponse.empty(
                    pageable.getPageNumber(), pageable.getPageSize(), plan.keywords(), plan.aiPlanned());
        }

        ItemSearchResponse result =
                itemSearchService.search(workspaceId, userId, plan.keywords(), page, size);

        return ItemAiSearchResponse.from(result, plan.keywords(), plan.aiPlanned());
    }

    private void verifyMembership(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new WorkspaceAccessDeniedException(workspaceId));
    }
}

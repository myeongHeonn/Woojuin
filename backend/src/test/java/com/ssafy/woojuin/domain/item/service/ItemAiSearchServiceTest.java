package com.ssafy.woojuin.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.query.AiQueryPlan;
import com.ssafy.woojuin.domain.ai.query.AiQueryPlanner;
import com.ssafy.woojuin.domain.item.dto.ItemAiSearchResponse;
import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ItemAiSearchServiceTest {

    @Mock
    private AiQueryPlanner aiQueryPlanner;

    @Mock
    private ItemSearchService itemSearchService;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    private ItemAiSearchService service;

    @BeforeEach
    void setUp() {
        service = new ItemAiSearchService(aiQueryPlanner, itemSearchService, workspaceMemberRepository);
        lenient().when(workspaceMemberRepository.findByWorkspaceIdAndUserId(any(), any()))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
    }

    private ItemSearchResponse searchResult(long total, boolean partialMatch) {
        return new ItemSearchResponse(List.of(), 0, 28, total, partialMatch);
    }

    @Test
    void 해석된_키워드로_기존_검색을_호출한다() {
        when(aiQueryPlanner.plan("그 파스타집 어디였지?")).thenReturn(AiQueryPlan.byAi("을지로 파스타"));
        when(itemSearchService.search(1L, 1L, "을지로 파스타", 0, 28)).thenReturn(searchResult(2, false));

        ItemAiSearchResponse response = service.search(1L, 1L, "그 파스타집 어디였지?", 0, 28);

        assertThat(response.interpretedQuery()).isEqualTo("을지로 파스타");
        assertThat(response.aiPlanned()).isTrue();
        assertThat(response.totalElements()).isEqualTo(2);
    }

    /** 규칙 기반 폴백이었음을 클라이언트가 알 수 있어야 한다(오타 교정이 안 된 상태). */
    @Test
    void 규칙기반_폴백이면_aiPlanned가_false로_내려간다() {
        when(aiQueryPlanner.plan(any())).thenReturn(AiQueryPlan.byRule("파스타집"));
        when(itemSearchService.search(any(), any(), eq("파스타집"), anyInt(), anyInt()))
                .thenReturn(searchResult(1, false));

        assertThat(service.search(1L, 1L, "그 파스타집 어디였지?", 0, 28).aiPlanned()).isFalse();
    }

    @Test
    void partialMatch는_키워드_검색_결과를_그대로_전달한다() {
        when(aiQueryPlanner.plan(any())).thenReturn(AiQueryPlan.byAi("파스타 을지로 카페"));
        when(itemSearchService.search(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(searchResult(3, true));

        assertThat(service.search(1L, 1L, "질문", 0, 28).partialMatch()).isTrue();
    }

    /** 검색어가 안 남으면 워크스페이스 전체를 긁지 않도록 조회 자체를 하지 않는다. */
    @Test
    void 검색어가_비면_조회하지_않고_빈_결과() {
        when(aiQueryPlanner.plan(any())).thenReturn(AiQueryPlan.byRule(""));

        ItemAiSearchResponse response = service.search(1L, 1L, "   ", 0, 28);

        assertThat(response.totalElements()).isZero();
        assertThat(response.content()).isEmpty();
        verify(itemSearchService, never()).search(any(), any(), any(), anyInt(), anyInt());
    }

    /**
     * 멤버십 검증이 LLM 호출보다 먼저여야 한다 — 아니면 남의 워크스페이스 id를 넣고 부르는
     * 것만으로 AI 토큰이 소모된다.
     */
    @Test
    void 멤버가_아니면_LLM을_부르지_않고_거절한다() {
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search(1L, 99L, "그 파스타집 어디였지?", 0, 28))
                .isInstanceOf(WorkspaceAccessDeniedException.class);

        verifyNoInteractions(aiQueryPlanner);
        verifyNoInteractions(itemSearchService);
    }
}

package com.ssafy.woojuin.domain.item.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.item.dto.ItemSearchResponse;
import com.ssafy.woojuin.domain.item.exception.WorkspaceAccessDeniedException;
import com.ssafy.woojuin.domain.item.service.ItemSearchService;
import com.ssafy.woojuin.global.security.aop.AuthenticationAspect;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ItemSearchController.class,
        excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CurrentUserResolver.class, AuthenticationAspect.class})
@EnableAspectJAutoProxy
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemSearchService itemSearchService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void 인증되지_않은_검색은_공통형식_401() throws Exception {
        mockMvc.perform(get("/api/workspaces/1/search").param("q", "파스타"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(itemSearchService, never()).search(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 정상_검색은_공통형식_200() throws Exception {
        authenticateAs(1L);
        when(itemSearchService.search(eq(1L), eq(1L), eq("파스타"), eq(0), eq(28)))
                .thenReturn(new ItemSearchResponse(List.of(), 0, 28, 0, false, false));

        mockMvc.perform(get("/api/workspaces/1/search").param("q", "파스타"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(28))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /** q는 선택 파라미터다 — 검색창을 비운 채 호출해도 400이 아니라 빈 결과여야 한다. */
    @Test
    void q가_없어도_400이_아니다() throws Exception {
        authenticateAs(1L);
        when(itemSearchService.search(eq(1L), eq(1L), eq(null), eq(0), eq(28)))
                .thenReturn(new ItemSearchResponse(List.of(), 0, 28, 0, false, false));

        mockMvc.perform(get("/api/workspaces/1/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void 멤버가_아닌_워크스페이스_검색은_공통형식_403() throws Exception {
        authenticateAs(99L);
        when(itemSearchService.search(eq(1L), eq(99L), eq("파스타"), eq(0), eq(28)))
                .thenThrow(new WorkspaceAccessDeniedException(1L));

        mockMvc.perform(get("/api/workspaces/1/search").param("q", "파스타"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}

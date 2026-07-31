package com.ssafy.woojuin.global.sse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.workspace.entity.WorkspaceMember;
import com.ssafy.woojuin.domain.workspace.repository.WorkspaceMemberRepository;
import com.ssafy.woojuin.global.security.aop.AuthenticationAspect;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = WorkspaceSseController.class, excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@org.springframework.context.annotation.Import({CurrentUserResolver.class, AuthenticationAspect.class})
@EnableAspectJAutoProxy
class WorkspaceSseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceSseRegistry sseRegistry;

    @MockBean
    private WorkspaceMemberRepository workspaceMemberRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void 멤버는_스트림으로_연결되고_프록시_버퍼링_해제_헤더를_받는다() throws Exception {
        authenticateAs(1L);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(sseRegistry.subscribe(1L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/workspaces/1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    /**
     * produces=text/event-stream 매핑에서 예외가 나도 JSON 에러 응답이 나가는지 확인한다.
     * 스트림 타입만 허용된다고 오해해 406으로 떨어지면 프론트의 "401이면 refresh" 분기가
     * 아예 안 탄다 — 그 회귀를 여기서 잡는다.
     */
    @Test
    void 비멤버는_공통형식_403() throws Exception {
        authenticateAs(2L);
        when(workspaceMemberRepository.findByWorkspaceIdAndUserId(1L, 2L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/workspaces/1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403));

        verify(sseRegistry, never()).subscribe(any());
    }

    @Test
    void 인증되지_않은_구독은_공통형식_401() throws Exception {
        mockMvc.perform(get("/api/workspaces/1/events").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));

        verify(sseRegistry, never()).subscribe(any());
    }
}

package com.ssafy.woojuin.domain.auth.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.ai.usage.AiUsageResponse;
import com.ssafy.woojuin.domain.ai.usage.AiUsageService;
import com.ssafy.woojuin.domain.auth.service.UserProfileService;
import com.ssafy.woojuin.domain.auth.service.UserWithdrawalService;
import com.ssafy.woojuin.global.security.aop.AuthenticationAspect;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = UserController.class, excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CurrentUserResolver.class, AuthenticationAspect.class})
@EnableAspectJAutoProxy
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

    @MockBean
    private UserWithdrawalService userWithdrawalService;

    @MockBean
    private AiUsageService aiUsageService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void withdraw_authenticatedUser_returnsSuccess() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(userWithdrawalService).withdraw(1L);
    }

    @Test
    void getMyAiUsage_returnsMonthlyOwnerUsage() throws Exception {
        authenticateAs(1L);
        when(aiUsageService.getUsage(1L)).thenReturn(new AiUsageResponse(
                "2026-07", 12, 50, 38L, false, true,
                OffsetDateTime.parse("2026-08-01T00:00:00+09:00")));

        mockMvc.perform(get("/api/users/me/ai-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.used").value(12))
                .andExpect(jsonPath("$.data.limit").value(50))
                .andExpect(jsonPath("$.data.remaining").value(38))
                .andExpect(jsonPath("$.data.unlimited").value(false));

        verify(aiUsageService).getUsage(1L);
    }

    @Test
    void withdraw_unauthenticatedUser_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verify(userWithdrawalService, never()).withdraw(1L);
    }

    private void authenticateAs(Long userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}

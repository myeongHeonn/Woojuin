package com.ssafy.woojuin.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.woojuin.domain.auth.dto.LoginRequest;
import com.ssafy.woojuin.domain.auth.dto.TokenResponse;
import com.ssafy.woojuin.domain.auth.service.LoginService;
import com.ssafy.woojuin.domain.auth.service.LogoutService;
import com.ssafy.woojuin.domain.auth.service.SignupService;
import com.ssafy.woojuin.domain.auth.service.TokenRefreshService;
import com.ssafy.woojuin.global.security.aop.AuthenticationAspect;
import com.ssafy.woojuin.global.security.aop.CurrentUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CurrentUserResolver.class, AuthenticationAspect.class})
@EnableAspectJAutoProxy
class AuthControllerTest {

    private static final String LOGIN_BODY = """
            {"email":"astronaut@woojuin.com","password":"password123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SignupService signupService;

    @MockBean
    private LoginService loginService;

    @MockBean
    private TokenRefreshService tokenRefreshService;

    @MockBean
    private LogoutService logoutService;

    @Test
    void login_validCredentials_returnsTokens() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenReturn(new TokenResponse("access-1", "refresh-1"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-1"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-1"));
    }

    /**
     * LoginService 가 AuthenticationManager 를 컨트롤러 안에서 직접 부르므로 이 예외는
     * 시큐리티 필터 체인이 아니라 GlobalExceptionHandler 로 온다. 핸들러가 없으면 500 이 되고
     * 그 응답에는 CORS 헤더가 없어 브라우저가 통째로 버린다(프론트가 상태코드도 못 본다).
     */
    @Test
    void login_wrongPassword_returns401WithMessage() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
    }

    /** 가입 여부를 캐낼 수 없도록 "계정 없음"도 비밀번호 오류와 같은 문구를 쓴다. */
    @Test
    void login_unknownEmail_returnsSameMessageAsWrongPassword() throws Exception {
        when(loginService.login(any(LoginRequest.class)))
                .thenThrow(new UsernameNotFoundException("User not found: astronaut@woojuin.com"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다"));
    }

    @Test
    void login_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"password123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}

package com.ssafy.woojuin.domain.auth.jwt;

import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.session.SessionRevocationStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final SessionRevocationStore revocationStore;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository,
                                   SessionRevocationStore revocationStore) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.revocationStore = revocationStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && tokenProvider.validateToken(token)) {
            String sessionId = tokenProvider.getSessionId(token);
            /*
             * 폐기 확인이 기기 해제를 **즉시** 반영한다 — 서명만 보면 해제된 기기의 access
             * token 이 만료까지(최대 1시간) 계속 통과한다. 인증을 안 하고 지나가면 아래
             * 엔트리 포인트가 401 을 주고, 프론트는 refresh 를 시도했다가 세션이 없어
             * 400 을 받고 로그인 화면으로 간다(S15P11C105-455 의 정리 흐름).
             *
             * sessionId 가 null 인 토큰(이 구조 배포 전 발급)은 폐기를 확인할 수 없다 —
             * 남은 수명(최대 1시간)만 통과하고, refresh 는 세션 조회에서 거부된다.
             *
             * DB 확인보다 먼저 보는 이유: 해제된 기기의 요청에 Postgres 조회까지 쓸
             * 필요가 없다(Redis GET 이 더 싸다).
             */
            if (sessionId != null && revocationStore.isRevoked(sessionId)) {
                filterChain.doFilter(request, response);
                return;
            }
            Long userId = tokenProvider.getUserId(token);
            if (userRepository.existsByIdAndDeletedAtIsNull(userId)) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                // 로그아웃·기기 목록의 "이 기기" 판별이 자기 sid 를 알아야 한다 — principal 은
                // userId(Long) 그대로 두고(수십 곳이 그 타입에 기대고 있다) details 로 실어 보낸다
                authentication.setDetails(sessionId);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

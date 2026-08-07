package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.LoginRequest;
import com.ssafy.woojuin.domain.auth.dto.TokenResponse;
import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.exception.WithdrawnUserException;
import com.ssafy.woojuin.domain.auth.jwt.JwtTokenProvider;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import com.ssafy.woojuin.domain.auth.security.CustomUserPrincipal;
import com.ssafy.woojuin.domain.auth.session.UserSession;
import com.ssafy.woojuin.domain.auth.session.UserSessionStore;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserSessionStore sessionStore;
    private final UserRepository userRepository;

    public LoginService(AuthenticationManager authenticationManager, JwtTokenProvider jwtTokenProvider,
                         UserSessionStore sessionStore, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.sessionStore = sessionStore;
        this.userRepository = userRepository;
    }

    /**
     * @param userAgent 기기 목록에 보여줄 이름의 재료 — 서버가 해석하므로 클라이언트 변경이 없다
     */
    public TokenResponse login(LoginRequest request, String userAgent) {
        // 파기 이전 버전에서 탈퇴한 행을 위한 안전망이다. 지금은 User.withdraw() 가 이메일을
        // 파기하므로 탈퇴한 LOCAL 계정은 이 조회에 잡히지 않고, 그 이메일은 재가입에 다시 쓸 수
        // 있다 — 그래서 옛 비밀번호로 로그인하면 "탈퇴한 회원"이 아니라 자격 증명 실패로 끝난다.
        // 재가입을 허용하는 정책에서는 그게 맞는 결말이다(그 주소는 이제 비어 있는 주소다).
        userRepository.findByEmailAndProvider(request.email(), AuthProvider.LOCAL)
                .filter(user -> user.isWithdrawn())
                .ifPresent(user -> {
                    throw new WithdrawnUserException();
                });

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        Long userId = principal.getUserId();

        // 로그인 = 세션 "추가". 덮어쓰기가 아니므로 다른 기기의 로그인이 유지된다 — 이게
        // "폰에서 로그인하면 PC 가 풀리던" 문제(S15P11C105-459)의 수정 지점이다.
        String sid = UserSession.newSessionId();
        String refreshToken = jwtTokenProvider.createRefreshToken(userId, sid);
        sessionStore.save(userId, UserSession.start(sid, refreshToken, userAgent));

        return new TokenResponse(jwtTokenProvider.createAccessToken(userId, sid), refreshToken);
    }
}

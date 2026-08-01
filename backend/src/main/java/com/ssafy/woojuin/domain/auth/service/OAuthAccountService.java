package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.event.UserSignedUpEvent;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * provider+providerId로 기존 계정을 찾거나 없으면 새로 만든다.
 * 카카오/구글 각각의 OAuth2User 속성 파싱은 이 서비스 밖(SecurityConfig 연동 단계)에서 처리한다.
 */
@Service
public class OAuthAccountService {

    /** User.nickname 컬럼 길이와 같아야 한다(초과분은 잘라 저장). */
    private static final int MAX_NICKNAME_LENGTH = 50;

    /** provider 닉네임도 이메일도 못 쓸 때의 최후 폴백. */
    private static final String DEFAULT_NICKNAME = "우주인";

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OAuthAccountService(UserRepository userRepository, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public User findOrCreateUser(AuthProvider provider, String providerId, String email, String nickname) {
        Optional<User> existing = userRepository.findByProviderAndProviderId(provider, providerId);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isWithdrawn()) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("withdrawn_user"), "탈퇴한 계정입니다.");
            }
            return user;
        }

        User saved = userRepository.save(User.builder()
                .email(email)
                .provider(provider)
                .providerId(providerId)
                .emailVerified(true)
                .nickname(resolveNickname(nickname, email))
                .build());
        eventPublisher.publishEvent(new UserSignedUpEvent(saved.getId()));
        return saved;
    }

    /**
     * 저장 가능한 닉네임을 만든다. provider가 준 값을 그대로 쓰지 않는 이유:
     *
     * <p>구글의 name 클레임은 <b>없을 수 있다</b>(프로필 이름을 지운 계정 등). nickname 컬럼은
     * NOT NULL length=50 이라 그대로 넣으면 가입 자체가 DataIntegrityViolation으로 터진다 —
     * 로그인 첫 시도에서 500이 나고 사용자는 원인을 알 수 없다.
     *
     * <p>폴백 순서는 사용자가 자기 계정임을 알아볼 수 있는 순서다:
     * provider 닉네임 → 이메일 아이디(@ 앞) → 고정값. 마이페이지에서 언제든 바꿀 수 있으므로
     * 완벽할 필요는 없고, 빈 값·초과 길이로 실패하지 않는 것이 중요하다.
     */
    private String resolveNickname(String nickname, String email) {
        String candidate = firstNotBlank(nickname, localPartOf(email), DEFAULT_NICKNAME);
        // 이름이 아주 긴 계정(구글은 name 길이를 제한하지 않는다)도 저장은 되어야 한다.
        return candidate.length() > MAX_NICKNAME_LENGTH
                ? candidate.substring(0, MAX_NICKNAME_LENGTH)
                : candidate;
    }

    private String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return DEFAULT_NICKNAME;
    }

    /** 이메일 아이디 부분. @ 가 없거나 앞이 비면 null 을 돌려 다음 폴백으로 넘긴다. */
    private String localPartOf(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : null;
    }
}

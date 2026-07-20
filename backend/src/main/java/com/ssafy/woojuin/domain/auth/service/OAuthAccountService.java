package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * provider+providerId로 기존 계정을 찾거나 없으면 새로 만든다.
 * 카카오/구글 각각의 OAuth2User 속성 파싱은 이 서비스 밖(SecurityConfig 연동 단계)에서 처리한다.
 */
@Service
public class OAuthAccountService {

    private final UserRepository userRepository;

    public OAuthAccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User findOrCreateUser(AuthProvider provider, String providerId, String email, String nickname) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .provider(provider)
                        .providerId(providerId)
                        .emailVerified(true)
                        .nickname(nickname)
                        .build()));
    }
}

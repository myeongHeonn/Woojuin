package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.AvatarColor;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.event.UserSignedUpEvent;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OAuthAccountService oAuthAccountService;

    @Test
    @DisplayName("같은 provider+providerId 계정이 없으면 신규 계정이다")
    void isNewAccount_noExistingAccount_returnsTrue() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-new"))
                .thenReturn(Optional.empty());

        assertThat(oAuthAccountService.isNewAccount(AuthProvider.GOOGLE, "google-new")).isTrue();
    }

    @Test
    @DisplayName("같은 provider+providerId 계정이 이미 있으면 신규 계정이 아니다")
    void isNewAccount_existingAccount_returnsFalse() {
        User existing = User.builder()
                .email("test@google.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-existing")
                .emailVerified(true)
                .nickname("우주인")
                .build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-existing"))
                .thenReturn(Optional.of(existing));

        assertThat(oAuthAccountService.isNewAccount(AuthProvider.GOOGLE, "google-existing")).isFalse();
    }

    @Test
    @DisplayName("이미 같은 provider+providerId 계정이 있으면 그대로 반환하고 저장하지 않는다")
    void findOrCreateUser_existingAccount_returnsExistingUserWithoutSaving() {
        User existing = User.builder()
                .email("test@kakao.com")
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-123")
                .emailVerified(true)
                .nickname("우주인")
                .build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-123"))
                .thenReturn(Optional.of(existing));

        User result = oAuthAccountService.findOrCreateUser(AuthProvider.KAKAO, "kakao-123", "test@kakao.com", "우주인");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("탈퇴한 OAuth 계정은 다시 로그인할 수 없다")
    void findOrCreateUser_withdrawnAccount_throwsAuthenticationException() {
        User withdrawn = User.builder()
                .email("withdrawn@google.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-withdrawn")
                .emailVerified(true)
                .nickname("탈퇴 전 닉네임")
                .build();
        withdrawn.withdraw();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-withdrawn"))
                .thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> oAuthAccountService.findOrCreateUser(
                AuthProvider.GOOGLE, "google-withdrawn", "withdrawn@google.com", "닉네임"))
                .isInstanceOf(OAuth2AuthenticationException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("계정이 없으면 emailVerified=true, passwordHash=null인 OAuth 유저를 생성해 저장한다")
    void findOrCreateUser_newAccount_createsAndSavesOAuthUser() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-456"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        oAuthAccountService.findOrCreateUser(AuthProvider.GOOGLE, "google-456", "test@google.com", "우주인");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getProviderId()).isEqualTo("google-456");
        assertThat(saved.getEmail()).isEqualTo("test@google.com");
        assertThat(saved.getNickname()).isEqualTo("우주인");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.getAvatarColor()).isEqualTo(AvatarColor.WHITE);
    }

    @Test
    @DisplayName("provider가 닉네임을 안 주면 이메일 아이디로 채운다 — NOT NULL 컬럼이라 비면 가입이 깨진다")
    void findOrCreateUser_blankNickname_fallsBackToEmailLocalPart() {
        User saved = createWithNickname(null, "hong.gildong@google.com");

        assertThat(saved.getNickname()).isEqualTo("hong.gildong");
    }

    @Test
    @DisplayName("닉네임도 이메일도 못 쓰면 고정 폴백 닉네임을 쓴다")
    void findOrCreateUser_noNicknameNoEmail_usesDefaultNickname() {
        User saved = createWithNickname("   ", null);

        assertThat(saved.getNickname()).isEqualTo("우주인");
    }

    @Test
    @DisplayName("닉네임이 50자를 넘으면 잘라 저장한다 — 컬럼 length=50")
    void findOrCreateUser_tooLongNickname_isTruncated() {
        String tooLong = "가".repeat(60);

        User saved = createWithNickname(tooLong, "test@google.com");

        assertThat(saved.getNickname()).hasSize(50);
    }

    /** 신규 가입 경로를 태우고 저장된 User 를 돌려준다. */
    private User createWithNickname(String nickname, String email) {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-new"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        oAuthAccountService.findOrCreateUser(AuthProvider.GOOGLE, "google-new", email, nickname);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("신규 계정을 생성하면 UserSignedUpEvent를 발행한다")
    void findOrCreateUser_newAccount_publishesUserSignedUpEvent() {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-456"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 5L);
            return user;
        });

        oAuthAccountService.findOrCreateUser(AuthProvider.GOOGLE, "google-456", "test@google.com", "우주인");

        ArgumentCaptor<UserSignedUpEvent> captor = ArgumentCaptor.forClass(UserSignedUpEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(5L);
    }
}

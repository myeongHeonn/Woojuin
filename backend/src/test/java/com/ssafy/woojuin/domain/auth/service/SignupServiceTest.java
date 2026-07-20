package com.ssafy.woojuin.domain.auth.service;

import com.ssafy.woojuin.domain.auth.dto.SignupRequest;
import com.ssafy.woojuin.domain.auth.entity.AuthProvider;
import com.ssafy.woojuin.domain.auth.entity.User;
import com.ssafy.woojuin.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupService signupService;

    @Test
    @DisplayName("신규 이메일이면 비밀번호를 인코딩해 LOCAL 유저를 저장한다")
    void signup_newEmail_savesEncodedLocalUser() {
        signupService = new SignupService(userRepository, passwordEncoder);
        SignupRequest request = new SignupRequest("test@woojuin.com", "raw-password", "우주인");

        when(userRepository.findByEmailAndProvider("test@woojuin.com", AuthProvider.LOCAL))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("raw-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        signupService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("test@woojuin.com");
        assertThat(saved.getNickname()).isEqualTo("우주인");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 예외를 던지고 저장하지 않는다")
    void signup_duplicateEmail_throwsAndDoesNotSave() {
        signupService = new SignupService(userRepository, passwordEncoder);
        SignupRequest request = new SignupRequest("test@woojuin.com", "raw-password", "우주인");
        User existing = User.builder()
                .email("test@woojuin.com")
                .passwordHash("already-encoded")
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .nickname("기존유저")
                .build();

        when(userRepository.findByEmailAndProvider("test@woojuin.com", AuthProvider.LOCAL))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
    }
}

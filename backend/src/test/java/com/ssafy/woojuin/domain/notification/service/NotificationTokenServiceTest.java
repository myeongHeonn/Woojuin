package com.ssafy.woojuin.domain.notification.service;

import com.ssafy.woojuin.domain.notification.dto.DeleteTokenRequest;
import com.ssafy.woojuin.domain.notification.dto.RegisterTokenRequest;
import com.ssafy.woojuin.domain.notification.entity.NotificationToken;
import com.ssafy.woojuin.domain.notification.exception.NotificationTokenNotFoundException;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationTokenServiceTest {

    @Mock
    private NotificationTokenRepository notificationTokenRepository;

    @InjectMocks
    private NotificationTokenService notificationTokenService;

    @Test
    @DisplayName("등록: 처음 보는 토큰이면 새로 저장한다")
    void register_newToken_saves() {
        RegisterTokenRequest request = new RegisterTokenRequest("fcm-token-1", "chrome-mac");
        when(notificationTokenRepository.findByToken("fcm-token-1")).thenReturn(Optional.empty());

        notificationTokenService.register(1L, request);

        ArgumentCaptor<NotificationToken> captor = ArgumentCaptor.forClass(NotificationToken.class);
        verify(notificationTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getToken()).isEqualTo("fcm-token-1");
        assertThat(captor.getValue().getDeviceInfo()).isEqualTo("chrome-mac");
    }

    @Test
    @DisplayName("등록: 이미 있는 토큰이면 새로 만들지 않고 소유자만 갱신한다")
    void register_existingToken_reassignsOwner() {
        NotificationToken existing = NotificationToken.builder()
                .userId(2L).token("fcm-token-1").deviceInfo("old-device").build();
        RegisterTokenRequest request = new RegisterTokenRequest("fcm-token-1", "new-device");
        when(notificationTokenRepository.findByToken("fcm-token-1")).thenReturn(Optional.of(existing));

        notificationTokenService.register(1L, request);

        assertThat(existing.getUserId()).isEqualTo(1L);
        assertThat(existing.getDeviceInfo()).isEqualTo("new-device");
        verify(notificationTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제: 본인 토큰이면 삭제한다")
    void delete_ownToken_deletes() {
        NotificationToken existing = NotificationToken.builder()
                .userId(1L).token("fcm-token-1").deviceInfo("chrome-mac").build();
        when(notificationTokenRepository.findByToken("fcm-token-1")).thenReturn(Optional.of(existing));

        notificationTokenService.delete(1L, new DeleteTokenRequest("fcm-token-1"));

        verify(notificationTokenRepository, times(1)).delete(existing);
    }

    @Test
    @DisplayName("삭제: 존재하지 않는 토큰이면 예외")
    void delete_missingToken_throws() {
        when(notificationTokenRepository.findByToken("no-such-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationTokenService.delete(1L, new DeleteTokenRequest("no-such-token")))
                .isInstanceOf(NotificationTokenNotFoundException.class);
    }

    @Test
    @DisplayName("삭제: 다른 사람 토큰이면 예외 (소유권 없이 삭제 불가)")
    void delete_otherUsersToken_throws() {
        NotificationToken existing = NotificationToken.builder()
                .userId(2L).token("fcm-token-1").deviceInfo("chrome-mac").build();
        when(notificationTokenRepository.findByToken("fcm-token-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> notificationTokenService.delete(1L, new DeleteTokenRequest("fcm-token-1")))
                .isInstanceOf(NotificationTokenNotFoundException.class);
        verify(notificationTokenRepository, never()).delete(any());
    }
}

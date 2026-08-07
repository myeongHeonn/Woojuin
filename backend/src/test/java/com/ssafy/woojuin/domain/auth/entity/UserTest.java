package com.ssafy.woojuin.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserTest {

    private User user() {
        return User.builder()
                .email("user@example.com")
                .passwordHash("encoded-password")
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .nickname("우주인")
                .build();
    }

    @Test
    void newUserIsActive() {
        User user = user();

        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void withdrawRecordsTheFirstWithdrawalTime() {
        User user = user();

        user.withdraw();
        OffsetDateTime firstWithdrawalTime = user.getDeletedAt();
        user.withdraw();

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(firstWithdrawalTime).isNotNull();
        assertThat(user.getDeletedAt()).isEqualTo(firstWithdrawalTime);
    }

    @Test
    @DisplayName("탈퇴하면 provider_id 를 파기한다 — 같은 구글 계정이 다시 가입할 수 있어야 한다")
    void withdrawClearsProviderId() {
        User user = googleUser(7L);

        user.withdraw();

        // uk_users_provider UNIQUE (provider, provider_id) 가 sub 를 붙들고 있으면 재가입이
        // 영구히 막힌다. NULL 은 서로 다른 값으로 취급되므로 탈퇴자가 여럿이어도 충돌하지 않는다.
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("탈퇴하면 이메일을 실제 주소가 될 수 없는 값으로 파기한다")
    void withdrawScrubsEmail() {
        User user = googleUser(7L);

        user.withdraw();

        // .invalid 는 RFC 2606 예약 TLD — 어딘가로 새어도 남의 메일함에 닿지 않는다.
        assertThat(user.getEmail()).isEqualTo("withdrawn+7@woojuin.invalid");
    }

    @Test
    @DisplayName("닉네임은 남긴다 — 로그인 식별자가 아니고 멤버 활동 피드가 쓴다")
    void withdrawKeepsNickname() {
        User user = googleUser(7L);

        user.withdraw();

        assertThat(user.getNickname()).isEqualTo("우주인");
    }

    @Test
    @DisplayName("두 번째 탈퇴 호출은 이미 파기한 이메일을 덮어쓰지 않는다")
    void withdrawTwiceKeepsTheFirstPlaceholder() {
        User user = googleUser(7L);

        user.withdraw();
        String firstPlaceholder = user.getEmail();
        user.withdraw();

        assertThat(user.getEmail()).isEqualTo(firstPlaceholder);
    }

    private User googleUser(Long id) {
        User user = User.builder()
                .email("real@gmail.com")
                .provider(AuthProvider.GOOGLE)
                .providerId("google-sub-abc")
                .emailVerified(true)
                .nickname("우주인")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

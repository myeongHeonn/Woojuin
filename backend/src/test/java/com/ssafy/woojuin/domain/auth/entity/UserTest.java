package com.ssafy.woojuin.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

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
}

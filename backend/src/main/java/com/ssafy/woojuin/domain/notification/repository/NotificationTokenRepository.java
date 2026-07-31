package com.ssafy.woojuin.domain.notification.repository;

import com.ssafy.woojuin.domain.notification.entity.NotificationToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {

    Optional<NotificationToken> findByToken(String token);

    List<NotificationToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

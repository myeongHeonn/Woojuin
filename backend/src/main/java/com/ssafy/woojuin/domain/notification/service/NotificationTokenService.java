package com.ssafy.woojuin.domain.notification.service;

import com.ssafy.woojuin.domain.notification.dto.DeleteTokenRequest;
import com.ssafy.woojuin.domain.notification.dto.RegisterTokenRequest;
import com.ssafy.woojuin.domain.notification.entity.NotificationToken;
import com.ssafy.woojuin.domain.notification.exception.NotificationTokenNotFoundException;
import com.ssafy.woojuin.domain.notification.repository.NotificationTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTokenService {

    private final NotificationTokenRepository notificationTokenRepository;

    public NotificationTokenService(NotificationTokenRepository notificationTokenRepository) {
        this.notificationTokenRepository = notificationTokenRepository;
    }

    @Transactional
    public void register(Long userId, RegisterTokenRequest request) {
        notificationTokenRepository.findByToken(request.token())
                .ifPresentOrElse(
                        existing -> existing.reassignTo(userId, request.deviceInfo()),
                        () -> notificationTokenRepository.save(NotificationToken.builder()
                                .userId(userId)
                                .token(request.token())
                                .deviceInfo(request.deviceInfo())
                                .build()));
    }

    @Transactional
    public void delete(Long userId, DeleteTokenRequest request) {
        NotificationToken token = notificationTokenRepository.findByToken(request.token())
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new NotificationTokenNotFoundException(request.token()));
        notificationTokenRepository.delete(token);
    }
}

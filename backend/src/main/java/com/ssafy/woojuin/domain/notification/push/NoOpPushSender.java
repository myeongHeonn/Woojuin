package com.ssafy.woojuin.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Firebase 프로젝트가 아직 없어 로그만 남기고 실제 발송은 건너뛴다.
 * FCM 구현체가 생기면 이 빈은 {@code @ConditionalOnMissingBean(PushSender.class)}로
 * 전환해 실제 구현이 있을 때는 물러나게 한다(AiAnalyzerConfig와 동일한 패턴).
 */
@Slf4j
@Component
public class NoOpPushSender implements PushSender {

    @Override
    public void send(String token, String title, String body) {
        log.info("푸시 발송 스텁(NoOp) — 실제 발송 생략: token={}, title={}", token, title);
    }
}

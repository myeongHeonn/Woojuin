package com.ssafy.woojuin.domain.notification.push;

import lombok.extern.slf4j.Slf4j;

/**
 * FCM 서비스 계정 키가 없을 때 쓰는 폴백 — 로그만 남기고 실제 발송은 건너뛴다.
 * 어떤 구현을 빈으로 올릴지는 {@link PushSenderConfig}가 자격증명 유무로 결정한다
 * (AiQueryConfig와 동일한 패턴 — 팀원 파트 부재가 아니라 자격증명 유무로 갈리므로
 * ImageTextExtractorConfig류의 @ConditionalOnMissingBean과는 다르다).
 */
@Slf4j
public class NoOpPushSender implements PushSender {

    @Override
    public void send(String token, String title, String body) {
        log.info("푸시 발송 스텁(NoOp) — 실제 발송 생략: token={}, title={}", token, title);
    }
}

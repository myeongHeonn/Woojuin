package com.ssafy.woojuin.domain.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스 계정 키가 설정돼 있을 때만 실제 FCM 발송기를 올린다. 키가 없으면 NoOp이 쓰여
 * 발송이 조용히 생략된다(AiQueryConfig와 동일한 "키 없어도 로컬에서 뜬다" 원칙).
 *
 * <p>키는 base64 문자열 하나로 받는다 — 원본 JSON은 private_key에 실제 개행이 들어 있어
 * .env(dotenv, key=value 한 줄 형식)에 그대로 넣을 수 없다.
 */
@Slf4j
@Configuration
public class PushSenderConfig {

    @Bean
    public PushSender pushSender(@Value("${FCM_SERVICE_ACCOUNT_KEY_BASE64:}") String serviceAccountKeyBase64) {
        String key = serviceAccountKeyBase64 == null ? "" : serviceAccountKeyBase64.trim();
        if (key.isBlank()) {
            log.info("FCM 푸시 발송: 서비스 계정 키가 없어 NoOp으로 동작합니다 "
                    + "(.env에 FCM_SERVICE_ACCOUNT_KEY_BASE64를 넣으면 실제 발송이 활성화됩니다)");
            return new NoOpPushSender();
        }

        try {
            byte[] jsonBytes = Base64.getDecoder().decode(key);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(jsonBytes));
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            log.info("FCM 푸시 발송: 활성화");
            return new FirebasePushSender(FirebaseMessaging.getInstance(app));
        } catch (Exception e) {
            log.warn("FCM 서비스 계정 키 로드 실패, NoOp으로 폴백합니다: cause={}", e.getMessage());
            return new NoOpPushSender();
        }
    }
}

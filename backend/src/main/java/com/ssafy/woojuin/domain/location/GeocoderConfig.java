package com.ssafy.woojuin.domain.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * REST 키가 있을 때만 카카오 지오코더를 올린다. 키가 없으면 {@link NoOpGeocoder}가 쓰여
 * 주소→좌표 변환만 꺼지고, 지도 공유 링크 파싱과 사진 EXIF는 그대로 동작한다 — 키 없는
 * 로컬 환경에서도 앱이 뜨고 지도에 핀이 찍히게 하려는 것이다.
 */
@Slf4j
@Configuration
public class GeocoderConfig {

    /**
     * 카카오 로컬 API는 <b>REST API 키</b>를 쓴다 — OAuth 로그인의
     * {@code KAKAO_OAUTH_CLIENT_ID}(JavaScript 키)와 같은 앱의 다른 키다. 헷갈리기 쉬워
     * 변수 이름을 따로 뒀다.
     *
     * <p>키를 application.yml의 {@code ${VAR:기본값}}으로 받지 않고 여기서 직접 읽는 이유는
     * {@code AiQueryConfig}가 이미 기록한 함정과 같다 — yml 기본값은 변수가 "없을 때"만
     * 적용돼서 {@code KAKAO_REST_API_KEY=} 같은 빈 줄을 "키 있음"으로 오해한다. blank까지 본다.
     */
    @Bean
    @ConditionalOnMissingBean(Geocoder.class)
    public Geocoder geocoder(ObjectMapper objectMapper,
            @Value("${KAKAO_REST_API_KEY:}") String restApiKey,
            @Value("${woojuin.geo.base-url}") String baseUrl,
            @Value("${woojuin.geo.timeout-ms}") long timeoutMs) {

        String key = restApiKey == null ? "" : restApiKey.trim();
        if (key.isBlank()) {
            log.info("지오코딩 비활성(NoOp) — KAKAO_REST_API_KEY가 없어 주소↔좌표 변환을 건너뜁니다. "
                    + "지도 공유 링크·사진 EXIF에서 직접 읽은 좌표는 그대로 저장됩니다");
            return new NoOpGeocoder();
        }

        log.info("지오코딩 활성 (카카오 로컬 API, baseUrl={}, timeoutMs={})", baseUrl, timeoutMs);
        return new KakaoLocalGeocoder(
                new KakaoLocalClient(baseUrl, key, Duration.ofMillis(timeoutMs), objectMapper));
    }
}

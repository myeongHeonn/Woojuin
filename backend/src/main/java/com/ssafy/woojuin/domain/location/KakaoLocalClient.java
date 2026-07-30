package com.ssafy.woojuin.domain.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 API 얇은 HTTP 래퍼. 응답의 {@code documents} 배열만 꺼내주고, 필드 해석은
 * {@link KakaoLocalGeocoder}가 한다.
 *
 * <p>{@code /v2/local/...} 은 카카오가 발행하는 실제 경로다 — 버전을 고른 게 아니라 이 API가
 * 그 아래에 서비스된다. base-url만 설정으로 뺀 이유는 버전 대응이 아니라 테스트에서 목 서버를
 * 가리키게 하려는 것이고, 카카오가 v3를 내면 응답 형태도 바뀌므로 그때는 {@link Geocoder}
 * 구현체를 새로 붙이는 게 맞다.
 *
 * <p><b>SSRF 아님</b>: 호스트와 경로가 우리가 고정한 신뢰 엔드포인트이고 사용자 문자열은
 * 인코딩된 쿼리 파라미터로만 전달된다 — {@code OEmbedClient}와 같은 구조다. 단
 * {@code uriBuilder.queryParam}으로만 조립한다. 문자열 접합을 쓰면 사용자 주소에 섞인
 * {@code &}·{@code #}·개행이 파라미터 주입이나 헤더 분리로 이어진다.
 *
 * <p>{@link ChatCompletionClient}에서 두 가지를 그대로 가져왔다 — 응답을 {@code String}으로
 * 받아 직접 파싱하는 것(Content-Type에 휘둘리지 않게)과, connect·read 양쪽 타임아웃이다.
 */
public class KakaoLocalClient {

    /** 사용자 콘텐츠에서 뽑은 문자열이 그대로 나가므로 길이를 제한한다(쿼터 위생 + 방어). */
    private static final int MAX_QUERY_LENGTH = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KakaoLocalClient(String baseUrl, String restApiKey, Duration timeout,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // 카카오 로컬 API는 REST API 키를 "KakaoAK " 접두사와 함께 보낸다.
                // OAuth 로그인에 쓰는 KAKAO_OAUTH_CLIENT_ID(JavaScript 키)와 다른 키다.
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + restApiKey)
                .requestFactory(new RestTemplateBuilder()
                        .setConnectTimeout(timeout)
                        .setReadTimeout(timeout)
                        .buildRequestFactory())
                .build();
    }

    /**
     * @return {@code documents} 배열. 응답이 비었거나 documents가 없으면 null.
     * @throws RuntimeException 네트워크·인증·서버·파싱 오류. 호출부가 흡수를 책임진다.
     */
    public JsonNode documents(String path, Map<String, String> params) throws Exception {
        String raw = restClient.get()
                .uri(builder -> {
                    builder.path(path);
                    params.forEach((key, value) -> builder.queryParam(key, sanitize(value)));
                    return builder.build();
                })
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        if (raw == null || raw.isBlank()) {
            return null;
        }
        JsonNode documents = objectMapper.readTree(raw).path("documents");
        return documents.isArray() ? documents : null;
    }

    /** 개행·제어문자를 걷어내고 길이를 자른다. */
    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        return cleaned.length() > MAX_QUERY_LENGTH ? cleaned.substring(0, MAX_QUERY_LENGTH) : cleaned;
    }
}

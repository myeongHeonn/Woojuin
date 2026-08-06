package com.ssafy.woojuin.domain.ai.music;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 노래 인식 사이드카({@code ai/ai-music}) 전송 담당. 해석은
 * {@link SidecarMusicRecognizer} 가 한다 — {@code WhisperClient}/{@code WhisperTranscriber} 와
 * 같은 분리이고, 이유도 같다(HTTP 를 떼면 응답 해석을 목 없이 테스트할 수 있다).
 *
 * <p><b>multipart 본문을 직접 만든다.</b> {@code WhisperClient} 에서 겪은 것과 같은 문제를
 * 피하려는 것이다 — Spring 폼 컨버터에 맡겼을 때 프록시가 본문을 다르게 해석해 400 을
 * 돌려줬고 원인을 짚기 어려웠다. 여기서는 상대가 우리 사이드카라 덜 까다롭겠지만, 같은
 * 계층에 두 가지 방식을 섞을 이유가 없다.
 */
public class SidecarMusicClient {

    private final RestClient restClient;

    public SidecarMusicClient(String baseUrl, Duration timeout) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory(timeout))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        // 업로드를 버퍼링해 Content-Length 를 붙인다(chunked 를 싫어하는 중간 장비 대비)
        factory.setOutputStreaming(false);
        return factory;
    }

    /**
     * @return 사이드카 응답 본문 원문(JSON 문자열)
     * @throws RuntimeException 네트워크·상태코드 오류. 호출부가 의미를 붙인다
     */
    public String recognizeRaw(byte[] audio, String filename) {
        String boundary = "woojuin" + UUID.randomUUID().toString().replace("-", "");
        return restClient.post()
                .uri("/recognize")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .body(multipartBody(boundary, audio, filename))
                .retrieve()
                .body(String.class);
    }

    /** curl 의 {@code -F} 와 같은 본문. 줄바꿈은 CRLF 다. */
    byte[] multipartBody(String boundary, byte[] audio, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "--" + boundary + "\r\n");
        writeUtf8(out,
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        writeAscii(out, "Content-Type: audio/wav\r\n\r\n");
        write(out, audio);
        writeAscii(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String text) {
        write(out, text.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeUtf8(ByteArrayOutputStream out, String text) {
        write(out, text.getBytes(StandardCharsets.UTF_8));
    }

    private void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }
}

package com.ssafy.woojuin.domain.ai.speech;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * OpenAI 호환 {@code /audio/transcriptions} 전송만 담당한다 — 해석은
 * {@link WhisperTranscriber} 가 한다({@code KakaoLocalClient}/{@code KakaoLocalGeocoder} 와 같은
 * 분리다. HTTP 를 떼어내면 응답 해석을 목 없이 테스트할 수 있다).
 *
 * <p>{@link com.ssafy.woojuin.domain.ai.query.ChatCompletionClient} 와 같은 base-url·키를 쓴다
 * (팀의 SSAFY GMS 프록시, {@code OPENAI_API_KEY}). 라이브로 확인한 것: {@code whisper-1} 은
 * 이 프록시로 동작하고(4초 오디오에 1.5초), {@code gpt-4o-transcribe} 계열은 프록시에 없다.
 * 실시간(웹소켓) 경로도 중계하지 않아 <b>파일 하나를 한 번에 올리는 방식만 가능하다</b>.
 *
 * <p><b>multipart 본문을 손으로 만든다.</b> Spring 의 폼 컨버터에 맡겼을 때 프록시가
 * cloudflare 의 HTML 400 을 돌려줬다 — 같은 WAV 를 curl 로 보내면 200 이라 우리가 만든
 * 바이트에 문제가 있었다는 뜻인데, 응답 본문이 OpenAI 오류처럼 보여 원인을 짚기 어려웠다.
 * 프록시가 multipart 를 다시 조립해 OpenAI 로 넘기는 구조라 사소한 차이(파트 헤더·charset)에
 * 민감하다. 그래서 curl 이 보내는 것과 같은 본문을 직접 만든다 — 무엇을 보내는지 테스트로
 * 고정할 수 있고, 프록시가 까다로워도 다시 헤맬 일이 없다.
 */
public class WhisperClient {

    private final RestClient restClient;
    private final String model;

    public WhisperClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory(timeout))
                .build();
    }

    /**
     * <b>업로드를 버퍼링해 {@code Content-Length} 를 붙인다.</b> 스트리밍으로 보내면
     * {@code Transfer-Encoding: chunked} 가 되는데, 프록시·CDN 조합이 그것을 싫어한다.
     */
    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        factory.setOutputStreaming(false);
        return factory;
    }

    /**
     * @param filename 확장자로 형식을 판별하므로 이름이 필요하다(예 {@code speech.wav})
     * @return 응답 본문 원문(JSON 문자열)
     * @throws RuntimeException 네트워크·인증·서버 오류. 호출부가 의미를 붙인다
     */
    public String transcribeRaw(byte[] audio, String filename) {
        String boundary = "woojuin" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, audio, filename);

        // 응답을 String 으로 받는다 — GMS 프록시가 JSON 을 octet-stream 으로 내려보내는
        // 경우가 있어(ChatCompletionClient 주석과 같은 이유) 타입 지정 역직렬화는
        // "no suitable HttpMessageConverter" 로 간헐 실패한다
        return restClient.post()
                .uri("/audio/transcriptions")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /**
     * curl 의 {@code -F} 가 만드는 것과 같은 본문. 줄바꿈은 반드시 CRLF 다 — LF 로 보내면
     * 파트 경계를 못 찾는 서버가 있다.
     *
     * <p>언어를 {@code ko} 로 못 박는다: 자동 감지에 맡기면 짧은 한마디("온화정")를 다른
     * 언어로 잡아 엉뚱한 문자로 받아쓴다.
     */
    byte[] multipartBody(String boundary, byte[] audio, String filename) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "--" + boundary + "\r\n");
        writeUtf8(out,
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
        writeAscii(out, "Content-Type: audio/wav\r\n\r\n");
        write(out, audio);
        writeAscii(out, "\r\n");
        writeField(out, boundary, "model", model);
        writeField(out, boundary, "language", "ko");
        writeAscii(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private void writeField(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeUtf8(out, value);
        writeAscii(out, "\r\n");
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

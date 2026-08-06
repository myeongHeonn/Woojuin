package com.ssafy.woojuin.domain.ai.speech;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * multipart 본문의 모양을 고정한다. <b>이 모양이 어긋나면 프록시가 cloudflare 의 HTML 400 을
 * 돌려주고, 응답만 봐서는 OpenAI 오류처럼 보여 원인을 찾기 어렵다</b> — 실제로 한 번 겪었다.
 * Spring 폼 컨버터에 맡기지 않고 직접 만드는 이유이자, 그 결정을 지키는 테스트다.
 */
class WhisperClientTest {

    private final WhisperClient client = new WhisperClient(
            "http://localhost", "key", "whisper-1", Duration.ofSeconds(1));

    private String bodyOf(byte[] audio, String filename) {
        // 오디오는 ISO-8859-1 로 읽으면 바이트가 1:1 로 보존돼 문자열 검사가 가능하다
        return new String(client.multipartBody("BOUND", audio, filename),
                StandardCharsets.ISO_8859_1);
    }

    @Test
    void 파일_파트에_이름과_오디오_타입이_있다() {
        String body = bodyOf(new byte[] {1, 2, 3}, "speech.wav");

        assertThat(body).contains("Content-Disposition: form-data; name=\"file\"; "
                + "filename=\"speech.wav\"");
        assertThat(body).contains("Content-Type: audio/wav");
    }

    /** 확장자가 없으면 형식을 판별하지 못해 400 이 온다 — 호출부가 넘긴 이름을 그대로 쓴다. */
    @Test
    void 넘긴_파일이름을_그대로_쓴다() {
        assertThat(bodyOf(new byte[] {1}, "voice-42.wav")).contains("filename=\"voice-42.wav\"");
    }

    @Test
    void 모델과_한국어_파트를_함께_보낸다() {
        String body = bodyOf(new byte[] {1}, "speech.wav");

        assertThat(body).contains("name=\"model\"\r\n\r\nwhisper-1");
        assertThat(body).contains("name=\"language\"\r\n\r\nko");
    }

    /** LF 로 보내면 파트 경계를 못 찾는 서버가 있다. */
    @Test
    void 줄바꿈은_CRLF_다() {
        String body = bodyOf(new byte[] {1}, "speech.wav");

        assertThat(body).startsWith("--BOUND\r\n");
        assertThat(body).endsWith("--BOUND--\r\n");
        assertThat(body.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void 오디오_바이트가_그대로_실린다() {
        byte[] audio = {0x52, 0x49, 0x46, 0x46, 0x00, 0x7F};

        String body = bodyOf(audio, "speech.wav");

        assertThat(body).contains(new String(audio, StandardCharsets.ISO_8859_1));
    }

    /** 경계 문자열은 요청마다 달라야 한다 — 오디오에 우연히 같은 바이트열이 있으면 깨진다. */
    @Test
    void 경계는_요청마다_다르다() {
        byte[] first = client.multipartBody("A", new byte[] {1}, "s.wav");
        byte[] second = client.multipartBody("B", new byte[] {1}, "s.wav");

        assertThat(first).isNotEqualTo(second);
    }
}

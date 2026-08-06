package com.ssafy.woojuin.domain.ai.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 받아쓰기 응답 해석 단위 테스트. HTTP 는 {@link WhisperClient} 를 목으로 두고 응답
 * 문자열만 넣는다 (KakaoLocalGeocoderTest·LlmQueryPlannerTest 와 같은 방식).
 *
 * <p>가장 중요한 검증은 <b>무음과 실패가 갈린다</b>는 것이다. 이걸 뭉개면 워치가 "들린
 * 내용이 없어요"만 반복 표시하고 진짜 원인(키·프록시)은 아무 데도 드러나지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class WhisperTranscriberTest {

    @Mock
    private WhisperClient client;

    private WhisperTranscriber transcriber;

    @BeforeEach
    void setUp() {
        transcriber = new WhisperTranscriber(client, new ObjectMapper());
    }

    @Test
    void 받아쓴_문장을_돌려준다() {
        when(client.transcribeRaw(any(), anyString()))
                .thenReturn("{\"text\":\"성수동 파스타집 온화정 다음주에 가보기\"}");

        assertThat(transcriber.transcribe(new byte[] {1, 2, 3}, "speech.wav"))
                .isEqualTo("성수동 파스타집 온화정 다음주에 가보기");
    }

    @Test
    void 앞뒤_공백은_다듬는다() {
        when(client.transcribeRaw(any(), anyString())).thenReturn("{\"text\":\"  온화정 \\n\"}");

        assertThat(transcriber.transcribe(new byte[] {1}, "speech.wav")).isEqualTo("온화정");
    }

    /** 무음은 실패가 아니다 — 화면은 "들린 내용이 없어요"를 보여줘야 한다. */
    @Test
    void 무음이면_빈_문자열이다() {
        when(client.transcribeRaw(any(), anyString())).thenReturn("{\"text\":\"\"}");

        assertThat(transcriber.transcribe(new byte[] {1}, "speech.wav")).isEmpty();
    }

    @Test
    void text_필드가_없어도_무음으로_본다() {
        when(client.transcribeRaw(any(), anyString())).thenReturn("{\"usage\":{\"seconds\":4}}");

        assertThat(transcriber.transcribe(new byte[] {1}, "speech.wav")).isEmpty();
    }

    @Test
    void 호출이_실패하면_예외로_올린다() {
        when(client.transcribeRaw(any(), anyString()))
                .thenThrow(new RuntimeException("503 from proxy"));

        assertThatThrownBy(() -> transcriber.transcribe(new byte[] {1}, "speech.wav"))
                .isInstanceOf(TranscriptionFailedException.class);
    }

    @Test
    void 응답이_JSON_이_아니면_예외로_올린다() {
        when(client.transcribeRaw(any(), anyString())).thenReturn("<html>Bad Gateway</html>");

        assertThatThrownBy(() -> transcriber.transcribe(new byte[] {1}, "speech.wav"))
                .isInstanceOf(TranscriptionFailedException.class);
    }

    @Test
    void 응답이_비면_예외로_올린다() {
        when(client.transcribeRaw(any(), anyString())).thenReturn("");

        assertThatThrownBy(() -> transcriber.transcribe(new byte[] {1}, "speech.wav"))
                .isInstanceOf(TranscriptionFailedException.class);
    }

    /** 빈 오디오로 쿼터를 태우지 않는다. */
    @Test
    void 오디오가_비면_호출하지_않는다() {
        assertThatThrownBy(() -> transcriber.transcribe(new byte[0], "speech.wav"))
                .isInstanceOf(TranscriptionFailedException.class);

        verifyNoInteractions(client);
    }

    @Test
    void 파일이름을_그대로_전달한다() {
        when(client.transcribeRaw(any(), eq("speech.wav"))).thenReturn("{\"text\":\"안녕\"}");

        assertThat(transcriber.transcribe(new byte[] {1}, "speech.wav")).isEqualTo("안녕");
    }

    /** 키가 없을 때는 무음이 아니라 오류다 — 원인이 드러나야 고칠 수 있다. */
    @Test
    void 키가_없으면_NoOp_이_실패로_올린다() {
        assertThatThrownBy(() -> new NoOpSpeechTranscriber().transcribe(new byte[] {1}, "a.wav"))
                .isInstanceOf(TranscriptionFailedException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}

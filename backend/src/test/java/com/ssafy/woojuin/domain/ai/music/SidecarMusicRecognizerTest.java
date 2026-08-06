package com.ssafy.woojuin.domain.ai.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.woojuin.domain.ai.music.MusicRecognizer.RecognizedSong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사이드카 응답 해석 단위 테스트. HTTP 는 {@link SidecarMusicClient} 를 목으로 둔다
 * (WhisperTranscriberTest·KakaoLocalGeocoderTest 와 같은 방식).
 *
 * <p>가장 중요한 검증은 <b>못 찾음과 실패가 갈린다</b>는 것이다. 못 찾음을 실패로 올리면
 * 조용한 방에서 녹음했을 때 "잠시 후 다시"가 뜨고, 실패를 못 찾음으로 뭉개면 사이드카가
 * 죽었는데도 사용자는 노래를 계속 들려주게 된다.
 */
@ExtendWith(MockitoExtension.class)
class SidecarMusicRecognizerTest {

    @Mock
    private SidecarMusicClient client;

    private SidecarMusicRecognizer recognizer;

    @BeforeEach
    void setUp() {
        recognizer = new SidecarMusicRecognizer(client, new ObjectMapper());
    }

    private static final String FOUND = """
            {"status":200,"message":"success","data":{
              "found":true,
              "title":"Butter-Fly",
              "artist":"전영호",
              "link":"https://www.shazam.com/track/620890971/butter-fly",
              "coverUrl":"https://is1-ssl.mzstatic.com/image/cover.jpg",
              "isrc":"KSA012163981"}}
            """;

    @Test
    void 찾은_곡을_돌려준다() {
        when(client.recognizeRaw(any(), anyString())).thenReturn(FOUND);

        Optional<RecognizedSong> song = recognizer.recognize(new byte[] {1, 2, 3}, "clip.wav");

        assertThat(song).isPresent();
        assertThat(song.get().title()).isEqualTo("Butter-Fly");
        assertThat(song.get().artist()).isEqualTo("전영호");
        assertThat(song.get().link()).isEqualTo("https://www.shazam.com/track/620890971/butter-fly");
        assertThat(song.get().coverUrl()).isEqualTo("https://is1-ssl.mzstatic.com/image/cover.jpg");
    }

    /** 못 찾은 것은 오류가 아니다 — 화면은 "노래를 찾지 못했어요"를 보여준다. */
    @Test
    void found_이_거짓이면_빈_결과다() {
        when(client.recognizeRaw(any(), anyString()))
                .thenReturn("{\"status\":200,\"data\":{\"found\":false}}");

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).isEmpty();
    }

    /** 링크가 없으면 URL 아이템으로 저장할 수 없다 — 찾은 것으로 보지 않는다. */
    @Test
    void 링크가_없으면_빈_결과다() {
        when(client.recognizeRaw(any(), anyString())).thenReturn(
                "{\"status\":200,\"data\":{\"found\":true,\"title\":\"곡\",\"artist\":\"가수\"}}");

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).isEmpty();
    }

    @Test
    void 커버가_없어도_찾은_것이다() {
        when(client.recognizeRaw(any(), anyString())).thenReturn(
                "{\"status\":200,\"data\":{\"found\":true,\"title\":\"곡\",\"artist\":\"가수\","
                        + "\"link\":\"https://www.shazam.com/track/1/x\"}}");

        Optional<RecognizedSong> song = recognizer.recognize(new byte[] {1}, "clip.wav");

        assertThat(song).isPresent();
        assertThat(song.get().coverUrl()).isNull();
    }

    @Test
    void 호출이_실패하면_예외로_올린다() {
        when(client.recognizeRaw(any(), anyString()))
                .thenThrow(new RuntimeException("502 from sidecar"));

        assertThatThrownBy(() -> recognizer.recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class);
    }

    @Test
    void 응답이_JSON_이_아니면_예외로_올린다() {
        when(client.recognizeRaw(any(), anyString())).thenReturn("<html>Bad Gateway</html>");

        assertThatThrownBy(() -> recognizer.recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class);
    }

    @Test
    void 응답이_비면_예외로_올린다() {
        when(client.recognizeRaw(any(), anyString())).thenReturn("");

        assertThatThrownBy(() -> recognizer.recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class);
    }

    /** 빈 오디오로 사이드카를 부르지 않는다. */
    @Test
    void 오디오가_비면_호출하지_않는다() {
        assertThatThrownBy(() -> recognizer.recognize(new byte[0], "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class);

        verifyNoInteractions(client);
    }

    @Test
    void 설정이_없으면_NoOp_이_실패로_올린다() {
        assertThatThrownBy(() -> new NoOpMusicRecognizer().recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class)
                .hasMessageContaining("설정되지 않았습니다");
    }
}

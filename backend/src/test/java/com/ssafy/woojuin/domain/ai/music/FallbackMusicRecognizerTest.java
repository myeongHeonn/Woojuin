package com.ssafy.woojuin.domain.ai.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ssafy.woojuin.domain.ai.music.MusicRecognizer.RecognizedSong;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 폴백 규칙을 고정한다. <b>"못 찾음"에 폴백하지 않는다</b>는 것이 가장 중요한 검증이다 —
 * 예비 경로(AudD)는 무료 300건 뒤 초과분이 자동 과금되므로, 조용한 방에서 녹음한 무음이
 * 매번 두 곳에 올라가면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class FallbackMusicRecognizerTest {

    @Mock
    private MusicRecognizer primary;

    @Mock
    private MusicRecognizer secondary;

    private FallbackMusicRecognizer recognizer;

    private static final RecognizedSong SONG =
            new RecognizedSong("Butter-Fly", "전영호", "https://example.test/track", null);
    private static final RecognizedSong BACKUP =
            new RecognizedSong("Butter-Fly", "전영호", "https://audd.test/track", null);

    @BeforeEach
    void setUp() {
        recognizer = new FallbackMusicRecognizer(primary, secondary);
    }

    @Test
    void 기본_경로가_찾으면_예비를_부르지_않는다() {
        when(primary.recognize(any(), anyString())).thenReturn(Optional.of(SONG));

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).contains(SONG);

        verifyNoInteractions(secondary);
    }

    /** 이 테스트가 이 클래스의 존재 이유다 — 과금이 걸려 있다. */
    @Test
    void 기본_경로가_못_찾으면_예비를_부르지_않는다() {
        when(primary.recognize(any(), anyString())).thenReturn(Optional.empty());

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).isEmpty();

        verifyNoInteractions(secondary);
    }

    @Test
    void 기본_경로가_실패하면_예비로_넘어간다() {
        when(primary.recognize(any(), anyString()))
                .thenThrow(new MusicRecognitionFailedException("사이드카 죽음"));
        when(secondary.recognize(any(), anyString())).thenReturn(Optional.of(BACKUP));

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).contains(BACKUP);
    }

    @Test
    void 예비도_실패하면_그_실패가_올라간다() {
        when(primary.recognize(any(), anyString()))
                .thenThrow(new MusicRecognitionFailedException("사이드카 죽음"));
        when(secondary.recognize(any(), anyString()))
                .thenThrow(new MusicRecognitionFailedException("AudD 거부"));

        assertThatThrownBy(() -> recognizer.recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class)
                .hasMessageContaining("AudD");
    }

    /** 기본이 깨진 상태에서 예비가 "못 찾음"을 주면 그건 정상적인 답이다. */
    @Test
    void 기본_실패_뒤_예비가_못_찾으면_빈_결과다() {
        when(primary.recognize(any(), anyString()))
                .thenThrow(new MusicRecognitionFailedException("사이드카 죽음"));
        when(secondary.recognize(any(), anyString())).thenReturn(Optional.empty());

        assertThat(recognizer.recognize(new byte[] {1}, "clip.wav")).isEmpty();
    }
}

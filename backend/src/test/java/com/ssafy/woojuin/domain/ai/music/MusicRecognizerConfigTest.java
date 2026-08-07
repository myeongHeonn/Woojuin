package com.ssafy.woojuin.domain.ai.music;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 키 유무에 따라 무엇이 올라오는지 고정한다({@code AiQueryConfigTest} 와 같은 방식).
 * <b>키가 없어도 앱은 떠야 한다</b> — 그 환경에서는 노래 찾기만 오류로 답한다.
 */
class MusicRecognizerConfigTest {

    private final MusicRecognizerConfig config = new MusicRecognizerConfig();

    private MusicRecognizer recognizerFor(String auddToken) {
        return config.musicRecognizer(new ObjectMapper(), 8_000L, auddToken);
    }

    @Test
    void 키가_있으면_AudD_다() {
        assertThat(recognizerFor("token")).isInstanceOf(AuddMusicRecognizer.class);
    }

    @Test
    void 키가_없으면_NoOp_이다() {
        assertThat(recognizerFor("")).isInstanceOf(NoOpMusicRecognizer.class);
    }

    @Test
    void 공백뿐인_키는_없는_것으로_본다() {
        assertThat(recognizerFor("   ")).isInstanceOf(NoOpMusicRecognizer.class);
    }

    @Test
    void null_도_없는_것으로_본다() {
        assertThat(recognizerFor(null)).isInstanceOf(NoOpMusicRecognizer.class);
    }

    /** NoOp 은 "못 찾음"이 아니라 실패로 올린다 — 원인이 드러나야 고칠 수 있다. */
    @Test
    void NoOp_은_빈_결과가_아니라_실패다() {
        MusicRecognizer recognizer = recognizerFor("");

        assertThat(recognizer).isInstanceOf(NoOpMusicRecognizer.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> recognizer.recognize(new byte[] {1}, "clip.wav"))
                .isInstanceOf(MusicRecognitionFailedException.class);
    }
}

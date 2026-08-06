package com.ssafy.woojuin.domain.ai.music;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * 어떤 설정 조합에 어떤 인식기가 올라오는지 고정한다({@code AiQueryConfigTest} 와 같은 방식).
 * 특히 <b>키가 없어도 사이드카 단독으로 동작해야 한다</b> — 키 없이 기능이 도는 것이 기본값이다.
 */
class MusicRecognizerConfigTest {

    private final MusicRecognizerConfig config = new MusicRecognizerConfig();

    private MusicRecognizer recognizerFor(String sidecarUrl, String auddToken) {
        return config.musicRecognizer(new ObjectMapper(), sidecarUrl, 25_000L, auddToken);
    }

    @Test
    void 사이드카와_키가_모두_있으면_폴백_구성이다() {
        assertThat(recognizerFor("http://localhost:8003", "token"))
                .isInstanceOf(FallbackMusicRecognizer.class);
    }

    @Test
    void 사이드카만_있으면_사이드카_단독이다() {
        assertThat(recognizerFor("http://localhost:8003", ""))
                .isInstanceOf(SidecarMusicRecognizer.class);
    }

    @Test
    void 키만_있으면_AudD_단독이다() {
        assertThat(recognizerFor("", "token")).isInstanceOf(AuddMusicRecognizer.class);
    }

    @Test
    void 둘_다_없으면_NoOp_이다() {
        assertThat(recognizerFor("", "")).isInstanceOf(NoOpMusicRecognizer.class);
    }

    @Test
    void 공백뿐인_값은_없는_것으로_본다() {
        assertThat(recognizerFor("   ", "  ")).isInstanceOf(NoOpMusicRecognizer.class);
    }

    @Test
    void null_도_없는_것으로_본다() {
        assertThat(recognizerFor(null, null)).isInstanceOf(NoOpMusicRecognizer.class);
    }
}

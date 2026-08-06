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
        return recognizerFor(sidecarUrl, auddToken, "sidecar");
    }

    private MusicRecognizer recognizerFor(String sidecarUrl, String auddToken, String primary) {
        return config.musicRecognizer(new ObjectMapper(), sidecarUrl, 8_000L, primary, auddToken);
    }

    /**
     * 배포 환경에서 순서를 뒤집을 수 있어야 한다 — 로컬에서 되는 비공식 경로가 EC2 에서
     * 막힐 수 있고, 막는 방식이 "응답 없음"이면 폴백까지 타임아웃을 다 기다린다.
     */
    @Test
    void primary_가_audd_면_AudD_를_먼저_쓴다() throws Exception {
        MusicRecognizer recognizer = recognizerFor("http://localhost:8003", "token", "audd");

        assertThat(recognizer).isInstanceOf(FallbackMusicRecognizer.class);
        assertThat(firstOf(recognizer)).isInstanceOf(AuddMusicRecognizer.class);
    }

    @Test
    void 기본값은_사이드카_우선이다() throws Exception {
        MusicRecognizer recognizer = recognizerFor("http://localhost:8003", "token", "sidecar");

        assertThat(firstOf(recognizer)).isInstanceOf(SidecarMusicRecognizer.class);
    }

    @Test
    void 알_수_없는_값은_사이드카_우선으로_본다() throws Exception {
        assertThat(firstOf(recognizerFor("http://localhost:8003", "token", "wat")))
                .isInstanceOf(SidecarMusicRecognizer.class);
    }

    /** 폴백 구성의 앞자리를 꺼내 본다 — 순서가 뒤집혔는지 확인할 다른 방법이 없다. */
    private MusicRecognizer firstOf(MusicRecognizer recognizer) throws Exception {
        java.lang.reflect.Field field = FallbackMusicRecognizer.class.getDeclaredField("primary");
        field.setAccessible(true);
        return (MusicRecognizer) field.get(recognizer);
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

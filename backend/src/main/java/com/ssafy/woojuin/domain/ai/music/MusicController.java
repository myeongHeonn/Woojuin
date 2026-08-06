package com.ssafy.woojuin.domain.ai.music;

import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 오디오 → 곡 (워치 노래 찾기가 쓴다).
 *
 * <p>아이템을 만들지 않고 곡 정보만 돌려준다 — 받아쓰기 엔드포인트와 같은 이유다
 * ({@link com.ssafy.woojuin.domain.ai.speech.SpeechController}). 워치가 이 링크를
 * URL 아이템으로 저장하는데, 그 저장은 이미 있는 경로({@code POST /workspaces/{id}/items})라
 * 여기서 대신 할 이유가 없다.
 */
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicRecognizer recognizer;

    public MusicController(MusicRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    /**
     * @param file 오디오 파일. 워치는 12초 16kHz 모노 WAV 를 올린다
     * @return 찾은 곡. <b>못 찾았으면 {@code found: false} 이고 그것도 200 이다</b> —
     *         클라이언트가 "노래를 찾지 못했어요"와 "잠시 후 다시"를 구분해야 한다
     */
    @AuthenticatedUser
    @PostMapping(path = "/recognitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecognitionResponse> recognize(@RequestPart MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("오디오 파일이 필요합니다");
        }
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException e) {
            throw new MusicRecognitionFailedException("업로드된 오디오를 읽을 수 없습니다", e);
        }
        String filename = file.getOriginalFilename();
        Optional<MusicRecognizer.RecognizedSong> song = recognizer.recognize(
                audio,
                filename == null || filename.isBlank() ? "clip.wav" : filename);
        return ApiResponse.success(song
                .map(RecognitionResponse::found)
                .orElseGet(RecognitionResponse::notFound));
    }

    /**
     * @param found 곡을 찾았는지. 거짓이면 나머지는 모두 null 이다
     * @param link 곡 페이지 링크 — 워치가 이걸 URL 아이템으로 저장한다
     */
    public record RecognitionResponse(
            boolean found, String title, String artist, String link, String coverUrl) {

        static RecognitionResponse found(MusicRecognizer.RecognizedSong song) {
            return new RecognitionResponse(
                    true, song.title(), song.artist(), song.link(), song.coverUrl());
        }

        static RecognitionResponse notFound() {
            return new RecognitionResponse(false, null, null, null, null);
        }
    }
}

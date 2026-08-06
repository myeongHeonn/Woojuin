package com.ssafy.woojuin.domain.ai.speech;

import com.ssafy.woojuin.global.common.ApiResponse;
import com.ssafy.woojuin.global.security.aop.AuthenticatedUser;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 오디오 → 문장 (워치 음성 저장·검색이 쓴다).
 *
 * <p>아이템을 만들지 않는다 — 글자만 돌려주고, 그걸로 무엇을 할지는 워치가 정한다(저장할 수도
 * 있고 검색어로 쓸 수도 있다). 저장까지 여기서 하면 검색 흐름이 남의 집을 지나가게 된다.
 *
 * <p>인증이 필요하다. 링크된 워치 세션이 곧 사용자이므로 별도 규칙을 두지 않는다.
 */
@RestController
@RequestMapping("/api/speech")
public class SpeechController {

    private final SpeechTranscriber transcriber;

    public SpeechController(SpeechTranscriber transcriber) {
        this.transcriber = transcriber;
    }

    /**
     * @param file 오디오 파일. 워치는 16kHz 모노 WAV 를 올린다
     * @return 받아쓴 문장. <b>무음이면 빈 문자열</b>이고 그것도 200 이다 — 클라이언트가
     *         "들린 내용이 없어요"와 "서버 오류"를 구분할 수 있어야 한다
     */
    @AuthenticatedUser
    @PostMapping(path = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TranscriptionResponse> transcribe(@RequestPart MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("오디오 파일이 필요합니다");
        }
        byte[] audio;
        try {
            audio = file.getBytes();
        } catch (IOException e) {
            throw new TranscriptionFailedException("업로드된 오디오를 읽을 수 없습니다", e);
        }
        String filename = file.getOriginalFilename();
        String text = transcriber.transcribe(
                audio,
                // 확장자로 형식을 판별하므로 이름이 없으면 wav 로 가정한다(워치가 보내는 형식)
                filename == null || filename.isBlank() ? "speech.wav" : filename);
        return ApiResponse.success(new TranscriptionResponse(text));
    }

    /** @param text 받아쓴 문장. 무음이면 빈 문자열 */
    public record TranscriptionResponse(String text) {}
}

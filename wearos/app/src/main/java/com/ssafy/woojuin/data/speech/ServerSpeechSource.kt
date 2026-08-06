package com.ssafy.woojuin.data.speech

import android.util.Log
import com.ssafy.woojuin.data.remote.WoojuinApi
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.SpeechTranscriptionException
import com.ssafy.woojuin.domain.repository.SpeechUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "WoojuinSpeech"

/**
 * 손목에서 녹음해 서버로 보내 받아쓰는 [SpeechSource].
 *
 * <p>기기 인식기(온디바이스 SODA)를 대체한다. 이유는 [MicRecorder] javadoc 에 있다 —
 * 요약하면 고유명사가 깨져서 저장물의 제목을 믿을 수 없었고, 두 방식을 함께 쓰는 길은
 * 실기기에서 막혀 있었다.
 *
 * <p><b>실시간 텍스트가 없다.</b> whisper 는 스트리밍을 지원하지 않고 GMS 프록시도 실시간
 * 경로를 중계하지 않는다. 대신 [SpeechEvent.Partial] 에 음량만 실어 보내 화면이 "듣고 있다"를
 * 보여주게 한다. 잘못 들은 글자가 실시간으로 뜨는 것보다, 맞는 글자가 조금 늦게 뜨는 쪽이
 * 낫다고 봤다(실기기에서 온디바이스는 여섯 단어 중 셋을 틀렸다).
 *
 * <p>흐름: 녹음 시작 → [SpeechEvent.Ready] → 말하는 동안 음량 → 말 끝(무음 감지) →
 * 업로드 → [SpeechEvent.Final]. 무음이면 [SpeechEvent.SilenceTimeout] 이다.
 */
class ServerSpeechSource(private val api: WoojuinApi) : SpeechSource {

    /** 진행 중인 녹음 — 화면의 "다 말했어요" 탭을 [finishNow] 로 받아 마감한다. */
    @Volatile
    private var recording: MicRecorder? = null

    override fun finishNow() {
        recording?.finish()
    }

    override fun listen(): Flow<SpeechEvent> = flow {
        var level = 0f
        val recorder = MicRecorder { level = it }
        recording = recorder

        // 마이크는 100ms 안에 열린다 — 탭하고 바로 말해도 앞부분이 살아 있다
        emit(SpeechEvent.Ready)

        // 블로킹 녹음 루프에는 코루틴 취소가 저절로 닿지 않는다 — Job 을 넘겨 직접 보게 한다
        val job = currentCoroutineContext()[Job]
        val recorded = try {
            recorder.record { job?.isActive != false }
        } finally {
            recording = null
        }
        if (recorded == null) {
            throw SpeechUnavailableException()
        }
        if (!recorded.spoke) {
            Log.d(TAG, "말소리 없음 — 업로드하지 않는다")
            emit(SpeechEvent.SilenceTimeout)
            return@flow
        }

        // 마이크는 닫혔다 — 화면이 "듣고 있어요"를 계속 보여주면 거짓이 된다
        emit(SpeechEvent.Transcribing)

        val wav = WavEncoder.wrap(recorded.pcm, MicRecorder.SAMPLE_RATE)
        Log.d(TAG, "받아쓰기 요청 — ${wav.size / 1024}KB")
        val text = transcribe(wav)
        if (text.isBlank()) {
            // 서버가 무음으로 판정 — 우리 문턱보다 서버가 엄격했던 경우다
            emit(SpeechEvent.SilenceTimeout)
        } else {
            Log.d(TAG, "받아쓰기 결과 — ${text.length}자")
            emit(SpeechEvent.Final(text, confident = true))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * @return 받아쓴 문장. 무음이면 빈 문자열(서버 계약)
     * @throws SpeechTranscriptionException 서버가 받아쓰기를 못 한 경우 —
     *   인증 실패([com.ssafy.woojuin.data.remote.AuthRequiredException])는 그대로 올려
     *   보내 화면이 링크로 돌아가게 한다(다른 API 호출과 같은 처리)
     */
    private fun transcribe(wav: ByteArray): String {
        val data = try {
            api.authorizedUpload(
                path = "/speech/transcriptions",
                fieldName = "file",
                filename = "speech.wav",
                contentType = "audio/wav",
                bytes = wav,
            ).getJSONObject("data")
        } catch (e: com.ssafy.woojuin.data.remote.AuthRequiredException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "받아쓰기 실패: $e")
            throw SpeechTranscriptionException()
        }
        return if (data.isNull("text")) "" else data.optString("text", "").trim()
    }
}

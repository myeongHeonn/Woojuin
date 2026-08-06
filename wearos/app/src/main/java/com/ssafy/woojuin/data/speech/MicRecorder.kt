package com.ssafy.woojuin.data.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

private const val TAG = "WoojuinSpeech"

/**
 * 마이크에서 PCM 을 모으고, 말이 끝나면 스스로 멈춘다. 서버 받아쓰기(whisper)로 보낼
 * 오디오를 만드는 것이 유일한 일이다.
 *
 * <p><b>왜 기기 인식기를 쓰지 않는가.</b> 이 워치에는 구글 검색 앱이 없어 온디바이스 SODA
 * 엔진만 붙고, 그 엔진은 짧은 발화용이라 고유명사가 자주 깨진다(실기기에서 "온화정" →
 * "운화점", 두 번째 시도에서는 "파스타집 온화정"이 통째로 "바닷가"가 됐다). 저장물의 제목이
 * 될 문장이라 정확도가 곧 기능이므로 whisper 로 보낸다.
 *
 * <p><b>둘을 함께 쓰는 길은 막혀 있다</b>(실기기에서 확인). 마이크는 한 클라이언트만 잡을 수
 * 있는데 인식기는 별도 프로세스이고, 우회로 두 개가 모두 닫혔다.
 * `EXTRA_AUDIO_SOURCE`(우리가 녹음해 파이프로 먹이기)는 세 번 모두 ERROR_CLIENT 로 거부됐고,
 * `onBufferReceived`(엔진이 오디오를 넘겨주기)는 인식에 성공한 세션에서도 한 번도 불리지
 * 않았다. 그래서 "실시간 자막 + 서버 정확도"를 같은 발화에서 얻을 수는 없다.
 *
 * <p>대신 얻은 것: **탭하면 100ms 안에 녹음이 시작된다.** 엔진 초기화를 기다리던 2~4초가
 * 사라졌고, 준비 전에 한 말이 버려지는 문제도 없어졌다.
 */
class MicRecorder(
    /**
     * **첫 오디오가 실제로 들어온 순간** 한 번 불린다. 화면이 "듣고 있어요"로 바뀔 근거는
     * 이것뿐이다 — 마이크를 열기 전에 그렇게 보여주면 사용자가 허공에 말하고 앞부분이
     * 잘린다(실기기에서 "성수동 파스타집..."이 "스타집..."으로 저장됐다).
     */
    private val onStarted: () -> Unit,
    /** 0..1 로 정규화한 현재 음량 — 100ms 마다 */
    private val onLevel: (Float) -> Unit,
) {

    companion object {
        /** whisper 가 내부적으로 16kHz 로 리샘플하므로 여기서 맞춰 보낸다(전송량도 최소). */
        const val SAMPLE_RATE = 16_000

        /** 한 번에 읽는 샘플 수 — 100ms. 음량 갱신·무음 판정의 단위다. */
        private const val CHUNK_SAMPLES = SAMPLE_RATE / 10

        /**
         * 이만큼 조용하면 말이 끝난 것으로 본다. 1.2초는 문장 사이의 숨돌림(0.3~0.6초)보다
         * 넉넉하고, 사용자가 "왜 안 끝나지" 하기 전에는 끝난다.
         */
        private const val SILENCE_TO_STOP_MS = 1_200

        /** 말이 시작되기 전 기다려 주는 시간. 이 안에 아무 소리도 없으면 무음으로 끝낸다. */
        private const val NO_SPEECH_TIMEOUT_MS = 6_000

        /** 안전 상한. 손목에서 하는 한마디는 길어도 10초 안쪽이다. */
        private const val MAX_DURATION_MS = 20_000

        /**
         * 말소리로 인정하는 최소 RMS(0..32767 기준). 조용한 실내 잡음이 100~300, 사람 말은
         * 1000 이상으로 올라온다 — 그 사이인 600 을 문턱으로 둔다. 낮추면 에어컨 소리에
         * 녹음이 끝나지 않고, 높이면 작게 말하는 사람의 말끝이 잘린다.
         */
        private const val SPEECH_RMS_THRESHOLD = 600.0
    }

    /** 녹음 결과. [spoke] 가 false 면 사용자가 아무 말도 하지 않았다는 뜻이다. */
    data class Recording(val pcm: ByteArray, val spoke: Boolean)

    @Volatile
    private var finished = false

    /** "다 말했어요" — 무음을 기다리지 않고 지금까지 모은 것으로 마감한다. */
    fun finish() {
        finished = true
    }

    /**
     * **고정 길이 녹음.** 노래 인식이 쓴다 — 음악은 말과 달리 침묵으로 끝을 판단할 수 없고,
     * 지문 매칭에 필요한 최소 길이가 있다(12초면 충분하다는 것을 실측으로 확인했다).
     * 무음 감지·조기 종료가 없으므로 [finish] 로만 앞당길 수 있다.
     *
     * @param seconds 녹음할 시간
     * @param isActive [record] 와 같은 의미 — 호출자가 아직 결과를 원하는지
     */
    fun recordFixed(seconds: Int, isActive: () -> Boolean = { true }): Recording? =
        record(fixedDurationMs = seconds * 1_000, isActive = isActive)

    /**
     * 녹음이 끝날 때까지 블로킹한다 — 호출자가 IO 디스패처에서 부른다.
     * 끝나는 조건: 말 뒤 [SILENCE_TO_STOP_MS] 침묵 / 처음부터 [NO_SPEECH_TIMEOUT_MS] 무음 /
     * [MAX_DURATION_MS] 초과 / [finish] / [isActive] 가 false.
     *
     * @param fixedDurationMs 정해진 시간만 녹음한다(노래 인식). null 이면 무음으로 끝낸다
     * @param isActive 호출자가 아직 결과를 원하는지. **블로킹 루프라 코루틴 취소가 저절로
     *   전달되지 않으므로** 화면을 벗어났을 때 마이크를 놓으려면 이걸 봐야 한다.
     *   마지막 파라미터로 둔 것은 `record { 아직 원하는가 }` 로 쓰이기 때문이다
     * @return 모은 PCM(16kHz 모노 16bit). 마이크를 열 수 없으면 null
     */
    @SuppressLint("MissingPermission")
    fun record(
        fixedDurationMs: Int? = null,
        isActive: () -> Boolean = { true },
    ): Recording? {
        val createdAt = SystemClock.elapsedRealtime()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.d(TAG, "녹음 — 버퍼 크기 조회 실패($minBuffer)")
            return null
        }
        val record = try {
            AudioRecord(
                // 인식용 소스 — 기기의 음성 전처리(잡음 억제·에코 제거)를 탄다
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_SAMPLES * 2 * 4),
            )
        } catch (e: Exception) {
            Log.d(TAG, "녹음 — AudioRecord 생성 실패: $e")
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.d(TAG, "녹음 — 초기화 실패(state=${record.state})")
            record.release()
            return null
        }

        val collected = ByteArrayOutputStream()
        try {
            val beforeStart = SystemClock.elapsedRealtime()
            record.startRecording()
            Log.d(TAG, "startRecording 반환 — 준비 ${beforeStart - createdAt}ms, 시작 " +
                "${SystemClock.elapsedRealtime() - beforeStart}ms")
            pump(record, collected, isActive, fixedDurationMs)
        } finally {
            runCatching { record.stop() }
            record.release()
        }

        val pcm = collected.toByteArray()
        val spoke = speechChunks > 0
        Log.d(TAG, "녹음 종료 — ${pcm.size / (SAMPLE_RATE * 2)}초, 말소리=${spoke}")
        return Recording(pcm, spoke)
    }

    /** 말소리로 판정된 조각 수 — 무음 여부의 근거 */
    private var speechChunks = 0

    private fun pump(
        record: AudioRecord,
        collected: ByteArrayOutputStream,
        isActive: () -> Boolean,
        fixedDurationMs: Int?,
    ) {
        val samples = ShortArray(CHUNK_SAMPLES)
        val bytes = ByteArray(CHUNK_SAMPLES * 2)
        var silentMs = 0
        var elapsedMs = 0
        speechChunks = 0

        // 고정 길이면 그 시간까지, 아니면 안전 상한까지
        val limitMs = fixedDurationMs ?: MAX_DURATION_MS
        while (!finished && isActive() && elapsedMs < limitMs) {
            val read = record.read(samples, 0, samples.size)
            if (read <= 0) continue
            if (elapsedMs == 0) {
                // 여기부터가 진짜 녹음이다 — 화면은 이 신호를 받아야 "듣고 있어요"가 된다
                Log.d(TAG, "첫 오디오 도착")
                onStarted()
            }
            val chunkMs = read * 1000 / SAMPLE_RATE
            elapsedMs += chunkMs

            var sumOfSquares = 0.0
            for (i in 0 until read) {
                val sample = samples[i].toInt()
                sumOfSquares += (sample * sample).toDouble()
                // 리틀엔디언 16bit — WAV 가 기대하는 순서
                bytes[i * 2] = (sample and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            collected.write(bytes, 0, read * 2)

            val rms = sqrt(sumOfSquares / read)
            // 말소리 구간(1000~8000)이 눈에 보이게 밀어서 0..1 로
            onLevel((rms / 6000.0).toFloat().coerceIn(0f, 1f))

            if (rms >= SPEECH_RMS_THRESHOLD) {
                speechChunks++
                silentMs = 0
            } else if (fixedDurationMs == null) {
                // 무음 감지는 말할 때만 쓴다 — 노래는 조용한 구간(간주·페이드)이 있어
                // 침묵으로 끊으면 지문에 쓸 길이가 모자란다
                silentMs += chunkMs
                // 말이 한 번이라도 있었으면 침묵으로 끝낸다. 아직 없었다면 더 기다린다
                val limit = if (speechChunks > 0) SILENCE_TO_STOP_MS else NO_SPEECH_TIMEOUT_MS
                if (silentMs >= limit) return
            }
        }
    }
}

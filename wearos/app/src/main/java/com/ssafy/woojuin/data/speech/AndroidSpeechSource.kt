package com.ssafy.woojuin.data.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechRecognitionException
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.SpeechUnavailableException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "WoojuinSpeech"

/**
 * Android SpeechRecognizer 기반 실음성 인식.
 * - 인식기를 매번 만들지 않고 한 번 바인딩해 재사용한다(pre-warm).
 *   탭 → 첫 partial까지의 콜드 스타트(서비스 바인딩+초기화)를 없애기 위함.
 * - 기기에 설치된 RecognitionService 중 품질이 나은 것(Google)을 우선 바인딩한다.
 * - 부분 인식(EXTRA_PARTIAL_RESULTS)을 스트리밍하고, onRmsChanged를 0..1로 정규화한다.
 * - 무음/무결과는 SilenceTimeout으로 정규화한다.
 *
 * 한계: 공개 API 인식 품질은 기기 기본 엔진에 좌우된다. Gemini급 품질이 필요하면
 * Spring 서버 경유 STT(Clova/Google Cloud/Whisper)로 [SpeechSource] 구현만 교체한다.
 */
class AndroidSpeechSource(private val context: Context) : SpeechSource {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    /** 앱 진입 시 미리 호출해 서비스 바인딩을 데워 둔다. */
    fun warmUp() {
        main.post { ensureRecognizer() }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        val service = pickRecognitionService()
        Log.d(TAG, "creating recognizer, service: ${service ?: "system default"}")
        val r = if (service != null) {
            SpeechRecognizer.createSpeechRecognizer(context, service)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        return r
    }

    /** 복구 불가능한 오류가 났을 때만 폐기 — 다음 listen에서 새로 만든다. */
    private fun discardRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    /**
     * 설치된 인식 서비스 중 선호 순서로 선택한다.
     * Google 검색 앱(googlequicksearchbox)의 서버 인식이 보통 기본 워치 엔진보다 낫다.
     */
    private fun pickRecognitionService(): ComponentName? {
        val services = context.packageManager.queryIntentServices(
            Intent(RecognitionService.SERVICE_INTERFACE),
            0,
        )
        services.forEach {
            Log.d(TAG, "RecognitionService: ${it.serviceInfo.packageName}/${it.serviceInfo.name}")
        }
        val preferredOrder = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.tts",
            "com.google.android.as",
        )
        for (pkg in preferredOrder) {
            val match = services.firstOrNull { it.serviceInfo.packageName == pkg }
            if (match != null) {
                return ComponentName(match.serviceInfo.packageName, match.serviceInfo.name)
            }
        }
        return null
    }

    override fun listen(): Flow<SpeechEvent> = callbackFlow {
        main.post {
            val r = ensureRecognizer()
            if (r == null) {
                close(SpeechUnavailableException())
                return@post
            }
            r.setRecognitionListener(object : RecognitionListener {
                private var lastText = ""

                override fun onRmsChanged(rmsdB: Float) {
                    // 대략 -2..10dB 범위를 0..1로
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    trySend(SpeechEvent.Partial(lastText, normalized))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotBlank()) {
                        lastText = text
                        trySend(SpeechEvent.Partial(text, 0.5f))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isBlank()) {
                        trySend(SpeechEvent.SilenceTimeout)
                    } else {
                        val score = results
                            ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                            ?.firstOrNull()
                        // 신뢰도가 매우 낮을 때만 확인 화면을 띄운다 (-1은 미제공 → 신뢰).
                        val confident = score == null || score < 0f || score >= 0.3f
                        trySend(SpeechEvent.Final(text, confident))
                    }
                    close()
                }

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> {
                            trySend(SpeechEvent.SilenceTimeout)
                            close()
                        }
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        -> {
                            // 인스턴스가 오염됐을 수 있으니 폐기 후 오류 전달
                            discardRecognizer()
                            close(SpeechRecognitionException(error))
                        }
                        else -> close(SpeechRecognitionException(error))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    // 문장을 끝내고 살짝 뜸을 들여도 끊기지 않게 무음 종료를 여유 있게 잡는다.
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500L,
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500L,
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                        2000L,
                    )
                    // 서버 인식이 가능한 서비스라면 온라인을 우선한다.
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
            )
        }

        awaitClose {
            // 세션만 종료하고 바인딩은 유지한다 — 다음 탭에서 즉시 시작되도록.
            main.post { recognizer?.cancel() }
        }
    }
}

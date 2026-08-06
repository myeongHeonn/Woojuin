package com.ssafy.woojuin.data.speech

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechRecognitionException
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.SpeechUnavailableException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "WoojuinSpeech"

/** BUSY 재시도 전 대기 — 엔진이 이전 세션을 정리할 틈을 준다 */
private const val BUSY_RETRY_DELAY_MS = 250L

/**
 * 웜업 세션 상한. 실기기에서 SODA 초기화가 8.8초였으므로 그보다 넉넉하되,
 * 마이크를 무한정 붙잡지 않게 자른다.
 */
private const val WARMUP_MAX_MS = 12_000L

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

    /**
     * 지금 듣고 있는 세션의 표식. 재사용 인스턴스 하나를 여러 세션이 나눠 쓰기 때문에,
     * **늦게 도착한 이전 세션의 정리 작업이 새 세션을 죽이는 일**을 막는 자물쇠다.
     *
     * 실기기 증상: 화면을 나갔다 바로 다시 들어오면 마이크가 열리지 않고 콜백도 오지 않아
     * "듣는 중"에서 멈춘다. awaitClose 의 cancel() 이 main 큐에 예약된 뒤 새 세션이
     * startListening() 을 부르고, 그 다음에 예약된 cancel() 이 실행돼 새 세션을 끊기 때문이다.
     */
    private var activeSession: Any? = null

    private val _ready = MutableStateFlow(false)
    /** 웜업이 끝나 엔진이 곧바로 들을 수 있는 상태 — 홈 화면이 버튼을 이걸로 가른다 */
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * 앱 진입 시 미리 호출해 **인식 엔진까지** 데운다.
     *
     * 인스턴스만 만들어 두는 것으로는 부족했다 — 실기기 로그에서 첫 인식이
     * `startListening` 부터 실제 청취 시작(`start detection`)까지 **8.8초**가 걸렸다.
     * SODA 엔진 초기화(`Initialize Soda` → `blockingReconnect`)가 첫 startListening 에서야
     * 일어나기 때문이다. 그 사이 사용자가 한 말은 버려지고, 기다리다 나가 버려 "첫 시도는
     * 안 되고 두 번째부터 된다"가 됐다.
     *
     * 그래서 세션을 실제로 한 번 열어 엔진을 초기화하고, 준비되는 즉시(onReadyForSpeech)
     * 끊는다. 마이크가 순간 열리는 대가가 있지만, 앱을 켠 직후이고 사용자가 음성을
     * 쓰려고 들어온 시점이라 감수한다 — 첫 저장이 통째로 실패하는 것이 훨씬 나쁘다.
     */
    fun warmUp() {
        main.post {
            val r = ensureRecognizer() ?: return@post
            // 권한이 없으면 웜업할 수 없다(화면이 나중에 권한을 받는다)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@post
            }
            // 이미 세션이 돌고 있으면 데울 필요가 없다
            if (activeSession != null) return@post

            val session = Any()
            activeSession = session
            Log.d(TAG, "warm-up 세션 시작 — 엔진 초기화")

            fun finish(reason: String) {
                if (activeSession !== session) return
                activeSession = null
                // 엔진이 한 번 올라왔으면 다음 세션은 즉시 시작된다
                _ready.value = true
                Log.d(TAG, "warm-up 종료($reason) — 엔진 준비 완료")
                r.cancel()
            }

            r.setRecognitionListener(object : RecognitionListener {
                /** 엔진이 오디오를 받을 준비가 됐다 = 초기화 끝. 여기서 바로 끊는다 */
                override fun onReadyForSpeech(params: Bundle?) = finish("ready")

                override fun onError(error: Int) {
                    if (activeSession === session) {
                        activeSession = null
                        Log.d(TAG, "warm-up 실패($error) — 실제 인식에서 다시 시도한다")
                    }
                }

                override fun onResults(results: Bundle?) = finish("results")
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            r.startListening(recognizeIntent())

            // 안전망 — onReadyForSpeech 가 오지 않는 엔진도 있다. 마이크를 오래 붙잡지 않는다
            main.postDelayed({ finish("timeout") }, WARMUP_MAX_MS)
        }
    }

    /**
     * 인식 요청. BUSY 재시도가 같은 설정으로 다시 시작해야 해서 함수로 둔다.
     *
     * EXTRA_PREFER_OFFLINE=false 는 **서버 경로를 가진 엔진에게만** 의미가 있다 —
     * Wear OS 에는 구글 앱(googlequicksearchbox)이 없어 온디바이스 SODA 엔진이 붙고,
     * 그 엔진에는 서버 경로가 없어 이 힌트가 무시된다. 서버 인식 품질이 필요해지면
     * [SpeechSource] 구현을 서버 경유 STT 로 교체하는 게 유일한 길이다.
     */
    private fun recognizeIntent(): Intent =
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L,
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
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
        activeSession = null
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
        val session = Any()
        main.post {
            val r = ensureRecognizer()
            if (r == null) {
                close(SpeechUnavailableException())
                return@post
            }
            // 이전 세션이 남아 있으면 여기서 끊는다 — 정리를 큐에 미루면 이 세션이 그 뒤에
            // 실행되는 cancel() 에 맞아 죽는다(위 activeSession 주석 참고)
            if (activeSession != null) r.cancel()
            activeSession = session

            r.setRecognitionListener(object : RecognitionListener {
                private var lastText = ""

                /** BUSY 재시도는 한 번만 — 두 번째 실패는 진짜 오류다 */
                private var retried = false

                /** 이 리스너가 여전히 현재 세션의 것인지 — 아니면 콜백을 버린다 */
                private fun stale(): Boolean = activeSession !== session

                override fun onRmsChanged(rmsdB: Float) {
                    if (stale()) return
                    // 대략 -2..10dB 범위를 0..1로
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    trySend(SpeechEvent.Partial(lastText, normalized))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (stale()) return
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
                    if (stale()) return
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
                    if (stale()) return
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
                            // 이전 세션이 엔진에서 아직 정리되기 전이면 BUSY 가 온다. 인스턴스를
                            // 새로 만들어 한 번만 다시 시작한다 — 사용자에게는 인식이 잠깐 늦게
                            // 시작되는 것으로 보인다. 두 번째도 실패하면 오류를 올린다
                            discardRecognizer()
                            if (retried) {
                                close(SpeechRecognitionException(error))
                                return
                            }
                            retried = true
                            Log.d(TAG, "recognizer busy($error) — 새 인스턴스로 1회 재시도")
                            main.postDelayed({
                                if (activeSession !== session) return@postDelayed
                                val fresh = ensureRecognizer()
                                if (fresh == null) {
                                    close(SpeechUnavailableException())
                                } else {
                                    activeSession = session
                                    fresh.setRecognitionListener(this)
                                    fresh.startListening(recognizeIntent())
                                }
                            }, BUSY_RETRY_DELAY_MS)
                        }
                        else -> close(SpeechRecognitionException(error))
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    if (stale()) return
                    // 여기부터 실제로 들린다 — 화면이 "준비 중"을 "듣는 중"으로 바꾼다
                    _ready.value = true
                    trySend(SpeechEvent.Ready)
                }

                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            r.startListening(recognizeIntent())
        }

        awaitClose {
            // 세션만 종료하고 바인딩은 유지한다 — 다음 탭에서 즉시 시작되도록.
            // **이 세션이 아직 현재일 때만** 끊는다. 이미 다음 세션이 시작됐다면
            // 여기서 cancel() 하는 순간 그 새 세션이 죽는다(실기기 간헐 무반응의 원인).
            main.post {
                if (activeSession === session) {
                    activeSession = null
                    recognizer?.cancel()
                }
            }
        }
    }
}

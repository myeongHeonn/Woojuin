package com.ssafy.woojuin.data.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
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

/** BUSY 재시도 전 대기 — 엔진이 이전 세션을 정리할 틈을 준다 */
private const val BUSY_RETRY_DELAY_MS = 250L

/**
 * API 31+ 온디바이스 전용 팩토리를 쓸지. 우리가 서비스를 직접 고르던 경로와 초기화
 * 거동이 다른지 재는 실험 스위치다 — 결과가 나쁘면 false 로 되돌린다.
 */
private const val USE_ON_DEVICE_FACTORY = true

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

    private fun elapsed(): Long =
        if (sessionStartedAt == 0L) -1 else SystemClock.elapsedRealtime() - sessionStartedAt

    /** 어떤 팩토리로 만든 인식기인지 — 실험 결과를 로그에서 구분하려고 남긴다 */
    private var recognizerKind: String = "?"

    /** startListening 시점(ms). 준비까지 얼마나 걸리는지 로그로 재려고 둔다 */
    private var sessionStartedAt = 0L

    /**
     * 앱 진입 시 미리 호출해 서비스 바인딩을 데워 둔다.
     *
     * **여기서 인식 세션을 열어 엔진까지 데우려 했으나 되돌렸다.** 첫 인식이 느린 진짜
     * 원인은 SODA 엔진 초기화(실측 8.8초, 두 번째부터 2초대)인데, 그걸 데우려고 세션을
     * 열고 준비되는 즉시 cancel 하면 초기화 도중에 끼어들어 엔진이
     * `startDetection failed / CancellationException` 을 내고, 세션 하나를 소비해 정작
     * 사용자 차례에 인식이 시작되지 않았다(실기기 로그).
     *
     * 우리가 통제하지 못하는 엔진을 상대로 트릭을 쓰는 대신, 화면이 "준비 중"과
     * "듣는 중"을 갈라 보여준다([SpeechEvent.Ready]) — 기다림은 남지만 사용자가
     * 허공에 말하는 일은 없어진다.
     */
    fun warmUp() {
        main.post { ensureRecognizer() }
        // 기기 지원 현황을 로그로 확정한다 — 서버 STT 판단의 근거가 된다
        diagnose()
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

    /**
     * 인식기를 만든다. **API 31+ 의 온디바이스 전용 팩토리를 먼저 쓴다.**
     *
     * 우리가 서비스를 직접 골라 바인딩하던 경로(`createSpeechRecognizer(context, component)`)는
     * `applicationDomain: AMBIENT_ONESHOT` 으로 잡혀 첫 초기화가 8초대였다. 온디바이스
     * 팩토리는 시스템이 자기 방식으로 경로를 고르므로 초기화 거동이 다를 수 있다 —
     * 실험이고, 나쁘면 [USE_ON_DEVICE_FACTORY] 를 false 로 두면 예전 경로로 돌아간다.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null

        if (USE_ON_DEVICE_FACTORY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            recognizerKind = "onDevice"
            Log.d(TAG, "creating recognizer: createOnDeviceSpeechRecognizer")
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                .also { recognizer = it }
        }

        val service = pickRecognitionService()
        recognizerKind = if (service != null) "explicit(${service.packageName})" else "systemDefault"
        Log.d(TAG, "creating recognizer, service: ${service ?: "system default"}")
        val r = if (service != null) {
            SpeechRecognizer.createSpeechRecognizer(context, service)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = r
        return r
    }

    /**
     * 이 기기가 뭘 지원하는지 **코드가 직접 확인한다**(API 33+). 지금까지 adb 로 추측했던
     * 것들(온디바이스뿐인가, 한국어 팩이 설치돼 있나)을 로그로 확정한다.
     *
     * 언어팩이 `pending` 이면 [SpeechRecognizer.triggerModelDownload] 로 받아올 수 있다 —
     * 그 판단 근거도 이 결과에서 나온다.
     */
    fun diagnose() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.d(TAG, "checkRecognitionSupport 미지원(API ${Build.VERSION.SDK_INT})")
            return
        }
        main.post {
            val r = ensureRecognizer() ?: return@post
            Log.d(TAG, "지원 조회 시작 — kind=$recognizerKind, onDeviceAvailable=" +
                "${SpeechRecognizer.isOnDeviceRecognitionAvailable(context)}")
            r.checkRecognitionSupport(
                recognizeIntent(),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        Log.d(TAG, "지원 결과 — 설치된 온디바이스: ${support.installedOnDeviceLanguages}")
                        Log.d(TAG, "지원 결과 — 받을 수 있는 온디바이스: ${support.supportedOnDeviceLanguages}")
                        Log.d(TAG, "지원 결과 — 다운로드 대기: ${support.pendingOnDeviceLanguages}")
                        Log.d(TAG, "지원 결과 — 온라인: ${support.onlineLanguages}")
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "지원 조회 실패($error)")
                    }
                },
            )
        }
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

                /** 오디오가 흐르기 시작했는지 — Ready 를 한 번만 보내려고 둔다 */
                private var audioFlowing = false

                /** 이 리스너가 여전히 현재 세션의 것인지 — 아니면 콜백을 버린다 */
                private fun stale(): Boolean = activeSession !== session

                override fun onRmsChanged(rmsdB: Float) {
                    if (stale()) return
                    // 음량 보고가 오기 시작했다 = 엔진이 오디오를 실제로 받고 있다.
                    // 화면이 "준비 중"을 "듣는 중"으로 바꾸는 **믿을 수 있는** 신호다
                    if (!audioFlowing) {
                        audioFlowing = true
                        Log.d(TAG, "실제 청취 시작 — ${elapsed()}ms")
                        trySend(SpeechEvent.Ready)
                    }
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
                        if (lastText.isEmpty()) Log.d(TAG, "첫 부분 결과 — ${elapsed()}ms")
                        lastText = text
                        trySend(SpeechEvent.Partial(text, 0.5f))
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (stale()) return
                    Log.d(TAG, "onResults — ${elapsed()}ms")
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
                    Log.d(TAG, "onError($error) — ${elapsed()}ms")
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

                /**
                 * **이 콜백은 "이제 들린다"는 뜻이 아니다.** 실기기 로그에서 이 콜백이
                 * 2.2초에 왔는데 엔진의 실제 청취 시작(`start detection`)은 6.4초였다 —
                 * 그 사이 한 말은 버려진다. 그래서 화면 전환 신호로 쓰지 않고 계측만 한다.
                 * 실제 신호는 [onRmsChanged] 다(오디오가 흘러야 음량이 보고된다).
                 */
                override fun onReadyForSpeech(params: Bundle?) {
                    if (stale()) return
                    Log.d(TAG, "onReadyForSpeech — ${elapsed()}ms (아직 청취 아님, kind=$recognizerKind)")
                }

                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            sessionStartedAt = SystemClock.elapsedRealtime()
            Log.d(TAG, "startListening — kind=$recognizerKind")
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

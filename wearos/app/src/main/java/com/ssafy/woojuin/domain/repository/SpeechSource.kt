package com.ssafy.woojuin.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 음성 인식 스트림 공급자. 실기기는 SpeechRecognizer, Preview/미지원 기기는 Fake. */
interface SpeechSource {
    fun listen(): Flow<SpeechEvent>

    /**
     * 엔진이 곧바로 들을 수 있는 상태인지. 워치 온디바이스 엔진은 첫 초기화가 느려서
     * (실측 8.8초) 앱 진입 직후에는 false 다 — 홈 화면이 음성 버튼을 "준비 중"으로
     * 보여주는 근거다. 웜업이 끝나면 true 로 바뀌고 그대로 유지된다.
     */
    val ready: StateFlow<Boolean>
}

/** 기기에서 음성 인식 서비스를 사용할 수 없을 때. */
class SpeechUnavailableException : Exception("음성 인식을 사용할 수 없어요")

/** 인식 도중 복구 불가능한 오류. */
class SpeechRecognitionException(val errorCode: Int) : Exception("음성 인식 오류: $errorCode")

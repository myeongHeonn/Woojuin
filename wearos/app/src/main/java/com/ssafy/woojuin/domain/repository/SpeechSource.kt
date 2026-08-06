package com.ssafy.woojuin.domain.repository

import kotlinx.coroutines.flow.Flow

/** 음성 인식 스트림 공급자. 실기기는 SpeechRecognizer, Preview/미지원 기기는 Fake. */
interface SpeechSource {
    fun listen(): Flow<SpeechEvent>

    /**
     * 사용자가 "다 말했어요"를 누른 것 — 무음을 기다리지 않고 지금까지 들은 것으로
     * 마무리한다. 흐름을 끊는 게 아니라 마감을 앞당기는 신호다.
     */
    fun finishNow() {}
}

/** 기기에서 음성 인식 서비스를 사용할 수 없을 때. */
class SpeechUnavailableException : Exception("음성 인식을 사용할 수 없어요")

/** 인식 도중 복구 불가능한 오류. */
class SpeechRecognitionException(val errorCode: Int) : Exception("음성 인식 오류: $errorCode")

/**
 * 서버가 받아쓰지 못했다 — 통신·프록시·키 쪽 문제다. 사용자가 잘못한 게 없고 다시
 * 말하면 될 수도 있어서, "쓸 수 없어요"([SpeechUnavailableException])와 구분한다.
 */
class SpeechTranscriptionException : Exception("받아쓰기가 실패했어요")

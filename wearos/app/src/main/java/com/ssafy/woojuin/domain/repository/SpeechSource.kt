package com.ssafy.woojuin.domain.repository

import kotlinx.coroutines.flow.Flow

/** 음성 인식 스트림 공급자. 실기기는 SpeechRecognizer, Preview/미지원 기기는 Fake. */
interface SpeechSource {
    fun listen(): Flow<SpeechEvent>
}

/** 기기에서 음성 인식 서비스를 사용할 수 없을 때. */
class SpeechUnavailableException : Exception("음성 인식을 사용할 수 없어요")

/** 인식 도중 복구 불가능한 오류. */
class SpeechRecognitionException(val errorCode: Int) : Exception("음성 인식 오류: $errorCode")

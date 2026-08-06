package com.ssafy.woojuin.domain.repository

import com.ssafy.woojuin.domain.model.PlaceCandidate
import com.ssafy.woojuin.domain.model.RecognizedSong
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SearchInterpretation
import com.ssafy.woojuin.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 음성 인식 스트림. 실제 구현은 SpeechRecognizer / ShazamKit,
 * Fake 구현은 지연을 흉내 낸 시뮬레이션이다.
 */
sealed interface SpeechEvent {
    /**
     * 마이크가 열렸다 — 지금부터 하는 말은 남는다. 기기 인식기를 쓰던 때는 엔진 초기화가
     * 수 초여서 이 신호 전에 한 말이 버려졌지만, 지금은 우리가 직접 녹음하므로
     * ([com.ssafy.woojuin.data.speech.MicRecorder]) 100ms 안에 도착한다.
     */
    data object Ready : SpeechEvent

    /**
     * @param text 지금까지 알아들은 글자. **서버 받아쓰기에는 실시간 텍스트가 없어 빈
     *   문자열이다** — 화면은 [rms] 로 "듣고 있다"를 보여준다(이유는 ServerSpeechSource).
     * @param rms 0..1 로 정규화한 음량
     */
    data class Partial(val text: String, val rms: Float) : SpeechEvent
    /**
     * 녹음은 끝났고 서버가 받아쓰는 중이다. 화면은 이때 "듣고 있어요"라고 하면 안 된다 —
     * 마이크는 이미 닫혀서 지금 하는 말은 남지 않는다.
     */
    data object Transcribing : SpeechEvent
    data class Final(val text: String, val confident: Boolean) : SpeechEvent
    data object SilenceTimeout : SpeechEvent
}

interface VoiceCaptureRepository {
    /** 마이크 청취 시작. 취소되면 청취도 중단된다. */
    fun listen(): Flow<SpeechEvent>

    /**
     * "다 말했어요" — 무음을 기다리지 않고 지금까지 녹음한 것으로 마무리한다.
     * 흐름은 계속 살아 있고, 곧 [SpeechEvent.Final] 이 온다(취소와 다르다).
     */
    fun finishListening() {}

    /** 로컬 큐에 우선 저장. 서버 동기화는 백그라운드에서 진행된다. */
    suspend fun saveLocal(text: String): SavedItem

    suspend fun undo(itemId: String)

    val lastSaved: StateFlow<SavedItem?>
}

interface SearchRepository {
    fun listenQuery(): Flow<SpeechEvent>

    /** [query] 는 키워드가 아니라 자연어 문장이다 — "그 파스타집 어디였지?". */
    suspend fun search(query: String): List<SavedItem>
    val lastQuery: StateFlow<String>
    val lastResults: StateFlow<List<SavedItem>>

    /** AI 가 질문을 무엇으로 이해했는지 — 결과 화면이 함께 보여준다. */
    val lastInterpretation: StateFlow<SearchInterpretation?>
    fun itemById(id: String): SavedItem?
}

sealed interface SongRecognitionEvent {
    data class Listening(val elapsedSeconds: Int) : SongRecognitionEvent
    data class Matched(val song: RecognizedSong) : SongRecognitionEvent
    data object NoMatch : SongRecognitionEvent
}

interface SongRepository {
    /** ShazamKit 스트리밍 인식. 곡을 찾는 즉시 종료된다. */
    fun recognize(): Flow<SongRecognitionEvent>
    suspend fun saveSong(song: RecognizedSong): SavedItem
    suspend fun undo(itemId: String)
    val lastMatched: StateFlow<RecognizedSong?>
}

interface PlaceRepository {
    /**
     * 현재 좌표 획득 → 서버 → Kakao Local 주변 장소 후보 (거리순 최대 30개).
     * 기본은 음식점·카페 위주이고, [expand]가 참이면 전 카테고리로 넓혀 다시 묻는다.
     */
    suspend fun nearbyCandidates(expand: Boolean = false): List<PlaceCandidate>

    /** 마지막 응답이 이미 전 카테고리 검색이었는지 — 참이면 "주변 더 찾기"를 숨긴다. */
    val lastExpanded: StateFlow<Boolean>

    suspend fun savePlace(candidate: PlaceCandidate): SavedItem
    val lastSavedPlace: StateFlow<PlaceCandidate?>
}

interface SyncRepository {
    val status: StateFlow<SyncStatus>
    val pendingItems: StateFlow<List<SavedItem>>
    fun reportLocalSaved(item: SavedItem)
    fun reportSynced(item: SavedItem)
}

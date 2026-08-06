package com.ssafy.woojuin.domain.repository

import com.ssafy.woojuin.domain.model.NearbyAlert
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
     * 엔진이 오디오를 받을 준비를 마쳤다. 이 이벤트 **전에 한 말은 버려진다** —
     * 워치의 온디바이스 엔진은 첫 초기화에 수 초가 걸리므로(AndroidSpeechSource 참고)
     * 화면이 "준비 중"과 "듣는 중"을 갈라 보여줄 근거가 필요하다.
     */
    data object Ready : SpeechEvent
    data class Partial(val text: String, val rms: Float) : SpeechEvent
    data class Final(val text: String, val confident: Boolean) : SpeechEvent
    data object SilenceTimeout : SpeechEvent
}

interface VoiceCaptureRepository {
    /** 마이크 청취 시작. 취소되면 청취도 중단된다. */
    fun listen(): Flow<SpeechEvent>

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

interface NearbyAlertRepository {
    /** 근처에 저장한 장소가 있을 때만 non-null. */
    val activeAlert: StateFlow<NearbyAlert?>
    fun muteToday(placeId: String)
    fun disableAlert(placeId: String)
}

interface SyncRepository {
    val status: StateFlow<SyncStatus>
    val pendingItems: StateFlow<List<SavedItem>>
    fun reportLocalSaved(item: SavedItem)
    fun reportSynced(item: SavedItem)
}

package com.ssafy.woojuin.data.fake

import com.ssafy.woojuin.domain.model.PlaceCandidate
import com.ssafy.woojuin.domain.model.RecognizedSong
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.model.SearchInterpretation
import com.ssafy.woojuin.domain.model.SyncState
import com.ssafy.woojuin.domain.model.SyncStatus
import com.ssafy.woojuin.domain.repository.PlaceRepository
import com.ssafy.woojuin.domain.repository.SearchRepository
import com.ssafy.woojuin.domain.repository.SongRecognitionEvent
import com.ssafy.woojuin.domain.repository.SongRepository
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechSource
import com.ssafy.woojuin.domain.repository.SyncRepository
import com.ssafy.woojuin.domain.repository.VoiceCaptureRepository
import java.util.UUID
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * API 연결 전 전체 UX를 검증하기 위한 Fake 구현.
 * 화면 코드는 인터페이스에만 의존하므로 실서버 연결 시 이 파일만 교체된다.
 */
object FakeData {
    val savedItems = listOf(
        SavedItem(
            id = "item-pasta",
            type = SavedItemType.LINK,
            title = "온화정",
            summary = "성수에서 가볼 파스타집. 웨이팅은 평일 저녁이 낫다",
            savedAtLabel = "5월 12일",
            sourceLabel = "장소 저장",
            memo = "지현이랑 가기로 한 곳",
            distanceLabel = "120m",
        ),
        SavedItem(
            id = "item-redis",
            type = SavedItemType.LINK,
            title = "Redis Streams로 이벤트 파이프라인 만들기",
            summary = "컨슈머 그룹 재처리 전략과 PEL 관리 방법 정리",
            savedAtLabel = "6월 3일",
            sourceLabel = "블로그 링크",
        ),
        SavedItem(
            id = "item-jeju",
            type = SavedItemType.MEMO,
            title = "제주도 여행 메모",
            summary = "협재 근처 스테이, 렌터카는 공항점이 더 저렴",
            savedAtLabel = "4월 28일",
            sourceLabel = "메모",
        ),
        SavedItem(
            id = "item-song",
            type = SavedItemType.LINK,
            title = "Supernova — aespa",
            summary = "어제 카페에서 저장한 노래",
            savedAtLabel = "어제",
            sourceLabel = "노래 찾기",
        ),
    )

    val placeCandidates = listOf(
        PlaceCandidate("p1", "스타벅스 광주장덕점", "카페", 34),
        PlaceCandidate("p2", "롯데마트 수완점", "대형마트", 82),
        PlaceCandidate("p3", "장덕동 손칼국수", "한식", 121),
        PlaceCandidate("p4", "온화정", "양식", 180, alreadySaved = true),
        PlaceCandidate("p5", "수완 호수공원", "공원", 240),
    )

    val song = RecognizedSong(
        title = "Supernova",
        artist = "aespa",
        albumLabel = "Armageddon · 2024",
    )

}

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

class FakeSyncRepository : SyncRepository {
    private val _status = MutableStateFlow(SyncStatus(SyncState.SYNCED, 0))
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _pendingItems = MutableStateFlow<List<SavedItem>>(emptyList())
    override val pendingItems: StateFlow<List<SavedItem>> = _pendingItems.asStateFlow()

    override fun reportLocalSaved(item: SavedItem) {
        _pendingItems.value = _pendingItems.value + item
        _status.value = SyncStatus(SyncState.SYNCING, _pendingItems.value.size)
    }

    override fun reportSynced(item: SavedItem) {
        _pendingItems.value = _pendingItems.value.filterNot { it.id == item.id }
        val remaining = _pendingItems.value.size
        _status.value = SyncStatus(if (remaining == 0) SyncState.SYNCED else SyncState.SYNCING, remaining)
    }
}

/** 부분 인식 텍스트를 점진적으로 흘려보내는 공용 시뮬레이터. */
private fun fakeSpeech(sentence: String, chunkDelayMs: Long = 350L): Flow<SpeechEvent> = flow {
    emit(SpeechEvent.Ready)
    val words = sentence.split(" ")
    var acc = ""
    words.forEachIndexed { index, word ->
        delay(chunkDelayMs)
        acc = if (acc.isEmpty()) word else "$acc $word"
        emit(SpeechEvent.Partial(acc, rms = 0.5f + 0.4f * sin(index.toFloat())))
    }
    delay(500L)
    emit(SpeechEvent.Final(sentence, confident = true))
}

/** Preview·음성 인식 미지원 환경용 SpeechSource. */
class FakeSpeechSource(private val sentence: String) : SpeechSource {
    override fun listen(): Flow<SpeechEvent> = fakeSpeech(sentence)
}

class FakeVoiceCaptureRepository(
    private val sync: SyncRepository,
    private val speech: SpeechSource,
) : VoiceCaptureRepository {
    private val _lastSaved = MutableStateFlow<SavedItem?>(null)
    override val lastSaved: StateFlow<SavedItem?> = _lastSaved.asStateFlow()

    override fun listen(): Flow<SpeechEvent> = speech.listen()

    override fun finishListening() = speech.finishNow()

    override suspend fun saveLocal(text: String): SavedItem {
        // 로컬 저장은 즉시 끝난다 — 서버/AI를 기다리지 않는다.
        val item = SavedItem(
            id = UUID.randomUUID().toString(),
            type = SavedItemType.MEMO,
            title = text,
            summary = "AI가 정리하고 있어요",
            savedAtLabel = "방금",
            sourceLabel = "메모",
        )
        _lastSaved.value = item
        sync.reportLocalSaved(item)
        appScope.launch {
            delay(2500L)
            sync.reportSynced(item)
        }
        return item
    }

    override suspend fun undo(itemId: String) {
        if (_lastSaved.value?.id == itemId) _lastSaved.value = null
    }
}

class FakeSearchRepository(
    private val speech: SpeechSource,
) : SearchRepository {
    private val _lastQuery = MutableStateFlow("")
    override val lastQuery: StateFlow<String> = _lastQuery.asStateFlow()

    private val _lastResults = MutableStateFlow<List<SavedItem>>(emptyList())
    override val lastResults: StateFlow<List<SavedItem>> = _lastResults.asStateFlow()

    private val _lastInterpretation = MutableStateFlow<SearchInterpretation?>(null)
    override val lastInterpretation: StateFlow<SearchInterpretation?> =
        _lastInterpretation.asStateFlow()

    override fun listenQuery(): Flow<SpeechEvent> = speech.listen()

    override suspend fun search(query: String): List<SavedItem> {
        _lastQuery.value = query
        delay(700L)
        val results =
            if (query.isBlank() || query.contains("없는")) emptyList()
            else FakeData.savedItems.take(3)
        _lastResults.value = results
        // 실서버에서는 AI 가 뽑아낸 검색어가 온다 — fake 는 문장에서 조사만 떼는 흉내
        _lastInterpretation.value = query
            .takeIf { it.isNotBlank() }
            ?.let { SearchInterpretation(it.replace("지난번에 저장한 ", ""), aiPlanned = true) }
        return results
    }

    override fun itemById(id: String): SavedItem? =
        FakeData.savedItems.firstOrNull { it.id == id } ?: _lastResults.value.firstOrNull { it.id == id }
}

class FakeSongRepository(private val sync: SyncRepository) : SongRepository {
    private val _lastMatched = MutableStateFlow<RecognizedSong?>(null)
    override val lastMatched: StateFlow<RecognizedSong?> = _lastMatched.asStateFlow()

    override fun recognize(): Flow<SongRecognitionEvent> = flow {
        repeat(3) { second ->
            emit(SongRecognitionEvent.Listening(second))
            delay(1000L)
        }
        _lastMatched.value = FakeData.song
        emit(SongRecognitionEvent.Matched(FakeData.song))
    }

    override suspend fun saveSong(song: RecognizedSong): SavedItem {
        val item = SavedItem(
            id = UUID.randomUUID().toString(),
            type = SavedItemType.LINK,
            title = "${song.title} — ${song.artist}",
            summary = song.albumLabel,
            savedAtLabel = "방금",
            sourceLabel = "노래 찾기",
        )
        sync.reportLocalSaved(item)
        appScope.launch {
            delay(2000L)
            sync.reportSynced(item)
        }
        return item
    }

    override suspend fun undo(itemId: String) {
        _lastMatched.value = null
    }
}

class FakePlaceRepository(private val sync: SyncRepository) : PlaceRepository {
    private val _lastSavedPlace = MutableStateFlow<PlaceCandidate?>(null)
    override val lastSavedPlace: StateFlow<PlaceCandidate?> = _lastSavedPlace.asStateFlow()

    // fake 는 더 찾을 것이 없다 — "주변 더 찾기"가 프리뷰에 뜨지 않게 항상 확장 완료 상태
    override val lastExpanded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    override suspend fun nearbyCandidates(expand: Boolean): List<PlaceCandidate> {
        delay(1400L)
        return FakeData.placeCandidates
    }

    override suspend fun savePlace(candidate: PlaceCandidate): SavedItem {
        val item = SavedItem(
            id = UUID.randomUUID().toString(),
            type = SavedItemType.LINK,
            title = candidate.name,
            summary = "${candidate.category} · ${candidate.distanceLabel}",
            savedAtLabel = "방금",
            sourceLabel = "장소 저장",
        )
        _lastSavedPlace.value = candidate
        sync.reportLocalSaved(item)
        appScope.launch {
            delay(2000L)
            sync.reportSynced(item)
        }
        return item
    }
}

/** 간단한 service locator. 실서버 연결 시 여기서 실제 구현으로 교체한다. */
object Repositories {
    private var appContext: android.content.Context? = null

    /** Application.onCreate에서 호출. Preview에서는 호출되지 않아 Fake로 동작한다. */
    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    /**
     * 음성 저장·검색이 공유하는 인식기 — 손목에서 녹음해 서버로 보낸다
     * ([com.ssafy.woojuin.data.speech.ServerSpeechSource]). 기기 인식기를 쓰지 않는 이유는
     * [com.ssafy.woojuin.data.speech.MicRecorder] javadoc 에 있다.
     *
     * <p>Preview 는 [AppServices] 가 없어 정해진 문장을 흘리는 fake 로 떨어진다.
     */
    private fun speechSource(fallbackSentence: String): SpeechSource =
        if (com.ssafy.woojuin.data.AppServices.initialized) {
            com.ssafy.woojuin.data.speech.ServerSpeechSource(com.ssafy.woojuin.data.AppServices.api)
        } else {
            FakeSpeechSource(fallbackSentence)
        }

    val sync: SyncRepository by lazy { FakeSyncRepository() }

    // 음성 저장·검색도 실서버로 전환됐다(-492). 인식기는 여기가 소유하므로 주입해 넘긴다 —
    // Preview 는 AppServices 가 없어 fake 로 돈다(위치 저장과 같은 폴백)
    val voiceCapture: VoiceCaptureRepository by lazy {
        val speech = speechSource("성수동 파스타집 온화정 다음 주에 가보기")
        if (com.ssafy.woojuin.data.AppServices.initialized) {
            com.ssafy.woojuin.data.AppServices.voiceCapture(speech)
        } else {
            FakeVoiceCaptureRepository(sync, speech)
        }
    }
    val search: SearchRepository by lazy {
        val speech = speechSource("지난번에 저장한 성수동 파스타집")
        if (com.ssafy.woojuin.data.AppServices.initialized) {
            com.ssafy.woojuin.data.AppServices.search(speech)
        } else {
            FakeSearchRepository(speech)
        }
    }
    val song: SongRepository by lazy { FakeSongRepository(sync) }
    // 위치 저장은 실서버로 전환됐다(-458). Preview 는 AppServices 가 없어 fake 로 돈다
    val place: PlaceRepository by lazy {
        if (com.ssafy.woojuin.data.AppServices.initialized) {
            com.ssafy.woojuin.data.AppServices.place
        } else {
            FakePlaceRepository(sync)
        }
    }
}

package com.ssafy.woojuin.data.remote

import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.model.SearchInterpretation
import com.ssafy.woojuin.domain.repository.SearchRepository
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.domain.repository.SpeechSource
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 검색의 실서버 구현 — FakeSearchRepository 의 후임.
 *
 * **AI 모드 검색**(`GET /workspaces/{id}/ai/search`)을 쓴다. 워치는 키보드가 없어 입력이 항상
 * 음성이고, 음성은 "그 파스타집 어디였지" 같은 문장으로 나온다 — 키워드 검색(`/search`)에
 * 그 문장을 그대로 넣으면 조사·군더더기까지 매칭 대상이 돼 결과가 나빠진다.
 */
class RemoteSearchRepository(
    private val api: WoojuinApi,
    private val workspaces: WorkspaceResolver,
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

    override val speechReady: Boolean get() = speech.ready.value

    override suspend fun search(query: String): List<SavedItem> = withContext(Dispatchers.IO) {
        _lastQuery.value = query
        if (query.isBlank()) {
            _lastResults.value = emptyList()
            _lastInterpretation.value = null
            return@withContext emptyList()
        }

        val encoded = URLEncoder.encode(query, "UTF-8")
        val data = api
            .authorizedGet(
                "/workspaces/${workspaces.personalSpaceId()}/ai/search" +
                    "?q=$encoded&page=0&size=$PAGE_SIZE",
            )
            .getJSONObject("data")

        val array = data.getJSONArray("content")
        val items = (0 until array.length()).map { toSavedItem(array.getJSONObject(it)) }

        _lastResults.value = items
        _lastInterpretation.value = data.optString("interpretedQuery", "")
            .takeIf { it.isNotBlank() }
            ?.let { SearchInterpretation(it, aiPlanned = data.optBoolean("aiPlanned", false)) }
        items
    }

    /** 결과 목록에서 찾는다 — 상세로 들어가는 경로가 검색 결과뿐이라 따로 받아올 필요가 없다. */
    override fun itemById(id: String): SavedItem? =
        _lastResults.value.firstOrNull { it.id == id }

    private fun toSavedItem(json: JSONObject): SavedItem {
        val title = text(json, "title")
        val summary = text(json, "summary")
        // 목록 응답에는 본문(content)이 없다 — 페이로드를 줄이려 서버가 뺐고, 대신 조립기가
        // 다듬은 preview.description 을 준다(ItemSummaryResponse javadoc). 처리 중 아이템의
        // 유일한 사람이 읽을 문구라 폴백으로 쓴다
        val previewDescription = json.optJSONObject("preview")?.let { text(it, "description") }
        // 아직 AI 가 돌기 전이면 제목·요약이 비어 온다. "제목 없음"으로 뭉개지 말고
        // 처리 중임을 정직하게 알린다 — 방금 저장한 것을 찾은 사용자가 헷갈리지 않게
        val processing = json.optString("status", "") == "PROCESSING"
        return SavedItem(
            id = json.getLong("itemId").toString(),
            type = savedItemType(json.optString("type", "")),
            title = title.ifBlank { previewDescription?.ifBlank { null } ?: fallbackTitle(processing) },
            summary = summary.ifBlank {
                previewDescription.takeIf { title.isNotBlank() }
                    ?: if (processing) "AI가 정리하고 있어요" else ""
            },
            savedAtLabel = savedAtLabel(json.optString("createdAt", "")),
            sourceLabel = null,
        )
    }

    private fun fallbackTitle(processing: Boolean): String =
        if (processing) "정리 중인 저장물" else "제목 없음"

    /** JSON null 을 문자열 "null" 로 주는 org.json 함정을 피한다 */
    private fun text(json: JSONObject, key: String): String =
        if (json.isNull(key)) "" else json.optString(key, "")

    /** 서버 타입을 그대로 옮긴다 — 워치가 따로 갈라 두는 종류는 없다. */
    private fun savedItemType(serverType: String): SavedItemType = when (serverType) {
        "MEMO" -> SavedItemType.MEMO
        "IMAGE" -> SavedItemType.IMAGE
        else -> SavedItemType.LINK
    }

    /** "8월 6일" 또는 오늘이면 "오늘". 워치 카드가 좁아 연도는 넣지 않는다. */
    private fun savedAtLabel(createdAt: String): String {
        val instant = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return ""
        val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
        return when {
            date == today -> "오늘"
            date == today.minusDays(1) -> "어제"
            else -> date.format(DATE_LABEL)
        }
    }

    companion object {
        /**
         * 결과 화면이 상위 3개(히어로 1 + 2)만 보여주므로 그만큼만 받는다 — 워치 응답이
         * 블루투스 프록시를 탈 수 있어서 안 쓸 것을 받아 둘 이유가 없다. 화면이 더 많이
         * 보여주게 되면 같이 올린다.
         */
        private const val PAGE_SIZE = 3
        private val DATE_LABEL = DateTimeFormatter.ofPattern("M월 d일")
    }
}

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
        val url = json.optString("url", "").takeIf { it.isNotBlank() && !json.isNull("url") }
        val title = json.optString("title", "").takeIf { !json.isNull("title") } ?: ""
        val summary = json.optString("summary", "").takeIf { !json.isNull("summary") } ?: ""
        return SavedItem(
            id = json.getLong("itemId").toString(),
            type = savedItemType(json.optString("type", ""), url),
            // 저장 직후엔 AI 가 아직 제목을 못 붙였을 수 있다 — 빈 제목으로 카드가 비지 않게 채운다
            title = title.ifBlank { summary.ifBlank { "제목 없음" } },
            summary = summary.ifBlank { "AI가 정리하고 있어요" },
            savedAtLabel = savedAtLabel(json.optString("createdAt", "")),
            sourceLabel = null,
        )
    }

    /**
     * 서버 타입(URL·IMAGE·MEMO)을 워치 화면의 종류로 옮긴다. 완전히 겹치지 않는다 —
     * 워치는 아이콘·문구를 고르려고 VOICE/LINK/SONG/PLACE 로 나눠 두었기 때문이다.
     *
     * URL 은 링크와 장소 저장이 공유한다(장소는 카카오맵 링크로 저장된다). 그래서 호스트를
     * 보고 장소를 갈라낸다 — 안 그러면 워치에서 저장한 장소가 검색 결과에서 링크로 보인다.
     */
    private fun savedItemType(serverType: String, url: String?): SavedItemType = when {
        serverType == "MEMO" -> SavedItemType.VOICE
        serverType == "URL" && url != null && KAKAO_PLACE_HOSTS.any { url.contains(it) } ->
            SavedItemType.PLACE
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
        private val KAKAO_PLACE_HOSTS = listOf("place.map.kakao.com", "map.kakao.com")
    }
}

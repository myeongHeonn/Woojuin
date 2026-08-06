package com.ssafy.woojuin.domain.model

/**
 * 화면이 아이콘·문구를 고르는 기준. 서버의 `ItemType`(URL·IMAGE·MEMO)과 일부러 다르다 —
 * 워치는 "이걸 어떻게 저장했는지"를 보여주는데, 서버는 "무엇을 저장했는지"만 안다.
 *
 * [VOICE] 는 **워치가 방금 음성으로 저장한 것**에만 쓴다. 서버에서 받아온 메모는 타이핑한
 * 것일 수도 있어(서버가 구분하지 않는다) [MEMO] 다 — 마이크를 붙이면 거짓이 된다.
 */
enum class SavedItemType { VOICE, MEMO, LINK, SONG, PLACE }

data class SavedItem(
    val id: String,
    val type: SavedItemType,
    val title: String,
    val summary: String,
    val savedAtLabel: String,
    val sourceLabel: String? = null,
    val memo: String? = null,
    val distanceLabel: String? = null,
)

/**
 * AI 검색이 질문을 무엇으로 이해했는지. **결과와 함께 반드시 보여준다** — 결과가 예상과
 * 다를 때 사용자가 원인을 알 수 있어야 하고, 워치는 화면이 좁아 결과만 보면 짐작할 길이
 * 아예 없다(서버 DTO 주석의 요구사항).
 *
 * [aiPlanned] 가 false 면 LLM 호출이 실패해 규칙 기반으로 폴백한 것이다 — 오타 교정·관련어
 * 확장이 적용되지 않았다는 뜻이라 결과가 빈약해도 이상한 게 아니다.
 */
data class SearchInterpretation(
    val query: String,
    val aiPlanned: Boolean,
)

data class PlaceCandidate(
    val id: String,
    val name: String,
    val category: String,
    val distanceMeters: Int,
    val alreadySaved: Boolean = false,
    // 실서버 저장에 그대로 실리는 값들 — fake 는 기본값으로 둔다 (화면은 안 쓴다)
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String? = null,
    val placeUrl: String? = null,
) {
    val distanceLabel: String get() = "${distanceMeters}m"
}

data class RecognizedSong(
    val title: String,
    val artist: String,
    val albumLabel: String,
)

data class NearbyAlert(
    val placeId: String,
    val placeName: String,
    val distanceMeters: Int,
    val summary: String,
    val memo: String?,
    val savedAtLabel: String,
    val lowAccuracy: Boolean = false,
)

enum class SyncState { SYNCED, SYNCING, PENDING, OFFLINE, FAILED }

data class SyncStatus(
    val state: SyncState = SyncState.SYNCED,
    val pendingCount: Int = 0,
)

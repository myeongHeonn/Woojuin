package com.ssafy.woojuin.domain.model

/**
 * 화면이 아이콘·문구를 고르는 기준. **서버 `ItemType`(URL·IMAGE·MEMO)과 1:1 이다.**
 *
 * 워치가 따로 갈라 두지 않는다 — 음성으로 저장한 것도, 장소로 저장한 것도 서버에는 각각
 * MEMO·URL 로 들어간다. 워치만 아는 구분을 만들면 같은 아이템이 화면마다 달라 보이고
 * (저장 직후엔 장소, 검색 결과에선 링크), 서버가 실제로 무엇을 들고 있는지도 흐려진다.
 *
 * 노래는 없다 — 서버에 그런 타입이 없어 실데이터로는 나올 수 없다. 노래 인식(FR-055)이
 * 실제로 붙고 서버가 그걸 어떻게 저장할지 정해질 때 다시 본다.
 */
enum class SavedItemType { MEMO, LINK, IMAGE }

data class SavedItem(
    val id: String,
    val type: SavedItemType,
    val title: String,
    val summary: String,
    val savedAtLabel: String,
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
    // 실서버 저장에 그대로 실리는 값들 — fake 는 기본값으로 둔다 (화면은 안 쓴다)
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String? = null,
    val placeUrl: String? = null,
) {
    val distanceLabel: String get() = "${distanceMeters}m"
}

/**
 * 인식된 곡. **앨범·발매년은 담지 않는다** — 쓰는 인식 API 가 곡 제목과 아티스트만 주고,
 * 예전에는 빈 문자열을 채워 두어 화면이 있지도 않은 줄을 그릴 준비를 하고 있었다.
 */
data class RecognizedSong(
    val title: String,
    val artist: String,
    /** 곡 페이지 링크 — 저장의 재료이자 "휴대폰에서 열기"의 대상이다 */
    val link: String? = null,
)

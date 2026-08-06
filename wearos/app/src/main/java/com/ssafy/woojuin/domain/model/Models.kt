package com.ssafy.woojuin.domain.model

enum class SavedItemType { VOICE, LINK, SONG, PLACE }

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

enum class SyncState { SYNCED, SYNCING, PENDING, OFFLINE, FAILED }

data class SyncStatus(
    val state: SyncState = SyncState.SYNCED,
    val pendingCount: Int = 0,
)

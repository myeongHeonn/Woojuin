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

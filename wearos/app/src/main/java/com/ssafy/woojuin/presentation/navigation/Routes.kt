package com.ssafy.woojuin.presentation.navigation

object Routes {
    const val SPLASH = "splash"
    const val LINK = "link"
    const val HOME = "home"

    const val VOICE_CAPTURE = "voiceCapture"
    const val VOICE_SAVE_SUCCESS = "voiceSaveSuccess"

    const val VOICE_SEARCH = "voiceSearch"
    const val VOICE_SEARCH_RESULTS = "voiceSearchResults"
    const val SAVED_ITEM_DETAIL = "savedItemDetail/{itemId}"
    fun savedItemDetail(itemId: String) = "savedItemDetail/$itemId"

    const val SONG_RECOGNITION = "songRecognition"
    const val SONG_RESULT = "songResult"

    const val PLACE_PICKER = "placePicker"
    const val PLACE_SAVE_SUCCESS = "placeSaveSuccess"

    const val NEARBY_PLACE_DETAIL = "nearbyPlaceDetail"

    const val OFFLINE_QUEUE = "offlineQueue"
    const val PERMISSION_GUIDE = "permissionGuide/{feature}"
    fun permissionGuide(feature: String) = "permissionGuide/$feature"
    const val OPEN_ON_PHONE = "openOnPhone"
}

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

    const val OFFLINE_QUEUE = "offlineQueue"
    const val PERMISSION_GUIDE = "permissionGuide/{feature}"
    fun permissionGuide(feature: String) = "permissionGuide/$feature"
    /**
     * 휴대폰에서 열기 결과. **성공 여부를 경로에 담는다** — 화면이 "열었어요"와
     * "연결된 휴대폰이 없어요"를 갈라 말해야 하고, 그 판단은 실제 호출 결과다.
     */
    const val OPEN_ON_PHONE = "openOnPhone/{opened}"
    fun openOnPhone(opened: Boolean) = "openOnPhone/$opened"
}

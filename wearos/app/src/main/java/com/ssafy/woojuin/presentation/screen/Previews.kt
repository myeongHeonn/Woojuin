package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.tooling.preview.devices.WearDevices
import com.ssafy.woojuin.presentation.component.LogoFrame
import com.ssafy.woojuin.presentation.component.WoojuinLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.theme.WoojuinTheme

/**
 * 화면별 원형 Preview.
 * 192dp(SMALL_ROUND)와 227dp+(LARGE_ROUND) 원형 화면을 모두 검증한다.
 */

@Composable
private fun PreviewShell(content: @Composable () -> Unit) {
    WoojuinTheme {
        AppScaffold {
            content()
        }
    }
}

// ─── 로고 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewStaticLogo() = PreviewShell {
    WoojuinStatusScreen {
        WoojuinLogo(modifier = Modifier.size(96.dp))
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewLaunchMidMotion() = PreviewShell {
    WoojuinStatusScreen {
        // 실행 모션 중간 프레임 (링 성장 중, 스파클 궤도 이동 중, 방사광 미노출)
        WoojuinLogo(
            modifier = Modifier.size(96.dp),
            frame = LogoFrame(
                discRadius = 11f,
                ringRadius = 7.5f,
                sparkleProgress = 0.45f,
                glowAlpha = 0f,
            ),
        )
    }
}

// ─── 홈 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewHome() = PreviewShell {
    HomeScreen(
        onVoiceCapture = {},
        onSearch = {},
        onSong = {},
        onPlace = {},
        onSyncStatus = {},
    )
}

// ─── 음성 저장 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewVoiceCapture() = PreviewShell {
    VoiceCaptureScreen(onSaved = {}, onBack = {})
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewVoiceSaveSuccess() = PreviewShell {
    VoiceSaveSuccessScreen(onDone = {})
}

// ─── 검색 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewVoiceSearch() = PreviewShell {
    VoiceSearchScreen(onResults = {})
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewSearchResults() = PreviewShell {
    VoiceSearchResultsScreen(onItem = {}, onRetry = {}, onOpenOnPhone = {})
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewSavedItemDetail() = PreviewShell {
    SavedItemDetailScreen(itemId = "item-pasta", onOpenOnPhone = {})
}

// ─── 노래 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewSongRecognition() = PreviewShell {
    SongRecognitionScreen(onSaved = {}, onCancel = {})
}

// ─── 장소 ───

// 위치 획득 화면은 PlacePickerScreen의 Locating 상태로 흡수됐다 — 픽커 프리뷰가
// fake 리포지토리의 1.4초 지연 동안 그 상태를 그대로 보여준다.
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PreviewPlacePicker() = PreviewShell {
    PlacePickerScreen(
        onSaved = {},
        onExistingItem = {},
        onVoiceCapture = {},
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewPlaceSaveSuccess() = PreviewShell {
    PlaceSaveSuccessScreen(onDone = {})
}

// ─── 주변 장소 / 기타 ───

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewOfflineQueue() = PreviewShell {
    OfflineQueueScreen()
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewPermissionGuideMic() = PreviewShell {
    PermissionGuideScreen(feature = PermissionFeature.MIC, onBack = {})
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewPermissionGuideLocation() = PreviewShell {
    PermissionGuideScreen(feature = PermissionFeature.LOCATION, onBack = {})
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun PreviewOpenOnPhone() = PreviewShell {
    OpenOnPhoneScreen(onDone = {})
}
package com.ssafy.woojuin.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ssafy.woojuin.presentation.component.WoojuinLaunchMotion
import com.ssafy.woojuin.presentation.screen.HomeScreen
import com.ssafy.woojuin.presentation.screen.NearbyPlaceDetailScreen
import com.ssafy.woojuin.presentation.screen.OfflineQueueScreen
import com.ssafy.woojuin.presentation.screen.OpenOnPhoneScreen
import com.ssafy.woojuin.presentation.screen.PermissionFeature
import com.ssafy.woojuin.presentation.screen.PermissionGuideScreen
import com.ssafy.woojuin.presentation.screen.PlaceLocatingScreen
import com.ssafy.woojuin.presentation.screen.PlacePickerScreen
import com.ssafy.woojuin.presentation.screen.PlacePickerViewModel
import com.ssafy.woojuin.presentation.screen.PlaceSaveSuccessScreen
import com.ssafy.woojuin.presentation.screen.SavedItemDetailScreen
import com.ssafy.woojuin.presentation.screen.SongRecognitionScreen
import com.ssafy.woojuin.presentation.screen.SongResultScreen
import com.ssafy.woojuin.presentation.screen.VoiceCaptureScreen
import com.ssafy.woojuin.presentation.screen.VoiceSaveSuccessScreen
import com.ssafy.woojuin.presentation.screen.VoiceSearchResultsScreen
import com.ssafy.woojuin.presentation.screen.VoiceSearchScreen

/**
 * 오른쪽 스와이프로 항상 이전 화면으로 돌아간다.
 * 고정 하단 내비게이션은 사용하지 않는다.
 */
@Composable
fun WoojuinNavHost(
    startDestination: String,
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    fun backToHome() {
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SPLASH) {
            WoojuinLaunchMotion(onFinished = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onVoiceCapture = { navController.navigate(Routes.VOICE_CAPTURE) },
                onSearch = { navController.navigate(Routes.VOICE_SEARCH) },
                onSong = { navController.navigate(Routes.SONG_RECOGNITION) },
                onPlace = { navController.navigate(Routes.PLACE_LOCATING) },
                onNearby = { navController.navigate(Routes.NEARBY_PLACE_DETAIL) },
                onSyncStatus = { navController.navigate(Routes.OFFLINE_QUEUE) },
            )
        }

        composable(Routes.VOICE_CAPTURE) {
            VoiceCaptureScreen(
                onSaved = {
                    navController.navigate(Routes.VOICE_SAVE_SUCCESS) {
                        popUpTo(Routes.VOICE_CAPTURE) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VOICE_SAVE_SUCCESS) {
            VoiceSaveSuccessScreen(onDone = {
                if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.VOICE_SAVE_SUCCESS) { inclusive = true }
                    }
                }
            })
        }

        composable(Routes.VOICE_SEARCH) {
            VoiceSearchScreen(
                onResults = {
                    navController.navigate(Routes.VOICE_SEARCH_RESULTS) {
                        popUpTo(Routes.VOICE_SEARCH) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VOICE_SEARCH_RESULTS) {
            VoiceSearchResultsScreen(
                onItem = { id -> navController.navigate(Routes.savedItemDetail(id)) },
                onRetry = { navController.navigate(Routes.VOICE_SEARCH) },
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
            )
        }
        composable(Routes.SAVED_ITEM_DETAIL) { backStackEntry ->
            SavedItemDetailScreen(
                itemId = backStackEntry.arguments?.getString("itemId").orEmpty(),
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
                onRetry = { navController.navigate(Routes.VOICE_SEARCH) },
            )
        }

        composable(Routes.SONG_RECOGNITION) {
            SongRecognitionScreen(
                onSaved = {
                    navController.navigate(Routes.SONG_RESULT) {
                        popUpTo(Routes.SONG_RECOGNITION) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.SONG_RESULT) {
            SongResultScreen(
                onUndoDone = { backToHome() },
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
                onRetry = { navController.navigate(Routes.SONG_RECOGNITION) },
            )
        }

        composable(Routes.PLACE_LOCATING) {
            PlaceLocatingScreen(
                onCandidates = {
                    navController.navigate(Routes.PLACE_PICKER) {
                        popUpTo(Routes.PLACE_LOCATING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PLACE_PICKER) {
            PlacePickerScreen(
                onSaved = {
                    navController.navigate(Routes.PLACE_SAVE_SUCCESS) {
                        popUpTo(Routes.PLACE_PICKER) { inclusive = true }
                    }
                },
                onExistingItem = { id -> navController.navigate(Routes.savedItemDetail(id)) },
                onVoiceCapture = { navController.navigate(Routes.VOICE_CAPTURE) },
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
            )
        }
        composable(Routes.PLACE_SAVE_SUCCESS) {
            PlaceSaveSuccessScreen(
                onDone = { backToHome() },
                onVoiceMemo = { navController.navigate(Routes.VOICE_CAPTURE) },
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
            )
        }

        composable(Routes.NEARBY_PLACE_DETAIL) {
            NearbyPlaceDetailScreen(
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
                onDisabled = { backToHome() },
            )
        }

        composable(Routes.OFFLINE_QUEUE) { OfflineQueueScreen() }
        composable(Routes.PERMISSION_GUIDE) { backStackEntry ->
            PermissionGuideScreen(
                feature = PermissionFeature.from(backStackEntry.arguments?.getString("feature")),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.OPEN_ON_PHONE) {
            OpenOnPhoneScreen(onDone = { navController.popBackStack() })
        }
    }
}
package com.ssafy.woojuin.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ssafy.woojuin.presentation.component.WoojuinLaunchMotion
import com.ssafy.woojuin.presentation.screen.HomeScreen
import com.ssafy.woojuin.presentation.screen.LinkScreen
import com.ssafy.woojuin.presentation.screen.OfflineQueueScreen
import com.ssafy.woojuin.presentation.screen.OpenOnPhoneScreen
import com.ssafy.woojuin.presentation.screen.PermissionFeature
import com.ssafy.woojuin.presentation.screen.PermissionGuideScreen
import com.ssafy.woojuin.presentation.screen.PlacePickerScreen
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
    /**
     * 스플래시가 끝난 뒤 갈 곳 — 토큰이 있으면 HOME, 없으면 LINK (MainActivity 가 정한다).
     * suspend 인 이유: 콜드 스타트에서는 토큰 읽기를 모션이 도는 동안으로 미루므로
     * 여기서 그 결과를 기다린다(모션 1.3초 안에 이미 끝나 있어 실제 대기는 없다).
     */
    postSplashDestination: suspend () -> String = { Routes.HOME },
    navController: NavHostController = rememberSwipeDismissableNavController(),
) {
    fun backToHome() {
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    // 웹 기기 관리에서 이 워치를 해제하면(-460) 다음 요청이 거부되며 토큰이 지워진다.
    // 그 순간을 여기서 받아 재시작 없이 링크 화면으로 보낸다 — AC "해제하면 로그인 상태를 잃는다".
    if (com.ssafy.woojuin.data.AppServices.initialized) {
        val loggedIn by com.ssafy.woojuin.data.AppServices.auth.isLoggedIn
            .collectAsState(initial = true)
        LaunchedEffect(loggedIn) {
            val route = navController.currentDestination?.route
            if (!loggedIn && route != Routes.LINK && route != Routes.SPLASH) {
                navController.navigate(Routes.LINK) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.SPLASH) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            WoojuinLaunchMotion(onFinished = {
                scope.launch {
                    val destination = postSplashDestination()
                    navController.navigate(destination) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            })
        }

        composable(Routes.LINK) {
            LinkScreen(onLinked = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LINK) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onVoiceCapture = { navController.navigate(Routes.VOICE_CAPTURE) },
                onSearch = { navController.navigate(Routes.VOICE_SEARCH) },
                onSong = { navController.navigate(Routes.SONG_RECOGNITION) },
                onPlace = { navController.navigate(Routes.PLACE_PICKER) },
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
                onOpenOnPhone = { navController.navigate(Routes.OPEN_ON_PHONE) },
                onRetry = { navController.navigate(Routes.SONG_RECOGNITION) },
            )
        }

        // 위치 획득(구 PLACE_LOCATING)은 PlacePickerScreen 안의 화면 상태가 됐다 —
        // destination 을 나누면 ViewModel 이 갈라져 후보 목록이 신선하다는 보장이 깨진다
        composable(Routes.PLACE_PICKER) {
            PlacePickerScreen(
                onSaved = {
                    navController.navigate(Routes.PLACE_SAVE_SUCCESS) {
                        popUpTo(Routes.PLACE_PICKER) { inclusive = true }
                    }
                },
                onExistingItem = { id -> navController.navigate(Routes.savedItemDetail(id)) },
                onVoiceCapture = { navController.navigate(Routes.VOICE_CAPTURE) },
            )
        }
        composable(Routes.PLACE_SAVE_SUCCESS) {
            PlaceSaveSuccessScreen(
                onDone = { backToHome() },
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
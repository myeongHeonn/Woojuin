package com.ssafy.woojuin.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material3.AppScaffold
import com.ssafy.woojuin.data.AppServices
import com.ssafy.woojuin.presentation.navigation.Routes
import com.ssafy.woojuin.presentation.navigation.WoojuinNavHost
import com.ssafy.woojuin.presentation.screen.OfflineGateScreen
import com.ssafy.woojuin.presentation.theme.WoojuinTheme

/** 프로세스 단위 콜드 스타트 추적 — 웜 스타트에서는 로고 모션을 반복하지 않는다. */
private object LaunchState {
    var coldStartConsumed = false
}

class MainActivity : ComponentActivity() {

    companion object {
        /** Tile/Complication에서 특정 화면으로 직접 진입할 때 사용하는 extra. */
        const val EXTRA_ROUTE = "com.ssafy.woojuin.extra.ROUTE"
        const val ROUTE_VOICE = "voice"
        const val ROUTE_SEARCH = "search"
        const val ROUTE_SONG = "song"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val deepLink = when (intent.getStringExtra(EXTRA_ROUTE)) {
            ROUTE_VOICE -> Routes.VOICE_CAPTURE
            ROUTE_SEARCH -> Routes.VOICE_SEARCH
            ROUTE_SONG -> Routes.SONG_RECOGNITION
            else -> null
        }

        val isColdStart = !LaunchState.coldStartConsumed
        LaunchState.coldStartConsumed = true

        // 로그인 게이트 — 토큰이 없으면 어디로 들어와도(딥링크 포함) 링크 화면이다.
        //
        // 콜드 스타트로 스플래시를 지나는 경우에만 이 읽기를 뒤로 미룬다. 목적지가 필요한
        // 시점은 1.3초 모션이 끝난 뒤인데, 디스크(DataStore) 동기 읽기를 시작 경로에서
        // 하면 첫 프레임이 실측 300ms 늦어졌다. 딥링크(타일)와 웜 스타트는 첫 목적지가
        // 곧바로 필요하므로 그대로 동기로 읽는다 — 그때는 이미 캐시가 더워 저렴하다.
        val startDestination: String
        val postSplash: suspend () -> String
        if (isColdStart && deepLink == null) {
            startDestination = Routes.SPLASH
            val loggedInAsync = lifecycleScope.async(Dispatchers.IO) {
                AppServices.tokenStore.hasTokensBlocking()
            }
            postSplash = { if (loggedInAsync.await()) Routes.HOME else Routes.LINK }
        } else {
            val loggedIn = AppServices.tokenStore.hasTokensBlocking()
            // 딥링크 진입은 스플래시 모션을 건너뛰고 기능으로 직행한다.
            startDestination = when {
                !loggedIn -> if (isColdStart) Routes.SPLASH else Routes.LINK
                deepLink != null -> deepLink
                isColdStart -> Routes.SPLASH
                else -> Routes.HOME
            }
            val target = if (loggedIn) Routes.HOME else Routes.LINK
            postSplash = { target }
        }

        setContent {
            WoojuinApp(startDestination = startDestination, postSplashDestination = postSplash)
        }
    }
}

@Composable
fun WoojuinApp(
    startDestination: String,
    postSplashDestination: suspend () -> String = { Routes.HOME },
) {
    WoojuinTheme {
        AppScaffold {
            Box {
                WoojuinNavHost(
                    startDestination = startDestination,
                    postSplashDestination = postSplashDestination,
                )
                // 오프라인 게이트 — 내비게이션 위에 덮기만 해서, 연결이 돌아오면
                // 하던 자리 그대로 이어진다 (Preview 는 AppServices 가 없어 게이트 없음)
                if (AppServices.initialized) {
                    val online by AppServices.connectivity.online.collectAsState()
                    if (!online) {
                        OfflineGateScreen()
                    }
                }
            }
        }
    }
}
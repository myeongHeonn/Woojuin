package com.ssafy.woojuin.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.compose.material3.AppScaffold
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.presentation.navigation.Routes
import com.ssafy.woojuin.presentation.navigation.WoojuinNavHost
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

        // 인식기 바인딩을 미리 데워 로고 탭 → 청취 시작 지연을 줄인다.
        Repositories.warmUpSpeech()

        val deepLink = when (intent.getStringExtra(EXTRA_ROUTE)) {
            ROUTE_VOICE -> Routes.VOICE_CAPTURE
            ROUTE_SEARCH -> Routes.VOICE_SEARCH
            ROUTE_SONG -> Routes.SONG_RECOGNITION
            else -> null
        }

        val isColdStart = !LaunchState.coldStartConsumed
        LaunchState.coldStartConsumed = true

        // 딥링크 진입은 스플래시 모션을 건너뛰고 기능으로 직행한다.
        val startDestination = deepLink ?: if (isColdStart) Routes.SPLASH else Routes.HOME

        setContent {
            WoojuinApp(startDestination = startDestination)
        }
    }
}

@Composable
fun WoojuinApp(startDestination: String) {
    WoojuinTheme {
        AppScaffold {
            WoojuinNavHost(startDestination = startDestination)
        }
    }
}
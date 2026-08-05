package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.theme.WoojuinColor

/**
 * 오프라인 게이트 — 통신이 없으면 앱 전체를 이 화면 하나로 막는다.
 *
 * 워치는 오프라인에서 할 수 있는 일이 없어(로컬 큐 없음) 화면마다 실패를 흩뿌리는
 * 대신 여기서 정직하게 알린다. 내비게이션을 갈아타지 않고 위에 덮기만 하므로,
 * 연결이 돌아오면 하던 자리 그대로 이어진다 — 토큰도 그대로라 재로그인이 없다.
 */
@Composable
fun OfflineGateScreen() {
    WoojuinStatusScreen {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = WoojuinColor.TextMuted,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "인터넷 연결이 필요해요",
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        CaptionText("연결되면 자동으로 이어집니다")
    }
}

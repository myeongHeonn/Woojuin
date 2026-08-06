package com.ssafy.woojuin.presentation.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.presentation.component.SpaceBackdrop
import com.ssafy.woojuin.presentation.component.WoojuinLogo
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.util.rememberHaptics
import kotlin.math.cos
import kotlin.math.sin

/**
 * 홈 — **궤도 허브**. 화면 전체가 우주인 로고다.
 *
 * <p>로고는 고리를 두른 행성이다. 그 그림을 화면 크기로 키워서, 가운데에 실제 로고가 앉고
 * 기능이 궤도 위 위성으로 앉는다. 목록(알약 행)을 버린 이유는 셋이다.
 *
 * <ul>
 *   <li><b>목록은 사각 화면의 문법이다.</b> 둥근 화면에서는 위·아래 항목이 곡면에 잘리고
 *       스크롤 없이는 한두 개만 보였다. 원 위에 놓으면 네 기능이 한 화면에 다 들어온다
 *   <li><b>기본 컴포넌트만 쓰면 어느 앱이든 똑같이 보인다.</b> 이 배치는 이 앱에서만 나온다
 *   <li><b>가운데는 로고다.</b> 보라색 원으로 채워 봤더니 브랜드가 사라지고 그냥 버튼이
 *       됐다. 로고 자체가 이 화면의 주 동작(말해서 저장)을 받는다
 * </ul>
 *
 * <p><b>위성에 테두리도 채움도 두지 않는다.</b> 원 안에 아이콘과 글자를 담아 봤더니 큰
 * 단추 세 개가 궤도에 매달린 꼴이 됐다. 아이콘이 기능 색 빛만 두르고 궤도선 위에 앉으면,
 * 고리가 그 셋을 묶어 주므로 테두리가 할 일이 없다.
 *
 * <p><b>치수는 전부 화면 폭의 비율이다.</b> 처음엔 dp 를 고정값으로 잡았다가 화면이
 * 454dp 인 줄 알고 52dp 원을 썼는데, 실제 폭은 에뮬레이터 227dp·실기기 198dp 였다
 * (454·396 은 픽셀, 밀도 320dpi = 2x). 원 하나가 화면의 1/4 을 먹어 전부 겹쳤다.
 */
@Composable
fun HomeScreen(
    onVoiceCapture: () -> Unit,
    onSearch: () -> Unit,
    onSong: () -> Unit,
    onPlace: () -> Unit,
) {
    val haptics = rememberHaptics()
    val w = LocalConfiguration.current.screenWidthDp.dp

    // 궤도 반지름과 로고 크기. 198dp(실기기)에서 아래 위성의 라벨 끝이 화면 반지름 99dp
    // 안쪽 82dp 에 들어오고, 로고와 위성 사이가 7dp 떨어진다
    val orbitRadius = w * 0.32f
    val logoSize = w * 0.36f

    ScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WoojuinColor.SpaceBlack),
            contentAlignment = Alignment.Center,
        ) {
            SpaceBackdrop(orbitRadius = orbitRadius)

            LogoHub(size = logoSize, onClick = { haptics.tapStart(); onVoiceCapture() })

            // 위성 — 각도는 12시(0°)에서 시계 방향. 아래쪽에 삼각형으로 벌린다.
            // 12시 부근은 시스템 시계 자리라 비워 둔다(워치의 관례를 깨지 않는다)
            OrbitNode(
                angleDegrees = 255f,
                radius = orbitRadius,
                iconSize = w * 0.10f,
                icon = Icons.Rounded.Search,
                accent = WoojuinColor.SearchAccent,
                label = "찾기",
                onClick = { haptics.tapStart(); onSearch() },
            )
            OrbitNode(
                angleDegrees = 180f,
                radius = orbitRadius,
                iconSize = w * 0.10f,
                icon = Icons.Rounded.MusicNote,
                accent = WoojuinColor.SongAccent,
                label = "노래",
                onClick = { haptics.tapStart(); onSong() },
            )
            OrbitNode(
                angleDegrees = 105f,
                radius = orbitRadius,
                iconSize = w * 0.10f,
                icon = Icons.Rounded.Place,
                accent = WoojuinColor.PlaceAccent,
                label = "장소",
                onClick = { haptics.tapStart(); onPlace() },
            )
        }
    }
}

/**
 * 가운데 로고 = 말해서 저장.
 *
 * <p>워치에서 제일 많이 하는 일이므로 조준이 필요 없는 표적이어야 한다 — 화면 가운데는
 * 손목을 보지 않고도 누를 수 있는 유일한 자리다. 로고만 두면 브랜드 그림으로 보여
 * 아무도 누르지 않으므로 바로 아래에 무엇을 하는 자리인지 적는다.
 */
@Composable
private fun LogoHub(size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "hubPress")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "말해서 저장",
            ) { onClick() },
    ) {
        WoojuinLogo(modifier = Modifier.size(size))
        // 로고 자산은 사방에 빈 여백을 두고 그려진다(궤도 타원이 상자의 2/3 만 차지한다) —
        // 그대로 아래에 글자를 붙이면 로고와 떨어져 위성 라벨과 한 줄처럼 보였다. 여백만큼
        // 끌어올려 로고에 붙인다
        Text(
            text = "말해서 저장",
            style = MaterialTheme.typography.labelMedium,
            color = WoojuinColor.TextPrimary,
            maxLines = 1,
            modifier = Modifier.offset(y = -size * 0.15f),
        )
    }
}

/**
 * 궤도 위의 기능 하나 — 기능 색 빛을 두른 아이콘과 두 글자 라벨.
 *
 * <p>채움도 테두리도 없다. 궤도선이 이미 셋을 하나로 묶고 있어서, 각자 원을 두르면
 * 고리에 단추를 매단 꼴이 된다. 대신 아이콘 뒤에 기능 색 방사 그라데이션을 아주 옅게
 * 깔아 궤도 위에서 빛나는 점처럼 보이게 한다.
 *
 * <p>탭 영역은 보이는 것보다 넓다(48dp 원) — 워치에서 아이콘 크기 그대로면 못 누른다.
 *
 * @param angleDegrees 12시(0°)에서 시계 방향 각도
 */
@Composable
private fun OrbitNode(
    angleDegrees: Float,
    radius: Dp,
    iconSize: Dp,
    icon: ImageVector,
    accent: Color,
    label: String,
    onClick: () -> Unit,
) {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = radius * sin(radians).toFloat()
    // 화면 좌표는 아래가 +y 이므로 코사인의 부호를 뒤집는다
    val dy = radius * -cos(radians).toFloat()

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "nodePress")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .offset(x = dx, y = dy)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(iconSize * 2.0f)
                .background(
                    Brush.radialGradient(
                        0f to accent.copy(alpha = 0.30f),
                        0.5f to accent.copy(alpha = 0.09f),
                        1f to Color.Transparent,
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WoojuinColor.TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // 빛 상자는 아이콘보다 넓다 — 그만큼 라벨이 멀어져 아이콘과 남남처럼 보였다
            modifier = Modifier.offset(y = -iconSize * 0.28f),
        )
    }
}

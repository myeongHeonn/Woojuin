package com.ssafy.woojuin.presentation.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.GlassButton
import com.ssafy.woojuin.presentation.component.WoojuinEdgeButton
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.theme.WoojuinColor

/** 권한 안내 화면이 어느 기능 이야기를 할지. 화면이 직접 부르므로 경로 인자가 없다. */
enum class PermissionFeature { MIC, LOCATION }

/**
 * 권한 안내 — 필요한 순간에만, 기능 중심 문구로.
 * 거절돼도 앱 전체를 막지 않고 설정 이동만 안내한다.
 */
@Composable
fun PermissionGuideScreen(feature: PermissionFeature, onBack: () -> Unit) {
    val context = LocalContext.current
    val (icon, tint, title, description) = when (feature) {
        PermissionFeature.MIC -> Quad(
            Icons.Rounded.Mic,
            WoojuinColor.AccentPurple,
            "마이크 권한이 필요해요",
            "말한 내용을 저장하거나 노래를 찾을 때만 마이크를 사용해요.",
        )
        PermissionFeature.LOCATION -> Quad(
            Icons.Rounded.LocationOn,
            WoojuinColor.PlaceAccent,
            "위치 권한이 필요해요",
            "지금 있는 곳을 저장할 때만 위치를 사용해요.",
        )
    }

    WoojuinStatusScreen(
        glowColor = tint,
        edgeButton = {
            WoojuinEdgeButton(
                label = "설정 열기",
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                primary = true,
            )
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        CaptionText(description, color = WoojuinColor.TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(label = "나중에", onClick = onBack)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * 휴대폰에서 열기 결과.
 *
 * **예전에는 무엇을 하든 "휴대폰으로 보냈어요"라고 했다** — 실제로는 아무것도 보내지 않는
 * 화면이었다. 지금은 [com.ssafy.woojuin.presentation.util.PhoneLauncher] 의 호출 결과를
 * 받아 성공·실패를 갈라 말한다(페어링이 없으면 실패한다).
 */
@Composable
fun OpenOnPhoneScreen(opened: Boolean, onDone: () -> Unit) {
    WoojuinStatusScreen(
        glowColor = if (opened) WoojuinColor.StarBlue else WoojuinColor.TextMuted,
        edgeButton = { WoojuinEdgeButton(label = "확인", onClick = onDone) },
    ) {
        Icon(
            imageVector = Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = if (opened) WoojuinColor.StarBlue else WoojuinColor.TextMuted,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (opened) "휴대폰에서 열었어요" else "연결된 휴대폰이 없어요",
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        CaptionText(
            if (opened) "휴대폰에서 이어서 확인하세요" else "휴대폰과 연결한 뒤 다시 시도해 주세요",
        )

    }
}
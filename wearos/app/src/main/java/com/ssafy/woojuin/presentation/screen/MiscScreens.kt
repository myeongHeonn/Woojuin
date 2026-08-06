package com.ssafy.woojuin.presentation.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.ItemCardContent
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.theme.WoojuinColor

/** 주변 저장 장소 상세 — 알림에서 바로 진입한다. */
@Composable
fun NearbyPlaceDetailScreen(
    onOpenOnPhone: () -> Unit,
    onDisabled: () -> Unit,
) {
    val alert by Repositories.nearby.activeAlert.collectAsState()

    val current = alert
    if (current == null) {
        WoojuinStatusScreen {
            Text(
                text = "근처에 저장한 장소가 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    WoojuinListScreen {
        item {
            ListHeader {
                Text(
                    text = current.placeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Text(
                text = "현재 ${current.distanceMeters}m",
                style = MaterialTheme.typography.labelSmall,
                color = WoojuinColor.NearbyAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = WoojuinColor.Surface),
            ) {
                ItemCardContent(
                    icon = Icons.Rounded.LocationOn,
                    iconTint = WoojuinColor.NearbyAccent,
                    title = "저장 당시 요약",
                    summary = current.summary,
                    metaLabel = current.savedAtLabel,
                    dotColor = WoojuinColor.NearbyAccent,
                )
            }
        }
        current.memo?.let { memo ->
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = WoojuinColor.SidebarBlack),
                ) {
                    Text(
                        text = "내 메모",
                        style = MaterialTheme.typography.labelSmall,
                        color = WoojuinColor.TextMuted,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = memo,
                        style = MaterialTheme.typography.bodySmall,
                        color = WoojuinColor.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onOpenOnPhone,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                icon = { Icon(Icons.Rounded.Map, contentDescription = null, tint = WoojuinColor.PlaceAccent, modifier = Modifier.size(18.dp)) },
                label = { Text("카카오맵에서 열기") },
            )
        }
        item {
            Button(
                onClick = {
                    Repositories.nearby.disableAlert(current.placeId)
                    onDisabled()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                icon = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                label = { Text("이 장소 알림 끄기") },
            )
        }
    }
}

/** 오프라인 큐 — 연결되면 자동 동기화. */
@Composable
fun OfflineQueueScreen() {
    val pending by Repositories.sync.pendingItems.collectAsState()

    WoojuinListScreen {
        item {
            ListHeader {
                Text(
                    text = "동기화 대기",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                )
            }
        }
        if (pending.isEmpty()) {
            item {
                CaptionText("모두 동기화됐어요", modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            item {
                CaptionText("연결되면 자동으로 동기화할게요")
            }
            pending.forEach { queued ->
                item {
                    Card(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = WoojuinColor.Surface),
                    ) {
                        ItemCardContent(
                            icon = Icons.Rounded.CloudQueue,
                            iconTint = WoojuinColor.TextSecondary,
                            title = queued.title,
                            summary = queued.savedAtLabel,
                            metaLabel = null,
                            dotColor = WoojuinColor.TextMuted,
                            titleMaxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

enum class PermissionFeature(val route: String) {
    MIC("mic"),
    LOCATION("location"),
    BACKGROUND("background");

    companion object {
        fun from(value: String?): PermissionFeature =
            entries.firstOrNull { it.route == value } ?: MIC
    }
}

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
            "현재 장소를 저장하고, 저장한 장소 근처에서 알려드릴게요.",
        )
        PermissionFeature.BACKGROUND -> Quad(
            Icons.Rounded.Notifications,
            WoojuinColor.NearbyAccent,
            "알림 권한이 필요해요",
            "저장해 둔 장소 근처에 도착했을 때만 알려드릴게요.",
        )
    }

    WoojuinStatusScreen {
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
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
            label = { Text("설정 열기") },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
            label = { Text("나중에") },
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** 긴 원문·상세 지도·수정은 휴대폰에서 잇는다. */
@Composable
fun OpenOnPhoneScreen(onDone: () -> Unit) {
    WoojuinStatusScreen {
        Icon(
            imageVector = Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = WoojuinColor.StarBlue,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "휴대폰으로 보냈어요",
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        CaptionText("휴대폰에서 이어서 확인하세요")
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
            label = { Text("확인") },
        )
    }
}
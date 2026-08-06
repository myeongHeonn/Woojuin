package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.NearbyAlert
import com.ssafy.woojuin.domain.model.SyncState
import com.ssafy.woojuin.domain.model.SyncStatus
import com.ssafy.woojuin.presentation.component.ColorDot
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinLogo
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.util.rememberHaptics

class HomeViewModel : ViewModel() {
    val nearbyAlert = Repositories.nearby.activeAlert
    val syncStatus = Repositories.sync.status

    /** 음성 엔진 준비 상태 — 로고 버튼이 이걸로 눌림 여부와 문구를 가른다 */
    val speechReady = Repositories.speechReady
}

/**
 * 홈. 로고가 가장 중요한 입력 장치다 — 탭 한 번으로 음성 저장을 시작한다.
 * 주변 장소 카드는 상황이 있을 때만 나타난다.
 */
@Composable
fun HomeScreen(
    onVoiceCapture: () -> Unit,
    onSearch: () -> Unit,
    onSong: () -> Unit,
    onPlace: () -> Unit,
    onNearby: () -> Unit,
    onSyncStatus: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val nearbyAlert by viewModel.nearbyAlert.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val speechReady by viewModel.speechReady.collectAsState()
    val haptics = rememberHaptics()

    WoojuinListScreen {
        nearbyAlert?.let { alert ->
            item { NearbyChip(alert = alert, onClick = onNearby) }
        }
        item {
            MainVoiceButton(ready = speechReady, onClick = {
                haptics.tapStart()
                onVoiceCapture()
            })
        }
        item {
            FeatureButton(
                icon = Icons.Rounded.Search,
                accent = WoojuinColor.SearchAccent,
                title = "저장한 자료 찾기",
                subtitle = "말로 지난 자료를 찾아요",
                onClick = { haptics.tapStart(); onSearch() },
            )
        }
        item {
            FeatureButton(
                icon = Icons.Rounded.MusicNote,
                accent = WoojuinColor.SongAccent,
                title = "노래 찾기",
                subtitle = "지금 들리는 곡을 저장해요",
                onClick = { haptics.tapStart(); onSong() },
            )
        }
        item {
            FeatureButton(
                icon = Icons.Rounded.Place,
                accent = WoojuinColor.PlaceAccent,
                title = "지금 장소 저장",
                subtitle = "주변 장소에서 골라 저장해요",
                onClick = { haptics.tapStart(); onPlace() },
            )
        }
        item { SyncStatusRow(status = syncStatus, onClick = onSyncStatus) }
    }
}

/**
 * 상황형 주변 장소 칩 — 홈 최상단에 작게만 노출된다.
 * 상세 정보(거리·요약·메모)는 탭하면 NearbyPlaceDetail에서 보여준다.
 */
@Composable
private fun NearbyChip(alert: NearbyAlert, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(WoojuinColor.Surface)
            .clickable(role = Role.Button, onClickLabel = "근처 저장 장소 보기") { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        ColorDot(color = WoojuinColor.NearbyAccent, size = 6.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (alert.lowAccuracy) "근처 저장 장소 보기" else "근처 · ‘${alert.placeName}’ 보기",
            style = MaterialTheme.typography.labelSmall,
            color = WoojuinColor.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MainVoiceButton(ready: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                // 준비 전에는 누를 수 없다 — 눌러도 엔진이 아직 못 들어서 말이 버려진다
                .clickable(
                    enabled = ready,
                    role = Role.Button,
                    onClickLabel = "말해서 저장",
                ) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            WoojuinLogo(
                modifier = Modifier
                    .size(96.dp)
                    .alpha(if (ready) 1f else 0.45f),
            )
            // 우측 아래 보라색 마이크 배지
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (ready) WoojuinColor.AccentPurple else WoojuinColor.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = if (ready) Color.White else WoojuinColor.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "말해서 저장",
            style = MaterialTheme.typography.titleMedium,
            color = if (ready) WoojuinColor.TextPrimary else WoojuinColor.TextMuted,
        )
        Text(
            // 엔진 초기화가 끝나기 전에 탭하면 말이 버려지므로 상태를 정직하게 알린다
            text = if (ready) "탭하고 바로 말하세요" else "음성 준비 중…",
            style = MaterialTheme.typography.bodySmall,
            color = WoojuinColor.TextMuted,
        )
    }
}

@Composable
private fun FeatureButton(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WoojuinColor.Surface,
            contentColor = WoojuinColor.TextPrimary,
        ),
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        },
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WoojuinColor.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WoojuinColor.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SyncStatusRow(status: SyncStatus, onClick: () -> Unit) {
    val (icon, label) = when (status.state) {
        SyncState.SYNCED -> Icons.Rounded.CloudDone to "동기화 완료"
        SyncState.SYNCING -> Icons.Rounded.CloudQueue to "동기화 중 · ${status.pendingCount}개"
        SyncState.PENDING, SyncState.OFFLINE -> Icons.Rounded.CloudQueue to "대기 중 · ${status.pendingCount}개"
        SyncState.FAILED -> Icons.Rounded.CloudQueue to "동기화 실패 · 다시 시도할게요"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "동기화 상태 보기") { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WoojuinColor.TextMuted,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WoojuinColor.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
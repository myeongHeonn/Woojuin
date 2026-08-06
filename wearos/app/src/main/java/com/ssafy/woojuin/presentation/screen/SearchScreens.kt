package com.ssafy.woojuin.presentation.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.ItemCardContent
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinListeningLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.component.rememberReduceMotion
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.util.rememberHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data class Listening(val partialText: String) : SearchUiState
    data class Searching(val query: String) : SearchUiState
    data object Done : SearchUiState
}

class VoiceSearchViewModel : ViewModel() {
    private val repository = Repositories.search

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Listening(""))
    val uiState = _uiState.asStateFlow()

    private var started = false
    private var lastPartial = ""

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                repository.listenQuery().collect { event ->
                    when (event) {
                        is SpeechEvent.Partial -> {
                            if (event.text.isNotBlank()) lastPartial = event.text
                            _uiState.value = SearchUiState.Listening(event.text)
                        }
                        is SpeechEvent.Final -> runSearch(event.text)
                        SpeechEvent.SilenceTimeout -> runSearch(lastPartial)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 인식 실패 시 지금까지 들린 문장으로라도 검색한다 (빈 문장이면 빈 결과 화면).
                runSearch(lastPartial)
            }
        }
    }

    private suspend fun runSearch(query: String) {
        _uiState.value = SearchUiState.Searching(query)
        repository.search(query)
        _uiState.value = SearchUiState.Done
    }
}

fun typeIcon(type: SavedItemType): Pair<ImageVector, Color> = when (type) {
    SavedItemType.VOICE -> Icons.Rounded.Mic to WoojuinColor.AccentPurple
    SavedItemType.LINK -> Icons.Rounded.Link to WoojuinColor.StarBlue
    SavedItemType.SONG -> Icons.Rounded.MusicNote to WoojuinColor.StarYellow
    SavedItemType.PLACE -> Icons.Rounded.Place to WoojuinColor.StarGreen
}

/** “저장한 자료 찾기” — 즉시 음성 검색 시작. */
@Composable
fun VoiceSearchScreen(
    onResults: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: VoiceSearchViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reduceMotion = rememberReduceMotion()

    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.start() else micDenied = true
    }
    LaunchedEffect(Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(uiState) {
        if (uiState is SearchUiState.Done) onResults()
    }

    if (micDenied) {
        PermissionGuideScreen(feature = PermissionFeature.MIC, onBack = onBack)
        return
    }

    KeepScreenOn()

    WoojuinStatusScreen {
        WoojuinListeningLogo(
            accent = WoojuinColor.SearchAccent,
            modifier = Modifier.size(80.dp),
            reduceMotion = reduceMotion,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (uiState) {
                is SearchUiState.Searching -> "찾고 있어요"
                else -> "무엇을 찾아볼까요?"
            },
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
        )
        val partial = (uiState as? SearchUiState.Listening)?.partialText
            ?: (uiState as? SearchUiState.Searching)?.query.orEmpty()
        if (partial.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = partial,
                style = MaterialTheme.typography.bodySmall,
                color = WoojuinColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 검색 결과 — 상위 3개, 최상위는 큰 카드. */
@Composable
fun VoiceSearchResultsScreen(
    onItem: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenOnPhone: () -> Unit,
) {
    val results by Repositories.search.lastResults.collectAsState()
    val query by Repositories.search.lastQuery.collectAsState()

    if (results.isEmpty()) {
        WoojuinStatusScreen {
            Text(
                text = "비슷한 자료를 찾지 못했어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                icon = { androidx.wear.compose.material3.Icon(Icons.Rounded.Mic, contentDescription = null, tint = WoojuinColor.SearchAccent, modifier = Modifier.size(18.dp)) },
                label = { Text("다시 말하기") },
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onOpenOnPhone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                icon = { androidx.wear.compose.material3.Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                label = { Text("휴대폰에서 검색") },
            )
        }
        return
    }

    WoojuinListScreen(
        edgeButton = {
            EdgeButton(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WoojuinColor.SurfaceRaised,
                    contentColor = WoojuinColor.TextPrimary,
                ),
            ) {
                Text("다시 검색")
            }
        },
    ) {
        item {
            ListHeader {
                Text(
                    text = if (query.isEmpty()) "검색 결과" else "‘$query’",
                    style = MaterialTheme.typography.titleSmall,
                    color = WoojuinColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        results.take(3).forEachIndexed { index, item ->
            item {
                ResultCard(item = item, hero = index == 0, onClick = { onItem(item.id) })
            }
        }
    }
}

@Composable
private fun ResultCard(item: SavedItem, hero: Boolean, onClick: () -> Unit) {
    val (icon, tint) = typeIcon(item.type)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (hero) 4.dp else 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hero) WoojuinColor.SurfaceActive else WoojuinColor.Surface,
        ),
    ) {
        ItemCardContent(
            icon = icon,
            iconTint = tint,
            title = item.title,
            summary = if (hero) item.summary else item.summary.take(40),
            metaLabel = item.distanceLabel ?: item.savedAtLabel,
            dotColor = tint,
            titleMaxLines = if (hero) 2 else 1,
        )
    }
}

/** 결과 상세 — 핵심 요약만. 긴 원문은 휴대폰에서 잇는다. */
@Composable
fun SavedItemDetailScreen(
    itemId: String,
    onOpenOnPhone: () -> Unit,
    onRetry: () -> Unit,
) {
    val detail = Repositories.search.itemById(itemId)

    if (detail == null) {
        WoojuinStatusScreen {
            Text(
                text = "자료를 찾을 수 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val (icon, tint) = typeIcon(detail.type)

    WoojuinListScreen(
        edgeButton = {
            EdgeButton(
                onClick = onOpenOnPhone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WoojuinColor.AccentPurple,
                    contentColor = Color.White,
                ),
            ) {
                Text("휴대폰에서 열기")
            }
        },
    ) {
        item {
            ListHeader {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Card(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = WoojuinColor.Surface),
            ) {
                ItemCardContent(
                    icon = icon,
                    iconTint = tint,
                    title = "요약",
                    summary = detail.summary,
                    metaLabel = listOfNotNull(detail.sourceLabel, detail.savedAtLabel).joinToString(" · "),
                    dotColor = tint,
                )
            }
        }
        detail.memo?.let { memo ->
            item {
                Card(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = WoojuinColor.SidebarBlack),
                ) {
                    Text(
                        text = "메모",
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
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                icon = { androidx.wear.compose.material3.Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WoojuinColor.SearchAccent, modifier = Modifier.size(18.dp)) },
                label = { Text("다시 검색") },
            )
        }
    }
}
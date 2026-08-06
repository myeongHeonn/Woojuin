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
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
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
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.SavedItem
import com.ssafy.woojuin.domain.model.SavedItemType
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.GlassButton
import com.ssafy.woojuin.presentation.component.GlassCard
import com.ssafy.woojuin.presentation.component.ItemCardContent
import com.ssafy.woojuin.presentation.component.WoojuinEdgeButton
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinListeningLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.component.rememberReduceMotion
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.theme.woojuinRowInset
import com.ssafy.woojuin.presentation.util.rememberHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    /** 마이크가 열리기 전(실측 100ms 안이라 스쳐 지나간다) */
    data object Preparing : SearchUiState
    data class Listening(val partialText: String) : SearchUiState

    /** 녹음은 끝났고 서버가 받아쓰는 중 — 마이크는 닫혀 있다 */
    data object Transcribing : SearchUiState
    data class Searching(val query: String) : SearchUiState
    data object Done : SearchUiState
}

class VoiceSearchViewModel : ViewModel() {
    private val repository = Repositories.search

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Preparing)
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
                        SpeechEvent.Ready -> _uiState.value = SearchUiState.Listening("")
                        is SpeechEvent.Partial -> {
                            if (event.text.isNotBlank()) lastPartial = event.text
                            _uiState.value = SearchUiState.Listening(event.text)
                        }
                        SpeechEvent.Transcribing -> _uiState.value = SearchUiState.Transcribing
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
    // StickyNote2 는 AutoMirrored 에만 있다(RTL 에서 뒤집히는 게 맞는 모양이라 옮겨졌다)
    SavedItemType.MEMO -> Icons.AutoMirrored.Rounded.StickyNote2 to WoojuinColor.AccentPurple
    SavedItemType.LINK -> Icons.Rounded.Link to WoojuinColor.StarBlue
    SavedItemType.IMAGE -> Icons.Rounded.Image to WoojuinColor.StarYellow
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

    // 마이크가 열려 있는 동안만 색을 살린다 — 받아쓰는 중에 말해도 남지 않기 때문이다
    val listening = uiState is SearchUiState.Listening
    WoojuinStatusScreen(
        glowColor = if (listening) WoojuinColor.SearchAccent else WoojuinColor.TextMuted,
    ) {
        WoojuinListeningLogo(
            accent = if (listening) WoojuinColor.SearchAccent else WoojuinColor.TextMuted,
            modifier = Modifier.size(80.dp),
            reduceMotion = reduceMotion,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (uiState) {
                is SearchUiState.Preparing -> "준비 중…"
                is SearchUiState.Transcribing -> "알아듣고 있어요"
                is SearchUiState.Searching -> "찾고 있어요"
                else -> "무엇을 찾아볼까요?"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (listening) WoojuinColor.TextPrimary else WoojuinColor.TextMuted,
        )
        if (uiState is SearchUiState.Listening) {
            Spacer(modifier = Modifier.height(4.dp))
            CaptionText("찾을 내용을 말하세요")
        }
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
    val interpretation by Repositories.search.lastInterpretation.collectAsState()

    if (results.isEmpty()) {
        WoojuinStatusScreen(
            glowColor = WoojuinColor.SearchAccent,
            edgeButton = {
                WoojuinEdgeButton(
                    label = "다시 말하기",
                    onClick = onRetry,
                    accent = WoojuinColor.SearchAccent,
                )
            },
        ) {
            Text(
                text = "비슷한 자료를 찾지 못했어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            // 0건일 때 "무엇으로 찾았는지"가 가장 중요하다 — 엉뚱한 검색어로 찾았다면
            // 사용자가 다시 말해서 고칠 수 있어야 하고, 그 판단 근거가 이것뿐이다
            interpretation?.let {
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText("‘${it.query}’(으)로 찾았어요")
            }
            Spacer(modifier = Modifier.height(8.dp))
            GlassButton(
                label = "휴대폰에서 검색",
                onClick = onOpenOnPhone,
                icon = Icons.Rounded.PhoneAndroid,
            )
        }
        return
    }

    WoojuinListScreen(
        glowColor = WoojuinColor.SearchAccent,
        edgeButton = {
            WoojuinEdgeButton(
                label = "다시 검색",
                onClick = onRetry,
                accent = WoojuinColor.SearchAccent,
            )
        },
    ) {
        item {
            ListHeader {
                Text(
                    // 말한 문장이 아니라 **AI 가 뽑아낸 검색어**를 보여준다 — 결과가 예상과
                    // 다를 때 원인을 알 수 있어야 한다(서버 DTO 의 요구사항)
                    text = interpretation?.let { "‘${it.query}’(으)로 찾았어요" }
                        ?: if (query.isEmpty()) "검색 결과" else "‘$query’",
                    style = MaterialTheme.typography.titleSmall,
                    color = WoojuinColor.TextSecondary,
                    // 목록은 스크롤되므로 줄을 늘리는 게 잘라내는 것보다 낫다 —
                    // 무엇으로 찾았는지가 결과를 이해하는 유일한 단서다
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // LLM 호출이 실패해 규칙 기반으로 찾은 경우 — 오타 교정·관련어 확장이 빠졌으므로
        // 결과가 빈약해도 이상한 게 아니라는 걸 알린다
        if (interpretation?.aiPlanned == false) {
            item { CaptionText("AI 해석 없이 찾았어요") }
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
    GlassCard(
        onClick = onClick,
        // 헤어라인에 종류 색을 준다 — 목록을 훑을 때 메모·링크·사진이 먼저 읽힌다.
        // 맨 위 결과만 색을 살려서 가장 비슷한 것임을 알린다
        accent = if (hero) tint else WoojuinColor.TextMuted,
        modifier = Modifier.woojuinRowInset(),
    ) {
        // 목록에는 제목만 — 요약까지 넣으면 카드가 커져 워치에서 두세 개밖에 안 보이고,
        // 어느 것인지 고르는 데는 제목이면 충분하다. 요약은 탭해서 들어가면 나온다
        ItemCardContent(
            icon = icon,
            iconTint = tint,
            title = item.title,
            metaLabel = item.distanceLabel ?: item.savedAtLabel,
            dotColor = tint,
            // 두 줄이면 "Redis Streams로 이…"처럼 잘렸다. 좁은 카드에서는 한 줄에 열 자
            // 남짓이라 세 줄로도 부족했다 — 목록은 스크롤되므로 네 줄까지 늘린다
            titleMaxLines = 4,
        )
    }
}

/** 결과 상세 — 핵심 요약만. 긴 원문은 휴대폰에서 잇는다. */
@Composable
fun SavedItemDetailScreen(
    itemId: String,
    onOpenOnPhone: () -> Unit,
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

    // 이 화면의 주 동작을 EdgeButton 에 두지 않는다 — "휴대폰에서 열기"는 그 버튼에
    // 한 줄로 들어가지 않아 "열기"가 곡면에 잘렸다. 목록 맨 아래 칸으로 둔다
    WoojuinListScreen(glowColor = tint) {
        item {
            ListHeader {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    // 상세 화면이다 — 제목을 잘라내면 무엇의 상세인지 알 수 없다
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            GlassCard(accent = tint, modifier = Modifier.woojuinRowInset()) {
                ItemCardContent(
                    icon = icon,
                    iconTint = tint,
                    title = "요약",
                    summary = detail.summary,
                    metaLabel = listOfNotNull(detail.sourceLabel, detail.savedAtLabel).joinToString(" · "),
                    dotColor = tint,
                    // 요약·메타를 두 줄에서 자르면 상세로 들어온 의미가 없다
                    summaryMaxLines = Int.MAX_VALUE,
                    metaMaxLines = 2,
                )
            }
        }
        detail.memo?.let { memo ->
            item {
                GlassCard(modifier = Modifier.woojuinRowInset()) {
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
                    )
                }
            }
        }
        // 동작은 내용을 다 보여준 뒤 맨 끝에 둔다 — 요약과 메모 사이에 끼우면 읽는 흐름이 끊긴다
        item {
            GlassButton(
                label = "휴대폰에서 열기",
                onClick = onOpenOnPhone,
                icon = Icons.Rounded.PhoneAndroid,
                accent = tint,
                primary = true,
                modifier = Modifier.woojuinRowInset(),
            )
        }
    }
}
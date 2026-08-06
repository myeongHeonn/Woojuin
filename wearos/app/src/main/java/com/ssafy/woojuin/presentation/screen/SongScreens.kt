package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.RecognizedSong
import com.ssafy.woojuin.domain.repository.SongRecognitionEvent
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.GlassButton
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinEdgeButton
import com.ssafy.woojuin.presentation.component.WoojuinListeningLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.component.rememberReduceMotion
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.theme.woojuinRowInset
import com.ssafy.woojuin.presentation.util.rememberHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MAX_LISTEN_SECONDS = 12

sealed interface SongRecognitionUiState {
    data class Listening(val elapsedSeconds: Int) : SongRecognitionUiState
    data object Saved : SongRecognitionUiState

    /**
     * @param message 화면에 그대로 보여줄 말. **"곡을 못 찾음"과 "인식이 실패함"을 가른다** —
     *   전자는 소리를 다시 들려주면 되지만 후자는 사용자가 할 수 있는 게 없다
     */
    data class Failed(val message: String, val hint: String) : SongRecognitionUiState
}

class SongRecognitionViewModel : ViewModel() {
    private val repository = Repositories.song

    private val _uiState = MutableStateFlow<SongRecognitionUiState>(SongRecognitionUiState.Listening(0))
    val uiState = _uiState.asStateFlow()

    fun start() {
        _uiState.value = SongRecognitionUiState.Listening(0)
        viewModelScope.launch {
            try {
                repository.recognize().collect { event ->
                    when (event) {
                        is SongRecognitionEvent.Listening ->
                            _uiState.value = SongRecognitionUiState.Listening(event.elapsedSeconds)
                        is SongRecognitionEvent.Matched -> {
                            // 인식 성공 즉시 자동 저장 — 별도 저장 버튼을 요구하지 않는다.
                            repository.saveSong(event.song)
                            _uiState.value = SongRecognitionUiState.Saved
                        }
                        SongRecognitionEvent.NoMatch -> _uiState.value = SongRecognitionUiState.Failed(
                            message = "곡을 찾지 못했어요",
                            hint = "소리가 잘 들리는 곳에서 다시 시도해 주세요",
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // 인식·저장이 실패했다(통신·서버). 못 찾은 것과 달리 사용자가 소리를 더
                // 들려줘도 소용없으므로 다른 말을 보여준다 — 이 갈래가 없으면 화면이
                // "듣고 있어요"에서 영원히 멈춘다
                _uiState.value = SongRecognitionUiState.Failed(
                    message = "지금은 노래를 찾을 수 없어요",
                    hint = "잠시 후 다시 시도해 주세요",
                )
            }
        }
    }
}

/** “노래 찾기” — 추가 시작 버튼 없이 바로 청취. */
@Composable
fun SongRecognitionScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SongRecognitionViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(uiState) {
        when (uiState) {
            is SongRecognitionUiState.Saved -> {
                haptics.success()
                onSaved()
            }
            is SongRecognitionUiState.Failed -> haptics.error()
            else -> Unit
        }
    }

    KeepScreenOn()

    when (uiState) {
        is SongRecognitionUiState.Failed -> {
            val failed = uiState as SongRecognitionUiState.Failed
            WoojuinStatusScreen(
                glowColor = WoojuinColor.Danger,
                edgeButton = {
                    WoojuinEdgeButton(
                        label = "다시 듣기",
                        onClick = { viewModel.start() },
                        accent = WoojuinColor.SongAccent,
                    )
                },
            ) {
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText(failed.hint)
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(label = "취소", onClick = onCancel)
            }
        }
        else -> {
            val elapsed = (uiState as? SongRecognitionUiState.Listening)?.elapsedSeconds ?: 0
            WoojuinStatusScreen(
                glowColor = WoojuinColor.SongAccent,
                edgeButton = { WoojuinEdgeButton(label = "취소", onClick = onCancel) },
            ) {
                WoojuinListeningLogo(
                    accent = WoojuinColor.SongAccent,
                    modifier = Modifier.size(80.dp),
                    reduceMotion = reduceMotion,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "노래를 듣고 있어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 고정 길이 녹음이라 "최대"가 아니다 — 음악은 침묵으로 끝을 못 정해서
                // 12초를 꽉 채운다(MicRecorder.recordFixed)
                CaptionText("${elapsed}초 / ${MAX_LISTEN_SECONDS}초")
            }
        }
    }
}

/** 인식 결과 — 이미 자동 저장된 상태. */
@Composable
fun SongResultScreen(
    onOpenOnPhone: () -> Unit,
    onRetry: () -> Unit,
) {
    val song by Repositories.song.lastMatched.collectAsState()

    val matched = song
    if (matched == null) {
        WoojuinStatusScreen(glowColor = WoojuinColor.SongAccent) {
            Text(
                text = "저장된 노래가 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    WoojuinListScreen(
        glowColor = WoojuinColor.SongAccent,
        // 다시 찾기를 베젤로 내린다 — 목록 맨 아래 사각 버튼은 곡면에 물린다
        edgeButton = {
            WoojuinEdgeButton(
                label = "다시 찾기",
                onClick = onRetry,
                accent = WoojuinColor.SongAccent,
            )
        },
    ) {
        item {
            // 앨범 아트 자리 — 지금 쓰는 인식 API 는 커버 이미지를 주지 않아 심벌로 둔다
            // (보관함 아이템에는 크롤이 썸네일을 채운다)
            // 회색 원이었다 — 홈의 위성처럼 기능 색 빛을 두른 원으로 바꾼다
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(WoojuinColor.SongAccent.copy(alpha = 0.14f))
                    .border(1.dp, WoojuinColor.SongAccent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = WoojuinColor.SongAccent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        item {
            Text(
                text = matched.title,
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                // 목록이라 스크롤된다 — 곡 제목을 잘라내면 무슨 곡을 저장했는지 모른다
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            Text(
                text = matched.artist,
                style = MaterialTheme.typography.bodySmall,
                color = WoojuinColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            Text(
                text = "노래를 저장했어요",
                style = MaterialTheme.typography.labelSmall,
                color = WoojuinColor.SongAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        item {
            GlassButton(
                label = "휴대폰에서 열기",
                onClick = onOpenOnPhone,
                icon = Icons.Rounded.PhoneAndroid,
                modifier = Modifier.woojuinRowInset(),
            )
        }

    }
}
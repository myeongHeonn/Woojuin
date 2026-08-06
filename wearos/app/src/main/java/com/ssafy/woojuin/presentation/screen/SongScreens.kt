package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.background
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.RecognizedSong
import com.ssafy.woojuin.domain.repository.SongRecognitionEvent
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinListeningLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.component.rememberReduceMotion
import com.ssafy.woojuin.presentation.theme.WoojuinColor
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
            WoojuinStatusScreen {
                Text(
                    text = failed.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText(failed.hint)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.start() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                    icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WoojuinColor.SongAccent, modifier = Modifier.size(18.dp)) },
                    label = { Text("다시 듣기") },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    label = { Text("취소") },
                )
            }
        }
        else -> {
            val elapsed = (uiState as? SongRecognitionUiState.Listening)?.elapsedSeconds ?: 0
            WoojuinStatusScreen {
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
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    label = { Text("취소") },
                )
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
        WoojuinStatusScreen {
            Text(
                text = "저장된 노래가 없어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    WoojuinListScreen {
        item {
            // 앨범 아트 자리 — 지금 쓰는 인식 API 는 커버 이미지를 주지 않아 심벌로 둔다
            // (보관함 아이템에는 크롤이 썸네일을 채운다)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(WoojuinColor.SurfaceRaised),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
        }
        item {
            Text(
                // 빈 칸을 구분점으로 이어붙이면 "HANRORO · " 처럼 꼬리가 남는다 —
                // 서버가 앨범을 주지 않는 경우가 있어 있는 것만 잇는다
                text = listOf(matched.artist, matched.albumLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
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
            Button(
                onClick = onOpenOnPhone,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                label = { Text("휴대폰에서 열기") },
            )
        }
        item {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = WoojuinColor.SongAccent, modifier = Modifier.size(18.dp)) },
                label = { Text("다시 찾기") },
            )
        }
    }
}
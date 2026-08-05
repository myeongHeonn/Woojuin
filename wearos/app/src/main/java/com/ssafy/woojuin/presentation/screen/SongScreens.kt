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
import androidx.compose.material.icons.rounded.Undo
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
import com.ssafy.woojuin.domain.model.SavedItem
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
    data class Saved(val song: RecognizedSong, val item: SavedItem) : SongRecognitionUiState
    data object Failed : SongRecognitionUiState
}

class SongRecognitionViewModel : ViewModel() {
    private val repository = Repositories.song

    private val _uiState = MutableStateFlow<SongRecognitionUiState>(SongRecognitionUiState.Listening(0))
    val uiState = _uiState.asStateFlow()

    fun start() {
        _uiState.value = SongRecognitionUiState.Listening(0)
        viewModelScope.launch {
            repository.recognize().collect { event ->
                when (event) {
                    is SongRecognitionEvent.Listening ->
                        _uiState.value = SongRecognitionUiState.Listening(event.elapsedSeconds)
                    is SongRecognitionEvent.Matched -> {
                        // 인식 성공 즉시 자동 저장 — 별도 저장 버튼을 요구하지 않는다.
                        val item = repository.saveSong(event.song)
                        _uiState.value = SongRecognitionUiState.Saved(event.song, item)
                    }
                    SongRecognitionEvent.NoMatch -> _uiState.value = SongRecognitionUiState.Failed
                }
            }
        }
    }

    fun undo(onDone: () -> Unit) {
        val state = _uiState.value as? SongRecognitionUiState.Saved ?: return
        viewModelScope.launch {
            repository.undo(state.item.id)
            onDone()
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
            WoojuinStatusScreen {
                Text(
                    text = "아직 곡을 찾지 못했어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText("소리가 잘 들리는 곳에서 다시 시도해 주세요")
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
                CaptionText("최대 ${MAX_LISTEN_SECONDS}초 · ${elapsed}초")
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
    onUndoDone: () -> Unit,
    onOpenOnPhone: () -> Unit,
    onRetry: () -> Unit,
) {
    val song by Repositories.song.lastMatched.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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
            // 앨범 아트 자리 — 서버 연동 전에는 심벌로 대체
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
                text = "${matched.artist} · ${matched.albumLabel}",
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
                onClick = {
                    scope.launch {
                        Repositories.song.undo("")
                        onUndoDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceRaised),
                icon = { Icon(Icons.Rounded.Undo, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                label = { Text("실행 취소") },
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
package com.ssafy.woojuin.presentation.screen

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.repository.SpeechEvent
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.WoojuinListScreen
import com.ssafy.woojuin.presentation.component.WoojuinListeningLogo
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.component.rememberReduceMotion
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.util.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VoiceCaptureUiState {
    data object Ready : VoiceCaptureUiState

    /**
     * 엔진 초기화 대기 — 아직 마이크가 열리지 않았다. 여기서 한 말은 버려지므로
     * "듣는 중"과 반드시 구분해 보여준다(온디바이스 엔진 첫 초기화가 수 초다).
     */
    data object Preparing : VoiceCaptureUiState
    data class Listening(val partialText: String, val rms: Float) : VoiceCaptureUiState
    data class Confirm(val text: String) : VoiceCaptureUiState
    data object LocalSaved : VoiceCaptureUiState
    data class Error(val message: String) : VoiceCaptureUiState
}

class VoiceCaptureViewModel : ViewModel() {
    private val repository = Repositories.voiceCapture

    private val _uiState = MutableStateFlow<VoiceCaptureUiState>(VoiceCaptureUiState.Ready)
    val uiState = _uiState.asStateFlow()

    private var listenJob: Job? = null
    private var latestText = ""

    fun start() {
        if (listenJob != null) return
        // 엔진이 실제로 들을 준비를 마칠 때까지(SpeechEvent.Ready) 준비 중이다 —
        // 그 전에 "듣고 있어요"라고 하면 사용자가 허공에 말한다
        _uiState.value = VoiceCaptureUiState.Preparing
        listenJob = viewModelScope.launch {
            try {
                repository.listen().collect { event ->
                    when (event) {
                        SpeechEvent.Ready -> {
                            _uiState.value = VoiceCaptureUiState.Listening("", 0.5f)
                        }
                        is SpeechEvent.Partial -> {
                            if (event.text.isNotBlank()) latestText = event.text
                            _uiState.value = VoiceCaptureUiState.Listening(event.text, event.rms)
                        }
                        is SpeechEvent.Final -> {
                            latestText = event.text
                            if (event.confident) save(event.text) else {
                                _uiState.value = VoiceCaptureUiState.Confirm(event.text)
                            }
                        }
                        SpeechEvent.SilenceTimeout -> {
                            // 무음 자동 종료 — 들린 내용이 있으면 그대로 저장한다.
                            if (latestText.isBlank()) {
                                _uiState.value = VoiceCaptureUiState.Error("들린 내용이 없어요")
                            } else {
                                save(latestText)
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = VoiceCaptureUiState.Error("지금은 음성 인식을 사용할 수 없어요")
            }
        }
    }

    /** 다시 탭하면 즉시 종료하고 지금까지 인식된 내용을 저장한다. */
    fun stopAndSave() {
        listenJob?.cancel()
        listenJob = null
        if (latestText.isBlank()) {
            _uiState.value = VoiceCaptureUiState.Error("들린 내용이 없어요")
            return
        }
        viewModelScope.launch { save(latestText) }
    }

    fun confirmSave() {
        viewModelScope.launch { save(latestText) }
    }

    private suspend fun save(text: String) {
        repository.saveLocal(text)
        _uiState.value = VoiceCaptureUiState.LocalSaved
    }
}

/** 청취 중 화면을 꺼지지 않게 유지한다. 완료 즉시 일반 정책으로 복귀. */
@Composable
fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** 홈 로고 탭 → 카운트다운 없이 즉시 녹음. */
@Composable
fun VoiceCaptureScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: VoiceCaptureViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()

    // 마이크 권한 — 이 기능에 진입한 순간에만 요청한다.
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

    if (micDenied) {
        PermissionGuideScreen(feature = PermissionFeature.MIC, onBack = onBack)
        return
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            // 엔진 준비에 수 초 걸린다 — 워치는 화면을 계속 보고 있지 않으므로
            // 이제 말해도 된다는 것을 진동으로 알린다
            is VoiceCaptureUiState.Listening -> haptics.tapStart()
            is VoiceCaptureUiState.LocalSaved -> {
                haptics.success()
                onSaved()
            }
            is VoiceCaptureUiState.Error -> haptics.error()
            else -> Unit
        }
    }

    KeepScreenOn()

    when (val state = uiState) {
        is VoiceCaptureUiState.Confirm -> {
            // 인식 신뢰도가 매우 낮을 때만 확인 화면을 표시한다.
            WoojuinStatusScreen {
                Text(
                    text = "이렇게 저장할까요?",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WoojuinColor.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.confirmSave() },
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.AccentPurple),
                    label = { Text("저장하기") },
                )
            }
        }
        is VoiceCaptureUiState.Error -> {
            WoojuinStatusScreen {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    label = { Text("홈으로") },
                )
            }
        }
        // 엔진 초기화 대기 — 여기서 한 말은 버려지므로 "듣고 있어요"라고 하지 않는다.
        // 탭도 받지 않는다(저장할 내용이 없어 "들린 내용이 없어요"만 뜬다)
        VoiceCaptureUiState.Preparing -> {
            WoojuinStatusScreen {
                WoojuinListeningLogo(
                    accent = WoojuinColor.TextMuted,
                    modifier = Modifier.size(88.dp),
                    breathScale = 0.98f,
                    reduceMotion = reduceMotion,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "준비 중…",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText("잠시 후 말씀하세요")
            }
        }
        else -> {
            val partial = (state as? VoiceCaptureUiState.Listening)?.partialText.orEmpty()
            val rms = (state as? VoiceCaptureUiState.Listening)?.rms ?: 0.5f
            WoojuinStatusScreen(
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "녹음 종료",
                ) {
                    haptics.stopCapture()
                    viewModel.stopAndSave()
                },
            ) {
                WoojuinListeningLogo(
                    accent = WoojuinColor.VoiceAccent,
                    modifier = Modifier.size(88.dp),
                    breathScale = 0.96f + 0.1f * rms.coerceIn(0f, 1f),
                    reduceMotion = reduceMotion,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "듣고 있어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                )
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
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    CaptionText("다시 탭하면 저장돼요")
                }
            }
        }
    }
}

/**
 * 저장 완료. 자동 저장 + 3초 실행 취소.
 * AI 분류는 백그라운드에서 진행되며 이 화면을 지연하지 않는다.
 */
@Composable
fun VoiceSaveSuccessScreen(
    onDone: () -> Unit,
) {
    val lastSaved by Repositories.voiceCapture.lastSaved.collectAsState()
    var secondsLeft by remember { mutableIntStateOf(3) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onDone()
    }

    WoojuinListScreen(
        edgeButton = {
            EdgeButton(
                onClick = {
                    val id = lastSaved?.id
                    if (id != null) {
                        scope.launch { Repositories.voiceCapture.undo(id) }
                    }
                    onDone()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = WoojuinColor.SurfaceRaised,
                    contentColor = WoojuinColor.TextPrimary,
                ),
            ) {
                Text("실행 취소 · ${secondsLeft}s")
            }
        },
    ) {
        item {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = WoojuinColor.StarGreen,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = lastSaved?.title ?: "저장했어요",
                style = MaterialTheme.typography.bodyMedium,
                color = WoojuinColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "우주에 저장했어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            CaptionText("AI가 정리하고 있어요")
        }
        }
    }
}
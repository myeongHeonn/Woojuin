package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.PlaceCandidate
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

sealed interface PlacePickerUiState {
    data object Locating : PlacePickerUiState
    data class Candidates(val places: List<PlaceCandidate>) : PlacePickerUiState
    data object Empty : PlacePickerUiState
    data class Duplicate(val place: PlaceCandidate) : PlacePickerUiState
    data object Saved : PlacePickerUiState
    data class Error(val message: String) : PlacePickerUiState
}

class PlacePickerViewModel : ViewModel() {
    private val repository = Repositories.place

    private val _uiState = MutableStateFlow<PlacePickerUiState>(
        repository.lastCandidates.value.let { cached ->
            if (cached.isEmpty()) PlacePickerUiState.Locating
            else PlacePickerUiState.Candidates(cached)
        }
    )
    val uiState = _uiState.asStateFlow()

    fun locate() {
        _uiState.value = PlacePickerUiState.Locating
        viewModelScope.launch {
            runCatching { repository.nearbyCandidates() }
                .onSuccess { candidates ->
                    _uiState.value =
                        if (candidates.isEmpty()) PlacePickerUiState.Empty
                        else PlacePickerUiState.Candidates(candidates)
                }
                .onFailure {
                    _uiState.value = PlacePickerUiState.Error("현재 위치를 정확히 찾지 못했어요")
                }
        }
    }

    /** 후보를 누르면 추가 확인 없이 바로 저장한다. 중복 장소만 예외. */
    fun select(place: PlaceCandidate) {
        if (place.alreadySaved) {
            _uiState.value = PlacePickerUiState.Duplicate(place)
            return
        }
        saveAnyway(place)
    }

    fun saveAnyway(place: PlaceCandidate) {
        viewModelScope.launch {
            repository.savePlace(place)
            _uiState.value = PlacePickerUiState.Saved
        }
    }
}

/** 위치 획득 중. */
@Composable
fun PlaceLocatingScreen(
    onCandidates: () -> Unit,
    viewModel: PlacePickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reduceMotion = rememberReduceMotion()
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionDenied by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locate() else permissionDenied = true
    }

    // 권한은 기능 진입 순간에 요청한다(매니페스트 방침). 있으면 바로 위치를 찾는다
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.locate()
        else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(uiState) {
        when (uiState) {
            is PlacePickerUiState.Candidates, PlacePickerUiState.Empty -> onCandidates()
            else -> Unit
        }
    }

    if (permissionDenied) {
        WoojuinStatusScreen {
            Text(
                text = "위치 권한이 필요해요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            CaptionText("현재 위치로 장소를 찾으려면 허용해 주세요")
            Spacer(modifier = Modifier.height(8.dp))
            androidx.wear.compose.material3.Button(onClick = {
                permissionDenied = false
                permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }) {
                Text("다시 허용하기")
            }
        }
        return
    }

    WoojuinStatusScreen {
        WoojuinListeningLogo(
            accent = WoojuinColor.PlaceAccent,
            modifier = Modifier.size(80.dp),
            reduceMotion = reduceMotion,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "주변 장소를 찾고 있어요",
            style = MaterialTheme.typography.titleMedium,
            color = WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 주변 장소 후보 — 거리순 최대 5개, 탭 즉시 저장. */
@Composable
fun PlacePickerScreen(
    onSaved: () -> Unit,
    onExistingItem: (String) -> Unit,
    onVoiceCapture: () -> Unit,
    onOpenOnPhone: () -> Unit,
    viewModel: PlacePickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = rememberHaptics()

    LaunchedEffect(uiState) {
        if (uiState is PlacePickerUiState.Saved) {
            haptics.success()
            onSaved()
        }
    }

    when (val state = uiState) {
        is PlacePickerUiState.Duplicate -> {
            WoojuinStatusScreen {
                Text(
                    text = "이미 저장한 장소예요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                CaptionText(state.place.name)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onVoiceCapture,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = WoojuinColor.AccentPurple, modifier = Modifier.size(18.dp)) },
                    label = { Text("메모 추가") },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.saveAnyway(state.place) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    label = { Text("다시 저장") },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { onExistingItem("item-pasta") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    label = { Text("기존 내용 보기") },
                )
            }
        }
        is PlacePickerUiState.Empty -> {
            WoojuinStatusScreen {
                Text(
                    text = "주변에서 장소를 찾지 못했어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.saveAnyway(PlaceCandidate("current", "현재 위치", "좌표", 0)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                    icon = { Icon(Icons.Rounded.Place, contentDescription = null, tint = WoojuinColor.PlaceAccent, modifier = Modifier.size(18.dp)) },
                    label = { Text("현재 위치만 저장") },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onVoiceCapture,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = WoojuinColor.AccentPurple, modifier = Modifier.size(18.dp)) },
                    label = { Text("장소 이름 말하기") },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onOpenOnPhone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.Surface),
                    icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                    label = { Text("휴대폰에서 선택") },
                )
            }
        }
        is PlacePickerUiState.Error -> {
            WoojuinStatusScreen {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.locate() },
                    colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                    label = { Text("다시 시도") },
                )
            }
        }
        else -> {
            val candidates = (state as? PlacePickerUiState.Candidates)?.places.orEmpty()
            WoojuinListScreen {
                item {
                    ListHeader {
                        Text(
                            text = "가까운 장소",
                            style = MaterialTheme.typography.titleSmall,
                            color = WoojuinColor.TextSecondary,
                        )
                    }
                }
                candidates.take(5).forEach { place ->
                    item {
                        Button(
                            onClick = {
                                haptics.tapStart()
                                viewModel.select(place)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WoojuinColor.Surface,
                                contentColor = WoojuinColor.TextPrimary,
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = WoojuinColor.PlaceAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = place.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            secondaryLabel = {
                                Text(
                                    text = "${place.category} · ${place.distanceLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WoojuinColor.TextSecondary,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 저장 완료 — 자동 저장 + 실행 취소, 한마디는 선택 사항. */
@Composable
fun PlaceSaveSuccessScreen(
    onDone: () -> Unit,
    onVoiceMemo: () -> Unit,
    onOpenOnPhone: () -> Unit,
) {
    val place by Repositories.place.lastSavedPlace.collectAsState()
    val scope = rememberCoroutineScope()

    WoojuinListScreen {
        item {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = WoojuinColor.StarGreen,
                modifier = Modifier.size(20.dp),
            )
        }
        item {
            Text(
                text = "${place?.name ?: "장소"}을 저장했어요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            )
        }
        item {
            Button(
                onClick = onVoiceMemo,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WoojuinColor.SurfaceActive),
                icon = { Icon(Icons.Rounded.Mic, contentDescription = null, tint = WoojuinColor.AccentPurple, modifier = Modifier.size(18.dp)) },
                label = { Text("한마디 남기기") },
            )
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        Repositories.place.undo("")
                        onDone()
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
                icon = { Icon(Icons.Rounded.Map, contentDescription = null, tint = WoojuinColor.TextSecondary, modifier = Modifier.size(18.dp)) },
                label = { Text("휴대폰 카카오맵에서 열기") },
            )
        }
    }
}
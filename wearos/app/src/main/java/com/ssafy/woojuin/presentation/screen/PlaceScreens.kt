package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.fake.Repositories
import com.ssafy.woojuin.domain.model.PlaceCandidate
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.GlassButton
import com.ssafy.woojuin.presentation.component.GlassCard
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

    // 항상 Locating에서 시작한다. 이전 방문의 후보를 초기값으로 쓰면 위치를 옮겨 재진입했을
    // 때 옛 장소가 그대로 보이고, 그대로 탭하면 몇 km 밖 장소가 저장된다.
    private val _uiState = MutableStateFlow<PlacePickerUiState>(PlacePickerUiState.Locating)
    val uiState = _uiState.asStateFlow()

    /** 화면에 펼쳐 보이는 개수 — "더 보기"가 [PLACE_PAGE_SIZE]씩 늘린다. */
    private val _visibleCount = MutableStateFlow(PLACE_PAGE_SIZE)
    val visibleCount = _visibleCount.asStateFlow()

    /** 서버에 전 카테고리 확장을 요청하는 중 — 버튼이 "더 찾는 중..."으로 바뀐다. */
    private val _expanding = MutableStateFlow(false)
    val expanding = _expanding.asStateFlow()

    /** 마지막 응답이 이미 전 카테고리였는지 — 참이면 서버에 더 물을 것이 없다. */
    val expanded = repository.lastExpanded

    fun locate() {
        _uiState.value = PlacePickerUiState.Locating
        _visibleCount.value = PLACE_PAGE_SIZE
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

    /**
     * "더 보기" — 안 펼친 후보가 남았으면 한 페이지 더 열고, 다 보였으면 [expandSearch].
     */
    fun showMore() {
        val current = (_uiState.value as? PlacePickerUiState.Candidates)?.places
        if (current != null && _visibleCount.value < current.size) {
            _visibleCount.value += PLACE_PAGE_SIZE
            return
        }
        expandSearch()
    }

    /**
     * "주변 더 찾기" — 같은 반경을 음식점·카페 밖 전 카테고리로 다시 뒤진다. 후보 목록에서도
     * 빈 화면에서도 부를 수 있다(허허벌판에서 확장이 막히면 저장할 길이 없어진다).
     *
     * 실패는 조용히 삼킨다 — 목록이 이미 있으면 그걸 남기는 게 낫고, 빈 화면이었으면
     * 그대로 빈 화면이다. 어느 쪽도 Error 화면으로 갈아치울 이유가 없다.
     */
    fun expandSearch() {
        if (_expanding.value || repository.lastExpanded.value) return
        val wasEmpty = _uiState.value is PlacePickerUiState.Empty
        _expanding.value = true
        viewModelScope.launch {
            runCatching { repository.nearbyCandidates(expand = true) }
                .onSuccess { candidates ->
                    if (candidates.isEmpty()) {
                        _uiState.value = PlacePickerUiState.Empty
                    } else {
                        _uiState.value = PlacePickerUiState.Candidates(candidates)
                        // 새로 온 후보부터 한 페이지 바로 연다 — 버튼을 두 번 누르게 하지 않는다
                        _visibleCount.value =
                            if (wasEmpty) PLACE_PAGE_SIZE else _visibleCount.value + PLACE_PAGE_SIZE
                    }
                }
            _expanding.value = false
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
            // 오프라인은 게이트가 앱째로 막는다 — 여기 오는 실패는 서버 오류나 전송 중
            // 끊긴 찰나다. 예외가 새면 앱이 죽으므로(코루틴) 가드는 남긴다
            runCatching { repository.savePlace(place) }
                .onSuccess { _uiState.value = PlacePickerUiState.Saved }
                .onFailure {
                    _uiState.value = PlacePickerUiState.Error("저장하지 못했어요. 잠시 후 다시 시도해 주세요")
                }
        }
    }
}

/** 한 번에 펼쳐 보이는 후보 수 — 서버는 최대 30개를 주고, "더 보기"가 이만큼씩 늘린다. */
private const val PLACE_PAGE_SIZE = 5

/**
 * 위치 저장 — 위치 획득부터 후보 선택·저장까지 한 화면이 전부 다룬다.
 *
 * 예전엔 위치 획득(PLACE_LOCATING)이 별도 destination이었는데, 두 화면이 각자
 * [PlacePickerViewModel]을 만들면서 상태가 단절돼 (1) 재진입 시 이전 위치의 캐시 후보가
 * 새로고침 없이 남고 (2) 0건일 때 Empty 분기에 도달하지 못했다. 상태 머신이 이미
 * [PlacePickerUiState]에 있으므로 Locating도 화면 상태로만 다룬다.
 *
 * 후보는 거리순 5개씩, "더 보기"로 서버가 준 만큼 펼치고, 다 펼쳤는데 서버가
 * 음식점·카페만 뒤진 상태면 같은 버튼이 "주변 더 찾기"(전 카테고리 확장)가 된다. 탭 즉시 저장.
 */
@Composable
fun PlacePickerScreen(
    onSaved: () -> Unit,
    onExistingItem: (String) -> Unit,
    onVoiceCapture: () -> Unit,
    viewModel: PlacePickerViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val visibleCount by viewModel.visibleCount.collectAsState()
    val expanding by viewModel.expanding.collectAsState()
    val expanded by viewModel.expanded.collectAsState()
    val haptics = rememberHaptics()
    val context = androidx.compose.ui.platform.LocalContext.current
    var permissionDenied by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.locate() else permissionDenied = true
    }

    // 권한은 기능 진입 순간에 요청한다(매니페스트 방침). 진입할 때마다 위치를 새로 찾는다 —
    // 다른 화면에 다녀온 뒤에도 다시 실행되는데, 그 사이 움직였을 수 있으니 그게 맞다
    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.locate()
        else permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(uiState) {
        if (uiState is PlacePickerUiState.Saved) {
            haptics.success()
            onSaved()
        }
    }

    if (permissionDenied) {
        WoojuinStatusScreen(
            glowColor = WoojuinColor.PlaceAccent,
            edgeButton = {
                WoojuinEdgeButton(
                    label = "다시 허용하기",
                    onClick = {
                        permissionDenied = false
                        permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    primary = true,
                )
            },
        ) {
            Text(
                text = "위치 권한이 필요해요",
                style = MaterialTheme.typography.titleMedium,
                color = WoojuinColor.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            CaptionText("현재 위치로 장소를 찾으려면 허용해 주세요")

        }
        return
    }

    when (val state = uiState) {
        PlacePickerUiState.Locating -> {
            val reduceMotion = rememberReduceMotion()
            WoojuinStatusScreen(glowColor = WoojuinColor.PlaceAccent) {
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
        is PlacePickerUiState.Duplicate -> {
            WoojuinStatusScreen(
                glowColor = WoojuinColor.PlaceAccent,
                edgeButton = {
                    WoojuinEdgeButton(
                        label = "메모 추가",
                        onClick = onVoiceCapture,
                        primary = true,
                    )
                },
            ) {
                Text(
                    text = "이미 저장한 장소예요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                CaptionText(state.place.name)
                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(label = "다시 저장", onClick = { viewModel.saveAnyway(state.place) })
                Spacer(modifier = Modifier.height(6.dp))
                GlassButton(label = "기존 내용 보기", onClick = { onExistingItem("item-pasta") })
            }
        }
        is PlacePickerUiState.Empty -> {
            // 여기까지 왔다면 서버가 전 카테고리까지 이미 뒤진 뒤다(0건이면 자동 확장) —
            // 반경 안에 저장할 수 있는 장소가 정말로 없다는 뜻이라 더 권할 행동이 없다.
            // 좌표만 저장하는 길도 없다(b111a06에서 좌표 전용 저장 경로를 걷어냄).
            // 나가기는 스와이프로 한다.
            WoojuinStatusScreen(glowColor = WoojuinColor.PlaceAccent) {
                Text(
                    text = "주변에서 장소를 찾지 못했어요",
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText("조금 이동한 뒤 다시 시도해 주세요")
            }
        }
        is PlacePickerUiState.Error -> {
            WoojuinStatusScreen(
                glowColor = WoojuinColor.Danger,
                edgeButton = {
                    WoojuinEdgeButton(
                        label = "다시 시도",
                        onClick = { viewModel.locate() },
                        accent = WoojuinColor.PlaceAccent,
                    )
                },
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )

            }
        }
        else -> {
            val candidates = (state as? PlacePickerUiState.Candidates)?.places.orEmpty()
            WoojuinListScreen(glowColor = WoojuinColor.PlaceAccent) {
                item {
                    ListHeader {
                        Text(
                            text = "가까운 장소",
                            style = MaterialTheme.typography.titleSmall,
                            color = WoojuinColor.TextSecondary,
                        )
                    }
                }
                candidates.take(visibleCount).forEach { place ->
                    item {
                        GlassCard(
                            onClick = {
                                haptics.tapStart()
                                viewModel.select(place)
                            },
                            accent = WoojuinColor.PlaceAccent,
                            modifier = Modifier.woojuinRowInset(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Place,
                                    contentDescription = null,
                                    tint = WoojuinColor.PlaceAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = WoojuinColor.TextPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${place.category} · ${place.distanceLabel}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WoojuinColor.TextSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                val hiddenCount = candidates.size - visibleCount
                if (hiddenCount > 0 || !expanded) {
                    item {
                        GlassButton(
                            label = when {
                                expanding -> "더 찾는 중…"
                                hiddenCount > 0 -> "더 보기 ($hiddenCount)"
                                // 로컬은 다 보였지만 서버가 음식점·카페만 뒤진 상태
                                else -> "주변 더 찾기"
                            },
                            onClick = { if (!expanding) viewModel.showMore() },
                            accent = WoojuinColor.PlaceAccent,
                            modifier = Modifier.woojuinRowInset(),
                        )
                    }
                }
            }
        }
    }
}

/** 저장 완료 — 확인과 복귀만. 취소는 웹 휴지통, 음성 한마디는 2단계(FR-054) 몫이다. */
@Composable
fun PlaceSaveSuccessScreen(
    onDone: () -> Unit,
) {
    val place by Repositories.place.lastSavedPlace.collectAsState()

    WoojuinListScreen(glowColor = WoojuinColor.StarGreen) {
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
                // 장소 이름이 들어가는 문장이다 — 목록이라 스크롤되니 줄을 늘린다
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            )
        }
        item {
            // 음성 한마디는 2단계(FR-054) 몫이다 — fake 버튼을 남기지 않고 완료만 둔다
            GlassButton(
                label = "완료",
                onClick = onDone,
                accent = WoojuinColor.StarGreen,
                modifier = Modifier.woojuinRowInset(),
            )
        }
    }
}
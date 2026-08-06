package com.ssafy.woojuin.presentation.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.data.AppServices
import com.ssafy.woojuin.domain.repository.LinkPollResult
import com.ssafy.woojuin.presentation.component.CaptionText
import com.ssafy.woojuin.presentation.component.WoojuinStatusScreen
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 링크 코드 로그인 화면 (S15P11C105-458).
 *
 * 워치는 코드를 띄우고 기다리는 쪽이다 — 입력은 웹(마이페이지 → 워치 연결)에서 한다.
 * 코드가 만료되면 새로 발급해 조용히 갈아끼운다. 통신 실패는 코드를 버리지 않고
 * 다음 폴링에서 다시 시도한다 — 블루투스가 잠깐 끊기는 건 워치의 일상이다.
 */
class LinkViewModel : ViewModel() {

    data class UiState(
        val code: String? = null,
        val linked: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var loop: Job? = null

    fun start() {
        if (loop?.isActive == true) return
        loop = viewModelScope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            val code = try {
                AppServices.auth.startLink()
            } catch (_: Exception) {
                // 오프라인은 게이트가 앱째로 막는다 — 여기 오는 실패는 일시적인 것, 조용히 재시도
                delay(RETRY_INTERVAL_MS)
                continue
            }
            _state.value = UiState(code = code.code)

            while (true) {
                delay(POLL_INTERVAL_MS)
                val result = try {
                    AppServices.auth.pollLink(code.code)
                } catch (_: Exception) {
                    continue // 통신 실패 — 코드는 살아 있다, 다음 턴에 다시 묻는다
                }
                when (result) {
                    LinkPollResult.Pending -> Unit
                    LinkPollResult.Expired -> break // 새 코드 발급으로
                    LinkPollResult.Approved -> {
                        _state.value = UiState(linked = true)
                        return
                    }
                }
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_500L
        private const val RETRY_INTERVAL_MS = 3_000L
    }
}

@Composable
fun LinkScreen(
    onLinked: () -> Unit,
    viewModel: LinkViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.linked) {
        if (state.linked) onLinked()
    }

    WoojuinStatusScreen {
        when {
            state.code == null -> {
                CaptionText("연결 코드를 받는 중…")
            }

            else -> {
                // 한 줄에 "웹 마이페이지 → 워치 연결에 입력"을 담았더니 마지막 낱말이
                // 넘쳐 "입/력"으로 쪼개졌다 — 짧은 두 줄로 나눠 어느 줄도 넘치지 않게 한다
                CaptionText("이 코드를 입력하세요")
                CaptionText(
                    text = "웹 마이페이지 → 워치 연결",
                    color = WoojuinColor.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.code.orEmpty(),
                    style = MaterialTheme.typography.displayMedium,
                    letterSpacing = 4.sp,
                    color = WoojuinColor.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                CaptionText("승인을 기다리는 중…")
            }
        }
    }
}

package com.ssafy.woojuin.presentation.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 둥근 화면의 레이아웃 규칙.
 *
 * <p><b>왜 필요한가.</b> 둥근 화면은 위·아래로 갈수록 쓸 수 있는 폭이 좁아진다. 고정 여백
 * (예전엔 좌우 8dp)을 주면 목록의 첫·마지막 항목이 곡면에 잘린다 — 실기기에서 홈의
 * "저장한 자료 찾기" 카드가 좌우와 아래로 먹혀 글자가 사라졌다. 그래서 여백을 **화면 폭의
 * 비율**로 잡는다. 396dp 원형에서 12% 면 좌우 47dp 씩이라 곡면 안쪽에 들어온다.
 *
 * <p>Wear 가이드도 원형 화면에서 비율 기반 여백을 권한다 — 기기 크기(384·396·454dp)가
 * 달라도 같은 규칙으로 안전해진다.
 */
object WoojuinLayout {

    /**
     * 목록 항목의 좌우 여백 비율 — 곡면에 닿지 않으면서 글자가 들어갈 만큼.
     * 12% 로 시작했더니 "저장한 자료 찾기"가 한 줄에 못 들어가 잘렸다(454dp 에서 좌우
     * 54dp). 9% 면 곡면 안쪽을 유지하면서 한 줄이 들어온다.
     */
    private const val LIST_SIDE_FRACTION = 0.09f

    /**
     * 상태 화면(청취·오류)의 좌우 여백. 10% 였을 때 화면 아래쪽의 버튼 모서리가 곡면
     * 밖으로 나갔다 — 아래로 갈수록 쓸 수 있는 폭이 줄기 때문이다. 주 동작은 EdgeButton
     * 으로 옮겼고, 남는 보조 버튼을 위해 여백을 12% 로 넓힌다.
     */
    private const val STATUS_SIDE_FRACTION = 0.12f

    /** 행 사이 간격 — 손가락이 옆 행을 누르지 않을 만큼만 */
    val RowGap: Dp = 6.dp

    @Composable
    private fun sideInset(fraction: Float): Dp {
        val width = LocalConfiguration.current.screenWidthDp
        return remember(width, fraction) { (width * fraction).dp }
    }

    /** 목록 항목에 붙이는 좌우 여백 */
    @Composable
    fun listSideInset(): Dp = sideInset(LIST_SIDE_FRACTION)

    /** 상태 화면 본문에 붙이는 좌우 여백 */
    @Composable
    fun statusSideInset(): Dp = sideInset(STATUS_SIDE_FRACTION)

    /** 목록 항목용 — 좌우는 비율, 위아래는 행 간격의 절반 */
    @Composable
    fun rowPadding(): PaddingValues =
        PaddingValues(horizontal = listSideInset(), vertical = RowGap / 2)
}

/** 목록 항목에 곡면 안전 여백을 붙인다. */
@Composable
fun Modifier.woojuinRowInset(): Modifier = padding(WoojuinLayout.rowPadding())

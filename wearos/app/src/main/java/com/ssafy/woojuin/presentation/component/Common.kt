package com.ssafy.woojuin.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import com.ssafy.woojuin.presentation.theme.WoojuinLayout

/**
 * 세로 스크롤 리스트 화면 공통 골격.
 * TransformingLazyColumn은 회전 베젤/rotary 입력을 기본 지원한다.
 *
 * @param glowColor 이 화면이 받는 빛의 색 — 기능 색을 주면 홈과 같은 하늘 아래 놓인다
 */
@Composable
fun WoojuinListScreen(
    modifier: Modifier = Modifier,
    glowColor: Color = WoojuinColor.AccentPurple,
    edgeButton: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val list: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit = { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().background(WoojuinColor.SpaceBlack)) {
            SpaceBackdrop(glowColor = glowColor)
            TransformingLazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                content = content,
            )
        }
    }
    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = edgeButton) { list(it) }
    } else {
        ScreenScaffold(scrollState = listState) { list(it) }
    }
}

/**
 * 중앙 정렬 상태 화면(청취·인식·위치 탐색 등) 골격.
 *
 * <p>좌우 여백은 화면 폭의 비율이다([WoojuinLayout]) — 고정 20dp 였을 때 둥근 화면에서
 * 긴 문장의 첫·끝 글자가 곡면에 먹혔다.
 *
 * @param glowColor 이 화면이 받는 빛의 색 — 기능 색을 주면 홈과 같은 하늘 아래 놓인다
 */
@Composable
fun WoojuinStatusScreen(
    modifier: Modifier = Modifier,
    glowColor: Color = WoojuinColor.AccentPurple,
    edgeButton: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ScreenScaffold {
        Box(modifier = Modifier.fillMaxSize().background(WoojuinColor.SpaceBlack)) {
            SpaceBackdrop(glowColor = glowColor)
            Column(
                modifier = modifier
                    .fillMaxSize()
                    // 아래 버튼이 차지하는 자리를 비운다 — 없으면 본문이 버튼에 깔린다
                    .padding(bottom = if (edgeButton != null) EDGE_BUTTON_RESERVE else 0.dp)
                    .padding(horizontal = WoojuinLayout.statusSideInset()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                content()
            }
            if (edgeButton != null) {
                // **ScreenScaffold 의 edgeButton 슬롯을 쓰지 않는다.** 그 슬롯은 스크롤에 따라
                // 버튼을 접는 애니메이션과 묶여 있어서, 스크롤이 없는 이 화면에서는 자리만
                // 예약하고 버튼을 아예 그리지 않았다(실측: "들린 내용이 없어요" 화면에
                // "홈으로"가 사라졌다). 직접 아래에 붙이면 항상 펼친 크기로 나온다
                Box(modifier = Modifier.align(Alignment.BottomCenter)) { edgeButton() }
            }
        }
    }
}

/** EdgeButton 이 화면 아래에서 차지하는 높이(Wear M3 기본 크기 + 여유). */
private val EDGE_BUTTON_RESERVE = 52.dp

/** 기능 색상 도트. 색상만으로 구분하지 않도록 항상 아이콘·문구와 함께 쓴다. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** 타입 아이콘 + 제목 2줄 + 요약 2줄 + 메타 정보를 담는 결과 카드 내용. */
@Composable
fun ItemCardContent(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    /** 비우면 설명 줄을 그리지 않는다 — 검색 결과처럼 제목만 보여야 하는 목록이 있다. */
    summary: String = "",
    metaLabel: String?,
    dotColor: Color,
    titleMaxLines: Int = 2,
    /** 상세 화면처럼 전문을 보여야 하는 곳은 [Int.MAX_VALUE] 를 준다 — 목록에서만 자른다 */
    summaryMaxLines: Int = 2,
    /** 메타 줄(출처·저장 시각)도 상세에서는 접지 않는다 — "장소 저장 · 5월 …"로 잘렸다 */
    metaMaxLines: Int = 1,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = WoojuinColor.TextPrimary,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = WoojuinColor.TextSecondary,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metaLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(color = dotColor)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = metaLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = WoojuinColor.TextMuted,
                        maxLines = metaMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 상태 화면 하단의 보조 문구. */
@Composable
fun CaptionText(text: String, modifier: Modifier = Modifier, color: Color = WoojuinColor.TextMuted) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

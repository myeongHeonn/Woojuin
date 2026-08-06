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

/**
 * 세로 스크롤 리스트 화면 공통 골격.
 * TransformingLazyColumn은 회전 베젤/rotary 입력을 기본 지원한다.
 */
@Composable
fun WoojuinListScreen(
    modifier: Modifier = Modifier,
    edgeButton: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.() -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    if (edgeButton != null) {
        ScreenScaffold(scrollState = listState, edgeButton = edgeButton) { contentPadding ->
            TransformingLazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                content = content,
            )
        }
    } else {
        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(
                modifier = modifier.fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                content = content,
            )
        }
    }
}

/** 중앙 정렬 상태 화면(청취·인식·위치 탐색 등) 골격. */
@Composable
fun WoojuinStatusScreen(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    ScreenScaffold {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            content()
        }
    }
}

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
                    maxLines = 2,
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
                        maxLines = 1,
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

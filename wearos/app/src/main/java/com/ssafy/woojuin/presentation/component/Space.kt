package com.ssafy.woojuin.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.ssafy.woojuin.presentation.theme.WoojuinColor
import kotlin.math.cos
import kotlin.math.sin

/**
 * 우주인 워치의 공용 시각 문법.
 *
 * <p>모든 화면이 같은 하늘 위에 있어야 한 앱으로 읽힌다. 예전에는 화면마다 검은 배경에
 * Wear 기본 버튼을 얹어서, 화면을 넘길 때마다 다른 앱처럼 보였다.
 *
 * <ul>
 *   <li>[SpaceBackdrop] — 중앙 발광 + 고정 별. 발광 색을 기능 색으로 주면 화면이 그 기능의
 *       빛을 받는다(음성=보라, 검색=파랑, 노래=노랑, 장소=초록)
 *   <li>[GlassButton] — 표면 그라데이션 + 기능 색 헤어라인. Wear 기본 버튼의 스타디움
 *       모양을 쓰지 않는다 — 그 모양이 "기본 앱" 인상의 절반이었다
 * </ul>
 */

/** 별 하나 — 화면 크기 대비 비율 좌표(0..1)와 지름·밝기. */
private data class Star(val x: Float, val y: Float, val size: Float, val alpha: Float)

/**
 * 별은 고정 좌표다 — 매 프레임 난수를 쓰면 리컴포지션마다 하늘이 흔들린다.
 * 궤도 바깥 띠에만 두어 궤도·노드·중앙 행성과 겹치지 않는다.
 */
private val STARS = listOf(
    Star(0.655f, 0.115f, 1.6f, 0.45f),
    Star(0.140f, 0.270f, 1.2f, 0.32f),
    Star(0.912f, 0.315f, 1.0f, 0.26f),
    Star(0.062f, 0.395f, 1.4f, 0.38f),
    Star(0.325f, 0.870f, 1.1f, 0.28f),
    Star(0.672f, 0.905f, 1.5f, 0.40f),
)

/**
 * 화면의 하늘 — 중앙 발광 + 별, 그리고 [orbitRadius] 를 주면 궤도선과 스파클까지.
 *
 * <p>궤도선을 균일한 회색 원으로 그리면 도형처럼 보인다. **스윕 그라데이션**으로 한쪽을
 * 밝히면 빛을 받은 고리로 읽혀서, 같은 1.5dp 선인데도 화면이 훨씬 깊어진다. 궤도 위의
 * 스파클은 로고의 그 스파클이다 — 브랜드의 서명을 한 점 남긴다.
 */
@Composable
fun SpaceBackdrop(
    modifier: Modifier = Modifier,
    glowColor: Color = WoojuinColor.AccentPurple,
    orbitRadius: Dp? = null,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val glowRadius = size.minDimension * 0.34f

        // 중앙 발광 — 내용이 검은 배경에 떠 있게 만든다(없으면 화면에 붙어 보인다)
        drawCircle(
            brush = Brush.radialGradient(
                0f to glowColor.copy(alpha = 0.26f),
                0.55f to glowColor.copy(alpha = 0.07f),
                1f to Color.Transparent,
                center = center,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = center,
        )

        if (orbitRadius != null) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0.00f to glowColor.copy(alpha = 0.60f),
                    0.30f to WoojuinColor.SurfaceActive,
                    0.62f to glowColor.copy(alpha = 0.30f),
                    1.00f to glowColor.copy(alpha = 0.60f),
                    center = center,
                ),
                radius = orbitRadius.toPx(),
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // 궤도 위 1시 방향의 스파클
            val sparkleAngle = Math.toRadians(42.0)
            drawSparkle(
                center = Offset(
                    center.x + orbitRadius.toPx() * sin(sparkleAngle).toFloat(),
                    center.y - orbitRadius.toPx() * cos(sparkleAngle).toFloat(),
                ),
                halfLength = 7.dp.toPx(),
            )
        }

        STARS.forEach { star ->
            drawCircle(
                color = WoojuinColor.StarWhite,
                radius = star.size.dp.toPx() / 2f,
                center = Offset(size.width * star.x, size.height * star.y),
                alpha = star.alpha,
            )
        }
    }
}

/** 로고의 스파클과 같은 방식 — 가로·세로로 눌린 방사 그라데이션 두 개를 겹친다. */
private fun DrawScope.drawSparkle(center: Offset, halfLength: Float) {
    val thinness = 0.104f
    val brush = Brush.radialGradient(
        0.036f to WoojuinColor.SparkleCore,
        1f to WoojuinColor.SparkleTail.copy(alpha = 0f),
        center = center,
        radius = halfLength,
    )
    listOf(true, false).forEach { vertical ->
        withTransform({
            if (vertical) {
                scale(scaleX = thinness, scaleY = 1f, pivot = center)
            } else {
                scale(scaleX = 1f, scaleY = thinness, pivot = center)
            }
        }) {
            drawCircle(brush = brush, radius = halfLength, center = center, blendMode = BlendMode.Plus)
        }
    }
}

/**
 * [GlassButton]·[GlassCard] 의 모서리.
 *
 * <p>18dp 였을 때 44dp 높이 버튼에서는 반원에 가까워 결국 알약처럼 보였다 — 걷어내려던
 * 그 모양이다. 12dp 는 부드럽지만 사각형으로 읽힌다.
 */
private val GlassShape = RoundedCornerShape(12.dp)

/**
 * 색 위에 얹을 글자색.
 *
 * <p>경계를 0.5 로 잡았더니 파랑(#8FB4FF, 밝기 0.46)에 흰 글자가 올라가 명암비가 2:1 밖에
 * 안 됐다 — 실측 화면에서 읽기 어려웠다. 0.30 이면 파랑·초록·노랑·연어색은 검은 글자,
 * 보라(0.21)만 흰 글자가 되어 네 색 모두 읽힌다.
 */
private fun onAccent(accent: Color): Color =
    if (accent.luminance() > 0.30f) WoojuinColor.SpaceBlack else Color.White

/**
 * 화면의 **주 동작** — 아래 베젤을 따라 휘는 버튼.
 *
 * <p>둥근 화면 아래쪽은 쓸 수 있는 폭이 급격히 좁아진다. 사각 버튼을 화면 아래에 두면
 * 아래 두 모서리가 곡면 밖으로 나가 잘려 보였다(노래 청취 화면의 "취소"에서 실측).
 * `EdgeButton` 은 그 곡면을 따라 모양이 휘므로 잘릴 자리가 없고, 워치에만 있는 모양이라
 * 화면이 워치답게 보인다.
 *
 * @param primary 되돌릴 수 없거나 화면의 목적인 동작이면 true — 색으로 채운다
 */
@Composable
fun WoojuinEdgeButton(
    label: String,
    onClick: () -> Unit,
    accent: Color = WoojuinColor.TextSecondary,
    primary: Boolean = false,
) {
    // 회색(SurfaceRaised) 채움을 쓰지 않는다 — 검은 배경 위에서 탁한 회색 덩어리로
    // 보였다. 화면의 기능 색을 옅게 깔고 글자를 그 색으로 두면 같은 자리가 선명해진다
    EdgeButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) accent else accent.copy(alpha = 0.20f),
            contentColor = if (primary) onAccent(accent) else accent,
        ),
    ) {
        // **라벨은 한 줄로 들어와야 한다.** EdgeButton 은 아래로 좁아지는 모양이라 두 줄이
        // 되면 둘째 줄이 곡면에 잘렸다("휴대폰에서 / 열기"에서 "열기"가 먹혔다). 글자를
        // 한 단계 작게 쓰고, 긴 문구는 이 버튼에 두지 않는다(본문의 GlassButton 을 쓴다)
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/**
 * 목록의 한 칸. 필요하면 탭도 받는다.
 *
 * <p>Wear 기본 `Card` 는 단색 채움이라 검은 배경 위에서 회색 사각형으로 보인다. 버튼과
 * 같은 문법을 쓴다 — 종류 색을 옅게 깔고 같은 색 테두리 한 겹. 카드는 여러 장이 겹쳐
 * 놓이므로 버튼보다 더 옅게 깐다.
 *
 * @param accent 헤어라인 색 — 항목의 종류 색을 주면 목록을 훑을 때 종류가 먼저 읽힌다
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = WoojuinColor.TextMuted,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.97f else 1f, label = "cardPress")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(GlassShape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.04f)),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.40f), GlassShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            // 카드 안쪽 여백은 좁게 — 둥근 화면에서 좌우 여백까지 겹치면 글자 폭이
            // 9자밖에 안 남아 제목이 자꾸 잘렸다
            .padding(horizontal = 10.dp, vertical = 10.dp),
        content = content,
    )
}

/**
 * 이 앱의 표준 동작 버튼.
 *
 * <p><b>채움을 걷었다.</b> 표면색 그라데이션(#343947 → #20242F)으로 채웠더니 검은 배경
 * 위에서 탁한 회색 덩어리가 됐다("더 보기"·"완료"에서 드러났다). 홈의 위성이 채움 없이
 * 기능 색 빛만 두르고 서 있는 것과 같은 문법으로 바꾼다 — **기능 색을 아주 옅게 깔고
 * 같은 색 테두리 한 겹**. 검은 배경에서 색이 살고, 화면마다 그 화면의 색을 입는다.
 *
 * <p>라벨에 [maxLines] 를 걸지 않는다. 워치에서 한국어 버튼 글자는 한 줄에 안 들어가는
 * 일이 흔한데, 잘라내면 무슨 버튼인지 알 수 없다 — 높이가 늘어나는 쪽이 항상 낫다.
 *
 * @param primary 화면의 주 동작이면 true — 유일하게 색으로 채운다(중앙 행성과 같은 보라)
 */
@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = WoojuinColor.TextSecondary,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "glassPress")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(GlassShape)
            .background(
                if (primary) {
                    // 주 동작만 색으로 채운다 — 화면에서 유일하게 채워진 것이어야 눈에 든다
                    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.82f)))
                } else {
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.06f)),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = if (primary) {
                    Color.White.copy(alpha = 0.24f)
                } else {
                    accent.copy(alpha = 0.55f)
                },
                shape = GlassShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) onAccent(accent) else accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (primary) onAccent(accent) else WoojuinColor.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

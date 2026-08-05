package com.ssafy.woojuin.presentation.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.ssafy.woojuin.presentation.theme.WoojuinColor

/**
 * 우주인 로고 지오메트리. 원본 SVG(viewBox 60x60)의 path·비율·궤도 방향을 그대로 사용한다.
 * 재디자인 금지 — 선 굵기만 소형 화면 가독성을 위해 하한을 둔다.
 */
object WoojuinLogoGeometry {
    const val VIEWPORT = 60f

    const val ORBIT_PATH =
        "M26.8904 25.2747C31.5656 21.7491 36.1933 19.0839 39.8947 17.6243C41.7456 16.8944 " +
            "43.3628 16.4672 44.6365 16.3833C45.9128 16.2992 46.831 16.5603 47.3045 17.1881C" +
            "47.7777 17.816 47.7759 18.7701 47.3442 19.9738C46.9133 21.1754 46.058 22.6128 " +
            "44.8473 24.1917C42.4263 27.3492 38.5915 31.0657 33.9163 34.5914C29.2409 38.1172 " +
            "24.6127 40.7828 20.9112 42.2425C19.0602 42.9724 17.4431 43.3995 16.1693 43.4835C" +
            "14.8933 43.5676 13.9755 43.3069 13.502 42.6794C13.0285 42.0515 13.0299 41.0969 " +
            "13.4616 39.893C13.8926 38.6913 14.7478 37.254 15.9585 35.675C18.3797 32.5175 " +
            "22.215 28.8006 26.8904 25.2747Z"

    /** 스파클 이동 경로(반시계, 왼쪽 아래 → 우측 상단 정위치). 정위치 기준 상대 좌표. */
    const val SPARKLE_MOTION_PATH =
        "M -32.998 26.1794 " +
            "C -32.5245 26.8076 -31.6067 27.0676 -30.3307 26.9835 " +
            "C -29.0569 26.8995 -27.4398 26.4724 -25.5888 25.7425 " +
            "C -21.8873 24.2828 -17.2591 21.6172 -12.5837 18.0914 " +
            "C -7.9085 14.5657 -4.0737 10.8492 -1.6527 7.6917 " +
            "C -0.442 6.1128 0.4133 4.6754 0.8442 3.4738 " +
            "C 1.2759 2.2701 1.2777 1.316 0.8045 0.6881 " +
            "C 0.64 0.47 0.39 0.24 0 0"

    val SPARKLE_CENTER = Offset(46.5f, 16.5f)
    val GLOW_CENTER = Offset(37.939f, 19.6898f)
    const val GLOW_RX = 5.94922f
    const val GLOW_RY = 3.84292f
    const val GLOW_ROTATION = 35.1926f
    const val SPARKLE_HALF_WIDTH = 0.885417f
    const val SPARKLE_HALF_LENGTH = 8.5f

    /**
     * 소형(워치) 렌더링에서 스파클이 상대적으로 커 보여 시각적 크기만 축소한다.
     * 궤도·링 지오메트리는 그대로 유지.
     */
    const val SPARKLE_VISUAL_SCALE = 0.72f
    const val RING_DISC_RADIUS = 15f
    const val RING_RADIUS = 11.5f
    const val RING_STROKE = 7f
    const val ORBIT_STROKE = 0.1f
}

/** 로고를 한 프레임 그리기 위한 파라미터. */
data class LogoFrame(
    /** 중앙 검정 디스크 반지름 (svg 단위) */
    val discRadius: Float = WoojuinLogoGeometry.RING_DISC_RADIUS,
    /** 중앙 흰 링 반지름 (svg 단위) */
    val ringRadius: Float = WoojuinLogoGeometry.RING_RADIUS,
    /** 스파클 이동 경로 진행도. 1f = 우측 상단 정위치 */
    val sparkleProgress: Float = 1f,
    /** 우측 상단 방사광 알파 */
    val glowAlpha: Float = 1f,
    /** 청취 펄스: 0f 없음, 0..1 강도 */
    val pulse: Float = 0f,
    val pulseColor: Color = WoojuinColor.AccentPurple,
    /** 전체 스케일 (마이크 RMS 반응, 0.96~1.06 권장) */
    val breathScale: Float = 1f,
)

private class LogoPaths {
    val orbit: Path = PathParser().parsePathString(WoojuinLogoGeometry.ORBIT_PATH).toPath()
    val motion: Path = PathParser().parsePathString(WoojuinLogoGeometry.SPARKLE_MOTION_PATH).toPath()
    val motionMeasure = PathMeasure().apply { setPath(motion, false) }
}

private fun LogoPaths.sparkleAt(progress: Float): Offset {
    val clamped = progress.coerceIn(0f, 1f)
    val end = motionMeasure.getPosition(motionMeasure.length)
    val pos = motionMeasure.getPosition(motionMeasure.length * clamped)
    // 경로 좌표는 정위치(끝점 0,0) 기준의 상대 좌표
    return Offset(
        WoojuinLogoGeometry.SPARKLE_CENTER.x + (pos.x - end.x),
        WoojuinLogoGeometry.SPARKLE_CENTER.y + (pos.y - end.y),
    )
}

/**
 * 정적/상태 표현이 모두 가능한 우주인 로고.
 * 기본값은 완성된 정적 로고이며 [frame]으로 청취 펄스·스파클 위치를 제어한다.
 */
@Composable
fun WoojuinLogo(
    modifier: Modifier = Modifier,
    frame: LogoFrame = LogoFrame(),
) {
    val paths = remember { LogoPaths() }
    val minOrbitStrokePx = with(androidx.compose.ui.platform.LocalDensity.current) { 0.75.dp.toPx() }
    Canvas(modifier = modifier) {
        drawLogoFrame(paths, frame, minOrbitStrokePx)
    }
}

/** 청취 상태 로고: 약한 펄스 + 궤도를 천천히 도는 스파클. */
@Composable
fun WoojuinListeningLogo(
    accent: Color,
    modifier: Modifier = Modifier,
    breathScale: Float = 1f,
    reduceMotion: Boolean = false,
) {
    if (reduceMotion) {
        WoojuinLogo(modifier = modifier, frame = LogoFrame(pulse = 0.5f, pulseColor = accent))
        return
    }
    val transition = rememberInfiniteTransition(label = "listening")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )
    val sparkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "sparkle",
    )
    WoojuinLogo(
        modifier = modifier,
        frame = LogoFrame(
            sparkleProgress = sparkle,
            pulse = pulse,
            pulseColor = accent,
            breathScale = breathScale.coerceIn(0.96f, 1.06f),
        ),
    )
}

private fun DrawScope.drawLogoFrame(paths: LogoPaths, frame: LogoFrame, minOrbitStrokePx: Float) {
    val s = size.minDimension / WoojuinLogoGeometry.VIEWPORT
    val center = Offset(size.width / 2f, size.height / 2f)
    val toCanvas = { p: Offset ->
        Offset(
            center.x + (p.x - WoojuinLogoGeometry.VIEWPORT / 2f) * s,
            center.y + (p.y - WoojuinLogoGeometry.VIEWPORT / 2f) * s,
        )
    }

    scale(frame.breathScale, pivot = center) {
        // 1. 청취 펄스 (링 바깥, 약하게)
        if (frame.pulse > 0f) {
            val pulseRadius = (WoojuinLogoGeometry.RING_DISC_RADIUS + 2.2f + frame.pulse * 2.4f) * s
            drawCircle(
                color = frame.pulseColor,
                radius = pulseRadius,
                center = center,
                alpha = 0.28f * frame.pulse,
                style = Stroke(width = 1.6f * s),
            )
        }

        // 2. 궤도선 — 원본 stroke 0.1, 소형 화면에서 하한 적용
        val orbitStroke = maxOf(WoojuinLogoGeometry.ORBIT_STROKE * s, minOrbitStrokePx)
        withTransform({
            translate(center.x - WoojuinLogoGeometry.VIEWPORT / 2f * s, center.y - WoojuinLogoGeometry.VIEWPORT / 2f * s)
            scale(s, s, pivot = Offset.Zero)
        }) {
            drawPath(
                path = paths.orbit,
                color = Color.White,
                alpha = 0.9f,
                style = Stroke(width = orbitStroke / s),
            )
        }

        // 3. 우측 상단 방사광
        if (frame.glowAlpha > 0f) {
            val glowCenter = toCanvas(WoojuinLogoGeometry.GLOW_CENTER)
            withTransform({
                rotate(WoojuinLogoGeometry.GLOW_ROTATION, pivot = glowCenter)
                scale(1f, WoojuinLogoGeometry.GLOW_RY / WoojuinLogoGeometry.GLOW_RX, pivot = glowCenter)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0.168f to Color.White.copy(alpha = 0.9f * frame.glowAlpha),
                        1f to Color.White.copy(alpha = 0f),
                        center = glowCenter,
                        radius = WoojuinLogoGeometry.GLOW_RX * s,
                    ),
                    radius = WoojuinLogoGeometry.GLOW_RX * s,
                    center = glowCenter,
                    blendMode = BlendMode.Plus,
                )
            }
        }

        // 4. 스파클 (세로 + 가로 광선, color-dodge 근사 = Plus)
        val sparkleCenter = toCanvas(paths.sparkleAt(frame.sparkleProgress))
        drawSparkleRay(sparkleCenter, s, vertical = true)
        drawSparkleRay(sparkleCenter, s, vertical = false)

        // 5. 중앙 링 (검정 디스크가 궤도선을 가린다)
        drawCircle(Color.Black, radius = frame.discRadius * s, center = center)
        drawCircle(
            color = Color.White,
            radius = frame.ringRadius * s,
            center = center,
            style = Stroke(width = WoojuinLogoGeometry.RING_STROKE * s),
        )
    }
}

private fun DrawScope.drawSparkleRay(center: Offset, s: Float, vertical: Boolean) {
    val half = WoojuinLogoGeometry.SPARKLE_HALF_LENGTH * WoojuinLogoGeometry.SPARKLE_VISUAL_SCALE
    val ratio = WoojuinLogoGeometry.SPARKLE_HALF_WIDTH / WoojuinLogoGeometry.SPARKLE_HALF_LENGTH
    withTransform({
        if (vertical) {
            scale(scaleX = ratio, scaleY = 1f, pivot = center)
        } else {
            scale(scaleX = 1f, scaleY = ratio, pivot = center)
        }
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0.036f to WoojuinColor.SparkleCore,
                1f to WoojuinColor.SparkleTail.copy(alpha = 0f),
                center = center,
                radius = half * s,
            ),
            radius = half * s,
            center = center,
            blendMode = BlendMode.Plus,
        )
    }
}

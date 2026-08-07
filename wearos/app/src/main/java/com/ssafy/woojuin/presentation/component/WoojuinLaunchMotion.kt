package com.ssafy.woojuin.presentation.component

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.wear.compose.material3.MaterialTheme

/** 시스템 애니메이션 비활성화(스케일 0) 여부. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * 콜드 스타트 로고 모션 (약 1.2초).
 * 원본 SVG(would-you-in-app-launch-constant-stroke.svg)의 타이밍을 재현한다.
 * - 링: 0.05s 시작, 0.82s 동안 r 4.5→11.9→11.5 (stroke 7 유지)
 * - 스파클: 0.14s 시작, 1.15s 동안 왼쪽 아래에서 반시계 궤도를 따라 이동
 * - 방사광: 0.70s에 0.28s 동안 페이드인
 */
@Composable
fun WoojuinLaunchMotion(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    logoSize: androidx.compose.ui.unit.Dp = 96.dp,
) {
    val reduceMotion = rememberReduceMotion()

    var elapsed by remember { mutableFloatStateOf(0f) }
    val totalSeconds = 1.32f

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            elapsed = totalSeconds
            onFinished()
            return@LaunchedEffect
        }
        var startNanos = -1L
        while (elapsed < totalSeconds) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                elapsed = (now - startNanos) / 1_000_000_000f
            }
        }
        onFinished()
    }

    val ringEase1 = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    val ringEase2 = remember { CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f) }
    val sparkleEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val glowEase = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }

    // 링: begin 0.05s, dur 0.82s, values 4.5→11.9→11.5 (keyTime 0.8에서 오버슛)
    val ringT = ((elapsed - 0.05f) / 0.82f).coerceIn(0f, 1f)
    val ringRadius: Float
    val discRadius: Float
    if (ringT < 0.8f) {
        val f = ringEase1.transform(ringT / 0.8f)
        ringRadius = 4.5f + (11.9f - 4.5f) * f
        discRadius = 8f + (15.4f - 8f) * f
    } else {
        val f = ringEase2.transform((ringT - 0.8f) / 0.2f)
        ringRadius = 11.9f + (11.5f - 11.9f) * f
        discRadius = 15.4f + (15f - 15.4f) * f
    }

    // 스파클: begin 0.14s, dur 1.15s
    val sparkleT = ((elapsed - 0.14f) / 1.15f).coerceIn(0f, 1f)
    val sparkleProgress = sparkleEase.transform(sparkleT)

    // 방사광: begin 0.70s, dur 0.28s
    val glowT = ((elapsed - 0.70f) / 0.28f).coerceIn(0f, 1f)
    val glowAlpha = glowEase.transform(glowT)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        WoojuinLogo(
            modifier = Modifier.size(logoSize),
            frame = LogoFrame(
                discRadius = discRadius,
                ringRadius = ringRadius,
                sparkleProgress = sparkleProgress,
                glowAlpha = glowAlpha,
            ),
        )
    }
}

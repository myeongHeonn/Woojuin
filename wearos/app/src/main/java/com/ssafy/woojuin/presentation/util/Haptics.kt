package com.ssafy.woojuin.presentation.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 공통 햅틱 피드백.
 * - 기능 시작: 가벼운 클릭
 * - 녹음 종료: 짧은 종료 피드백
 * - 저장 성공: 성공 햅틱
 * - 오류: 이중 짧은 햅틱
 */
class Haptics(private val vibrator: Vibrator?) {

    fun tapStart() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

    fun stopCapture() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

    fun success() = play(
        VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 60), intArrayOf(0, 120, 0, 200), -1)
    )

    fun error() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))

    private fun play(effect: VibrationEffect) {
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(effect)
    }

    companion object {
        fun from(context: Context): Haptics {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            return Haptics(manager?.defaultVibrator)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics.from(context) }
}

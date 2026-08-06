package com.ssafy.woojuin.presentation.util

import android.content.Context
import android.os.Build
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
        /**
         * `VibratorManager` 는 API 31 부터다. minSdk 가 30 이므로 그 아래에서는 클래스가
         * 없어 `NoClassDefFoundError` 로 죽는다 — 워치 하나에서 됐다고 다 되는 게 아니라
         * (이 워치는 API 36) 갈라 둔다. lint 가 잡아준 결함이다.
         */
        fun from(context: Context): Haptics {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                return Haptics(manager?.defaultVibrator)
            }
            @Suppress("DEPRECATION")
            return Haptics(context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember { Haptics.from(context) }
}

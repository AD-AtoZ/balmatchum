package kr.ac.cnu.balmatchum.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import kr.ac.cnu.balmatchum.core.ArrivalState

/**
 * 상태가 바뀔 때 울리는 진동.
 * 여유 있음은 무진동, 서두르면 맞춤은 짧게 1번, 뛰어야 맞춤은 짧게 3번,
 * 뛰어도 어려움은 길게 1번으로 구분한다.
 */
class Haptics(context: Context) {

    private val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun play(state: ArrivalState) {
        val effect = when (state) {
            ArrivalState.RELAXED -> {
                vibrator.cancel()
                return
            }
            ArrivalState.HURRY -> VibrationEffect.createOneShot(SHORT_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            ArrivalState.RUN -> VibrationEffect.createWaveform(
                // 대기 → 진동 → 휴지 → 진동 → 휴지 → 진동. 반복하지 않는다.
                longArrayOf(0, SHORT_DURATION_MS, PAUSE_MS, SHORT_DURATION_MS, PAUSE_MS, SHORT_DURATION_MS),
                -1,
            )
            ArrivalState.TOO_LATE -> VibrationEffect.createOneShot(LONG_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    /** 목적지에 도착했을 때. */
    fun playArrived() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
    }

    private companion object {
        const val SHORT_DURATION_MS = 200L
        const val LONG_DURATION_MS = 600L
        const val PAUSE_MS = 100L
    }
}

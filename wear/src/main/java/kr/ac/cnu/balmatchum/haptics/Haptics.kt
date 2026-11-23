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

    /**
     * 확인 진동 모드용. [play]와 같은 길이·간격 패턴을 최대 세기로 울린다(옷 위에서도 느끼게).
     * 기본 모드의 [play]는 바꾸지 않으려고 따로 둔다.
     */
    fun playStrong(state: ArrivalState) {
        val timings: LongArray = when (state) {
            ArrivalState.RELAXED -> {
                vibrator.cancel()
                return
            }
            ArrivalState.HURRY -> longArrayOf(0, SHORT_DURATION_MS)
            ArrivalState.RUN -> longArrayOf(0, SHORT_DURATION_MS, PAUSE_MS, SHORT_DURATION_MS, PAUSE_MS, SHORT_DURATION_MS)
            ArrivalState.TOO_LATE -> longArrayOf(0, LONG_DURATION_MS)
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            // 짝수 칸(대기)은 0, 홀수 칸(진동)은 최대.
            val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 0) 0 else MAX_AMPLITUDE }
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }

    /** 진행 중인 진동을 멈춘다. */
    fun cancel() = vibrator.cancel()

    private companion object {
        const val MAX_AMPLITUDE = 255
        const val SHORT_DURATION_MS = 200L
        const val LONG_DURATION_MS = 600L
        const val PAUSE_MS = 100L
    }
}

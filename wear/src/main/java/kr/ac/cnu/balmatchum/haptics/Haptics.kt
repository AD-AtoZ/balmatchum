package kr.ac.cnu.balmatchum.haptics

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import kr.ac.cnu.balmatchum.core.ArrivalState

/**
 * 상태가 바뀔 때 울리는 진동. 지금은 "여유 있음"만 조용하고 나머지는 모두 같은 짧은 진동 한 번이다.
 * 상태별로 다르게 느껴지는 패턴은 TODOLIST T7에서 정한다(오즈의 마법사 테스트 T8 결과 반영).
 */
class Haptics(context: Context) {

    private val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    fun play(state: ArrivalState) {
        if (state == ArrivalState.RELAXED) return
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** 목적지에 도착했을 때. */
    fun playArrived() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100), -1))
    }
}

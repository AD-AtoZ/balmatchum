package kr.ac.cnu.balmatchum.guidance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.PersonalSpeeds

/** 안내를 시작할 때 화면이 서비스에 넘기는 값. */
data class GuidanceRequest(
    val destination: Destination,
    /** 도착 목표 시각(epoch ms). */
    val deadlineMillis: Long,
    val speeds: PersonalSpeeds,
    /** null이면 실제 위치(GPS·Wi-Fi). 값이 있으면 그 속도(m/s)로 경로를 따라 가상으로 걷는다. */
    val simulatedSpeedMps: Double? = null,
)

/** 안내가 끝났을 때 결과 화면에 보여 줄 값. */
data class GuidanceResult(
    val destinationName: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val deadlineMillis: Long,
    /** 목적지 반경 안에 들어와서 끝났으면 true, 사용자가 중간에 끝냈으면 false. */
    val arrived: Boolean,
    /** 걸은 거리 ÷ 걸린 시간(m/s). */
    val averageSpeedMps: Double,
    /** 이번 안내 기록 CSV 파일 경로(워치 안). */
    val csvPath: String?,
) {
    /** 양수면 목표보다 일찍, 음수면 늦게 도착(초). */
    val earlySec: Double get() = (deadlineMillis - endedAtMillis) / 1000.0
}

sealed interface GuidanceState {
    data object Idle : GuidanceState
    data class Running(
        val request: GuidanceRequest,
        val startedAtMillis: Long,
        /** 가장 최근 판단 결과. 첫 위치를 받기 전에는 null. */
        val latest: CoachUpdate?,
    ) : GuidanceState
    data class Finished(val result: GuidanceResult) : GuidanceState
}

/**
 * 안내 상태를 화면과 서비스가 함께 보는 곳. 서비스([GuidanceService])가 값을 바꾸고 화면은 구독만 한다.
 * 같은 앱 프로세스 안에서만 쓰므로 간단히 object로 둔다.
 */
object GuidanceSession {

    private val _state = MutableStateFlow<GuidanceState>(GuidanceState.Idle)
    val state: StateFlow<GuidanceState> = _state

    internal fun set(state: GuidanceState) {
        _state.value = state
    }

    /** 결과 화면을 닫고 처음으로 돌아갈 때. */
    fun reset() {
        if (_state.value !is GuidanceState.Running) _state.value = GuidanceState.Idle
    }
}

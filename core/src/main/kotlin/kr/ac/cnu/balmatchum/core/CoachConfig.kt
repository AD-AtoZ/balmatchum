package kr.ac.cnu.balmatchum.core

/**
 * 판단 기준값 모음. **모두 시작값(제안, 미검증)이다.**
 * 오즈의 마법사 테스트와 교내 실측으로 고친다. 사람마다 다른 속도는 여기가 아니라 [PersonalSpeeds]에 있다.
 */
data class CoachConfig(
    /**
     * "여유 있음"이 되려면 보통 걸음으로 가도 남아야 하는 시간(초)의 최댓값.
     * 실제 여유는 min(이 값, 남은 보통 걸음 시간 × [relaxedMarginRatio])라서, 도착 직전에는 여유 요구가 작아진다.
     */
    val relaxedMarginSec: Double = 30.0,
    val relaxedMarginRatio: Double = 0.2,
    /** 같은 상태가 이 시간(ms) 이상 이어져야 상태를 바꾼다(오락가락 방지). */
    val stateHoldMillis: Long = 5_000,
    /** 이동 속도 평균을 낼 때 보는 최근 "움직인 시간"(ms). 멈춘 시간은 세지 않는다. */
    val speedWindowMillis: Long = 30_000,
    /** 이 속도(m/s) 미만은 멈춘 것으로 보고 이동 속도에서 뺀다. 신호 대기 오경보 방지. */
    val stopSpeedMps: Double = 0.4,
    /** 표본 간격이 이보다 길면(ms) 이 값으로 자른다. GPS가 끊겼다 돌아올 때 한 표본이 과대 반영되지 않게. */
    val maxSampleGapMillis: Long = 5_000,
    /** 목적지까지 경로상 남은 거리가 이 값(m) 이하이면 도착으로 본다. */
    val arrivalRadiusM: Double = 20.0,
    /** 이탈 기준 거리의 최솟값(m). 실제 기준은 max(이 값, GPS 정확도 × 2)(project.md 5-4). */
    val offRouteMinM: Double = 30.0,
    /** 이탈 후보가 이 시간(ms) 이상 이어지고 진행이 없을 때만 이탈로 확정한다. */
    val offRouteConfirmMillis: Long = 10_000,
    /** 이탈 후보 동안 경로상 진행이 이 거리(m) 이하이면 "진행이 없다"고 본다. */
    val offRouteProgressToleranceM: Double = 5.0,
    /** 다음 안내 지점(좌회전 등)까지 이 거리(m) 이하이면 화면에 큰 화살표로 띄운다. */
    val turnNearM: Double = 30.0,
    /** 안내 지점을 이 거리(m) 지나야 다음 안내 지점으로 넘어간다. GPS가 늦게 따라오는 것을 감안한 값. */
    val turnPassedM: Double = 10.0,

    // ---- GPS 보정 (T22). 실보행 4회의 GPS 정확도는 대부분 5~12m였다 ----

    /** GPS 정확도가 이 값(m)보다 나쁘면 그 위치는 판정에 쓰지 않는다. */
    val maxAccuracyM: Double = 30.0,
    /** 직전 위치에서 이 속도(m/s)보다 빨리 움직인 것처럼 나오면 튄 위치로 보고 버린다. 사람이 걸어서는 못 내는 속도. */
    val maxJumpSpeedMps: Double = 5.0,
    /** 튄 위치로 버리는 것이 이 시간(ms) 이어지면 새 위치를 받아들인다. 정말로 위치가 바뀐 경우에 갇히지 않게. */
    val jumpResetMillis: Long = 10_000,
    /** 정확도가 나빠 버리는 것이 이 시간(ms) 이어지면 나빠도 받아들인다. 걸음으로 메우는 최대 시간과 같게 둔다. */
    val lowAccuracyResetMillis: Long = 60_000,
    /** 위치 다듬기(칼만 필터)를 쓸지. */
    val smoothPositions: Boolean = true,
    /** 칼만 필터가 가정하는 걷는 사람의 가속도 흔들림(m/s²). 크면 위치를 빨리 따라가고, 작으면 더 매끄럽다. */
    val smoothingAccelNoise: Double = 1.0,
    /** 경로상 진행이 이 거리(m)보다 크게 뒤로 가면 바로 반영하지 않는다. */
    val backtrackToleranceM: Double = 3.0,
    /** 뒤로 간 위치가 이 시간(ms) 이어져야 남은 거리를 늘린다. 한두 번 튄 위치에 남은 거리가 늘지 않게. */
    val backtrackHoldMillis: Long = 5_000,
    /** 쓸 만한 GPS 위치가 이 시간(ms) 없으면 걸음 수로 경로를 따라 진행시킨다. */
    val deadReckonAfterMillis: Long = 3_000,
    /** 걸음 수로만 진행시키는 최대 시간(ms). 넘으면 진행을 멈추고 GPS를 기다린다(오차가 쌓이지 않게). */
    val deadReckonMaxMillis: Long = 60_000,
    /** 걸음 길이(m) 시작값. 안내 중 GPS가 좋을 때 실제 걸음 길이로 고쳐 간다. */
    val defaultStepLengthM: Double = 0.7,
)

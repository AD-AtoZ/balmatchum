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
)

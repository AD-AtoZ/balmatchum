package kr.ac.cnu.balmatchum.core

/** 도착 상태. 이름은 디자인 개요서 2번을 따른다. */
enum class ArrivalState(val label: String) {
    RELAXED("여유 있음"),
    HURRY("서두르면 맞춤"),
    RUN("뛰어야 맞춤"),
    TOO_LATE("뛰어도 어려움"),
}

data class Judgement(
    val state: ArrivalState,
    /** 남은 시간 안에 도착하려면 필요한 속도(m/s) = 남은 거리 ÷ 남은 시간. 시간이 지났으면 무한대. */
    val requiredSpeedMps: Double,
    /** 내 보통 걸음으로 갔을 때 남는 시간(초). 음수면 보통 걸음으로는 늦는다. */
    val slackAtWalkSec: Double,
    /** 내 뛰는 속도로도 늦는 시간(초). 늦지 않으면 0. "뛰어도 어려움"일 때 예상 지각으로 보여 준다. */
    val lateAtRunSec: Double,
)

/**
 * 한 시점의 상태를 **내 속도** 기준으로 판단한다.
 *
 * | 상태 | 조건 |
 * |---|---|
 * | 여유 있음 | 보통 걸음으로 가도 여유가 남는다 (최대 30초, 도착 직전엔 남은 걸음 시간의 20%) |
 * | 서두르면 맞춤 | 빠른 걸음이면 남은 시간 안에 도착 |
 * | 뛰어야 맞춤 | 뛰어야 남은 시간 안에 도착 |
 * | 뛰어도 어려움 | 뛰어도 늦음 |
 */
fun judge(
    remainingM: Double,
    remainingTimeSec: Double,
    speeds: PersonalSpeeds = PersonalSpeeds.DEFAULT,
    config: CoachConfig = CoachConfig(),
): Judgement {
    val atWalk = remainingM / speeds.walkMps
    val atBrisk = remainingM / speeds.briskMps
    val atRun = remainingM / speeds.runMps
    val state = when {
        atWalk + minOf(config.relaxedMarginSec, atWalk * config.relaxedMarginRatio) <= remainingTimeSec -> ArrivalState.RELAXED
        atBrisk <= remainingTimeSec -> ArrivalState.HURRY
        atRun <= remainingTimeSec -> ArrivalState.RUN
        else -> ArrivalState.TOO_LATE
    }
    return Judgement(
        state = state,
        requiredSpeedMps = if (remainingTimeSec > 0) remainingM / remainingTimeSec else Double.POSITIVE_INFINITY,
        slackAtWalkSec = remainingTimeSec - atWalk,
        lateAtRunSec = (atRun - remainingTimeSec).coerceAtLeast(0.0),
    )
}

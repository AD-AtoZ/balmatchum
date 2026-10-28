package kr.ac.cnu.balmatchum.core

/** 워치가 받은 위치 표본 한 개. */
data class LocationSample(
    val timeMillis: Long,
    val point: GeoPoint,
    /** 플랫폼(Health Services)이 준 속도(m/s). 없으면 null, 직전 표본과의 거리로 대신 계산한다. */
    val speedMps: Double? = null,
    /** GPS 수평 정확도(m). 없으면 null. */
    val accuracyM: Double? = null,
)

/** [ArrivalCoach.update] 한 번의 결과. 화면과 진동은 이것만 보고 정한다. */
data class CoachUpdate(
    val remainingM: Double,
    val remainingTimeSec: Double,
    /** 지금 실제로 걷고 있는 속도(멈춘 시간 제외, m/s). 아직 이동 표본이 없으면 내 보통 걸음. 화면·기록용. */
    val movingSpeedMps: Double,
    /** 남은 시간 안에 도착하려면 필요한 속도(m/s). 진행 화면에서 "필요한 속도 vs 내 속도"로 보여 준다. */
    val requiredSpeedMps: Double,
    /** 내 보통 걸음으로 갔을 때 남는 시간(초). */
    val slackAtWalkSec: Double,
    /** 뛰어도 늦는 시간(초). "뛰어도 어려움"일 때 예상 지각. */
    val lateAtRunSec: Double,
    /** 이번 표본만 보고 낸 상태. */
    val observedState: ArrivalState,
    /** 5초 유지 규칙을 거친 확정 상태. 화면 색과 진동은 이것을 따른다. */
    val state: ArrivalState,
    /** 확정 상태가 바뀌었으면 true. 첫 표본에서도 true다. 진동은 이때만 울린다. */
    val stateChanged: Boolean,
    val offRoute: Boolean,
    val offRouteChanged: Boolean,
    val arrived: Boolean,
)

/**
 * 판단 로직을 하나로 묶는다. 안드로이드에 의존하지 않으므로 시간과 위치를 모두 인자로 받는다.
 *
 * @param deadlineMillis 도착 목표 시각(epoch ms)
 * @param speeds 사용자가 미리 잰 자기 속도. 상태 판단 기준
 */
class ArrivalCoach(
    val route: Route,
    val deadlineMillis: Long,
    val speeds: PersonalSpeeds = PersonalSpeeds.DEFAULT,
    private val config: CoachConfig = CoachConfig(),
) {
    private val speed = MovingSpeedEstimator(config)
    private val debouncer = StateDebouncer<ArrivalState>(config.stateHoldMillis)
    private val offRoute = OffRouteDetector(config)
    private var lastSample: LocationSample? = null
    private var lastAlong: Double? = null

    fun update(sample: LocationSample): CoachUpdate {
        speed.add(sample.timeMillis, sample.speedMps ?: derivedSpeed(sample))
        lastSample = sample

        val projection = route.project(sample.point, lastAlong)
        lastAlong = projection.distanceAlong

        val remainingTimeSec = (deadlineMillis - sample.timeMillis) / 1000.0
        val judgement = judge(projection.remaining, remainingTimeSec, speeds, config)
        val stateChanged = debouncer.update(sample.timeMillis, judgement.state)
        val offRouteChanged = offRoute.update(
            sample.timeMillis, projection.offset, sample.accuracyM, projection.distanceAlong,
        )

        return CoachUpdate(
            remainingM = projection.remaining,
            remainingTimeSec = remainingTimeSec,
            movingSpeedMps = speed.movingSpeed() ?: speeds.walkMps,
            requiredSpeedMps = judgement.requiredSpeedMps,
            slackAtWalkSec = judgement.slackAtWalkSec,
            lateAtRunSec = judgement.lateAtRunSec,
            observedState = judgement.state,
            state = debouncer.current!!,
            stateChanged = stateChanged,
            offRoute = offRoute.isOffRoute,
            offRouteChanged = offRouteChanged,
            arrived = projection.remaining <= config.arrivalRadiusM,
        )
    }

    private fun derivedSpeed(sample: LocationSample): Double {
        val prev = lastSample ?: return 0.0
        val dt = (sample.timeMillis - prev.timeMillis) / 1000.0
        return if (dt <= 0) 0.0 else distanceMeters(prev.point, sample.point) / dt
    }
}

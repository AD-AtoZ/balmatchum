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

/** 이번 판정에 쓴 진행 거리가 어디서 왔는지. */
enum class PositionSource {
    /** GPS 위치(거르고 다듬은 것). */
    GPS,

    /** GPS가 나빠 걸음 수 × 걸음 길이로 경로를 따라 진행시킨 것. */
    STEPS,

    /** GPS도 걸음도 쓸 수 없어 직전 진행 거리를 그대로 둔 것. */
    HOLD,
}

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
    /** 도착. GPS 위치로만 판정한다(걸음 수로 메운 진행으로는 도착시키지 않는다). 실내 시연은 걸음 수로 판정한다. */
    val arrived: Boolean,
    /** 이번 판정에 쓴 진행 거리의 출처. 기록·분석용. */
    val source: PositionSource = PositionSource.GPS,
    /** 이번 GPS 위치를 버렸으면 그 이유. */
    val rejected: RejectReason? = null,
    /** 판정에 쓴 위치. GPS면 다듬은 위치, 아니면 경로 위의 추정 위치. */
    val position: GeoPoint? = null,
    /** 판정에 쓴 GPS 위치에서 경로 선까지 거리(m). GPS를 못 쓴 표본은 null. */
    val offsetM: Double? = null,
    /** 판정에 쓴 위치에서 경로 끝(목적지 입구 좌표)까지 직선거리(m). */
    val destinationDistanceM: Double? = null,
    /** 안내 시작부터 감지한 걸음 수. 걸음 센서가 없거나 권한이 없으면 0. */
    val steps: Int = 0,
    /** 지금 쓰는 걸음 길이(m). */
    val stepLengthM: Double = 0.0,
    /** 최근 분당 걸음 수. 실내 시연에서만 잰다. */
    val cadenceSpm: Double? = null,
    /** 다음 체크포인트까지 경로상 남은 거리(m). 기다리는 체크포인트가 없으면 null. 실내 시연에서만 쓴다. */
    val checkpointRemainingM: Double? = null,
    /** 지금 체크포인트 통과 확인을 받을 수 있는지. 화면에 확인 버튼을 띄울 때 쓴다. */
    val checkpointConfirmable: Boolean = false,
)

/**
 * 판단 로직을 하나로 묶는다. 안드로이드에 의존하지 않으므로 시간과 위치를 모두 인자로 받는다.
 *
 * 위치는 판정 전에 거르고 다듬는다(T22).
 * 1. [LocationGate]: 정확도가 나쁘거나 튄 위치는 버린다
 * 2. [PositionSmoother]: 남은 위치를 칼만 필터로 다듬는다
 * 3. 경로상 진행이 잠깐 뒤로 가는 것은 [CoachConfig.backtrackHoldMillis] 동안 반영하지 않는다
 * 4. 쓸 만한 GPS가 [CoachConfig.deadReckonAfterMillis] 넘게 없으면 걸음 수로 경로를 따라 진행시킨다([StepTracker])
 *
 * 실내 시연([indoor])에서는 GPS를 쓰지 않고 [updateIndoor]로 걸음 수만으로 진행시킨다.
 *
 * @param deadlineMillis 도착 목표 시각(epoch ms)
 * @param speeds 사용자가 미리 잰 자기 속도. 상태 판단 기준
 * @param indoor 실내 시연 설정. null이면 실외(GPS) 안내
 */
class ArrivalCoach(
    val route: Route,
    val deadlineMillis: Long,
    val speeds: PersonalSpeeds = PersonalSpeeds.DEFAULT,
    private val config: CoachConfig = CoachConfig(),
    private val indoor: IndoorSettings? = null,
) {
    private val speed = MovingSpeedEstimator(config)
    private val debouncer = StateDebouncer<ArrivalState>(config.stateHoldMillis)
    private val offRoute = OffRouteDetector(config)
    private val gate = LocationGate(config)
    private val smoother = PositionSmoother(config.smoothingAccelNoise)
    private val stepTracker = StepTracker(config.defaultStepLengthM)
    private val cadence = CadenceMeter()

    /** 마지막으로 받아들인 GPS 원본 표본. 속도 계산용. */
    private var lastSample: LocationSample? = null
    private var lastAlong: Double? = null
    private var lastTimeMillis: Long? = null
    private var lastGpsMillis: Long? = null
    private var backtrackSince: Long? = null

    /** [lastAlong]을 정할 때의 걸음 수. 걸음으로 메울 때 이 뒤의 걸음만 센다. */
    private var stepsAtAlong = 0
    private var stepsAtGps = 0
    private var alongAtGps = 0.0

    /** 통과를 확인한 체크포인트 개수. */
    private var checkpointsPassed = 0
    private var lastCadenceSpm: Double? = null
    private var indoorStepLengthM = indoor?.walk?.stepLengthM ?: 0.0

    /** 걸음 센서가 걸음을 감지할 때마다 부른다. */
    fun onStep(count: Int = 1) = stepTracker.onStep(count)

    fun update(sample: LocationSample): CoachUpdate {
        val rejected = gate.check(sample)
        if (rejected != null) return estimate(sample.timeMillis, rejected)

        val t = monotonic(sample.timeMillis)
        speed.add(t, sample.speedMps ?: derivedSpeed(sample))
        lastSample = sample
        if (gate.reAnchored) smoother.reset()

        val point =if (config.smoothPositions) smoother.update(sample) else sample.point
        val projection = route.project(point, lastAlong)
        val along = holdBacktrack(t, projection.distanceAlong)

        if (lastGpsMillis != null) stepTracker.learn(stepTracker.steps - stepsAtGps, along - alongAtGps)
        stepsAtGps = stepTracker.steps
        alongAtGps = along
        lastGpsMillis = t
        setAlong(along)

        val offRouteChanged = offRoute.update(t, projection.offset, sample.accuracyM, along)
        return build(t, along, PositionSource.GPS, null, point, projection.offset, offRouteChanged)
    }

    /**
     * 위치 콜백이 아예 오지 않을 때 1초마다 부른다. 걸음 수로 진행시키거나, 그것도 안 되면 진행 거리를 그대로 둔 채
     * 남은 시간만 줄여 판정한다.
     */
    fun updateWithoutGps(timeMillis: Long): CoachUpdate = estimate(timeMillis, null)

    /**
     * 실내 시연에서 1초마다 부른다. 출발점에서 시작해, 그사이 감지한 걸음 수 × 걸음 길이만큼 경로를 따라 진행시킨다.
     * 걸음 길이는 최근 분당 걸음 수에 맞춰 고른다. 통과를 확인하지 않은 체크포인트는 넘지 않는다.
     */
    fun updateIndoor(timeMillis: Long): CoachUpdate {
        val settings = requireNotNull(indoor) { "실내 시연 설정이 없다" }
        val t = monotonic(timeMillis)
        val base = lastAlong ?: 0.0
        val newSteps = stepTracker.steps - stepsAtAlong
        val spm = cadence.add(t, stepTracker.steps)
        val stepLength = settings.stepLength(spm)
        val limit = nextCheckpointM() ?: route.totalDistance
        val along = minOf(limit, base + newSteps * stepLength)

        // 체크포인트에 막혀 있어도 걷고 있으면 걷는 속도를 보여 준다.
        speed.add(t, if (spm != null) spm / 60.0 * stepLength else 0.0)
        lastCadenceSpm = spm
        indoorStepLengthM = stepLength
        setAlong(along)
        return build(
            t, along, if (newSteps > 0) PositionSource.STEPS else PositionSource.HOLD, rejected = null,
            position = null, offsetM = null, offRouteChanged = false,
        )
    }

    /**
     * 사용자가 체크포인트를 지나며 화면을 눌렀을 때 부른다. 진행 거리를 체크포인트 위치로 맞추고 그 뒤로 나아가게 한다.
     * 걸음 수 추정이 앞서 있었든 뒤처져 있었든 여기서 바로잡힌다. 받아들였으면 true.
     */
    fun confirmCheckpoint(): Boolean {
        val settings = indoor ?: return false
        val checkpoint = nextCheckpointM() ?: return false
        if (checkpoint - (lastAlong ?: 0.0) > settings.checkpointConfirmRangeM) return false
        checkpointsPassed++
        setAlong(checkpoint)
        return true
    }

    private fun nextCheckpointM(): Double? = indoor?.checkpointsM?.getOrNull(checkpointsPassed)

    private fun estimate(timeMillis: Long, rejected: RejectReason?): CoachUpdate {
        val t = monotonic(timeMillis)
        val base = lastAlong ?: 0.0
        val sinceGps = lastGpsMillis?.let { t - it }
        val canStep = sinceGps != null && sinceGps >= config.deadReckonAfterMillis && sinceGps <= config.deadReckonMaxMillis
        val newSteps = stepTracker.steps - stepsAtAlong
        val stepped = canStep && newSteps > 0
        val along = if (stepped) minOf(route.totalDistance, base + newSteps * stepTracker.stepLengthM) else base

        val dt = lastTimeMillis?.let { (t - it) / 1000.0 } ?: 0.0
        speed.add(t, if (dt > 0) (along - base) / dt else 0.0)
        if (stepped || (sinceGps != null && sinceGps > config.deadReckonMaxMillis)) setAlong(along)

        return build(
            t, along, if (stepped) PositionSource.STEPS else PositionSource.HOLD, rejected,
            position = null, offsetM = null, offRouteChanged = false,
        )
    }

    private fun build(
        t: Long,
        along: Double,
        source: PositionSource,
        rejected: RejectReason?,
        position: GeoPoint?,
        offsetM: Double?,
        offRouteChanged: Boolean,
    ): CoachUpdate {
        lastTimeMillis = t
        val remaining = (route.totalDistance - along).coerceAtLeast(0.0)
        val remainingTimeSec = (deadlineMillis - t) / 1000.0
        val judgement = judge(remaining, remainingTimeSec, speeds, config)
        val stateChanged = debouncer.update(t, judgement.state)
        val used = position ?: route.pointAt(along)
        val checkpointRemaining = nextCheckpointM()?.let { (it - along).coerceAtLeast(0.0) }

        return CoachUpdate(
            remainingM = remaining,
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
            arrived = if (indoor != null) {
                remaining <= indoor.arrivalRadiusM
            } else {
                source == PositionSource.GPS && remaining <= config.arrivalRadiusM
            },
            source = source,
            rejected = rejected,
            position = used,
            offsetM = offsetM,
            destinationDistanceM = distanceMeters(used, route.points.last()),
            steps = stepTracker.steps,
            stepLengthM = if (indoor != null) indoorStepLengthM else stepTracker.stepLengthM,
            cadenceSpm = lastCadenceSpm,
            checkpointRemainingM = checkpointRemaining,
            checkpointConfirmable = indoor != null && checkpointRemaining != null &&
                checkpointRemaining <= indoor.checkpointConfirmRangeM,
        )
    }

    private fun setAlong(along: Double) {
        lastAlong = along
        stepsAtAlong = stepTracker.steps
    }

    /** 경로상 진행이 뒤로 간 것은 [CoachConfig.backtrackHoldMillis] 이어질 때만 받아들인다. */
    private fun holdBacktrack(t: Long, along: Double): Double {
        val last = lastAlong ?: return along
        if (along >= last - config.backtrackToleranceM) {
            backtrackSince = null
            return along
        }
        val since = backtrackSince ?: t.also { backtrackSince = it }
        if (t - since < config.backtrackHoldMillis) return last
        backtrackSince = null
        return along
    }

    /** GPS 시각과 워치 시계가 조금 어긋나도 시간이 뒤로 가지 않게 한다. */
    private fun monotonic(timeMillis: Long): Long = maxOf(timeMillis, lastTimeMillis ?: timeMillis)

    private fun derivedSpeed(sample: LocationSample): Double {
        val prev = lastSample ?: return 0.0
        val dt = (sample.timeMillis - prev.timeMillis) / 1000.0
        return if (dt <= 0) 0.0 else distanceMeters(prev.point, sample.point) / dt
    }
}

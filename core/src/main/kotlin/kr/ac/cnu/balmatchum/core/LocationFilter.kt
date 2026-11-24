package kr.ac.cnu.balmatchum.core

import kotlin.math.max

/** 위치를 판정에서 뺀 이유. */
enum class RejectReason {
    /** GPS 정확도가 [CoachConfig.maxAccuracyM]보다 나쁨. */
    LOW_ACCURACY,

    /** 직전 위치에서 사람이 못 내는 속도로 움직인 것처럼 나옴. */
    JUMP,
}

/**
 * 튀는 위치 거르기(T22 2번).
 *
 * 정확도가 나쁜 위치와, 직전에 받아들인 위치에서 [CoachConfig.maxJumpSpeedMps]보다 빨리 움직인 것처럼 나오는 위치를 버린다.
 * 이동 거리에서 두 위치의 정확도를 빼고 계산하므로 정확도 안쪽의 흔들림은 튐으로 보지 않는다.
 * 버리는 것이 오래 이어지면(튐 [CoachConfig.jumpResetMillis], 정확도 [CoachConfig.lowAccuracyResetMillis]) 다시 받아들인다.
 * 아직 받아들인 위치가 하나도 없을 때는 둘 다 [CoachConfig.jumpResetMillis]만 기다린다.
 */
class LocationGate(private val config: CoachConfig = CoachConfig()) {

    private var lastAccepted: LocationSample? = null
    private var rejectingSince: Long? = null

    /** 직전 [check]가 오래 버리다가 시간이 다 돼서 받아들인 것이면 true. 이때는 위치 다듬기를 새로 시작한다. */
    var reAnchored = false
        private set

    /** 받아들이면 null, 버리면 그 이유. */
    fun check(sample: LocationSample): RejectReason? {
        val reason = when {
            (sample.accuracyM ?: 0.0) > config.maxAccuracyM -> RejectReason.LOW_ACCURACY
            isJump(sample) -> RejectReason.JUMP
            else -> null
        }
        reAnchored = reason != null
        if (reason != null) {
            val since = rejectingSince ?: sample.timeMillis.also { rejectingSince = it }
            val limit = when {
                lastAccepted == null -> config.jumpResetMillis
                reason == RejectReason.JUMP -> config.jumpResetMillis
                else -> config.lowAccuracyResetMillis
            }
            if (sample.timeMillis - since < limit) return reason
        }
        lastAccepted = sample
        rejectingSince = null
        return null
    }

    private fun isJump(sample: LocationSample): Boolean {
        val prev = lastAccepted ?: return false
        val dt = (sample.timeMillis - prev.timeMillis) / 1000.0
        if (dt <= 0) return false
        val moved = distanceMeters(prev.point, sample.point) - (sample.accuracyM ?: 0.0) - (prev.accuracyM ?: 0.0)
        return max(moved, 0.0) > config.maxJumpSpeedMps * dt
    }
}

/**
 * 위치 다듬기(T22 3번). 동·북 방향 각각 "위치 + 속도"를 추정하는 칼만 필터.
 *
 * GPS 정확도가 나쁠수록 새 위치를 덜 믿고 직전 위치·속도로 예측한 쪽을 더 믿는다.
 * 표본 간격이 [RESET_GAP_MILLIS]보다 길면 예측이 의미 없으므로 새 위치에서 다시 시작한다.
 */
class PositionSmoother(private val accelNoise: Double = 1.0) {

    private var origin: GeoPoint? = null
    private var lastTimeMillis = 0L
    private val east = Axis()
    private val north = Axis()

    fun reset() {
        origin = null
    }

    fun update(sample: LocationSample): GeoPoint {
        val r = (sample.accuracyM ?: DEFAULT_ACCURACY_M).coerceAtLeast(1.0).let { it * it }
        val o = origin
        if (o == null || sample.timeMillis - lastTimeMillis > RESET_GAP_MILLIS) {
            origin = sample.point
            lastTimeMillis = sample.timeMillis
            east.start(0.0, r)
            north.start(0.0, r)
            return sample.point
        }
        val dt = ((sample.timeMillis - lastTimeMillis) / 1000.0).coerceAtLeast(0.0)
        lastTimeMillis = sample.timeMillis
        val z = toLocal(o, sample.point)
        east.step(dt, z.x, r, accelNoise)
        north.step(dt, z.y, r, accelNoise)
        return fromLocal(o, Local(east.p, north.p))
    }

    /** 한 방향의 위치 p, 속도 v와 그 공분산. */
    private class Axis {
        var p = 0.0
        var v = 0.0
        private var p11 = 0.0
        private var p12 = 0.0
        private var p22 = 0.0

        fun start(z: Double, r: Double) {
            p = z
            v = 0.0
            p11 = r
            p12 = 0.0
            p22 = INITIAL_SPEED_VAR
        }

        fun step(dt: Double, z: Double, r: Double, accelNoise: Double) {
            // 예측: 같은 속도로 dt초 더 갔다고 본다.
            p += v * dt
            val q = accelNoise * accelNoise
            val dt2 = dt * dt
            val n11 = p11 + 2 * dt * p12 + dt2 * p22 + q * dt2 * dt2 / 4
            val n12 = p12 + dt * p22 + q * dt2 * dt / 2
            val n22 = p22 + q * dt2
            // 보정: 새 위치와 예측의 차이를 정확도에 맞춰 반영한다.
            val s = n11 + r
            val k1 = n11 / s
            val k2 = n12 / s
            val y = z - p
            p += k1 * y
            v += k2 * y
            p11 = (1 - k1) * n11
            p12 = (1 - k1) * n12
            p22 = n22 - k2 * n12
        }
    }

    private companion object {
        const val DEFAULT_ACCURACY_M = 5.0
        const val RESET_GAP_MILLIS = 10_000L

        /** 처음 속도를 모를 때의 분산. 2m/s 정도 틀릴 수 있다고 본다. */
        const val INITIAL_SPEED_VAR = 4.0
    }
}

/**
 * 걸음 수로 진행 거리 메우기(T22 4번).
 *
 * 경로를 따라 걷는다고 보고, GPS가 나쁜 동안 걸은 걸음 수 × 걸음 길이만큼 경로상 진행을 늘린다.
 * 방향은 경로가 정해 주므로 나침반은 쓰지 않는다(손목은 걸을 때 흔들려 방향 값이 크게 흔들린다).
 * 걸음 길이는 GPS가 좋을 때 "경로상 진행 거리 ÷ 걸음 수"로 고쳐 간다.
 */
class StepTracker(defaultStepLengthM: Double = 0.7) {

    /** 지금까지 감지한 걸음 수. */
    var steps = 0
        private set

    /** 지금 쓰는 걸음 길이(m). */
    var stepLengthM = defaultStepLengthM
        private set

    private var learnSteps = 0
    private var learnDistance = 0.0

    fun onStep(count: Int = 1) {
        steps += count
    }

    /**
     * GPS가 좋을 때 두 위치 사이의 걸음 수와 경로상 진행 거리를 넣는다.
     * [LEARN_MIN_STEPS]걸음이 모일 때마다 걸음 길이를 고친다. 사람 걸음으로 말이 안 되는 값은 버린다.
     */
    fun learn(stepsBetween: Int, progressM: Double) {
        if (stepsBetween <= 0 || progressM <= 0) return
        learnSteps += stepsBetween
        learnDistance += progressM
        if (learnSteps < LEARN_MIN_STEPS) return
        val measured = learnDistance / learnSteps
        if (measured in MIN_STEP_M..MAX_STEP_M) {
            stepLengthM = stepLengthM * (1 - LEARN_WEIGHT) + measured * LEARN_WEIGHT
        }
        learnSteps = 0
        learnDistance = 0.0
    }

    private companion object {
        const val LEARN_MIN_STEPS = 20
        const val LEARN_WEIGHT = 0.3
        const val MIN_STEP_M = 0.3
        const val MAX_STEP_M = 1.5
    }
}

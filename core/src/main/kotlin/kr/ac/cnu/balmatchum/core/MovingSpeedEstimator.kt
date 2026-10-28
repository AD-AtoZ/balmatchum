package kr.ac.cnu.balmatchum.core

/**
 * 멈춘 시간을 뺀 이동 속도(project.md 5-1).
 *
 * 플랫폼이 주는 속도 표본을 받아, 멈춤 기준 이상인 표본만 최근 [CoachConfig.speedWindowMillis]
 * "움직인 시간" 동안 시간 가중 평균한다. 신호등에서 1분 서 있어도 평균이 0으로 끌려 내려가지 않는다.
 * 멈춰서 잃은 시간은 남은 시간이 줄어드는 것으로 이미 반영된다.
 */
class MovingSpeedEstimator(private val config: CoachConfig = CoachConfig()) {

    private class Slice(val durationMillis: Long, val speedMps: Double)

    private val slices = ArrayDeque<Slice>()
    private var windowMillis = 0L
    private var lastTimeMillis: Long? = null

    fun add(timeMillis: Long, speedMps: Double) {
        val previous = lastTimeMillis
        lastTimeMillis = timeMillis
        if (speedMps.isNaN() || speedMps < config.stopSpeedMps) return

        // 첫 표본은 앞 간격을 모르니 1초로 친다.
        val dt = if (previous == null) 1_000L else (timeMillis - previous).coerceIn(0L, config.maxSampleGapMillis)
        if (dt == 0L) return
        slices.addLast(Slice(dt, speedMps))
        windowMillis += dt
        while (windowMillis - slices.first().durationMillis >= config.speedWindowMillis) {
            windowMillis -= slices.removeFirst().durationMillis
        }
    }

    /** 이동 속도(m/s). 아직 움직인 표본이 없으면 null. */
    fun movingSpeed(): Double? {
        if (slices.isEmpty()) return null
        return slices.sumOf { it.speedMps * it.durationMillis } / windowMillis
    }
}

package kr.ac.cnu.balmatchum.core

/**
 * 한 번의 개인 속도 측정에서 대표 속도(m/s)를 구한다. Android 없이 사용할 수 있다.
 *
 * 첫 [add] 호출의 시각을 시작으로 삼아 처음 5초와 0.4 m/s 미만의 표본을 제외한다.
 * 시각은 같은 기준의 밀리초 값을 사용한다. 30초 측정의 시작·종료는 호출자가 관리하며,
 * 보통 걸음·빠른 걸음·뛰기 등 새 측정마다 새 인스턴스를 만든다.
 */
class SpeedCalibrator {

    private var startTimeMillis: Long? = null
    private val speeds = mutableListOf<Double>()

    fun add(timeMillis: Long, speedMps: Double) {
        val start = startTimeMillis ?: timeMillis.also { startTimeMillis = it }
        if (timeMillis < start || timeMillis - start < WARMUP_MILLIS) return
        if (!speedMps.isFinite() || speedMps < MIN_SPEED_MPS) return
        speeds.add(speedMps)
    }

    /** 유효한 표본의 중앙값(m/s). 표본이 없으면 null. 호출해도 측정값은 유지된다. */
    fun result(): Double? {
        if (speeds.isEmpty()) return null
        val sorted = speeds.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle]
        else sorted[middle - 1] / 2.0 + sorted[middle] / 2.0
    }

    private companion object {
        const val WARMUP_MILLIS = 5_000L
        const val MIN_SPEED_MPS = 0.4
    }
}

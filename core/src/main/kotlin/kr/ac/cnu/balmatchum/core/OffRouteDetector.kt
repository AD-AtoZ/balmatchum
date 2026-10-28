package kr.ac.cnu.balmatchum.core

/**
 * 경로 이탈 판단(project.md 5-4, §11-2 "있으면 좋은 것").
 *
 * 경로 선까지 거리가 max(최소 기준, GPS 정확도 × 2)를 넘는 상태가 일정 시간 이어지고,
 * 그동안 경로상 진행이 거의 없을 때만 이탈로 확정한다. 경로 근처로 돌아오면 바로 해제한다.
 */
class OffRouteDetector(private val config: CoachConfig = CoachConfig()) {

    var isOffRoute = false
        private set

    private var candidateSince: Long? = null
    private var alongAtCandidate = 0.0

    /** 이탈 여부가 바뀌었으면 true. */
    fun update(timeMillis: Long, offsetM: Double, accuracyM: Double?, distanceAlong: Double): Boolean {
        val before = isOffRoute
        val threshold = maxOf(config.offRouteMinM, (accuracyM ?: 0.0) * 2)
        if (offsetM <= threshold) {
            candidateSince = null
            isOffRoute = false
        } else {
            val since = candidateSince ?: timeMillis.also {
                candidateSince = it
                alongAtCandidate = distanceAlong
            }
            val noProgress = distanceAlong <= alongAtCandidate + config.offRouteProgressToleranceM
            if (timeMillis - since >= config.offRouteConfirmMillis && noProgress) isOffRoute = true
        }
        return isOffRoute != before
    }
}

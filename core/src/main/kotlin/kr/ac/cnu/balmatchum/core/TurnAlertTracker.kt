package kr.ac.cnu.balmatchum.core

/** 안내 세션마다 새로 만든다. GPS가 흔들리거나 이전 지점으로 돌아가도 같은 지점은 한 번만 알린다. */
class TurnAlertTracker {
    private val alertedTurns = mutableSetOf<RouteTurn>()

    fun shouldAlert(next: UpcomingTurn?): Boolean {
        val turn = next?.turn ?: return false
        // TurnGuide의 근접 기준(기본 30m)을 화면과 함께 쓴다. 방향전환이 없는 시설 안내는 제외한다.
        if (!next.isNear || turn.arrowDegrees == 0f) return false
        return alertedTurns.add(turn)
    }
}

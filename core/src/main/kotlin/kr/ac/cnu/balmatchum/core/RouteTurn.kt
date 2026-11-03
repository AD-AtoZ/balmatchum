package kr.ac.cnu.balmatchum.core

/**
 * 경로 위 안내 지점 하나(좌회전, 횡단보도 등). TMAP 보행자 경로 응답의 Point(`turnType`)에서 만든다.
 * 화면에는 [arrowDegrees] 방향 화살표와 [label]을 띄운다.
 */
data class RouteTurn(
    /** 출발점부터 이 지점까지 경로를 따라 잰 거리(m). */
    val distanceAlongM: Double,
    /** TMAP `turnType` 원래 값. */
    val turnType: Int,
    /** 화살표 각도(도). 0 = 앞, -90 = 왼쪽, 90 = 오른쪽, 180 = 뒤. */
    val arrowDegrees: Float,
    /** 짧은 안내 이름. 예: "좌회전", "횡단보도". */
    val label: String,
    /** TMAP 안내 문장. 예: "지니OA에서 우회전 후 42m 이동". */
    val description: String,
) {
    companion object {
        /**
         * TMAP `turnType`을 안내 지점으로 바꾼다. 화면에 띄울 필요가 없는 지점은 null.
         * - null: 직진(11, 233), 안내 없음(1~7), 경유지(184~189), 출발지(200), 목적지(201, 화면이 따로 "도착까지"로 보여 준다)
         * - 표에 없는 값도 null. 모르는 화살표를 그리느니 안 띄운다.
         */
        fun fromTmap(distanceAlongM: Double, turnType: Int, description: String): RouteTurn? {
            val (degrees, label) = KINDS[turnType] ?: return null
            return RouteTurn(distanceAlongM, turnType, degrees, label, description)
        }

        /** TMAP 보행자 경로안내 문서의 turnType 표(2026-11-03 확인). */
        private val KINDS: Map<Int, Pair<Float, String>> = mapOf(
            12 to (-90f to "좌회전"),
            13 to (90f to "우회전"),
            14 to (180f to "유턴"),
            16 to (-135f to "8시 방향 좌회전"),
            17 to (-45f to "10시 방향 좌회전"),
            18 to (45f to "2시 방향 우회전"),
            19 to (135f to "4시 방향 우회전"),
            125 to (0f to "육교"),
            126 to (0f to "지하보도"),
            127 to (0f to "계단"),
            128 to (0f to "경사로"),
            129 to (0f to "계단·경사로"),
            211 to (0f to "횡단보도"),
            212 to (-90f to "왼쪽 횡단보도"),
            213 to (90f to "오른쪽 횡단보도"),
            214 to (-135f to "8시 방향 횡단보도"),
            215 to (-45f to "10시 방향 횡단보도"),
            216 to (45f to "2시 방향 횡단보도"),
            217 to (135f to "4시 방향 횡단보도"),
            218 to (0f to "엘리베이터"),
        )
    }
}

/**
 * 지금 위치에서 다음에 할 일.
 * [turn]이 null이면 남은 안내 지점이 없어 목적지까지 그대로 가면 된다는 뜻이고, [distanceM]은 목적지까지 거리다.
 */
data class UpcomingTurn(
    val turn: RouteTurn?,
    /** 그 지점까지 경로를 따라 남은 거리(m). 이미 지났으면 0. */
    val distanceM: Double,
    /** [CoachConfig.turnNearM] 안으로 들어왔는지. 화면을 크게 바꿀 때 쓴다. */
    val isNear: Boolean,
)

/**
 * 경로를 따라 온 거리로 다음 안내 지점을 고른다.
 * 안내 지점을 [CoachConfig.turnPassedM]만큼 지나야 다음 지점으로 넘어간다(GPS가 늦게 따라오는 것을 감안).
 * 다음 지점이 그보다 가까우면 둘 사이 가운데에서 넘어간다.
 */
class TurnGuide(
    private val turns: List<RouteTurn>,
    private val totalDistanceM: Double,
    private val config: CoachConfig = CoachConfig(),
) {
    fun next(distanceAlongM: Double): UpcomingTurn {
        val index = turns.indices.firstOrNull { i ->
            val gapToNext = turns.getOrNull(i + 1)?.let { it.distanceAlongM - turns[i].distanceAlongM }
            val passed = minOf(config.turnPassedM, gapToNext?.div(2) ?: Double.MAX_VALUE)
            distanceAlongM < turns[i].distanceAlongM + passed
        }
        val turn = index?.let { turns[it] }
        val distance = ((turn?.distanceAlongM ?: totalDistanceM) - distanceAlongM).coerceAtLeast(0.0)
        return UpcomingTurn(turn, distance, isNear = turn != null && distance <= config.turnNearM)
    }
}

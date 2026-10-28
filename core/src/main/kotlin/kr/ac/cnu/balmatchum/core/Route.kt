package kr.ac.cnu.balmatchum.core

import kotlin.math.abs
import kotlin.math.hypot

/** 현재 위치를 경로 선 위로 투영한 결과. */
data class RouteProjection(
    /** 출발점부터 경로를 따라 온 거리(m). */
    val distanceAlong: Double,
    /** 경로를 따라 남은 거리(m). 직선거리가 아니다. */
    val remaining: Double,
    /** 현재 위치에서 경로 선까지의 최단거리(m). 이탈 판단에 쓴다. */
    val offset: Double,
)

/**
 * 도보 경로(꺾은선). TMAP 응답의 LineString 좌표를 순서대로 넣는다.
 * 남은 거리는 위치를 경로 선에 투영해 경로를 따라 잰다(project.md 5-2).
 */
class Route(val points: List<GeoPoint>) {

    /** cumulative[i] = 출발점부터 points[i]까지 경로 거리(m). */
    private val cumulative: DoubleArray

    val totalDistance: Double

    init {
        require(points.size >= 2) { "경로에는 점이 2개 이상 필요하다" }
        cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumulative[i] = cumulative[i - 1] + distanceMeters(points[i - 1], points[i])
        }
        totalDistance = cumulative.last()
    }

    /**
     * [p]를 경로에 투영한다.
     *
     * [hintAlong]에 직전 진행 거리를 주면 그 근처 구간을 우선한다. 갔다가 되돌아오는 길처럼
     * 경로가 자기 자신과 가까이 붙는 곳에서 진행 거리가 앞뒤로 튀지 않게 하기 위해서다.
     * 근처 구간 중에서는 "경로까지 거리 + 직전 진행 거리와의 차이 × [HINT_WEIGHT]"가 가장 작은 곳을 고르고,
     * 그곳이 전체 최근접 구간보다 [HINT_SLACK_M] 넘게 멀면 전체 최근접을 쓴다.
     */
    fun project(p: GeoPoint, hintAlong: Double? = null): RouteProjection {
        var best: Candidate? = null
        var bestNearHint: Candidate? = null
        var bestNearHintScore = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val c = projectOnSegment(i, p)
            if (best == null || c.offset < best.offset) best = c
            if (hintAlong != null &&
                cumulative[i + 1] >= hintAlong - HINT_BEHIND_M &&
                cumulative[i] <= hintAlong + HINT_AHEAD_M
            ) {
                val score = c.offset + HINT_WEIGHT * abs(c.along - hintAlong)
                if (score < bestNearHintScore) {
                    bestNearHint = c
                    bestNearHintScore = score
                }
            }
        }
        val chosen = if (bestNearHint != null && bestNearHint.offset <= best!!.offset + HINT_SLACK_M) {
            bestNearHint
        } else {
            best!!
        }
        return RouteProjection(
            distanceAlong = chosen.along,
            remaining = (totalDistance - chosen.along).coerceAtLeast(0.0),
            offset = chosen.offset,
        )
    }

    /** 출발점부터 경로를 따라 [distanceAlong]만큼 간 지점. 가상 걷기에서 가짜 위치를 만들 때 쓴다. */
    fun pointAt(distanceAlong: Double): GeoPoint {
        val d = distanceAlong.coerceIn(0.0, totalDistance)
        val i = (0 until points.size - 1).lastOrNull { cumulative[it] <= d } ?: 0
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen == 0.0) 0.0 else (d - cumulative[i]) / segLen
        val a = points[i]
        val b = points[i + 1]
        return GeoPoint(lat = a.lat + (b.lat - a.lat) * t, lon = a.lon + (b.lon - a.lon) * t)
    }

    private data class Candidate(val along: Double, val offset: Double)

    private fun projectOnSegment(i: Int, p: GeoPoint): Candidate {
        val a = points[i]
        val b = toLocal(a, points[i + 1])
        val q = toLocal(a, p)
        val len2 = b.x * b.x + b.y * b.y
        val t = if (len2 == 0.0) 0.0 else ((q.x * b.x + q.y * b.y) / len2).coerceIn(0.0, 1.0)
        val offset = hypot(q.x - t * b.x, q.y - t * b.y)
        val segLen = cumulative[i + 1] - cumulative[i]
        return Candidate(along = cumulative[i] + t * segLen, offset = offset)
    }

    private companion object {
        const val HINT_BEHIND_M = 30.0
        const val HINT_AHEAD_M = 150.0
        const val HINT_SLACK_M = 15.0
        const val HINT_WEIGHT = 0.2
    }
}

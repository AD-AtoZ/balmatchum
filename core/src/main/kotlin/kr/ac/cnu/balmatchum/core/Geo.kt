package kr.ac.cnu.balmatchum.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도(도 단위). */
data class GeoPoint(val lat: Double, val lon: Double)

internal const val EARTH_RADIUS_M = 6_371_000.0

/** 두 점 사이의 지표 거리(m). 하버사인 공식. */
fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** origin 기준 평면 좌표(동쪽 x, 북쪽 y, m). 교내처럼 몇 km 안에서는 오차가 무시할 만하다. */
internal data class Local(val x: Double, val y: Double)

internal fun toLocal(origin: GeoPoint, p: GeoPoint): Local = Local(
    x = Math.toRadians(p.lon - origin.lon) * EARTH_RADIUS_M * cos(Math.toRadians(origin.lat)),
    y = Math.toRadians(p.lat - origin.lat) * EARTH_RADIUS_M,
)

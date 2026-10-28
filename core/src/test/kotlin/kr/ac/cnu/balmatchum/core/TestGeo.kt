package kr.ac.cnu.balmatchum.core

import kotlin.math.cos

/** 충남대 근처 기준점. 테스트 좌표는 여기서 동·북으로 m 단위로 옮겨 만든다. */
val BASE = GeoPoint(36.3670, 127.3440)

fun at(eastM: Double, northM: Double, origin: GeoPoint = BASE): GeoPoint = GeoPoint(
    lat = origin.lat + Math.toDegrees(northM / EARTH_RADIUS_M),
    lon = origin.lon + Math.toDegrees(eastM / (EARTH_RADIUS_M * cos(Math.toRadians(origin.lat)))),
)

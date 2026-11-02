package kr.ac.cnu.balmatchum

import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.GeoPoint
import kr.ac.cnu.balmatchum.core.TmapApi
import kr.ac.cnu.balmatchum.core.TmapRoute

internal class ArrivalTimeExpiredException : Exception()

/** 현재 위치와 경로 요청이 끝나도 목표 시각이 유효할 때만 전송할 목적지를 만든다. */
internal suspend fun preparePhoneDestination(
    poi: TmapApi.Poi,
    arrivalTimeMillis: Long,
    currentPoint: suspend () -> GeoPoint,
    routeJson: suspend (GeoPoint, TmapApi.Poi) -> String,
    now: () -> Long = System::currentTimeMillis,
): Destination {
    fun checkDeadline() {
        if (arrivalTimeMillis <= now()) throw ArrivalTimeExpiredException()
    }
    checkDeadline()
    val start = currentPoint()
    checkDeadline()
    val json = routeJson(start, poi)
    TmapRoute.parse(json)
    checkDeadline()
    return Destination(poi.name, json, arrivalTimeMillis = arrivalTimeMillis)
}

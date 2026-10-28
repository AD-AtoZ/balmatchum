package kr.ac.cnu.balmatchum.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** TMAP 보행자 경로안내 응답에서 꺼낸 경로. */
data class TmapRoute(
    val route: Route,
    /** 출발점(pointType=SP)의 totalDistance(m). */
    val totalDistanceM: Double,
    /** 출발점(pointType=SP)의 totalTime(초). */
    val totalTimeSec: Double,
) {
    /** TMAP이 가정한 걷는 속도(m/s). 목표 시각 기본값을 정할 때 참고한다. */
    val tmapSpeedMps: Double? get() = if (totalTimeSec > 0) totalDistanceM / totalTimeSec else null

    companion object {
        /**
         * `POST /tmap/routes/pedestrian` 응답(GeoJSON FeatureCollection)을 읽는다.
         * LineString 좌표를 순서대로 이어 붙이고, 이어지는 점이 같으면 하나만 남긴다.
         * 좌표는 [경도, 위도] 순서다.
         */
        fun parse(json: String): TmapRoute {
            val features = Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray
            val points = mutableListOf<GeoPoint>()
            var totalDistance: Double? = null
            var totalTime: Double? = null
            for (feature in features) {
                val f = feature.jsonObject
                val geometry = f.getValue("geometry").jsonObject
                val properties = f["properties"] as? JsonObject
                when (geometry.getValue("type").jsonPrimitive.content) {
                    "Point" -> if (properties?.get("pointType")?.jsonPrimitive?.content == "SP") {
                        totalDistance = properties["totalDistance"]?.jsonPrimitive?.double
                        totalTime = properties["totalTime"]?.jsonPrimitive?.double
                    }
                    "LineString" -> for (c in geometry.getValue("coordinates").jsonArray) {
                        val lonLat = c as JsonArray
                        val p = GeoPoint(lat = lonLat[1].jsonPrimitive.double, lon = lonLat[0].jsonPrimitive.double)
                        if (points.lastOrNull() != p) points += p
                    }
                }
            }
            val route = Route(points)
            return TmapRoute(
                route = route,
                totalDistanceM = totalDistance ?: route.totalDistance,
                totalTimeSec = totalTime ?: 0.0,
            )
        }
    }
}

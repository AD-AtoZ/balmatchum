package kr.ac.cnu.balmatchum.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * TMAP Open API 호출. 네트워크를 쓰므로 안드로이드에서는 메인 스레드가 아닌 곳(Dispatchers.IO)에서 부른다.
 * 키는 BuildConfig.TMAP_APP_KEY로 넘겨받는다(팀 공용 키는 저장소의 `secrets.defaults.properties`). 키를 로그에 찍지 않는다.
 */
class TmapApi(private val appKey: String) {

    data class Poi(
        val name: String,
        val address: String,
        /** 건물 입구 좌표(frontLat/frontLon). 길 안내 목적지로 이것을 쓴다. */
        val entrance: GeoPoint,
    )

    /**
     * 장소 검색(POI 통합 검색). [near]를 주면 그 근처 결과를 우선한다.
     * 결과가 없으면 빈 목록.
     */
    fun searchPois(keyword: String, near: GeoPoint? = null, count: Int = 10): List<Poi> {
        val q = buildString {
            append("version=1&count=").append(count)
            append("&searchKeyword=").append(URLEncoder.encode(keyword, "UTF-8"))
            append("&reqCoordType=WGS84GEO&resCoordType=WGS84GEO")
            if (near != null) append("&centerLat=${near.lat}&centerLon=${near.lon}&radius=0")
        }
        val body = request("GET", "https://apis.openapi.sk.com/tmap/pois?$q", null) ?: return emptyList()
        val pois = Json.parseToJsonElement(body).jsonObject["searchPoiInfo"]?.jsonObject
            ?.get("pois")?.jsonObject?.get("poi") as? JsonArray ?: return emptyList()
        return pois.map { it.jsonObject }.map { p ->
            fun s(key: String) = p[key]?.jsonPrimitive?.content.orEmpty()
            Poi(
                name = s("name"),
                address = listOf(s("upperAddrName"), s("middleAddrName"), s("lowerAddrName"), s("firstNo"))
                    .filter { it.isNotBlank() }.joinToString(" "),
                entrance = GeoPoint(s("frontLat").toDouble(), s("frontLon").toDouble()),
            )
        }
    }

    /**
     * 보행자 경로. 응답 JSON 원문을 돌려준다. [TmapRoute.parse]나 [Destination]에 그대로 넣으면 된다.
     * @param searchOption 0 추천, 4 추천+대로우선, 10 최단, 30 최단거리+계단제외
     */
    fun pedestrianRouteJson(
        start: GeoPoint,
        end: GeoPoint,
        startName: String = "출발",
        endName: String = "도착",
        searchOption: Int = 0,
    ): String {
        val payload = buildJsonObject {
            put("startX", start.lon)
            put("startY", start.lat)
            put("endX", end.lon)
            put("endY", end.lat)
            put("startName", URLEncoder.encode(startName, "UTF-8"))
            put("endName", URLEncoder.encode(endName, "UTF-8"))
            put("searchOption", searchOption.toString())
        }.toString()
        return request("POST", "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", payload)
            ?: throw IOException("TMAP 경로 응답이 비어 있다")
    }

    /** 응답 본문. 204(결과 없음)면 null. 실패하면 상태 코드와 함께 IOException. */
    private fun request(method: String, url: String, jsonBody: String?): String? {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("appKey", appKey)
            conn.setRequestProperty("Accept", "application/json")
            if (jsonBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code == 204) return null
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("TMAP HTTP $code ${err.take(200)}")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

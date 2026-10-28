package kr.ac.cnu.balmatchum.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 안내할 목적지 하나. 경로는 TMAP 보행자 경로 응답을 그대로 들고 다닌다.
 * 워치에 저장된 시연 경로([Source.DEMO])와 휴대폰이 보내 준 목적지([Source.PHONE])가 같은 모양이다.
 */
data class Destination(
    /** 목록에 보일 이름. 예: "공대 5호관 → 경영경제연구소" */
    val name: String,
    /** TMAP `POST /tmap/routes/pedestrian` 응답 JSON 원문. */
    val tmapJson: String,
    val source: Source = Source.DEMO,
) {
    enum class Source { DEMO, PHONE }

    /** 경로·총거리·총시간. 처음 쓸 때 한 번만 읽는다. */
    val tmap: TmapRoute by lazy { TmapRoute.parse(tmapJson) }

    /** 휴대폰 → 워치로 보낼 때 쓰는 JSON. */
    fun toJson(): String = buildJsonObject {
        put("name", name)
        put("tmap", tmapJson)
    }.toString()

    companion object {
        fun fromJson(json: String, source: Source = Source.PHONE): Destination {
            val o = Json.parseToJsonElement(json).jsonObject
            return Destination(
                name = o.getValue("name").jsonPrimitive.content,
                tmapJson = o.getValue("tmap").jsonPrimitive.content,
                source = source,
            )
        }
    }
}

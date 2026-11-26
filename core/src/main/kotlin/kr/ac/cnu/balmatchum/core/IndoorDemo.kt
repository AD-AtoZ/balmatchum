package kr.ac.cnu.balmatchum.core

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** 한 가지 걸음걸이의 실측값. 시연 복도에서 정해진 거리를 걸어 걸음 수와 시간으로 구한다. */
data class Gait(
    /** 분당 걸음 수. */
    val cadenceSpm: Double,
    /** 걸음 길이(m). */
    val stepLengthM: Double,
) {
    init {
        require(cadenceSpm > 0 && stepLengthM > 0) { "걸음 수와 걸음 길이는 0보다 커야 한다" }
    }

    /** 이 걸음걸이로 걸을 때의 속도(m/s). */
    val speedMps: Double get() = cadenceSpm / 60.0 * stepLengthM
}

/**
 * 실내 시연 설정. 실내에서는 GPS가 잡히지 않으므로 걸음 수만으로 경로를 따라 진행시킨다.
 *
 * 걸음 길이는 걸음 빠르기에 따라 달라지므로, 보통 걸음과 빠른 걸음 두 실측값을 잇는 직선에서 분당 걸음 수로 고른다.
 * 걸음 수만으로는 돌아간 걸음과 앞으로 간 걸음을 구분하지 못한다. 그래서 장애물 구간 끝에 체크포인트를 두고,
 * 사용자가 그 지점을 지나며 화면을 누를 때까지 진행 거리가 체크포인트를 넘지 않게 막는다.
 */
data class IndoorSettings(
    val walk: Gait,
    val brisk: Gait,
    /** 경로 끝까지 남은 거리가 이 값(m) 이하이면 도착으로 본다. 실외 기준(20m)은 짧은 복도에 너무 크다. */
    val arrivalRadiusM: Double = 2.0,
    /** 체크포인트의 경로상 거리(m). 출발점에서 가까운 순서. */
    val checkpointsM: List<Double> = emptyList(),
    /** 체크포인트까지 이 거리(m) 안으로 들어와야 통과 확인을 받는다. 멀리서 잘못 누르는 것을 막는다. */
    val checkpointConfirmRangeM: Double = 10.0,
) {
    init {
        require(brisk.cadenceSpm > walk.cadenceSpm) { "빠른 걸음의 분당 걸음 수가 보통 걸음보다 커야 한다" }
        require(checkpointsM == checkpointsM.sorted()) { "체크포인트는 출발점에서 가까운 순서여야 한다" }
    }

    /**
     * [cadenceSpm]으로 걸을 때의 걸음 길이(m). 아직 걸음 빠르기를 모르면(null) 보통 걸음 길이.
     * 두 실측값 바깥으로는 간격의 절반까지만 늘이거나 줄인다. 1초 단위로 센 걸음 수는 한두 걸음씩 흔들리는데,
     * 실측값에서 딱 자르면 그 흔들림이 한쪽으로만 반영되어 걸음 길이가 치우친다.
     */
    fun stepLength(cadenceSpm: Double?): Double {
        if (cadenceSpm == null) return walk.stepLengthM
        val t = ((cadenceSpm - walk.cadenceSpm) / (brisk.cadenceSpm - walk.cadenceSpm)).coerceIn(-0.5, 1.5)
        return walk.stepLengthM + (brisk.stepLengthM - walk.stepLengthM) * t
    }

    /** 상태 판단에 쓸 속도. 보통·빠른 걸음은 실내 실측값으로 바꾸고, 뛰기는 [base]를 그대로 둔다. */
    fun speeds(base: PersonalSpeeds = PersonalSpeeds.DEFAULT): PersonalSpeeds = PersonalSpeeds(
        walkMps = walk.speedMps,
        briskMps = brisk.speedMps,
        runMps = maxOf(base.runMps, brisk.speedMps * 1.2),
    )
}

/** 최근 [windowMillis] 동안의 걸음 수로 분당 걸음 수를 잰다. */
class CadenceMeter(private val windowMillis: Long = 5_000) {

    private class Mark(val timeMillis: Long, val steps: Int)

    private val marks = ArrayDeque<Mark>()

    /**
     * 지금까지의 총 걸음 수를 넣는다. 분당 걸음 수를 돌려주며, 아직 [MIN_SPAN_MILLIS]만큼 지켜보지 못했으면 null.
     */
    fun add(timeMillis: Long, totalSteps: Int): Double? {
        marks.addLast(Mark(timeMillis, totalSteps))
        while (marks.size > 2 && timeMillis - marks[1].timeMillis >= windowMillis) marks.removeFirst()
        val first = marks.first()
        val span = timeMillis - first.timeMillis
        if (span < MIN_SPAN_MILLIS) return null
        return (totalSteps - first.steps) * 60_000.0 / span
    }

    private companion object {
        const val MIN_SPAN_MILLIS = 2_000L
    }
}

/**
 * 실내 시연 값 모음. **현장에서 잰 값으로 바꿔 쓴다.** 지금 값은 시작값(제안, 미검증)이다.
 *
 * 재는 방법: 시연 복도에서 20m를 표시하고 보통 걸음과 빠른 걸음으로 각각 걸어 걸음 수와 걸린 시간을 적는다.
 * 걸음 길이 = 20 ÷ 걸음 수, 분당 걸음 수 = 걸음 수 ÷ 걸린 시간(초) × 60.
 */
object IndoorDemo {
    /** 목록에 보일 이름. */
    const val NAME = "실내 시연 (누르면 바로 출발)"

    /** 출발선에서 도착선까지 거리(m). */
    const val ROUTE_LENGTH_M = 55.0

    /** 장애물 구간이 끝나는 지점의 경로상 거리(m). */
    const val CHECKPOINT_M = 21.0

    /** 목표 시각을 정할 때 "여유 있음" 기준에 더 얹는 시간(초). 작을수록 장애물 구간에서 진동이 빨리 온다. */
    const val TARGET_CUSHION_SEC = 3.0

    val WALK = Gait(cadenceSpm = 110.0, stepLengthM = 0.70)
    val BRISK = Gait(cadenceSpm = 130.0, stepLengthM = 0.80)

    val settings: IndoorSettings
        get() = IndoorSettings(walk = WALK, brisk = BRISK, checkpointsM = listOf(CHECKPOINT_M))

    /**
     * 출발을 누른 뒤 목표 시각까지 시간(초). 출발할 때 겨우 "여유 있음"이 되도록 잡는다.
     * 이렇게 해야 장애물 구간에서 조금만 늦어져도 "서두르면 맞춤"으로 바뀐다.
     */
    fun targetSec(lengthM: Double = ROUTE_LENGTH_M, config: CoachConfig = CoachConfig()): Int {
        val atWalk = lengthM / WALK.speedMps
        val margin = minOf(config.relaxedMarginSec, atWalk * config.relaxedMarginRatio)
        return kotlin.math.ceil(atWalk + margin + TARGET_CUSHION_SEC).toInt()
    }

    /** 실내 시연용 목적지. 위치는 쓰지 않으므로 좌표는 경로 길이만 맞춘 가짜 직선이다. */
    fun destination(lengthM: Double = ROUTE_LENGTH_M): Destination =
        Destination(name = NAME, tmapJson = straightRouteJson(lengthM), source = Destination.Source.DEMO)

    /** 북쪽으로 [lengthM]만큼 곧게 뻗은 경로를 TMAP 보행자 경로 응답 모양으로 만든다. */
    fun straightRouteJson(lengthM: Double): String {
        require(lengthM > 0) { "경로 길이는 0보다 커야 한다" }
        val start = GeoPoint(lat = 36.3660, lon = 127.3450)
        val end = fromLocal(start, Local(x = 0.0, y = lengthM))
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "Point")
                        putJsonArray("coordinates") { add(start.lon); add(start.lat) }
                    }
                    putJsonObject("properties") {
                        put("pointType", "SP")
                        put("totalDistance", lengthM)
                        put("totalTime", lengthM / WALK.speedMps)
                    }
                }
                addJsonObject {
                    put("type", "Feature")
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        putJsonArray("coordinates") {
                            addJsonArray { add(start.lon); add(start.lat) }
                            addJsonArray { add(end.lon); add(end.lat) }
                        }
                    }
                    putJsonObject("properties") {}
                }
            }
        }.toString()
    }
}

package kr.ac.cnu.balmatchum.core

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

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
 * 휴대폰 카메라 추적이 워치로 보내는 위치 한 개. 출발선과 출발 방향이 기준이다.
 * 실내에서는 GPS 대신 휴대폰 카메라가 "눈" 역할을 해서 얼마나, 어느 쪽으로 움직였는지 잰다.
 */
data class IndoorPose(
    /** 출발선에서 복도 방향으로 나아간 거리(m). 뒤로 가면 음수. */
    val forwardM: Double,
    /** 복도 중심선에서 오른쪽으로 벗어난 거리(m). 왼쪽이면 음수. */
    val lateralM: Double,
    /** 복도 방향 기준으로 지금 보고 있는 방향(도). 오른쪽으로 돌면 양수. */
    val headingDeg: Double,
    /** 휴대폰이 잰 시각(epoch ms). */
    val timeMillis: Long,
) {
    /** 워치로 보낼 글자. 쉼표로 구분한 네 값. */
    fun encode(): String = String.format(Locale.US, "%.2f,%.2f,%.1f,%d", forwardM, lateralM, headingDeg, timeMillis)

    companion object {
        /** 휴대폰 → 워치 메시지 경로. */
        const val PATH = "/balmatchum/indoor-pose"

        /** [encode]의 반대. 모양이 맞지 않으면 null. */
        fun decode(text: String): IndoorPose? {
            val parts = text.trim().split(",")
            if (parts.size != 4) return null
            val forward = parts[0].toDoubleOrNull() ?: return null
            val lateral = parts[1].toDoubleOrNull() ?: return null
            val heading = parts[2].toDoubleOrNull() ?: return null
            val time = parts[3].toLongOrNull() ?: return null
            if (!forward.isFinite() || !lateral.isFinite() || !heading.isFinite()) return null
            return IndoorPose(forward, lateral, heading, time)
        }
    }
}

/**
 * 출발선에서 잡은 기준. 카메라 추적 좌표(수평면의 x, z)를 "출발선에서 앞으로·오른쪽으로 몇 m"로 바꾼다.
 *
 * 카메라 추적 좌표는 위쪽이 y이고 x·z가 수평면이다. 출발선에서 복도를 바라본 방향을 앞으로 삼는다.
 *
 * @param forwardX 출발선에서 카메라가 바라본 방향의 수평 성분 x
 * @param forwardZ 같은 방향의 수평 성분 z
 */
class IndoorFrame(private val originX: Double, private val originZ: Double, forwardX: Double, forwardZ: Double) {

    private val fx: Double
    private val fz: Double

    init {
        val length = hypot(forwardX, forwardZ)
        require(length >= MIN_HORIZONTAL) { "카메라가 거의 위나 아래를 보고 있어 방향을 정할 수 없다" }
        fx = forwardX / length
        fz = forwardZ / length
    }

    /** 지금 위치([x], [z])와 카메라가 바라보는 방향의 수평 성분([viewX], [viewZ])을 출발선 기준으로 바꾼다. */
    fun toPose(x: Double, z: Double, viewX: Double, viewZ: Double, timeMillis: Long): IndoorPose {
        val dx = x - originX
        val dz = z - originZ
        // 위쪽이 y일 때 오른쪽 = 앞 × 위 = (-fz, fx)
        val rx = -fz
        val rz = fx
        return IndoorPose(
            forwardM = dx * fx + dz * fz,
            lateralM = dx * rx + dz * rz,
            headingDeg = Math.toDegrees(atan2(viewX * rx + viewZ * rz, viewX * fx + viewZ * fz)),
            timeMillis = timeMillis,
        )
    }

    companion object {
        /** 바라보는 방향의 수평 성분이 이보다 작으면 방향을 믿지 않는다. */
        const val MIN_HORIZONTAL = 0.3

        /** 카메라가 충분히 앞을 보고 있어 기준을 잡을 수 있는지. */
        fun canAlign(viewX: Double, viewZ: Double): Boolean = hypot(viewX, viewZ) >= MIN_HORIZONTAL
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
    const val NAME = "실내 시연 · 걸음 (누르면 바로 출발)"

    /** 휴대폰 카메라로 위치를 받는 실내 시연의 이름. */
    const val CAMERA_NAME = "실내 시연 · 카메라 (누르면 바로 출발)"

    /** 휴대폰이 보내는 위치의 정확도로 넣을 값(m). 카메라 추적은 GPS보다 훨씬 정확하다. */
    const val CAMERA_ACCURACY_M = 1.0

    /** 가짜 직선 경로의 출발점. 실내 시연은 실제 위치를 쓰지 않으므로 좌표 자체에는 뜻이 없다. */
    private val START = GeoPoint(lat = 36.3660, lon = 127.3450)

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
    fun destination(lengthM: Double = ROUTE_LENGTH_M, name: String = NAME): Destination =
        Destination(name = name, tmapJson = straightRouteJson(lengthM), source = Destination.Source.DEMO)

    /** 출발선에서 앞으로 [forwardM], 오른쪽으로 [lateralM] 간 곳을 가짜 직선 경로와 같은 좌표계의 위치로 바꾼다. */
    fun pointAt(forwardM: Double, lateralM: Double): GeoPoint = fromLocal(START, Local(x = lateralM, y = forwardM))

    /** 북쪽으로 [lengthM]만큼 곧게 뻗은 경로를 TMAP 보행자 경로 응답 모양으로 만든다. */
    fun straightRouteJson(lengthM: Double): String {
        require(lengthM > 0) { "경로 길이는 0보다 커야 한다" }
        val start = START
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

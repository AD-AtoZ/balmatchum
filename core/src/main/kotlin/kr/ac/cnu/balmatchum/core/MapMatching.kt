package kr.ac.cnu.balmatchum.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 스캔으로 만든 벽 지도. 칸마다 "가장 가까운 벽까지 거리(칸 수)"와 "걸을 수 있는 곳인지"가 들어 있다.
 * 만드는 쪽은 `tools/build_w5_wallmap.py`이고, 좌표는 실내 시연 경로 기준이다:
 * forward = 출발선에서 복도 방향으로 간 거리(m), lateral = 중심선에서 오른쪽으로 벗어난 거리(m).
 *
 * 걸을 수 있는 곳은 스캔된 건물 안쪽(바닥이 찍힌 곳 + 메운 구멍 + 경로 복도)에서 벽 근처를 뺀, 경로와 이어진 영역이다.
 * [walkable]이 없으면(옛 BWM1 파일) 지도 안의 벽이 아닌 칸을 모두 걸을 수 있다고 본다.
 */
class WallMap(
    val forwardCells: Int,
    val lateralCells: Int,
    val cellM: Double,
    val forwardMin: Double,
    val lateralMin: Double,
    private val distance: ByteArray,
    private val walkable: ByteArray? = null,
) {
    init {
        require(distance.size == forwardCells * lateralCells) { "벽 지도 크기가 맞지 않는다" }
        require(walkable == null || walkable.size == distance.size) { "걸을 수 있는 곳 지도 크기가 맞지 않는다" }
    }

    val hasWalkable: Boolean get() = walkable != null

    /** 그 자리에서 가장 가까운 벽까지 거리(m). 지도 밖이면 [OUTSIDE_M]. */
    fun distanceToWallM(forward: Double, lateral: Double): Double {
        val fi = floor((forward - forwardMin) / cellM).toInt()
        val li = floor((lateral - lateralMin) / cellM).toInt()
        if (fi < 0 || fi >= forwardCells || li < 0 || li >= lateralCells) return OUTSIDE_M
        return (distance[li * forwardCells + fi].toInt() and 0xFF) * cellM
    }

    /** 칸([fi], [li])에서 가장 가까운 벽까지 거리(칸 수, 0~255). 미니맵을 그릴 때 쓴다. */
    fun cellDistance(fi: Int, li: Int): Int = distance[li * forwardCells + fi].toInt() and 0xFF

    /** 칸([fi], [li])이 걸을 수 있는 곳인지. 지도 밖이면 false. */
    fun cellWalkable(fi: Int, li: Int): Boolean {
        if (fi < 0 || fi >= forwardCells || li < 0 || li >= lateralCells) return false
        val k = li * forwardCells + fi
        return if (walkable != null) walkable[k].toInt() != 0 else distance[k].toInt() != 0
    }

    /** 그 자리가 걸을 수 있는 곳인지. */
    fun isWalkable(forward: Double, lateral: Double): Boolean =
        cellWalkable(floor((forward - forwardMin) / cellM).toInt(), floor((lateral - lateralMin) / cellM).toInt())

    /**
     * 그 자리에서 [maxM] 안의 가장 가까운 걸을 수 있는 칸의 가운데 (forward, lateral). 이미 걸을 수 있으면 그 자리 그대로.
     * 찾지 못하면 null.
     */
    fun nearestWalkable(forward: Double, lateral: Double, maxM: Double): Pair<Double, Double>? {
        if (isWalkable(forward, lateral)) return forward to lateral
        val fi0 = floor((forward - forwardMin) / cellM).toInt()
        val li0 = floor((lateral - lateralMin) / cellM).toInt()
        val reach = (maxM / cellM).toInt()
        var best: Pair<Double, Double>? = null
        var bestD = Double.MAX_VALUE
        for (r in 1..reach) {
            // r칸 고리만 본다. 고리 안에서 가장 가까운 칸을 찾으면, 다음 고리는 그보다 가까울 수 없을 때까지만 더 본다.
            if (best != null && (r - 1) * cellM > bestD) break
            for (dl in -r..r) for (df in -r..r) {
                if (maxOf(abs(dl), abs(df)) != r) continue
                if (!cellWalkable(fi0 + df, li0 + dl)) continue
                val cf = forwardMin + (fi0 + df + 0.5) * cellM
                val cl = lateralMin + (li0 + dl + 0.5) * cellM
                val d = hypot(cf - forward, cl - lateral)
                if (d <= maxM && d < bestD) { bestD = d; best = cf to cl }
            }
        }
        return best
    }

    val forwardMax: Double get() = forwardMin + forwardCells * cellM
    val lateralMax: Double get() = lateralMin + lateralCells * cellM

    companion object {
        const val OUTSIDE_M = 1e9

        /** `tools/build_w5_wallmap.py`가 만든 파일(BWM1: 벽 거리만, BWM2: 벽 거리 + 걸을 수 있는 곳)을 읽는다. */
        fun parse(bytes: ByteArray): WallMap {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = String(ByteArray(4).also { buffer.get(it) }, Charsets.US_ASCII)
            require(magic == "BWM1" || magic == "BWM2") { "벽 지도 파일이 아니다" }
            val forwardCells = buffer.int
            val lateralCells = buffer.int
            val cell = buffer.float.toDouble()
            val forwardMin = buffer.float.toDouble()
            val lateralMin = buffer.float.toDouble()
            val distance = ByteArray(forwardCells * lateralCells).also { buffer.get(it) }
            val walkable = if (magic == "BWM2") ByteArray(forwardCells * lateralCells).also { buffer.get(it) } else null
            return WallMap(forwardCells, lateralCells, cell, forwardMin, lateralMin, distance, walkable)
        }
    }
}

/**
 * 카메라 추적 좌표(위쪽이 y인 ARCore 좌표의 수평면 x, z)를 지도 좌표(forward, lateral)로 옮기는 변환.
 * 지도 = 회전([thetaRad]) × 추적 좌표 + ([tf], [tl]). 위에서 내려다본 두 좌표계는 손잡이 방향이 같아서 회전만으로 맞는다.
 */
data class MapTransform(val thetaRad: Double, val tf: Double, val tl: Double) {
    private val c = cos(thetaRad)
    private val s = sin(thetaRad)

    fun forward(x: Double, z: Double): Double = tf + c * x - s * z
    fun lateral(x: Double, z: Double): Double = tl + s * x + c * z

    /** 카메라 위치와 바라보는 방향(수평 성분)을 지도 기준 [IndoorPose]로 바꾼다. */
    fun toPose(camX: Double, camZ: Double, viewX: Double, viewZ: Double, timeMillis: Long): IndoorPose {
        val vf = c * viewX - s * viewZ
        val vl = s * viewX + c * viewZ
        return IndoorPose(
            forwardM = forward(camX, camZ),
            lateralM = lateral(camX, camZ),
            headingDeg = Math.toDegrees(atan2(vl, vf)),
            timeMillis = timeMillis,
        )
    }

    /** [other] 쪽으로 [weight]만큼 옮긴 변환. 한 번에 크게 튀지 않게 조금씩 바로잡을 때 쓴다. */
    fun blend(other: MapTransform, weight: Double): MapTransform {
        var d = other.thetaRad - thetaRad
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return MapTransform(thetaRad + d * weight, tf + (other.tf - tf) * weight, tl + (other.tl - tl) * weight)
    }

    companion object {
        /** 추적 좌표의 카메라 위치([camX], [camZ])가 지도의 ([camF], [camL])에 오도록 하는 변환. */
        fun placing(camX: Double, camZ: Double, camF: Double, camL: Double, thetaRad: Double): MapTransform {
            val c = cos(thetaRad)
            val s = sin(thetaRad)
            return MapTransform(thetaRad, camF - (c * camX - s * camZ), camL - (s * camX + c * camZ))
        }
    }
}

/** 지도 맞추기 기준값. **모두 시작값(제안, 미검증)이다.** 실제 카메라 점으로 현장에서 고친다. */
data class MatchConfig(
    /** 점이 벽에서 이 거리(m) 안이면 일치로 친다. 가까울수록 점수가 높다. */
    val inlierM: Double = 0.25,
    /** 이보다 점이 적으면 맞추지 않는다. */
    val minPoints: Int = 60,
    /** 한 번 계산에 쓰는 점의 최대 개수. 많으면 고르게 골라 줄인다. */
    val maxPoints: Int = 250,
    /** 일치 점수(0~1)가 이 값보다 낮으면 결과를 받아들이지 않는다. */
    val acceptScore: Double = 0.45,
    /** 처음 위치 찾기: 방향을 이 간격(도)으로 한 바퀴 돌려 본다. */
    val globalAngleStepDeg: Double = 3.0,
    /** 처음 위치 찾기: 위치를 이 간격(m)으로 훑는다. */
    val globalStepM: Double = 0.3,
    /** 처음 위치 찾기: 벽에서 이만큼(m) 떨어진 곳만 서 있을 수 있는 자리로 본다. */
    val minClearanceM: Double = 0.3,
    /** 처음 위치 찾기: 벽에서 이보다 먼 곳(m)은 스캔 밖으로 보고 빼고 훑는다. */
    val maxClearanceM: Double = 3.0,
    /** 두 후보가 이만큼(m 또는 도) 떨어져 있는데 점수가 [ambiguityMargin] 안으로 비슷하면 "확실하지 않음"으로 본다. */
    val distinctM: Double = 2.0,
    val distinctDeg: Double = 20.0,
    val ambiguityMargin: Double = 0.05,
    /** 걷는 중 바로잡기: 찾아보는 범위. */
    val trackLateralM: Double = 0.6,
    val trackForwardM: Double = 1.0,
    val trackAngleDeg: Double = 4.0,
    /** 걷는 중 바로잡기: 앞뒤로 옮겼을 때 점수가 이만큼 좋아져야 앞뒤를 고친다. 비슷하게 생긴 복도에서 미끄러지지 않게. */
    val forwardGain: Double = 0.05,
    /** 위치가 걸을 수 있는 곳 밖으로 나가면 이 거리(m) 안의 가장 가까운 칸으로 당겨 온다. */
    val snapM: Double = 3.0,
)

/** 지도 맞추기 결과. */
data class MatchResult(
    val transform: MapTransform,
    /** 일치 점수 0~1. 카메라 점이 벽에 얼마나 가깝게 놓이는지의 평균. */
    val score: Double,
    /** 계산에 쓴 점 개수. */
    val points: Int,
    /** 멀리 떨어진 다른 자리도 거의 같은 점수라 위치를 확신할 수 없으면 true. */
    val ambiguous: Boolean = false,
    /** 두 번째로 좋은 (멀리 떨어진) 자리의 점수. */
    val runnerUpScore: Double = 0.0,
    /** 두 번째로 좋은 자리. 위치가 확실하지 않을 때 어디와 헷갈리는지 보여 줄 때 쓴다. */
    val runnerUp: MapTransform? = null,
)

/**
 * 카메라가 찾아낸 점들(추적 좌표의 수평 위치 x, z)을 [WallMap]에 대 보고, 지도에서 어디에 있는지 찾는다.
 *
 * 점수는 "점이 벽 위에 놓이면 1, [MatchConfig.inlierM]보다 멀면 0"의 평균이다. 위치·방향을 바꿔 가며 점수가 가장 높은
 * 자리를 고른다. 처음 위치 찾기에서는 "카메라에서 그 점까지 벽을 뚫고 보지 않았는가"도 함께 본다. 책상이 많은 교실처럼
 * 어디에 점을 놓아도 무언가에 닿는 곳에서, 점수만 보면 엉뚱한 자리가 높게 나오기 때문이다. [localize]는 지도 전체를 한 바퀴 돌려 보며 찾고, [refine]은 지금 자리 근처만 찾아 조금씩 바로잡는다.
 *
 * @param xz 점 좌표를 x0, z0, x1, z1 … 순서로 담은 배열
 */
class MapMatcher(private val map: WallMap, private val config: MatchConfig = MatchConfig()) {

    /** [transform]으로 옮긴 점들의 일치 점수. [limit]개까지만 본다. */
    fun score(xz: DoubleArray, transform: MapTransform, limit: Int = xz.size / 2): Double {
        val n = min(limit, xz.size / 2)
        if (n == 0) return 0.0
        var sum = 0.0
        for (i in 0 until n) {
            val x = xz[2 * i]
            val z = xz[2 * i + 1]
            val d = map.distanceToWallM(transform.forward(x, z), transform.lateral(x, z))
            if (d < config.inlierM) sum += 1 - d / config.inlierM
        }
        return sum / n
    }

    /**
     * 처음 위치 찾기. 제자리에서 한 바퀴 돌며 모은 점으로 지도 전체를 훑는다.
     * [camX], [camZ]는 그때 카메라의 추적 좌표. 점이 모자라면 null.
     */
    fun localize(points: DoubleArray, camX: Double, camZ: Double): MatchResult? {
        val xz = sample(points)
        val n = xz.size / 2
        if (n < config.minPoints) return null

        val positions = standingSpots()
        val quick = min(n, QUICK_POINTS)
        val candidates = ArrayList<Candidate>()
        var steps = 0
        val angleStep = Math.toRadians(config.globalAngleStepDeg)
        val angles = (0 until (360.0 / config.globalAngleStepDeg).toInt()).map { it * angleStep }
        for (theta in angles) {
            for (p in positions.indices step 2) {
                val f = positions[p]
                val l = positions[p + 1]
                val t = MapTransform.placing(camX, camZ, f, l, theta)
                steps++
                // 앞쪽 일부 점으로 먼저 거르고, 가망 있는 자리만 전부 본다.
                val q = score(xz, t, quick)
                if (q < QUICK_CUTOFF) continue
                candidates += Candidate(f, l, theta, score(xz, t))
            }
        }
        if (candidates.isEmpty()) return MatchResult(MapTransform(0.0, 0.0, 0.0), 0.0, n)

        // 서로 떨어진 상위 후보 몇 개를 촘촘하게 다시 본다.
        candidates.sortByDescending { it.score }
        val peaks = ArrayList<Candidate>()
        for (c in candidates) {
            if (peaks.none { it.near(c) }) peaks += c
            if (peaks.size >= PEAKS) break
        }
        val refined = peaks.map { fineSearch(xz, camX, camZ, it) }
            .map { it.copyWithRank(it.score * clearSight(xz, camX, camZ, it)) }
            .sortedByDescending { it.rank }
        val best = refined.first()
        val second = refined.drop(1).firstOrNull { !it.near(best) }
        val runnerUp = second?.score ?: 0.0
        return MatchResult(
            transform = MapTransform.placing(camX, camZ, best.f, best.l, best.theta),
            score = best.score,
            points = n,
            ambiguous = second != null && second.rank >= best.rank - config.ambiguityMargin,
            runnerUpScore = runnerUp,
            runnerUp = second?.let { MapTransform.placing(camX, camZ, it.f, it.l, it.theta) },
        )
    }

    /**
     * 걷는 중 바로잡기. 지금 변환([current]) 근처에서 점수가 가장 좋은 변환을 찾는다.
     * 점이 모자라거나 점수가 기준보다 낮으면 null. 앞뒤 위치는 점수가 뚜렷이 좋아질 때만 고친다.
     * 카메라가 걸을 수 없는 곳에 놓이는 후보는 보지 않는다.
     */
    fun refine(points: DoubleArray, current: MapTransform, camX: Double, camZ: Double): MatchResult? {
        val xz = sample(points)
        val n = xz.size / 2
        if (n < config.minPoints) return null
        val f0 = current.forward(camX, camZ)
        val l0 = current.lateral(camX, camZ)

        var best: Candidate? = null
        var bestSameForward: Candidate? = null
        val angleSteps = config.trackAngleDeg.toInt()
        for (a in -angleSteps..angleSteps) {
            val theta = current.thetaRad + Math.toRadians(a.toDouble())
            var df = -config.trackForwardM
            while (df <= config.trackForwardM + 1e-9) {
                var dl = -config.trackLateralM
                while (dl <= config.trackLateralM + 1e-9) {
                    if (!map.isWalkable(f0 + df, l0 + dl)) { dl += TRACK_LATERAL_STEP_M; continue }
                    val s = score(xz, MapTransform.placing(camX, camZ, f0 + df, l0 + dl, theta))
                    val c = Candidate(f0 + df, l0 + dl, theta, s)
                    if (best == null || s > best.score) best = c
                    if (abs(df) < 1e-9 && (bestSameForward == null || s > bestSameForward.score)) bestSameForward = c
                    dl += TRACK_LATERAL_STEP_M
                }
                df += TRACK_FORWARD_STEP_M
            }
        }
        if (best == null || bestSameForward == null) return null
        val chosen = if (best.score - bestSameForward.score >= config.forwardGain) best else bestSameForward
        if (chosen.score < config.acceptScore) return null
        return MatchResult(MapTransform.placing(camX, camZ, chosen.f, chosen.l, chosen.theta), chosen.score, n)
    }

    /**
     * 카메라 위치가 걸을 수 있는 곳 밖으로 나가면, 가장 가까운 걸을 수 있는 칸으로 옮긴 변환을 낸다(방향은 그대로).
     * 이미 안이면 [current] 그대로. [MatchConfig.snapM] 안에 걸을 수 있는 칸이 없으면(지도 밖 멀리) 그대로 둔다.
     */
    fun constrain(current: MapTransform, camX: Double, camZ: Double): MapTransform {
        val f = current.forward(camX, camZ)
        val l = current.lateral(camX, camZ)
        if (map.isWalkable(f, l)) return current
        val (nf, nl) = map.nearestWalkable(f, l, config.snapM) ?: return current
        return MapTransform(current.thetaRad, current.tf + (nf - f), current.tl + (nl - l))
    }

    /**
     * 처음 위치 찾기에서 서 있을 수 있는 자리들(forward, lateral 순서로 이어 붙임).
     * 걸을 수 있는 곳 지도가 있으면 그 안만, 없으면 벽에서 너무 가깝거나 먼 곳을 뺀 곳.
     */
    private val standingSpotsCache: DoubleArray by lazy {
        val out = ArrayList<Double>()
        var f = map.forwardMin
        while (f < map.forwardMax) {
            var l = map.lateralMin
            while (l < map.lateralMax) {
                val d = map.distanceToWallM(f, l)
                val ok = if (map.hasWalkable) map.isWalkable(f, l) else d >= config.minClearanceM && d <= config.maxClearanceM
                if (ok) { out += f; out += l }
                l += config.globalStepM
            }
            f += config.globalStepM
        }
        out.toDoubleArray()
    }

    private fun standingSpots(): DoubleArray = standingSpotsCache

    private fun fineSearch(xz: DoubleArray, camX: Double, camZ: Double, start: Candidate): Candidate {
        var best = start
        val half = config.globalStepM
        val angleHalf = Math.toRadians(config.globalAngleStepDeg)
        var a = -angleHalf
        while (a <= angleHalf + 1e-9) {
            var df = -half
            while (df <= half + 1e-9) {
                var dl = -half
                while (dl <= half + 1e-9) {
                    val theta = start.theta + a
                    val s = score(xz, MapTransform.placing(camX, camZ, start.f + df, start.l + dl, theta))
                    if (s > best.score) best = Candidate(start.f + df, start.l + dl, theta, s)
                    dl += FINE_STEP_M
                }
                df += FINE_STEP_M
            }
            a += Math.toRadians(FINE_ANGLE_STEP_DEG)
        }
        return best
    }

    /** 점이 [MatchConfig.maxPoints]보다 많으면 고르게 골라 줄인다. */
    private fun sample(points: DoubleArray): DoubleArray {
        val n = points.size / 2
        if (n <= config.maxPoints) return points
        val out = DoubleArray(config.maxPoints * 2)
        for (i in 0 until config.maxPoints) {
            val j = (i.toLong() * n / config.maxPoints).toInt()
            out[2 * i] = points[2 * j]
            out[2 * i + 1] = points[2 * j + 1]
        }
        return out
    }

    /**
     * 카메라에서 각 점까지 벽을 뚫지 않고 보이는 비율(0~1). 점 바로 앞 [SIGHT_END_MARGIN_M]은 점이 놓인 벽이라 빼고 본다.
     * 계산이 무거워 앞쪽 [SIGHT_POINTS]개 점만 쓴다.
     */
    private fun clearSight(xz: DoubleArray, camX: Double, camZ: Double, c: Candidate): Double {
        val t = MapTransform.placing(camX, camZ, c.f, c.l, c.theta)
        val n = min(xz.size / 2, SIGHT_POINTS)
        var clear = 0
        for (i in 0 until n) {
            val pf = t.forward(xz[2 * i], xz[2 * i + 1])
            val pl = t.lateral(xz[2 * i], xz[2 * i + 1])
            val length = hypot(pf - c.f, pl - c.l)
            var blocked = false
            var d = SIGHT_STEP_M
            while (d < length - SIGHT_END_MARGIN_M) {
                val r = d / length
                if (map.distanceToWallM(c.f + (pf - c.f) * r, c.l + (pl - c.l) * r) == 0.0) {
                    blocked = true
                    break
                }
                d += SIGHT_STEP_M
            }
            if (!blocked) clear++
        }
        return if (n == 0) 0.0 else clear.toDouble() / n
    }

    private inner class Candidate(
        val f: Double,
        val l: Double,
        val theta: Double,
        val score: Double,
        /** 후보끼리 순위를 매길 때 쓰는 값. 처음 위치 찾기에서는 점수 × 벽을 뚫지 않은 비율. */
        val rank: Double = score,
    ) {
        fun copyWithRank(rank: Double) = Candidate(f, l, theta, score, rank)

        fun near(other: Candidate): Boolean {
            var d = abs(theta - other.theta) % (2 * PI)
            d = min(d, 2 * PI - d)
            return hypot(f - other.f, l - other.l) < config.distinctM && Math.toDegrees(d) < config.distinctDeg
        }
    }

    private companion object {
        const val QUICK_POINTS = 40
        const val QUICK_CUTOFF = 0.2
        const val PEAKS = 6
        const val FINE_STEP_M = 0.05
        const val FINE_ANGLE_STEP_DEG = 0.5
        const val TRACK_LATERAL_STEP_M = 0.1
        const val TRACK_FORWARD_STEP_M = 0.2
        const val SIGHT_POINTS = 80
        const val SIGHT_STEP_M = 0.1
        const val SIGHT_END_MARGIN_M = 0.3
    }
}

/** 두 방향(도)의 차이. 0~180. */
internal fun headingDifferenceDeg(a: Double, b: Double): Double {
    var d = (a - b) % 360.0
    if (d > 180) d -= 360
    if (d < -180) d += 360
    return abs(d)
}

/** 처음 위치 찾기를 할 만큼 주위를 둘러봤는지 재는 도구. 방향을 30도씩 12칸으로 나눠 본 칸을 센다. */
class TurnCoverage {
    private val seen = BooleanArray(12)

    fun add(viewX: Double, viewZ: Double) {
        if (hypot(viewX, viewZ) < IndoorFrame.MIN_HORIZONTAL) return
        val deg = (Math.toDegrees(atan2(viewZ, viewX)) + 360.0) % 360.0
        seen[(deg / 30.0).toInt().coerceIn(0, 11)] = true
    }

    /** 둘러본 각도(도). 0~360. */
    val degrees: Int get() = seen.count { it } * 30

    fun clear() = seen.fill(false)

    /** 한 바퀴 거의 다 돌았는지. */
    val enough: Boolean get() = degrees >= 300
}

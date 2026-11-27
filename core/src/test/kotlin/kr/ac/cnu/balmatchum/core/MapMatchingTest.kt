package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * 실제 w5 벽 지도(휴대폰 앱에 넣은 파일)에 가짜 카메라 점을 뿌려 시험한다.
 * 가짜 점은 진짜 자리에서 보이는 벽 칸(가려지지 않은 것)에 흔들림과 잡음 점을 섞어 만든다.
 */
class MapMatchingTest {

    companion object {
        private lateinit var map: WallMap

        @BeforeClass
        @JvmStatic
        fun load() {
            map = WallMap.parse(File("../mobile/src/main/assets/w5_wallmap.bin").readBytes())
        }
    }

    /** 진짜 자리: 지도 좌표 ([f], [l]), 추적 좌표 원점에서 지도까지의 회전 [thetaDeg]. 카메라는 추적 좌표 (camX, camZ). */
    private class Truth(val f: Double, val l: Double, val thetaDeg: Double, val camX: Double = 1.3, val camZ: Double = -0.8) {
        val transform = MapTransform.placing(camX, camZ, f, l, Math.toRadians(thetaDeg))
    }

    /**
     * [truth] 자리에서 [rangeM] 안에 보이는 벽 칸을 점으로 만든다. [fromDeg]~[toDeg]는 지도 기준으로 바라본 방향 범위.
     * 지도 좌표의 점을 추적 좌표로 되돌려 x, z 배열로 준다.
     */
    private fun cameraPoints(
        truth: Truth,
        rangeM: Double = 8.0,
        fromDeg: Double = -180.0,
        toDeg: Double = 180.0,
        noiseM: Double = 0.05,
        outlierRatio: Double = 0.15,
        seed: Int = 1,
        keep: Double = 0.9,
    ): DoubleArray {
        val rnd = Random(seed)
        val pts = ArrayList<Double>()
        val step = map.cellM
        var f = truth.f - rangeM
        while (f <= truth.f + rangeM) {
            var l = truth.l - rangeM
            while (l <= truth.l + rangeM) {
                val d = hypot(f - truth.f, l - truth.l)
                val bearing = Math.toDegrees(kotlin.math.atan2(l - truth.l, f - truth.f))
                if (d <= rangeM && d > 0.5 && bearing in fromDeg..toDeg &&
                    map.distanceToWallM(f, l) == 0.0 && visible(truth.f, truth.l, f, l) && rnd.nextDouble() < keep
                ) {
                    pts += f + rnd.nextDouble(-noiseM, noiseM)
                    pts += l + rnd.nextDouble(-noiseM, noiseM)
                }
                l += step
            }
            f += step
        }
        val real = pts.size / 2
        repeat((real * outlierRatio).toInt()) {
            val a = rnd.nextDouble(0.0, 2 * Math.PI)
            val r = rnd.nextDouble(0.5, rangeM)
            pts += truth.f + r * cos(a)
            pts += truth.l + r * sin(a)
        }
        // 지도 좌표 → 추적 좌표 (변환의 역)
        val th = Math.toRadians(truth.thetaDeg)
        val c = cos(th)
        val s = sin(th)
        val out = DoubleArray(pts.size)
        for (i in 0 until pts.size / 2) {
            val df = pts[2 * i] - truth.transform.tf
            val dl = pts[2 * i + 1] - truth.transform.tl
            out[2 * i] = c * df + s * dl
            out[2 * i + 1] = -s * df + c * dl
        }
        // 점 순서를 섞는다(카메라 점에는 순서가 없다)
        val idx = (0 until out.size / 2).shuffled(rnd)
        return DoubleArray(out.size) { k -> out[2 * idx[k / 2] + k % 2] }
    }

    /** 두 점 사이에 벽 칸이 끼어 있지 않은지. 끝 칸은 빼고 본다. */
    private fun visible(f0: Double, l0: Double, f1: Double, l1: Double): Boolean {
        val d = hypot(f1 - f0, l1 - l0)
        val n = (d / (map.cellM * 0.5)).toInt()
        for (i in 1 until n - 2) {
            val t = i.toDouble() / n
            if (map.distanceToWallM(f0 + (f1 - f0) * t, l0 + (l1 - l0) * t) == 0.0) return false
        }
        return true
    }

    private fun assertFound(truth: Truth, result: MatchResult, posTol: Double = 0.3, angleTol: Double = 3.0) {
        val pose = result.transform.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
        val want = truth.transform.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
        val posErr = hypot(pose.forwardM - want.forwardM, pose.lateralM - want.lateralM)
        val angErr = headingDifferenceDeg(pose.headingDeg, want.headingDeg)
        assertTrue("위치 오차 ${"%.2f".format(posErr)}m, 방향 오차 ${"%.1f".format(angErr)}도, 점수 ${result.score}", posErr <= posTol && angErr <= angleTol)
    }

    @Test
    fun parsesTheBundledMap() {
        assertEquals(680, map.forwardCells)
        assertEquals(260, map.lateralCells)
        assertEquals(0.1, map.cellM, 1e-6)
        // 출발선 복도 한가운데는 비어 있고, 그 옆 1.5m 근처에는 벽이 있다.
        assertTrue(map.distanceToWallM(1.0, 0.0) >= 0.5)
        assertEquals(WallMap.OUTSIDE_M, map.distanceToWallM(-100.0, 0.0), 0.0)
    }

    @Test
    fun transformRoundTrip() {
        val t = MapTransform.placing(camX = 2.0, camZ = -3.0, camF = 10.0, camL = -1.0, thetaRad = Math.toRadians(70.0))
        assertEquals(10.0, t.forward(2.0, -3.0), 1e-9)
        assertEquals(-1.0, t.lateral(2.0, -3.0), 1e-9)
        val half = MapTransform(0.0, 0.0, 0.0).blend(MapTransform(Math.toRadians(20.0), 4.0, -2.0), 0.5)
        assertEquals(10.0, Math.toDegrees(half.thetaRad), 1e-9)
        assertEquals(2.0, half.tf, 1e-9)
        // 각도는 짧은 쪽으로 섞는다: 350도와 10도의 가운데는 0도
        val wrap = MapTransform(Math.toRadians(350.0), 0.0, 0.0).blend(MapTransform(Math.toRadians(10.0), 0.0, 0.0), 0.5)
        assertEquals(0.0, headingDifferenceDeg(Math.toDegrees(wrap.thetaRad), 0.0), 1e-6)
    }

    @Test
    fun findsTheStartAfterTurningAround() {
        // 출발선 근처에서 한 바퀴 돌며 본 점으로 지도 전체에서 자리를 찾는다.
        val truth = Truth(f = 0.6, l = 0.2, thetaDeg = 137.0)
        val pts = cameraPoints(truth)
        val started = System.nanoTime()
        val result = MapMatcher(map).localize(pts, truth.camX, truth.camZ)
        assertNotNull("점 ${pts.size / 2}개", result)
        result!!
        val ms = (System.nanoTime() - started) / 1e6
        val second = result.runnerUp?.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
        val first = result.transform.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
        println("점 ${pts.size / 2}개, 점수 ${"%.2f".format(result.score)}, 2등 ${"%.2f".format(result.runnerUpScore)}, ${"%.0f".format(ms)}ms")
        println("1등 ${"%.2f".format(first.forwardM)}, ${"%.2f".format(first.lateralM)}, ${"%.0f".format(first.headingDeg)}도 / 2등 ${second?.let { "%.2f, %.2f, %.0f도".format(it.forwardM, it.lateralM, it.headingDeg) }}")
        assertFound(truth, result)
        assertTrue(result.score > 0.6)
    }

    @Test
    fun findsAPlaceNearTheClassroom() {
        val truth = Truth(f = 37.0, l = -0.3, thetaDeg = -48.0, camX = -4.0, camZ = 2.5)
        val result = MapMatcher(map).localize(cameraPoints(truth, seed = 7), truth.camX, truth.camZ)!!
        assertFound(truth, result)
    }

    @Test
    fun plainCorridorIsEitherRightOrFlaggedAsUnsure() {
        // 문도 꺾임도 없는 복도 한가운데는 앞뒤로 비슷한 자리가 많다. 틀렸으면 반드시 "확실하지 않음"이어야 한다.
        val truth = Truth(f = 20.0, l = 0.0, thetaDeg = 15.0)
        val result = MapMatcher(map).localize(cameraPoints(truth, rangeM = 4.0, seed = 3), truth.camX, truth.camZ)!!
        val pose = result.transform.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
        val right = hypot(pose.forwardM - truth.f, pose.lateralM - truth.l) <= 0.5
        assertTrue("점수 ${result.score}, 2등 ${result.runnerUpScore}, 찾은 자리 ${pose.forwardM}", right || result.ambiguous)
    }

    @Test
    fun refineFixesSidewaysAndHeadingDrift() {
        val truth = Truth(f = 3.0, l = 0.1, thetaDeg = 20.0)
        val pts = cameraPoints(truth, fromDeg = -60.0, toDeg = 60.0, seed = 11) // 앞쪽만 보며 걷는 중
        // 추적이 옆으로 0.4m, 방향 3도 틀어졌다.
        val drifted = MapTransform.placing(truth.camX, truth.camZ, 3.0, 0.5, Math.toRadians(23.0))
        val result = MapMatcher(map).refine(pts, drifted, truth.camX, truth.camZ)!!
        assertFound(truth, result, posTol = 0.25, angleTol = 1.5)
    }

    @Test
    fun refineDoesNotSlideAlongAPlainCorridor() {
        // 밋밋한 복도에서 앞뒤 위치가 0.4m 틀어져 있을 때, 엉뚱하게 더 멀리 미끄러지지 않는다.
        val truth = Truth(f = 25.0, l = 0.0, thetaDeg = 0.0)
        val pts = cameraPoints(truth, rangeM = 5.0, fromDeg = -60.0, toDeg = 60.0, seed = 5)
        val drifted = MapTransform.placing(truth.camX, truth.camZ, 25.4, 0.0, 0.0)
        val result = MapMatcher(map).refine(pts, drifted, truth.camX, truth.camZ) ?: return
        val f = result.transform.forward(truth.camX, truth.camZ)
        assertTrue("앞뒤 위치 $f", abs(f - truth.f) <= 0.4 + 1e-9)
    }

    @Test
    fun findsTheRightSpotAllAlongTheDemoRoute() {
        // 시연 경로 곳곳에서 한 바퀴 돌며 찾는다. 틀린 자리를 확신하는 경우는 없어야 한다.
        for ((i, f) in listOf(0.5, 10.0, 21.0, 30.0, 45.0, 54.0).withIndex()) {
            val truth = Truth(f = f, l = 0.0, thetaDeg = (i * 71.0) % 360)
            val result = MapMatcher(map).localize(cameraPoints(truth, seed = i + 20), truth.camX, truth.camZ)!!
            val pose = result.transform.toPose(truth.camX, truth.camZ, 1.0, 0.0, 0)
            val right = hypot(pose.forwardM - truth.f, pose.lateralM - truth.l) <= 0.3
            assertTrue("$f m 지점: 찾은 자리 ${pose.forwardM}, 확실하지 않음=${result.ambiguous}", right || result.ambiguous)
            assertTrue("$f m 지점은 찾아야 한다", right)
        }
    }

    @Test
    fun tooFewPointsGiveNoAnswer() {
        val few = DoubleArray(40) { it * 0.1 }
        assertNull(MapMatcher(map).localize(few, 0.0, 0.0))
        assertNull(MapMatcher(map).refine(few, MapTransform(0.0, 0.0, 0.0), 0.0, 0.0))
    }

    @Test
    fun walkableAreaCoversTheRouteOnly() {
        assertTrue(map.hasWalkable)
        // 경로 0~55m 중심선은 전부 걸을 수 있다(바닥 스캔이 빈 교차로 포함).
        var f = 0.0
        while (f <= 55.0) {
            assertTrue("$f m 중심선", map.isWalkable(f, 0.0))
            f += 0.5
        }
        // 복도 옆 스캔 밖, 지도 밖은 걸을 수 없다.
        assertTrue(!map.isWalkable(20.0, 10.0))
        assertTrue(!map.isWalkable(-100.0, 0.0))
        // 밖에 찍힌 자리는 가까운 복도 안으로 당겨진다.
        val (nf, nl) = map.nearestWalkable(20.0, 3.0, 3.0)!!
        assertTrue(map.isWalkable(nf, nl))
        assertTrue(abs(nf - 20.0) < 0.5 && nl < 3.0)
    }

    @Test
    fun constrainKeepsTheCameraInside() {
        val m = MapMatcher(map)
        val outside = MapTransform.placing(1.0, 2.0, 20.0, 4.0, Math.toRadians(30.0))
        val fixed = m.constrain(outside, 1.0, 2.0)
        assertTrue(map.isWalkable(fixed.forward(1.0, 2.0), fixed.lateral(1.0, 2.0)))
        assertEquals(outside.thetaRad, fixed.thetaRad, 1e-12)
        // 이미 안이면 그대로
        val inside = MapTransform.placing(1.0, 2.0, 20.0, 0.0, 0.0)
        assertEquals(inside, m.constrain(inside, 1.0, 2.0))
    }

    @Test
    fun fixedStartTrackingStaysOnTheRoute() {
        // 출발선에서 시작해 걷는다. 카메라 추적은 거리를 6% 길게 재고 옆으로 조금씩 휜다(흔한 오차를 과장).
        // 지도 맞추기로 1초마다 바로잡고 걸을 수 있는 곳 안으로 묶으면, 바로잡지 않을 때보다 오차가 작아야 한다.
        val m = MapMatcher(map)
        var est = MapTransform.placing(0.0, 0.0, 0.0, 0.0, 0.0)
        val raw = est
        var worstFixed = 0.0
        var worstRaw = 0.0
        var f = 0.0
        var seed = 100
        while (f <= 54.0) {
            val camX = f * 1.06
            val camZ = 0.015 * f
            val theta = -Math.toDegrees(kotlin.math.atan2(0.015, 1.06))
            val truth = Truth(f = f, l = 0.0, thetaDeg = theta, camX = camX, camZ = camZ)
            val pts = cameraPoints(truth, rangeM = 8.0, fromDeg = -70.0, toDeg = 70.0, seed = seed++)
            m.refine(pts, est, camX, camZ)?.let { est = est.blend(it.transform, 0.5) }
            est = m.constrain(est, camX, camZ)
            val e = hypot(est.forward(camX, camZ) - f, est.lateral(camX, camZ))
            val r = hypot(raw.forward(camX, camZ) - f, raw.lateral(camX, camZ))
            worstFixed = maxOf(worstFixed, e)
            worstRaw = maxOf(worstRaw, r)
            assertTrue(map.isWalkable(est.forward(camX, camZ), est.lateral(camX, camZ)))
            f += 1.5
        }
        println("고정 출발 추적: 바로잡음 최대 오차 ${"%.2f".format(worstFixed)}m, 안 바로잡음 ${"%.2f".format(worstRaw)}m")
        assertTrue("바로잡음 $worstFixed / 안 바로잡음 $worstRaw", worstFixed < worstRaw)
    }

    @Test
    fun turnCoverageCountsDirections() {
        val turn = TurnCoverage()
        for (deg in 0 until 360 step 10) turn.add(cos(Math.toRadians(deg.toDouble())), sin(Math.toRadians(deg.toDouble())))
        assertEquals(360, turn.degrees)
        assertTrue(turn.enough)
        turn.clear()
        turn.add(1.0, 0.0)
        turn.add(0.05, 0.05) // 바닥을 보고 있으면 세지 않는다
        assertEquals(30, turn.degrees)
    }
}

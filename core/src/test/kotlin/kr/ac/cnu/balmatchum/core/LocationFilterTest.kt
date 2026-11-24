package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LocationFilterTest {

    private fun sample(sec: Double, northM: Double, accuracy: Double? = 5.0, eastM: Double = 0.0) =
        LocationSample((sec * 1000).toLong(), at(eastM, northM), accuracyM = accuracy)

    // ---- LocationGate ----

    @Test
    fun gateDropsLowAccuracy() {
        val gate = LocationGate()
        assertNull(gate.check(sample(0.0, 0.0)))
        assertEquals(RejectReason.LOW_ACCURACY, gate.check(sample(1.0, 1.0, accuracy = 45.0)))
        assertNull(gate.check(sample(2.0, 2.5)))
    }

    @Test
    fun gateDropsJumpButNotNoiseInsideAccuracy() {
        val gate = LocationGate()
        assertNull(gate.check(sample(0.0, 0.0, accuracy = 8.0)))
        // 1초에 12m: 정확도 8m 두 개를 빼면 0이라 튐이 아니다.
        assertNull(gate.check(sample(1.0, 12.0, accuracy = 8.0)))
        // 1초에 80m: 사람이 못 낸다.
        assertEquals(RejectReason.JUMP, gate.check(sample(2.0, 92.0, accuracy = 8.0)))
        assertNull(gate.check(sample(3.0, 13.5, accuracy = 8.0)))
    }

    @Test
    fun gateAcceptsAgainAfterLongJump() {
        val gate = LocationGate()
        assertNull(gate.check(sample(0.0, 0.0)))
        // 정말로 200m 떨어진 곳으로 위치가 바뀌었으면 10초 뒤에는 받아들인다.
        for (s in 1..9) assertEquals(RejectReason.JUMP, gate.check(sample(s.toDouble(), 200.0)))
        assertNull(gate.check(sample(11.0, 200.0)))
        assertTrue(gate.reAnchored)
        assertNull(gate.check(sample(12.0, 201.0)))
        assertFalse(gate.reAnchored)
    }

    @Test
    fun gateAcceptsLowAccuracyAfterAMinute() {
        val gate = LocationGate()
        assertNull(gate.check(sample(0.0, 0.0)))
        assertEquals(RejectReason.LOW_ACCURACY, gate.check(sample(1.0, 1.0, accuracy = 50.0)))
        assertEquals(RejectReason.LOW_ACCURACY, gate.check(sample(59.0, 1.0, accuracy = 50.0)))
        assertNull(gate.check(sample(61.0, 1.0, accuracy = 50.0)))
    }

    // ---- PositionSmoother ----

    @Test
    fun smootherReducesGpsNoise() {
        // 1.3m/s로 북쪽으로 걷는데 GPS가 표준편차 5m로 흔들린다.
        val random = Random(22)
        val smoother = PositionSmoother()
        var rawError = 0.0
        var smoothError = 0.0
        for (s in 0 until 120) {
            val truth = at(0.0, 1.3 * s)
            val noisy = at(random.nextGaussian() * 5, 1.3 * s + random.nextGaussian() * 5)
            val out = smoother.update(LocationSample(s * 1000L, noisy, accuracyM = 5.0))
            if (s >= 10) {
                rawError += distanceMeters(noisy, truth)
                smoothError += distanceMeters(out, truth)
            }
        }
        assertTrue("다듬은 오차 $smoothError 가 원본 $rawError 보다 작아야 한다", smoothError < rawError * 0.8)
    }

    @Test
    fun smootherFollowsCleanWalk() {
        val smoother = PositionSmoother()
        var last = BASE
        for (s in 0 until 60) last = smoother.update(LocationSample(s * 1000L, at(0.0, 1.4 * s), accuracyM = 5.0))
        assertEquals(0.0, distanceMeters(last, at(0.0, 1.4 * 59)), 1.0)
    }

    // ---- StepTracker ----

    @Test
    fun stepLengthLearnsFromGps() {
        val steps = StepTracker(0.7)
        steps.learn(10, 8.0)
        assertEquals(0.7, steps.stepLengthM, 1e-9) // 아직 20걸음이 안 됨
        steps.learn(10, 8.0)
        assertEquals(0.7 * 0.7 + 0.8 * 0.3, steps.stepLengthM, 1e-9)
        steps.learn(20, 100.0) // 걸음당 5m는 말이 안 돼서 버린다
        assertEquals(0.73, steps.stepLengthM, 1e-9)
    }

    // ---- ArrivalCoach 연결 ----

    private val route = Route(listOf(at(0.0, 0.0), at(0.0, 300.0)))

    @Test
    fun badGpsSectionIsFilledWithSteps() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        // GPS 좋음: 1.4m/s, 1초에 2걸음(걸음 길이 0.7m)
        for (s in 0..30) {
            if (s > 0) coach.onStep(2)
            coach.update(sample(s.toDouble(), 1.4 * s))
        }
        val before = coach.update(sample(31.0, 1.4 * 31)).remainingM
        // 건물 옆: 정확도 60m로 10초. 걸음은 계속 감지된다.
        val bad = (32..41).map { s ->
            coach.onStep(2)
            coach.update(sample(s.toDouble(), 1.4 * s + 40, accuracy = 60.0))
        }
        assertTrue(bad.all { it.rejected == RejectReason.LOW_ACCURACY })
        assertEquals(PositionSource.HOLD, bad.first().source) // 처음 3초는 그대로 둔다
        assertEquals(PositionSource.STEPS, bad.last().source)
        // 10초 동안 20걸음 × 약 0.7m = 약 14m 진행
        assertEquals(before - 14.0, bad.last().remainingM, 2.0)
        // GPS가 돌아오면 다시 GPS로
        assertEquals(PositionSource.GPS, coach.update(sample(42.0, 1.4 * 42)).source)
    }

    @Test
    fun noCallbacksAtAllAlsoUsesSteps() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        for (s in 0..20) {
            if (s > 0) coach.onStep(2)
            coach.update(sample(s.toDouble(), 1.4 * s))
        }
        repeat(10) { coach.onStep(2) }
        val u = coach.updateWithoutGps(30_000)
        assertEquals(PositionSource.STEPS, u.source)
        assertEquals(300 - 1.4 * 20 - 14.0, u.remainingM, 2.0)
    }

    @Test
    fun stepsNeverDeclareArrival() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        for (s in 0..190) {
            if (s > 0) coach.onStep(2)
            coach.update(sample(s.toDouble(), 1.4 * s))
        }
        // 266m에서 GPS가 나빠지고 걸음으로 50m 넘게 간다.
        val bad = (191..230).map { s ->
            coach.onStep(2)
            coach.update(sample(s.toDouble(), 266.0, accuracy = 60.0))
        }
        assertTrue(bad.last().remainingM <= 20.0)
        assertFalse(bad.any { it.arrived })
        // GPS로 도착을 확인해야 도착이다.
        assertTrue(coach.update(sample(231.0, 295.0)).arrived)
    }

    @Test
    fun shortBacktrackDoesNotIncreaseRemaining() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000, config = CoachConfig(smoothPositions = false))
        for (s in 0..20) coach.update(sample(s.toDouble(), 1.4 * s, accuracy = null))
        val held = coach.update(sample(22.0, 1.4 * 20 - 8, accuracy = null))
        assertEquals(300 - 28.0, held.remainingM, 0.01)
        // 계속 뒤에 있으면 5초 뒤에는 받아들인다.
        (23..26).forEach { coach.update(sample(it.toDouble(), 20.0, accuracy = null)) }
        assertEquals(280.0, coach.update(sample(27.0, 20.0, accuracy = null)).remainingM, 0.01)
    }

    @Test
    fun logsDistanceToDestination() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        val u = coach.update(sample(0.0, 100.0, eastM = 0.0))
        assertEquals(200.0, u.destinationDistanceM!!, 0.5)
        assertEquals(0.0, u.offsetM!!, 0.5)
    }
}

package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorDemoTest {

    private val walk = Gait(cadenceSpm = 110.0, stepLengthM = 0.70)
    private val brisk = Gait(cadenceSpm = 130.0, stepLengthM = 0.80)

    /** 걸음과 1초 틱을 순서대로 넣어 주는 가짜 시계. */
    private class Walker(val coach: ArrivalCoach) {
        var nowMillis = 0L
        val updates = mutableListOf<CoachUpdate>()
        private var stepDebt = 0.0

        /** [seconds]초 동안 분당 [cadenceSpm]걸음으로 걷는다. 0이면 서 있는다. */
        fun go(seconds: Int, cadenceSpm: Double) {
            repeat(seconds) {
                stepDebt += cadenceSpm / 60.0
                val whole = stepDebt.toInt()
                stepDebt -= whole
                if (whole > 0) coach.onStep(whole)
                nowMillis += 1_000
                updates += coach.updateIndoor(nowMillis)
            }
        }

        /** 도착할 때까지 걷는다. 걸린 초를 돌려준다. */
        fun goUntilArrived(cadenceSpm: Double, limitSec: Int = 300): Int {
            var seconds = 0
            while (!updates.last().arrived && seconds < limitSec) {
                go(1, cadenceSpm)
                seconds++
            }
            return seconds
        }
    }

    private fun coach(
        lengthM: Double,
        deadlineSec: Int,
        checkpointsM: List<Double> = emptyList(),
    ): ArrivalCoach {
        val settings = IndoorSettings(walk, brisk, checkpointsM = checkpointsM)
        val route = TmapRoute.parse(IndoorDemo.straightRouteJson(lengthM)).route
        return ArrivalCoach(route, deadlineSec * 1_000L, settings.speeds(), indoor = settings)
    }

    @Test
    fun straightRouteHasRequestedLength() {
        val tmap = TmapRoute.parse(IndoorDemo.straightRouteJson(55.0))
        assertEquals(55.0, tmap.route.totalDistance, 0.05)
        assertEquals(55.0, tmap.totalDistanceM, 0.0)
        assertTrue(tmap.turns.isEmpty())
    }

    @Test
    fun stepLengthFollowsCadence() {
        val settings = IndoorSettings(walk, brisk)
        assertEquals(0.70, settings.stepLength(null), 1e-9)
        assertEquals(0.70, settings.stepLength(110.0), 1e-9)
        assertEquals(0.75, settings.stepLength(120.0), 1e-9)
        assertEquals(0.80, settings.stepLength(130.0), 1e-9)
        // 실측값 바깥으로는 간격의 절반(0.05m)까지만 따라간다.
        assertEquals(0.65, settings.stepLength(60.0), 1e-9)
        assertEquals(0.85, settings.stepLength(170.0), 1e-9)
    }

    @Test
    fun cadenceMeterNeedsTwoSecondsThenTracksRecentSteps() {
        val meter = CadenceMeter(windowMillis = 5_000)
        assertNull(meter.add(0, 0))
        assertNull(meter.add(1_000, 2))
        assertEquals(120.0, meter.add(2_000, 4)!!, 1e-9)
        // 멈추면 5초 창이 지나며 0으로 내려간다.
        var last = 0.0
        for (t in 3..10) last = meter.add(t * 1_000L, 4)!!
        assertEquals(0.0, last, 1e-9)
    }

    @Test
    fun walkingAdvancesByStepsAndArrivesNearTheEnd() {
        // 40m, 넉넉한 목표. 분당 110걸음 × 0.70m = 1.28m/s.
        val w = Walker(coach(lengthM = 40.0, deadlineSec = 120))
        w.go(10, 110.0)
        assertEquals(40.0 - 18 * 0.70, w.updates.last().remainingM, 0.5) // 10초에 18걸음
        assertEquals(PositionSource.STEPS, w.updates.last().source)
        assertFalse(w.updates.last().arrived) // 실외 기준 20m에서는 도착시키지 않는다

        val seconds = w.goUntilArrived(110.0)
        assertTrue(w.updates.last().arrived)
        assertTrue(w.updates.last().remainingM <= 2.0)
        assertTrue("도착까지 ${10 + seconds}초", 10 + seconds in 28..32) // 38m ÷ 1.28m/s ≈ 30초
    }

    @Test
    fun briskWalkUsesLongerSteps() {
        val w = Walker(coach(lengthM = 200.0, deadlineSec = 600))
        w.go(30, 130.0)
        // 처음 2초는 걸음 빠르기를 몰라 보통 걸음 길이를 쓴다. 그 뒤로는 0.80m.
        val walked = 200.0 - w.updates.last().remainingM
        assertEquals(65 * 0.80, walked, 2.5)
        // 1초 단위로 센 걸음 수는 5초에 10~11걸음으로 흔들려, 그 순간의 분당 걸음 수는 120~132 사이다.
        assertEquals(0.80, w.updates.last().stepLengthM, 0.06)
        assertEquals(130.0, w.updates.last().cadenceSpm!!, 11.0)
    }

    @Test
    fun standingStillHoldsPosition() {
        val w = Walker(coach(lengthM = 40.0, deadlineSec = 120))
        w.go(5, 110.0)
        val before = w.updates.last().remainingM
        w.go(10, 0.0)
        assertEquals(before, w.updates.last().remainingM, 1e-9)
        assertEquals(PositionSource.HOLD, w.updates.last().source)
    }

    @Test
    fun progressStopsAtCheckpointUntilConfirmed() {
        val w = Walker(coach(lengthM = 55.0, deadlineSec = 300, checkpointsM = listOf(21.0)))
        w.go(5, 110.0)
        assertFalse(w.updates.last().checkpointConfirmable) // 아직 10m 넘게 남음
        assertFalse(w.coach.confirmCheckpoint())

        // 장애물을 돌아가느라 40초 동안 걷는다. 걸음 수로는 50m쯤이지만 체크포인트에서 멈춘다.
        w.go(40, 110.0)
        assertEquals(55.0 - 21.0, w.updates.last().remainingM, 1e-9)
        assertEquals(0.0, w.updates.last().checkpointRemainingM!!, 1e-9)
        assertTrue(w.updates.last().checkpointConfirmable)
        assertFalse(w.updates.last().arrived)

        assertTrue(w.coach.confirmCheckpoint())
        w.go(10, 110.0)
        assertEquals(55.0 - 21.0 - 18 * 0.70, w.updates.last().remainingM, 0.8)
        assertNull(w.updates.last().checkpointRemainingM)
        assertFalse(w.coach.confirmCheckpoint()) // 남은 체크포인트가 없다
    }

    @Test
    fun confirmingEarlySnapsForwardToCheckpoint() {
        // 걸음 길이를 실제보다 짧게 잡아 추정이 뒤처진 경우. 체크포인트에서 누르면 앞으로 당겨진다.
        val w = Walker(coach(lengthM = 55.0, deadlineSec = 300, checkpointsM = listOf(21.0)))
        w.go(10, 110.0) // 12.6m
        assertTrue(w.updates.last().checkpointConfirmable)
        assertTrue(w.coach.confirmCheckpoint())
        w.go(1, 0.0)
        assertEquals(55.0 - 21.0, w.updates.last().remainingM, 1e-9)
    }

    @Test
    fun demoScenarioTurnsToHurryDuringDetourAndArrivesInTime() {
        // 55m, 목표 55초 뒤. 보통 걸음 9m → 장애물 구간(12m 전진에 22초) → 빠른 걸음 34m.
        val w = Walker(coach(lengthM = 55.0, deadlineSec = 55, checkpointsM = listOf(21.0)))
        w.go(7, 110.0)
        assertEquals(ArrivalState.RELAXED, w.updates.last().state)

        w.go(24, 110.0) // 돌아가느라 걸음은 계속 걷지만 진행은 체크포인트에서 멈춘다
        assertEquals(ArrivalState.HURRY, w.updates.last().state)
        assertTrue(w.coach.confirmCheckpoint())

        w.goUntilArrived(130.0)
        val last = w.updates.last()
        assertTrue(last.arrived)
        val states = w.updates.filter { it.stateChanged }.map { it.state }
        // 빠른 걸음으로 따라잡으면 도착 전에 다시 "여유 있음"으로 돌아올 수 있다.
        assertEquals(listOf(ArrivalState.RELAXED, ArrivalState.HURRY), states.take(2))
        assertFalse(ArrivalState.RUN in states)
        assertTrue("남은 시간 ${last.remainingTimeSec}초", last.remainingTimeSec in 0.0..10.0)
    }

    @Test
    fun longDetourEscalatesToRun() {
        // 같은 경로에서 장애물 구간에 36초를 쓰면 빠른 걸음으로도 못 맞춰 "뛰어야 맞춤"까지 올라간다.
        val w = Walker(coach(lengthM = 55.0, deadlineSec = 55, checkpointsM = listOf(21.0)))
        w.go(7, 110.0)
        w.go(36, 110.0)
        assertEquals(ArrivalState.RUN, w.updates.last().state)
    }

    @Test
    fun targetTimeStartsBarelyRelaxed() {
        // 55m: 보통 걸음 42.9초 + 여유 8.6초 + 3초 → 55초. 출발 때는 "여유 있음"이다.
        assertEquals(55, IndoorDemo.targetSec(55.0))
        val coach = ArrivalCoach(
            TmapRoute.parse(IndoorDemo.straightRouteJson(55.0)).route,
            deadlineMillis = IndoorDemo.targetSec(55.0) * 1_000L,
            speeds = IndoorDemo.settings.speeds(),
            indoor = IndoorDemo.settings,
        )
        assertEquals(ArrivalState.RELAXED, coach.updateIndoor(0).state)
    }

    @Test
    fun outdoorCoachIgnoresIndoorFeatures() {
        val coach = ArrivalCoach(Route(listOf(at(0.0, 0.0), at(0.0, 300.0))), deadlineMillis = 360_000)
        assertFalse(coach.confirmCheckpoint())
        val update = coach.update(LocationSample(0, at(0.0, 0.0), speedMps = 1.3))
        assertNull(update.checkpointRemainingM)
        assertFalse(update.checkpointConfirmable)
    }
}

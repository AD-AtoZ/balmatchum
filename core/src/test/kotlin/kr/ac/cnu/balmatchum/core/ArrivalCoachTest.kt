package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalCoachTest {

    private val route = Route(listOf(at(0.0, 0.0), at(0.0, 300.0))) // 북쪽으로 300m

    /** 1초마다 [speed]로 북쪽으로 걷는 표본을 [seconds]개 만든다. */
    private fun walk(startSec: Int, seconds: Int, startNorthM: Double, speed: Double): List<LocationSample> =
        (0 until seconds).map { i ->
            LocationSample(
                timeMillis = (startSec + i) * 1_000L,
                point = at(0.0, startNorthM + speed * i),
                speedMps = speed,
            )
        }

    @Test
    fun relaxedWalkStaysRelaxedAndArrives() {
        // 300m, 목표 6분 뒤. 기본 보통 걸음(1.3m/s)으로 231초라 여유가 충분하다.
        val coach = ArrivalCoach(route, deadlineMillis = 360_000)
        val updates = walk(0, 220, 0.0, 1.4).map(coach::update)
        assertTrue(updates.first().stateChanged)
        assertTrue(updates.all { it.state == ArrivalState.RELAXED })
        assertEquals(1, updates.count { it.stateChanged })
        assertTrue(updates.last().arrived)
    }

    @Test
    fun dawdlingTurnsIntoRunAfterHoldTime() {
        // 목표 200초 뒤, 300m. 빠른 걸음(1.7m/s)이면 176초라 처음엔 "서두르면 맞춤".
        // 0.9m/s로 느릿느릿 걸으면 약 50초 뒤부터 빠른 걸음으로도 못 맞춰 "뛰어야 맞춤"이 된다.
        val coach = ArrivalCoach(route, deadlineMillis = 200_000)
        val updates = walk(0, 80, 0.0, 0.9).map(coach::update)
        assertEquals(ArrivalState.HURRY, updates.first().state)

        val firstRunObserved = updates.indexOfFirst { it.observedState == ArrivalState.RUN }
        val firstRunConfirmed = updates.indexOfFirst { it.state == ArrivalState.RUN }
        assertTrue(firstRunObserved in 45..55)
        assertEquals(firstRunObserved + 5, firstRunConfirmed) // 5초 유지 뒤 확정
        assertTrue(updates[firstRunConfirmed].stateChanged)
    }

    @Test
    fun personalSpeedsChangeTheVerdict() {
        // 같은 길, 같은 목표 시각이라도 걸음이 빠른 사람은 "여유 있음".
        val fast = PersonalSpeeds(walkMps = 1.8, briskMps = 2.2, runMps = 3.2)
        val slowCoach = ArrivalCoach(route, deadlineMillis = 200_000)
        val fastCoach = ArrivalCoach(route, deadlineMillis = 200_000, speeds = fast)
        val sample = LocationSample(0, at(0.0, 0.0), speedMps = 0.0)
        assertEquals(ArrivalState.HURRY, slowCoach.update(sample).state)
        assertEquals(ArrivalState.RELAXED, fastCoach.update(sample).state)
    }

    @Test
    fun showsWalkSpeedBeforeMoving() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000, speeds = PersonalSpeeds(1.1, 1.5, 2.4))
        val u = coach.update(LocationSample(0, at(0.0, 0.0), speedMps = 0.0))
        assertEquals(1.1, u.movingSpeedMps, 1e-9)
    }

    @Test
    fun derivesSpeedWhenPlatformGivesNone() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        coach.update(LocationSample(0, at(0.0, 0.0)))
        val u = coach.update(LocationSample(10_000, at(0.0, 15.0)))
        assertEquals(1.5, u.movingSpeedMps, 0.01)
    }

    @Test
    fun waitingAtCrossingDoesNotChangeMovingSpeed() {
        val coach = ArrivalCoach(route, deadlineMillis = 600_000)
        walk(0, 20, 0.0, 1.4).forEach { coach.update(it) }
        val waiting = (20 until 80).map { t ->
            coach.update(LocationSample(t * 1_000L, at(0.0, 28.0), speedMps = 0.0))
        }
        assertEquals(1.4, waiting.last().movingSpeedMps, 1e-9)
        assertFalse(waiting.last().offRoute)
    }
}

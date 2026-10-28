package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalJudgeTest {

    // 보통 1.2, 빠른 걸음 1.6, 뛰기 2.5 m/s인 사람이 120m 남았다.
    // 보통 걸음 100초, 빠른 걸음 75초, 뛰기 48초. "여유 있음" 여유는 min(30초, 100초 × 20%) = 20초.
    private val me = PersonalSpeeds(walkMps = 1.2, briskMps = 1.6, runMps = 2.5)

    @Test
    fun relaxedWhenWalkLeavesMargin() {
        assertEquals(ArrivalState.RELAXED, judge(120.0, 200.0, me).state)
        assertEquals(ArrivalState.RELAXED, judge(120.0, 120.0, me).state) // 경계: 정확히 20초 남음
    }

    @Test
    fun hurryWhenBriskWalkMakesIt() {
        val j = judge(120.0, 110.0, me)
        assertEquals(ArrivalState.HURRY, j.state)
        assertEquals(10.0, j.slackAtWalkSec, 1e-9)
        assertEquals(ArrivalState.HURRY, judge(120.0, 75.0, me).state) // 경계: 빠른 걸음으로 딱 맞음
    }

    @Test
    fun runWhenOnlyRunningMakesIt() {
        val j = judge(120.0, 60.0, me)
        assertEquals(ArrivalState.RUN, j.state)
        assertEquals(2.0, j.requiredSpeedMps, 1e-9)
        assertEquals(0.0, j.lateAtRunSec, 1e-9)
    }

    @Test
    fun tooLateWhenEvenRunningIsLate() {
        val j = judge(120.0, 40.0, me)
        assertEquals(ArrivalState.TOO_LATE, j.state)
        assertEquals(8.0, j.lateAtRunSec, 1e-9)
    }

    @Test
    fun tooLateAfterDeadlinePassed() {
        val j = judge(50.0, -10.0, me)
        assertEquals(ArrivalState.TOO_LATE, j.state)
        assertEquals(Double.POSITIVE_INFINITY, j.requiredSpeedMps, 0.0)
    }

    @Test
    fun nearArrivalOnTimeIsRelaxed() {
        // 20m 남고 28초 남음: 보통 걸음 약 17초면 도착하니 "서두르면"이 아니라 "여유 있음"이어야 한다.
        assertEquals(ArrivalState.RELAXED, judge(20.0, 28.0, me).state)
    }

    @Test
    fun fasterPersonGetsMoreRelaxedVerdict() {
        // 같은 상황(120m, 90초)이라도 걸음이 빠른 사람에게는 덜 급하다.
        assertEquals(ArrivalState.HURRY, judge(120.0, 90.0, me).state)
        val fast = PersonalSpeeds(walkMps = 2.0, briskMps = 2.4, runMps = 3.5)
        assertEquals(ArrivalState.RELAXED, judge(120.0, 90.0, fast).state)
    }
}

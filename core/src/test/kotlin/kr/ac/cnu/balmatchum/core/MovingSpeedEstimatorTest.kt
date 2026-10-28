package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovingSpeedEstimatorTest {

    private fun MovingSpeedEstimator.feed(fromSec: Int, untilSec: Int, speed: Double) {
        for (t in fromSec until untilSec) add(t * 1_000L, speed)
    }

    @Test
    fun noMovingSampleGivesNull() {
        val est = MovingSpeedEstimator()
        est.feed(0, 10, 0.0)
        assertNull(est.movingSpeed())
    }

    @Test
    fun constantWalk() {
        val est = MovingSpeedEstimator()
        est.feed(0, 20, 1.5)
        assertEquals(1.5, est.movingSpeed()!!, 1e-9)
    }

    @Test
    fun stopAtCrossingDoesNotDragSpeedDown() {
        val est = MovingSpeedEstimator()
        est.feed(0, 10, 1.5)
        est.feed(10, 70, 0.1) // 신호 대기 1분
        assertEquals(1.5, est.movingSpeed()!!, 1e-9)
        est.feed(70, 75, 1.5)
        assertEquals(1.5, est.movingSpeed()!!, 1e-9)
    }

    @Test
    fun onlyRecentMovingTimeCounts() {
        val est = MovingSpeedEstimator() // 창 30초
        est.feed(0, 60, 1.0)
        est.feed(60, 90, 2.0)
        assertEquals(2.0, est.movingSpeed()!!, 0.05)
    }

    @Test
    fun longGpsGapIsCapped() {
        val est = MovingSpeedEstimator()
        est.feed(0, 10, 1.0)
        est.add(70_000L, 2.0) // 1분 끊겼다 돌아온 표본은 5초로만 친다
        // (1.0 × 10초 + 2.0 × 5초) / 15초
        assertEquals(20.0 / 15.0, est.movingSpeed()!!, 1e-9)
    }
}

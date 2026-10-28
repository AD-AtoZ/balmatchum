package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteDetectorTest {

    @Test
    fun confirmsAfterTenSecondsWithoutProgress() {
        val d = OffRouteDetector()
        for (t in 0..9) d.update(t * 1_000L, 50.0, null, 100.0)
        assertFalse(d.isOffRoute)
        assertTrue(d.update(10_000, 50.0, null, 100.0))
        assertTrue(d.isOffRoute)
    }

    @Test
    fun poorAccuracyWidensThreshold() {
        val d = OffRouteDetector()
        for (t in 0..20) d.update(t * 1_000L, 50.0, 40.0, 100.0) // 기준 80m
        assertFalse(d.isOffRoute)
    }

    @Test
    fun progressAlongRouteIsNotOffRoute() {
        // 경로에서 떨어져 보여도 진행 거리가 계속 늘면 GPS가 옆으로 밀린 것으로 본다.
        val d = OffRouteDetector()
        for (t in 0..20) d.update(t * 1_000L, 50.0, null, 100.0 + t * 1.4)
        assertFalse(d.isOffRoute)
    }

    @Test
    fun comingBackClearsImmediately() {
        val d = OffRouteDetector()
        for (t in 0..12) d.update(t * 1_000L, 50.0, null, 100.0)
        assertTrue(d.isOffRoute)
        assertTrue(d.update(13_000, 5.0, null, 100.0))
        assertFalse(d.isOffRoute)
    }
}

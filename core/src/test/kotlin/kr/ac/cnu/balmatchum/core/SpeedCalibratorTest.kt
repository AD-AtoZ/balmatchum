package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedCalibratorTest {

    private fun SpeedCalibrator.feed(fromSec: Int, untilSec: Int, speed: Double) {
        for (t in fromSec until untilSec) add(t * 1_000L, speed)
    }

    @Test
    fun noSamplesGivesNull() {
        assertNull(SpeedCalibrator().result())
    }

    @Test
    fun constantWalkForThirtySeconds() {
        val calibrator = SpeedCalibrator()
        calibrator.feed(0, 30, 1.5)
        assertEquals(1.5, calibrator.result()!!, 1e-9)
    }

    @Test
    fun firstFiveSecondsAreIgnoredRelativeToFirstSample() {
        val calibrator = SpeedCalibrator()
        val start = 1_800_000_000_000L
        calibrator.add(start, 9.0)
        calibrator.add(start + 4_999L, 9.0)
        assertNull(calibrator.result())
        calibrator.add(start + 5_000L, 1.2)
        assertEquals(1.2, calibrator.result()!!, 1e-9)
    }

    @Test
    fun stopDuringMeasurementDoesNotDragSpeedDown() {
        val calibrator = SpeedCalibrator()
        calibrator.feed(0, 10, 1.5)
        calibrator.feed(10, 25, 0.1)
        calibrator.feed(25, 30, 1.5)
        assertEquals(1.5, calibrator.result()!!, 1e-9)
    }

    @Test
    fun oneGpsSpikeDoesNotChangeMedian() {
        val calibrator = SpeedCalibrator()
        calibrator.feed(0, 29, 1.5)
        calibrator.add(29_000L, 100.0)
        assertEquals(1.5, calibrator.result()!!, 1e-9)
    }

    @Test
    fun oddNumberOfSamplesUsesSortedMiddle() {
        val calibrator = SpeedCalibrator()
        calibrator.add(0L, 0.0)
        calibrator.add(5_000L, 3.0)
        calibrator.add(6_000L, 1.0)
        calibrator.add(7_000L, 2.0)
        assertEquals(2.0, calibrator.result()!!, 1e-9)
    }

    @Test
    fun evenNumberOfSamplesUsesMeanOfTwoMiddleValues() {
        val calibrator = SpeedCalibrator()
        calibrator.add(0L, 0.0)
        calibrator.add(5_000L, 4.0)
        calibrator.add(6_000L, 1.0)
        calibrator.add(7_000L, 2.0)
        calibrator.add(8_000L, 3.0)
        assertEquals(2.5, calibrator.result()!!, 1e-9)
    }

    @Test
    fun onlyStopsGivesNull() {
        val calibrator = SpeedCalibrator()
        calibrator.feed(0, 30, 0.0)
        calibrator.add(30_000L, -1.0)
        calibrator.add(31_000L, 0.399)
        assertNull(calibrator.result())
    }

    @Test
    fun exactlyStopThresholdIsIncluded() {
        val calibrator = SpeedCalibrator()
        calibrator.add(0L, 0.0)
        calibrator.add(5_000L, 0.4)
        assertEquals(0.4, calibrator.result()!!, 1e-9)
    }

    @Test
    fun nonFiniteSpeedsAreIgnored() {
        val calibrator = SpeedCalibrator()
        calibrator.add(0L, Double.NaN)
        calibrator.add(5_000L, Double.NaN)
        calibrator.add(6_000L, Double.POSITIVE_INFINITY)
        calibrator.add(7_000L, Double.NEGATIVE_INFINITY)
        assertNull(calibrator.result())
        calibrator.add(8_000L, 1.5)
        assertEquals(1.5, calibrator.result()!!, 1e-9)
    }

    @Test
    fun resultCanBeReadRepeatedlyAndMoreSamplesAdded() {
        val calibrator = SpeedCalibrator()
        calibrator.add(0L, 0.0)
        calibrator.add(5_000L, 3.0)
        calibrator.add(6_000L, 1.0)
        assertEquals(2.0, calibrator.result()!!, 1e-9)
        assertEquals(2.0, calibrator.result()!!, 1e-9)
        calibrator.add(7_000L, 1.0)
        assertEquals(1.0, calibrator.result()!!, 1e-9)
    }
}

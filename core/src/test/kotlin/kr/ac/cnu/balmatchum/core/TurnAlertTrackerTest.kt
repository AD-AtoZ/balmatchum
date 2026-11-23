package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnAlertTrackerTest {
    private fun turn(along: Double, type: Int = 12) = RouteTurn.fromTmap(along, type, "")!!

    @Test
    fun alertsAtThirtyMetersOnlyOnceDespiteGpsJitter() {
        val guide = TurnGuide(listOf(turn(100.0)), totalDistanceM = 300.0)
        val alerts = TurnAlertTracker()
        assertFalse(alerts.shouldAlert(guide.next(69.9)))
        assertTrue(alerts.shouldAlert(guide.next(70.0)))
        assertFalse(alerts.shouldAlert(guide.next(75.0)))
        assertFalse(alerts.shouldAlert(guide.next(69.0)))
        assertFalse(alerts.shouldAlert(guide.next(71.0)))
    }

    @Test
    fun alertsWhenGuidanceStartsInsideThirtyMeters() {
        val guide = TurnGuide(listOf(turn(20.0)), totalDistanceM = 300.0)
        assertTrue(TurnAlertTracker().shouldAlert(guide.next(0.0)))
    }

    @Test
    fun eachTurnAlertsEvenWithSameDirectionAndReturningToEarlierTurnDoesNotRepeat() {
        val guide = TurnGuide(listOf(turn(100.0), turn(120.0)), totalDistanceM = 300.0)
        val alerts = TurnAlertTracker()
        assertTrue(alerts.shouldAlert(guide.next(70.0)))
        assertTrue(alerts.shouldAlert(guide.next(111.0)))
        assertFalse(alerts.shouldAlert(guide.next(112.0)))
        assertFalse(alerts.shouldAlert(guide.next(75.0)))
        assertFalse(alerts.shouldAlert(guide.next(111.0)))
    }

    @Test
    fun newSessionCanAlertSameTurnAgain() {
        val next = TurnGuide(listOf(turn(100.0)), totalDistanceM = 300.0).next(75.0)
        assertTrue(TurnAlertTracker().shouldAlert(next))
        assertTrue(TurnAlertTracker().shouldAlert(next))
    }

    @Test
    fun ignoresMissingTurnDestinationAndNonDirectionalFacilities() {
        val alerts = TurnAlertTracker()
        assertFalse(alerts.shouldAlert(null))
        assertFalse(alerts.shouldAlert(UpcomingTurn(null, 10.0, isNear = false)))
        val stairs = TurnGuide(listOf(turn(100.0, 127)), totalDistanceM = 300.0)
        assertFalse(alerts.shouldAlert(stairs.next(75.0)))
    }

    @Test
    fun alertsForRightTurnUTurnAndDirectionalCrosswalk() {
        for (type in listOf(13, 14, 17, 18, 212, 213)) {
            val guide = TurnGuide(listOf(turn(100.0, type)), totalDistanceM = 300.0)
            assertTrue(TurnAlertTracker().shouldAlert(guide.next(75.0)))
        }
    }
}

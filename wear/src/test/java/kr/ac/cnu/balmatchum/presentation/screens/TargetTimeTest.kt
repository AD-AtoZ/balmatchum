package kr.ac.cnu.balmatchum.presentation.screens

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetTimeTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 2)

    private fun millis(date: LocalDate, time: LocalTime) =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    @Test fun rejectsPastTimeInsteadOfRollingToTomorrow() {
        assertNull(futureTargetTimeMillis(today, LocalTime.of(13, 59),
            millis(today, LocalTime.of(14, 0)), zone))
    }

    @Test fun rejectsCurrentMinuteEvenWhenSecondsRemain() {
        assertNull(futureTargetTimeMillis(today, LocalTime.of(14, 0),
            millis(today, LocalTime.of(14, 0, 30)), zone))
    }

    @Test fun rejectsExactCurrentTime() {
        assertNull(futureTargetTimeMillis(today, LocalTime.of(14, 0),
            millis(today, LocalTime.of(14, 0)), zone))
    }

    @Test fun acceptsNextMinuteAndDropsSeconds() {
        assertEquals(millis(today, LocalTime.of(14, 1)),
            futureTargetTimeMillis(today, LocalTime.of(14, 1, 55),
                millis(today, LocalTime.of(14, 0, 59)), zone))
    }

    @Test fun acceptsExplicitTomorrowAcrossMidnight() {
        assertEquals(millis(today.plusDays(1), LocalTime.MIDNIGHT),
            futureTargetTimeMillis(today.plusDays(1), LocalTime.MIDNIGHT,
                millis(today, LocalTime.of(23, 59, 59)), zone))
    }

    @Test fun rejectsExpiredDateAfterMidnight() {
        assertNull(futureTargetTimeMillis(today, LocalTime.of(23, 59),
            millis(today.plusDays(1), LocalTime.MIDNIGHT), zone))
    }

    @Test fun rechecksWhenSelectedTimeExpiresBeforeConfirmation() {
        val time = LocalTime.of(14, 1)
        assertEquals(millis(today, time),
            futureTargetTimeMillis(today, time, millis(today, LocalTime.of(14, 0)), zone))
        assertNull(futureTargetTimeMillis(today, time, millis(today, time), zone))
    }
}

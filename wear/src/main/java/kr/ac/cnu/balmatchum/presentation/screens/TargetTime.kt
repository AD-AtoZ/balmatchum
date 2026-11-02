package kr.ac.cnu.balmatchum.presentation.screens

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 선택한 날짜의 시·분을 변환한다. 지난 시각을 자동으로 내일로 바꾸지 않는다. */
internal fun futureTargetTimeMillis(
    date: LocalDate,
    time: LocalTime,
    nowMillis: Long,
    zone: ZoneId,
): Long? {
    val candidate = date.atTime(time.withSecond(0).withNano(0)).atZone(zone).toInstant().toEpochMilli()
    return candidate.takeIf { it > nowMillis }
}

package kr.ac.cnu.balmatchum.presentation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 화면에 숫자를 보여 줄 때 쓰는 작은 도우미들. */
object Format {

    /** 초 → "3분 20초" / "45초". 음수면 앞에 "-". */
    fun duration(sec: Double): String {
        val s = abs(sec).roundToInt()
        val text = if (s >= 60) "${s / 60}분 ${s % 60}초" else "${s}초"
        return if (sec < 0) "-$text" else text
    }

    /** m → "949m" / "1.2km" */
    fun distance(m: Double): String = if (m >= 1000) "%.1fkm".format(Locale.KOREA, m / 1000) else "${m.roundToInt()}m"

    /** m/s → "1.3m/s (4.7km/h)" */
    fun speed(mps: Double): String = "%.1fm/s (%.1fkm/h)".format(Locale.KOREA, mps, mps * 3.6)

    /** epoch ms → "14:05" */
    fun clock(millis: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(millis))
}

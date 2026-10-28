package kr.ac.cnu.balmatchum.log

import android.content.Context
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.LocationSample
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 안내 한 번을 CSV 한 파일로 남긴다. 실측 결과(포스터·보고서 근거)를 정리할 때 쓴다.
 *
 * 파일 위치: 워치 앱 내부 `files/logs/날짜_시각_목적지.csv`
 * PC로 가져오기: `adb shell run-as kr.ac.cnu.balmatchum cat files/logs/파일이름.csv > 파일이름.csv`
 */
class CsvLogger(context: Context, destinationName: String, private val startedAtMillis: Long) {

    val file: File = File(context.filesDir, "logs").apply { mkdirs() }.resolve(
        "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(startedAtMillis))}_${safe(destinationName)}.csv",
    )
    private val out = PrintWriter(file.bufferedWriter(Charsets.UTF_8))
    private val clock = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.KOREA)

    init {
        out.println(
            "time,elapsed_s,lat,lon,accuracy_m,gps_speed_mps,moving_speed_mps,remaining_m,remaining_s," +
                "required_speed_mps,observed_state,state,state_changed,off_route",
        )
    }

    fun log(sample: LocationSample, u: CoachUpdate) {
        out.println(
            listOf(
                clock.format(Date(sample.timeMillis)),
                "%.1f".format(Locale.US, (sample.timeMillis - startedAtMillis) / 1000.0),
                "%.7f".format(Locale.US, sample.point.lat),
                "%.7f".format(Locale.US, sample.point.lon),
                sample.accuracyM?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                sample.speedMps?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
                "%.2f".format(Locale.US, u.movingSpeedMps),
                "%.1f".format(Locale.US, u.remainingM),
                "%.1f".format(Locale.US, u.remainingTimeSec),
                if (u.requiredSpeedMps.isFinite()) "%.2f".format(Locale.US, u.requiredSpeedMps) else "",
                u.observedState.name,
                u.state.name,
                u.stateChanged,
                u.offRoute,
            ).joinToString(","),
        )
    }

    fun close() {
        out.flush()
        out.close()
    }

    private companion object {
        fun safe(name: String) = name.replace(Regex("[^0-9A-Za-z가-힣]+"), "_").trim('_').take(40)
    }
}

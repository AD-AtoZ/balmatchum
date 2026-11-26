package kr.ac.cnu.balmatchum.log

import android.content.Context
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.LocationSample
import kr.ac.cnu.balmatchum.core.UpcomingTurn
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
 *
 * 열(뒤 8개는 T22에서 추가. 앞 16개는 예전 기록과 같다)
 * - lat, lon, accuracy_m, gps_speed_mps: GPS 원본. 위치 콜백이 끊겨 걸음 수로 판정한 줄은 비어 있다
 * - source: 판정에 쓴 진행 거리 출처(GPS / STEPS 걸음 수 / HOLD 그대로 둠)
 * - rejected: GPS 위치를 버린 이유(LOW_ACCURACY / JUMP), 안 버렸으면 빈칸
 * - used_lat, used_lon: 판정에 쓴 위치(다듬은 GPS 위치 또는 경로 위 추정 위치)
 * - offset_m: 판정에 쓴 위치에서 경로 선까지 거리, dest_m: 경로 끝(목적지 입구 좌표)까지 직선거리
 * - steps, step_len_m: 안내 시작부터 걸음 수, 지금 쓰는 걸음 길이
 * - cadence_spm, checkpoint_m: 실내 시연에서만 채운다. 최근 분당 걸음 수, 다음 체크포인트까지 남은 거리
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
                "required_speed_mps,observed_state,state,state_changed,off_route,next_turn,next_turn_m," +
                "source,rejected,used_lat,used_lon,offset_m,dest_m,steps,step_len_m,cadence_spm,checkpoint_m",
        )
    }

    /** [sample]은 위치 콜백이 끊겨 걸음 수로 판정한 줄이면 null. */
    fun log(timeMillis: Long, sample: LocationSample?, u: CoachUpdate, next: UpcomingTurn?) {
        out.println(
            listOf(
                clock.format(Date(timeMillis)),
                "%.1f".format(Locale.US, (timeMillis - startedAtMillis) / 1000.0),
                sample?.let { "%.7f".format(Locale.US, it.point.lat) }.orEmpty(),
                sample?.let { "%.7f".format(Locale.US, it.point.lon) }.orEmpty(),
                sample?.accuracyM?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                sample?.speedMps?.let { "%.2f".format(Locale.US, it) }.orEmpty(),
                "%.2f".format(Locale.US, u.movingSpeedMps),
                "%.1f".format(Locale.US, u.remainingM),
                "%.1f".format(Locale.US, u.remainingTimeSec),
                if (u.requiredSpeedMps.isFinite()) "%.2f".format(Locale.US, u.requiredSpeedMps) else "",
                u.observedState.name,
                u.state.name,
                u.stateChanged,
                u.offRoute,
                next?.let { it.turn?.label ?: "도착" }.orEmpty(),
                next?.let { "%.1f".format(Locale.US, it.distanceM) }.orEmpty(),
                u.source.name,
                u.rejected?.name.orEmpty(),
                u.position?.let { "%.7f".format(Locale.US, it.lat) }.orEmpty(),
                u.position?.let { "%.7f".format(Locale.US, it.lon) }.orEmpty(),
                u.offsetM?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                u.destinationDistanceM?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
                u.steps,
                "%.2f".format(Locale.US, u.stepLengthM),
                u.cadenceSpm?.let { "%.0f".format(Locale.US, it) }.orEmpty(),
                u.checkpointRemainingM?.let { "%.1f".format(Locale.US, it) }.orEmpty(),
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

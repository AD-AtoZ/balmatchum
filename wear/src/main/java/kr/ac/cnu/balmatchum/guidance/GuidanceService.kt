package kr.ac.cnu.balmatchum.guidance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.ac.cnu.balmatchum.core.ArrivalCoach
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.LocationSample
import kr.ac.cnu.balmatchum.core.TurnAlertTracker
import kr.ac.cnu.balmatchum.core.TurnGuide
import kr.ac.cnu.balmatchum.haptics.Haptics
import kr.ac.cnu.balmatchum.location.FusedLocationSource
import kr.ac.cnu.balmatchum.location.LocationSource
import kr.ac.cnu.balmatchum.location.ReplayLocationSource
import kr.ac.cnu.balmatchum.location.StepSource
import kr.ac.cnu.balmatchum.log.CsvLogger
import kr.ac.cnu.balmatchum.presentation.MainActivity

/**
 * 안내 중에 화면이 꺼져도 계속 위치를 받고 판단·진동·기록을 하는 포그라운드 서비스.
 *
 * 위치 표본으로 도착·갈림길 접근·상태 변경을 판단하고 진동·화면·CSV 기록에 반영한다.
 * 목적지 반경에 들어오면 스스로 끝내고 결과를 남긴다.
 *
 * 실제 위치로 안내할 때는 걸음 센서도 켠다. 위치 콜백이 [NO_GPS_MILLIS] 넘게 오지 않으면
 * 1초마다 [ArrivalCoach.updateWithoutGps]로 걸음 수 기반 판정을 이어 간다(T22).
 */
class GuidanceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var source: LocationSource? = null
    private var steps: StepSource? = null
    private var noGpsTicker: Job? = null

    /** 마지막 위치 콜백을 받은 워치 시각. 위치가 끊겼는지 볼 때 쓴다. */
    private var lastSampleAt: Long? = null
    private var logger: CsvLogger? = null
    private var coach: ArrivalCoach? = null
    private var turnGuide: TurnGuide? = null
    private var turnAlerts = TurnAlertTracker()
    private var firstRemaining: Double? = null
    private var lastRemaining: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(arrived = false)
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        val request = pendingRequest ?: return stopSelf()
        pendingRequest = null
        startInForeground(request.destination.name)

        val startedAt = System.currentTimeMillis()
        val route = request.destination.tmap.route
        coach = ArrivalCoach(route, request.deadlineMillis, request.speeds)
        turnGuide = TurnGuide(request.destination.tmap.turns, route.totalDistance)
        turnAlerts = TurnAlertTracker()
        logger = CsvLogger(this, request.destination.name, startedAt)
        val haptics = Haptics(this)
        GuidanceSession.set(GuidanceState.Running(request, startedAt, latest = null))

        lastSampleAt = null
        source = request.simulatedSpeedMps?.let { ReplayLocationSource(route, it, scope) } ?: FusedLocationSource(this)
        source?.start { sample -> onSample(sample, request, startedAt, haptics) }
        if (request.simulatedSpeedMps == null) {
            steps = StepSource(this).also { s -> s.start { n -> coach?.onStep(n) } }
            noGpsTicker = scope.launch {
                while (isActive) {
                    delay(1_000)
                    val at = lastSampleAt ?: continue
                    val now = System.currentTimeMillis()
                    if (now - at < NO_GPS_MILLIS) continue
                    val update = coach?.updateWithoutGps(now) ?: continue
                    handle(now, null, update, request, startedAt, haptics)
                }
            }
        }
    }

    private fun onSample(sample: LocationSample, request: GuidanceRequest, startedAt: Long, haptics: Haptics) {
        lastSampleAt = System.currentTimeMillis()
        val update = coach?.update(sample) ?: return
        handle(sample.timeMillis, sample, update, request, startedAt, haptics)
    }

    /** [sample]은 위치 콜백이 끊겨 걸음 수로 판정한 경우 null. */
    private fun handle(
        timeMillis: Long,
        sample: LocationSample?,
        update: CoachUpdate,
        request: GuidanceRequest,
        startedAt: Long,
        haptics: Haptics,
    ) {
        if (coach == null) return
        // 경로를 따라 온 거리 = 경로 전체 길이 - 남은 거리.
        val nextTurn = coach?.route?.let { turnGuide?.next(it.totalDistance - update.remainingM) }
        logger?.log(timeMillis, sample, update, nextTurn)
        if (firstRemaining == null) firstRemaining = update.remainingM
        lastRemaining = update.remainingM
        GuidanceSession.set(GuidanceState.Running(request, startedAt, update, nextTurn))
        if (update.arrived) {
            haptics.playArrived()
            finish(arrived = true)
        } else if (turnAlerts.shouldAlert(nextTurn)) {
            // 같은 표본에서 상태 변경 진동이 갈림길 진동을 덮어쓰지 않게 우선한다.
            haptics.playTurn()
        } else if (update.stateChanged) {
            haptics.play(update.state)
        }
    }

    private fun finish(arrived: Boolean) {
        source?.stop()
        source = null
        steps?.stop()
        steps = null
        noGpsTicker?.cancel()
        noGpsTicker = null
        coach = null
        logger?.close()
        val running = GuidanceSession.state.value as? GuidanceState.Running
        if (running != null) {
            val endedAt = System.currentTimeMillis()
            val walked = (firstRemaining ?: 0.0) - (lastRemaining ?: 0.0)
            GuidanceSession.set(
                GuidanceState.Finished(
                    GuidanceResult(
                        destinationName = running.request.destination.name,
                        startedAtMillis = running.startedAtMillis,
                        endedAtMillis = endedAt,
                        deadlineMillis = running.request.deadlineMillis,
                        arrived = arrived,
                        averageSpeedMps = walked / ((endedAt - running.startedAtMillis) / 1000.0).coerceAtLeast(1.0),
                        csvPath = logger?.file?.absolutePath,
                    ),
                ),
            )
        }
        logger = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground(destinationName: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "길 안내", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("발맞춤 안내 중")
            .setContentText(destinationName)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    override fun onDestroy() {
        source?.stop()
        steps?.stop()
        logger?.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "guidance"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "kr.ac.cnu.balmatchum.STOP"

        /** 위치 콜백이 이 시간(ms) 넘게 없으면 걸음 수로 판정을 이어 간다. */
        private const val NO_GPS_MILLIS = 3_000L

        /** 같은 프로세스 안에서 넘기므로 Intent에 담지 않고 여기에 잠깐 둔다. */
        private var pendingRequest: GuidanceRequest? = null

        fun start(context: Context, request: GuidanceRequest) {
            pendingRequest = request
            ContextCompat.startForegroundService(context, Intent(context, GuidanceService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GuidanceService::class.java).setAction(ACTION_STOP))
        }
    }
}

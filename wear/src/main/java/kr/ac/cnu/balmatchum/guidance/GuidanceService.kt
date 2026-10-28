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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kr.ac.cnu.balmatchum.core.ArrivalCoach
import kr.ac.cnu.balmatchum.core.LocationSample
import kr.ac.cnu.balmatchum.haptics.Haptics
import kr.ac.cnu.balmatchum.location.FusedLocationSource
import kr.ac.cnu.balmatchum.location.LocationSource
import kr.ac.cnu.balmatchum.location.ReplayLocationSource
import kr.ac.cnu.balmatchum.log.CsvLogger
import kr.ac.cnu.balmatchum.presentation.MainActivity

/**
 * 안내 중에 화면이 꺼져도 계속 위치를 받고 판단·진동·기록을 하는 포그라운드 서비스.
 *
 * 흐름: 위치 표본 → [ArrivalCoach] 판단 → 상태가 바뀌면 진동 → [GuidanceSession]에 최신 값 → CSV 한 줄.
 * 목적지 반경에 들어오면 스스로 끝내고 결과를 남긴다.
 */
class GuidanceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var source: LocationSource? = null
    private var logger: CsvLogger? = null
    private var coach: ArrivalCoach? = null
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
        logger = CsvLogger(this, request.destination.name, startedAt)
        val haptics = Haptics(this)
        GuidanceSession.set(GuidanceState.Running(request, startedAt, latest = null))

        source = request.simulatedSpeedMps?.let { ReplayLocationSource(route, it, scope) } ?: FusedLocationSource(this)
        source?.start { sample -> onSample(sample, request, startedAt, haptics) }
    }

    private fun onSample(sample: LocationSample, request: GuidanceRequest, startedAt: Long, haptics: Haptics) {
        val update = coach?.update(sample) ?: return
        logger?.log(sample, update)
        if (firstRemaining == null) firstRemaining = update.remainingM
        lastRemaining = update.remainingM
        GuidanceSession.set(GuidanceState.Running(request, startedAt, update))
        if (update.arrived) {
            haptics.playArrived()
            finish(arrived = true)
        } else if (update.stateChanged) {
            haptics.play(update.state)
        }
    }

    private fun finish(arrived: Boolean) {
        source?.stop()
        source = null
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
        logger?.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "guidance"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "kr.ac.cnu.balmatchum.STOP"

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

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
import kotlinx.coroutines.launch
import kr.ac.cnu.balmatchum.core.ArrivalCoach
import kr.ac.cnu.balmatchum.core.ArrivalState
import kr.ac.cnu.balmatchum.core.CoachConfig
import kr.ac.cnu.balmatchum.core.ConfirmRepeatPolicy
import kr.ac.cnu.balmatchum.core.LocationSample
import kr.ac.cnu.balmatchum.core.TurnGuide
import kr.ac.cnu.balmatchum.haptics.Haptics
import kr.ac.cnu.balmatchum.location.FusedLocationSource
import kr.ac.cnu.balmatchum.location.LocationSource
import kr.ac.cnu.balmatchum.location.ReplayLocationSource
import kr.ac.cnu.balmatchum.log.CsvLogger
import kr.ac.cnu.balmatchum.presentation.MainActivity
import java.util.Locale

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
    private var turnGuide: TurnGuide? = null
    private var firstRemaining: Double? = null
    private var lastRemaining: Double? = null

    // 확인 진동 모드 (기본 모드에서는 policy가 null이라 아래 값을 쓰지 않는다).
    private var haptics: Haptics? = null
    private var policy: ConfirmRepeatPolicy? = null
    private var repeatJob: Job? = null
    private var firstVibrateAt: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(arrived = false)
            ACTION_ACK -> acknowledge()
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
        logger = CsvLogger(this, request.destination.name, startedAt)
        haptics = Haptics(this)
        policy = if (request.hapticMode == HapticMode.CONFIRM) {
            val config = CoachConfig()
            ConfirmRepeatPolicy(config.confirmRepeatIntervalMs, config.confirmRepeatMax)
        } else {
            null
        }
        GuidanceSession.set(GuidanceState.Running(request, startedAt, latest = null))

        source = request.simulatedSpeedMps?.let { ReplayLocationSource(route, it, scope) } ?: FusedLocationSource(this)
        source?.start { sample -> onSample(sample, request, startedAt) }
    }

    private fun onSample(sample: LocationSample, request: GuidanceRequest, startedAt: Long) {
        val update = coach?.update(sample) ?: return
        // 경로를 따라 온 거리 = 경로 전체 길이 - 남은 거리.
        val nextTurn = coach?.route?.let { turnGuide?.next(it.totalDistance - update.remainingM) }
        logger?.log(sample, update, nextTurn)
        if (firstRemaining == null) firstRemaining = update.remainingM
        lastRemaining = update.remainingM
        if (update.arrived) {
            stopRepeat()
            GuidanceSession.set(GuidanceState.Running(request, startedAt, update, nextTurn))
            haptics?.playArrived()
            finish(arrived = true)
            return
        }
        if (update.stateChanged) {
            val p = policy
            if (p == null) {
                haptics?.play(update.state) // 기본 모드: 지금까지와 똑같이 한 번만
            } else {
                startConfirmCycle(p, update.state)
            }
        }
        GuidanceSession.set(GuidanceState.Running(request, startedAt, update, nextTurn, awaitingAck = policy?.isPending == true))
    }

    /** 새 상태 진입: 이전 반복을 끊고, 처음 진동을 울린 뒤 간격마다 반복한다. */
    private fun startConfirmCycle(p: ConfirmRepeatPolicy, state: ArrivalState) {
        repeatJob?.cancel()
        haptics?.cancel()
        if (!p.onStateChanged(state)) return // 여유: 진동 없음
        firstVibrateAt = System.currentTimeMillis()
        vibrateAndLog(state, p.playedCount)
        repeatJob = scope.launch {
            while (true) {
                delay(p.intervalMs)
                if (!p.nextRepeat()) break
                vibrateAndLog(state, p.playedCount)
            }
            // 최대 횟수까지 울렸는데 탭이 없었다.
            if (p.expire()) {
                logger?.logEvent(System.currentTimeMillis(), "NO_ACK", "${state.name} after #${p.playedCount}")
                publishAwaiting()
            }
        }
    }

    private fun vibrateAndLog(state: ArrivalState, count: Int) {
        haptics?.playStrong(state)
        logger?.logEvent(System.currentTimeMillis(), "VIBRATE", "${state.name} #$count")
    }

    /** 화면 탭 = 확인. 반복을 멈추고 확인까지 걸린 시간과 울린 횟수를 남긴다. */
    private fun acknowledge() {
        val p = policy ?: return
        val state = p.state
        val count = p.playedCount
        if (!p.acknowledge()) return
        repeatJob?.cancel()
        repeatJob = null
        haptics?.cancel()
        val now = System.currentTimeMillis()
        logger?.logEvent(now, "ACK", "${state?.name} after #$count in %.1fs".format(Locale.US, (now - firstVibrateAt) / 1000.0))
        publishAwaiting()
    }

    private fun publishAwaiting() {
        val running = GuidanceSession.state.value as? GuidanceState.Running ?: return
        GuidanceSession.set(running.copy(awaitingAck = policy?.isPending == true))
    }

    private fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
        policy?.reset()
        haptics?.cancel()
    }

    private fun finish(arrived: Boolean) {
        stopRepeat()
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
        stopRepeat()
        source?.stop()
        logger?.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "guidance"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "kr.ac.cnu.balmatchum.STOP"
        private const val ACTION_ACK = "kr.ac.cnu.balmatchum.ACK"

        /** 같은 프로세스 안에서 넘기므로 Intent에 담지 않고 여기에 잠깐 둔다. */
        private var pendingRequest: GuidanceRequest? = null

        fun start(context: Context, request: GuidanceRequest) {
            pendingRequest = request
            ContextCompat.startForegroundService(context, Intent(context, GuidanceService::class.java))
        }

        /** 안내 화면을 탭하면 부른다(확인 진동 모드). */
        fun acknowledge(context: Context) {
            context.startService(Intent(context, GuidanceService::class.java).setAction(ACTION_ACK))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, GuidanceService::class.java).setAction(ACTION_STOP))
        }
    }
}

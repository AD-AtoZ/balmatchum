package kr.ac.cnu.balmatchum

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kr.ac.cnu.balmatchum.core.WallMap
import java.util.Locale

/** 지금 켜져 있는 카메라 추적. 앱이 뒤로 가거나 돌아올 때 MainActivity가 멈추고 다시 켠다. */
object IndoorTracking {
    @Volatile
    var active: IndoorTracker? = null
}

/**
 * 실내 시연 화면. 휴대폰 카메라로 본 주변을 w5 복도 스캔 지도와 비교해 위치를 찾고, 워치로 보낸다.
 *
 * 쓰는 순서
 * 1. 출발선 근처에서 "출발 위치 찾기"를 누르고 제자리에서 천천히 한 바퀴 돈다
 * 2. 지도에서 위치를 찾으면 그때부터 워치로 위치를 보낸다
 * 3. 워치에서 "실내 시연 · 카메라"를 눌러 출발한다
 */
@Composable
fun IndoorTrackingScreen(modifier: Modifier = Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val watchLink = remember { WatchLink(context) }
    val wallMap = remember {
        runCatching { WallMap.parse(context.assets.open(WALL_MAP).use { it.readBytes() }) }.getOrNull()
    }

    var hasCamera by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(TrackerUpdate(tracking = false, pose = null, canAlign = false)) }
    var watches by remember { mutableStateOf(0) }
    var refineOn by remember { mutableStateOf(true) }

    val tracker = remember {
        var lastShownAt = 0L
        var lastSentAt = 0L
        IndoorTracker(activity, wallMap) { update ->
            // 화면을 그리는 스레드에서 1초에 수십 번 불린다. 화면 갱신과 워치 전송은 간격을 두고 한다.
            val now = System.currentTimeMillis()
            if (now - lastShownAt >= SHOW_INTERVAL_MILLIS) {
                lastShownAt = now
                state = update
                watches = watchLink.poseWatchCount
            }
            val current = update.pose
            if (current != null && now - lastSentAt >= SEND_INTERVAL_MILLIS) {
                lastSentAt = now
                watchLink.sendIndoorPose(current)
            }
        }
    }

    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (!granted) error = "카메라 권한이 있어야 실내에서 위치를 추적할 수 있습니다"
    }
    LaunchedEffect(Unit) {
        if (!hasCamera) askCamera.launch(Manifest.permission.CAMERA)
    }

    // 카메라 권한을 받은 뒤에 추적을 시작하고, 화면을 떠나면 카메라를 놓아준다.
    DisposableEffect(hasCamera) {
        if (hasCamera) {
            error = tracker.resume()
            IndoorTracking.active = tracker
        }
        onDispose {
            IndoorTracking.active = null
            tracker.pause()
            tracker.close()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("실내 시연 · 카메라로 위치 찾기", style = MaterialTheme.typography.titleLarge)
        Text(
            if (wallMap == null) {
                "복도 지도를 읽지 못했습니다. \"여기가 출발선\"으로 출발선을 손으로 정해 주세요."
            } else {
                "출발선 근처에서 \"출발 위치 찾기\"를 누르고 제자리에서 천천히 한 바퀴 도세요. 카메라가 본 주변을 복도 스캔 지도와 비교해 위치를 찾습니다."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (hasCamera) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext -> GLSurfaceView(viewContext).also { tracker.attach(it) } },
                )
                if (wallMap != null) {
                    IndoorMiniMap(
                        map = wallMap,
                        pose = state.pose,
                        seenPoints = state.seenPoints,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(width = 150.dp, height = 220.dp),
                    )
                }
            }
        } else {
            Text("카메라 권한을 기다리는 중입니다.", modifier = Modifier.weight(1f))
        }

        Text(statusText(error, hasCamera, state, watches), style = MaterialTheme.typography.titleMedium)
        Text(poseText(state), style = MaterialTheme.typography.bodyLarge)
        matchText(state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.step) {
                LocateStep.SCANNING -> Button(enabled = state.pointCount >= 120, onClick = { tracker.searchNow() }) {
                    Text("지금 찾기")
                }
                LocateStep.UNSURE -> {
                    Button(onClick = { tracker.acceptPending() }) { Text("이 위치로 시작") }
                    OutlinedButton(onClick = { tracker.startScan() }) { Text("다시 돌아보기") }
                }
                else -> Button(enabled = wallMap != null && state.tracking, onClick = { tracker.startScan() }) {
                    Text(if (state.step == LocateStep.LOCATED) "위치 다시 찾기" else "출발 위치 찾기")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(enabled = state.tracking && state.canAlign, onClick = { tracker.markStart() }) {
                Text("여기가 출발선")
            }
            Text("지도 보정")
            Switch(
                checked = refineOn,
                enabled = wallMap != null,
                onCheckedChange = {
                    refineOn = it
                    tracker.refineEnabled = it
                },
            )
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
    }
}

private fun statusText(error: String?, hasCamera: Boolean, s: TrackerUpdate, watches: Int): String = when {
    error != null -> error
    !hasCamera -> "카메라 권한 필요"
    !s.tracking -> "주변을 인식하는 중… 휴대폰을 천천히 좌우로 움직여 주세요"
    s.step == LocateStep.SCANNING -> "한 바퀴 도는 중: ${s.scanDegrees}° / 360° · 점 ${s.pointCount}개"
    s.step == LocateStep.SEARCHING -> "지도에서 위치를 찾는 중…"
    s.step == LocateStep.UNSURE -> "위치가 확실하지 않습니다. 다른 곳과 비슷하게 보입니다"
    s.pose == null -> "\"출발 위치 찾기\"를 누르세요"
    else -> "워치로 보내는 중 (연결된 워치 ${watches}대)"
}

private fun poseText(s: TrackerUpdate): String {
    val p = s.pose ?: return "앞으로 — · 옆으로 — · 방향 —"
    return String.format(Locale.US, "출발선에서 앞으로 %.1fm · 옆으로 %+.1fm · 방향 %+.0f°", p.forwardM, p.lateralM, p.headingDeg)
}

private fun matchText(s: TrackerUpdate): String? {
    val m = s.lastMatch ?: return null
    val ago = if (s.lastMatchAt > 0) (System.currentTimeMillis() - s.lastMatchAt) / 1000.0 else null
    val base = String.format(Locale.US, "지도 일치 %.0f%% (점 %d개)", m.score * 100, m.points)
    val other = if (s.step == LocateStep.UNSURE) String.format(Locale.US, " · 다른 후보 %.0f%%", m.runnerUpScore * 100) else ""
    val since = if (ago != null && s.step == LocateStep.LOCATED) String.format(Locale.US, " · %.0f초 전", ago) else ""
    return base + other + since
}

/** 휴대폰 앱에 넣은 w5 복도 벽 지도(`tools/build_w5_wallmap.py`로 만듦). */
private const val WALL_MAP = "w5_wallmap.bin"

private const val SHOW_INTERVAL_MILLIS = 200L

/** 워치로 위치를 보내는 간격. 1초에 두 번. */
private const val SEND_INTERVAL_MILLIS = 500L

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
 * 1. 출발점(기본: 시연 출발선, 미니맵 초록 선)에 서서 휴대폰으로 복도 앞쪽을 비추고 "출발점에서 시작"을 누른다
 * 2. 그때부터 카메라 이동량으로 위치를 따라가며 1초마다 스캔 지도와 비교해 바로잡고, 워치로 위치를 보낸다
 * 3. 워치에서 "실내 시연 · 카메라"를 눌러 출발한다
 *
 * 출발점은 "시작점 바꾸기"로 미니맵에서 골라 바꿀 수 있다. 한 바퀴 돌며 찾기는 실험 기능으로 남겨 둔다.
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
    var start by remember { mutableStateOf(0.0 to 0.0) }
    var picking by remember { mutableStateOf(false) }

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
            if (current != null && update.step == LocateStep.LOCATED && now - lastSentAt >= SEND_INTERVAL_MILLIS) {
                lastSentAt = now
                // 워치는 출발점에서 앞으로 간 거리로 남은 거리를 계산한다.
                watchLink.sendIndoorPose(current.copy(forwardM = current.forwardM - update.startF))
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
                "복도 지도를 읽지 못했습니다. 출발선에 서서 \"출발점에서 시작\"을 누르면 카메라 이동량만으로 따라갑니다."
            } else {
                "① 미니맵의 초록 점(출발점)에 서서 휴대폰을 가슴 높이로 세워 들고 복도 앞쪽을 비춥니다. ② \"출발점에서 시작\"을 누릅니다. " +
                    "③ 걸으면 카메라 이동량으로 따라가면서 1초마다 스캔 지도와 비교해 바로잡습니다. 미니맵의 노란 점이 벽 위에 줄지어 있으면 잘 맞는 것입니다."
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
                    val shownStart = if (state.step == LocateStep.LOCATED && !picking) state.startF to state.startL else start
                    IndoorMiniMap(
                        map = wallMap,
                        pose = if (picking) null else state.pose,
                        seenPoints = if (picking) null else state.seenPoints,
                        trail = if (picking) null else state.trail,
                        startF = shownStart.first,
                        startL = shownStart.second,
                        onPick = if (!picking) null else { f, l ->
                            wallMap.nearestWalkable(f, l, 3.0)?.let {
                                start = it
                                picking = false
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(width = if (picking) 220.dp else 170.dp, height = if (picking) 330.dp else 250.dp),
                    )
                }
            }
        } else {
            Text("카메라 권한을 기다리는 중입니다.", modifier = Modifier.weight(1f))
        }

        Text(statusText(error, hasCamera, state, watches, picking), style = MaterialTheme.typography.titleMedium)
        Text(poseText(state), style = MaterialTheme.typography.bodyLarge)
        matchText(state)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        when (state.step) {
            LocateStep.SCANNING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = state.pointCount >= 120, onClick = { tracker.searchNow() }) { Text("지금 찾기") }
                OutlinedButton(onClick = { tracker.startHere(start.first, start.second) }) { Text("그만하고 출발점에서 시작") }
            }
            LocateStep.UNSURE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { tracker.acceptPending() }) { Text("이 위치로 시작") }
                OutlinedButton(onClick = { tracker.startScan() }) { Text("다시 돌아보기") }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = state.tracking && state.canAlign && !picking,
                    onClick = { tracker.startHere(start.first, start.second) },
                ) {
                    Text(if (state.step == LocateStep.LOCATED) "출발점에서 다시 시작" else "출발점에서 시작")
                }
                OutlinedButton(enabled = wallMap != null, onClick = { picking = !picking }) {
                    Text(if (picking) "고르기 취소" else "시작점 바꾸기")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("지도 보정")
            Switch(
                checked = refineOn,
                enabled = wallMap != null,
                onCheckedChange = {
                    refineOn = it
                    tracker.refineEnabled = it
                },
            )
            OutlinedButton(enabled = wallMap != null && state.tracking, onClick = { tracker.startScan() }) {
                Text("자동 찾기(실험)")
            }
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
    }
}

private fun statusText(error: String?, hasCamera: Boolean, s: TrackerUpdate, watches: Int, picking: Boolean): String = when {
    error != null -> error
    !hasCamera -> "카메라 권한 필요"
    picking -> "미니맵에서 출발점으로 쓸 곳을 누르세요 (가까운 복도 안으로 맞춰집니다)"
    !s.tracking -> "주변을 인식하는 중… 휴대폰을 천천히 좌우로 움직여 주세요"
    s.step == LocateStep.SCANNING && s.lastSearchFailed ->
        "지도와 잘 맞는 자리를 못 찾았습니다. 한 바퀴 더 돌거나, 출발점에서 시작하세요 (${s.scanDegrees}° / 360°)"
    s.step == LocateStep.SCANNING ->
        "(실험) 제자리에서 천천히 한 바퀴 도세요: ${s.scanDegrees}° / 360° · 점 ${s.pointCount}개"
    s.step == LocateStep.SEARCHING -> "지도에서 위치를 찾는 중…"
    s.step == LocateStep.UNSURE -> "위치가 확실하지 않습니다. 다른 곳과 비슷하게 보입니다"
    s.pose == null && !s.canAlign -> "휴대폰을 세워 복도 앞쪽을 비춰 주세요"
    s.pose == null -> "출발점에 서서 \"출발점에서 시작\"을 누르세요"
    else -> "워치로 보내는 중 (연결된 워치 ${watches}대)"
}

private fun poseText(s: TrackerUpdate): String {
    val p = s.pose ?: return "앞으로 — · 옆으로 — · 방향 —"
    return String.format(
        Locale.US, "출발점에서 앞으로 %.1fm · 옆으로 %+.1fm · 방향 %+.0f°",
        p.forwardM - s.startF, p.lateralM, p.headingDeg,
    )
}

private fun matchText(s: TrackerUpdate): String? {
    val m = s.lastMatch
    val pts = s.seenPoints
    val yellow = pts?.let { a -> (0 until a.size / 3).count { a[3 * it + 2] < 0.25f } }
    val parts = ArrayList<String>()
    if (m != null) parts += String.format(Locale.US, "지도 일치 %.0f%%", m.score * 100)
    if (pts != null) parts += "노란 점 $yellow / ${pts.size / 3}개"
    if (s.step == LocateStep.UNSURE && m != null) parts += String.format(Locale.US, "다른 후보 %.0f%%", m.runnerUpScore * 100)
    if (s.step == LocateStep.LOCATED) {
        val ago = if (s.lastMatchAt > 0) (System.currentTimeMillis() - s.lastMatchAt) / 1000 else null
        parts += if (ago == null) "아직 바로잡기 전(카메라 이동량만)" else if (ago > 5) "${ago}초째 못 맞춤(카메라 이동량만)" else "${ago}초 전 바로잡음"
        if (s.snaps > 0) parts += "벽 밖으로 나가 당겨 옴 ${s.snaps}번"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** 휴대폰 앱에 넣은 w5 복도 벽 지도(`tools/build_w5_wallmap.py`로 만듦). */
private const val WALL_MAP = "w5_wallmap.bin"

private const val SHOW_INTERVAL_MILLIS = 200L

/** 워치로 위치를 보내는 간격. 1초에 두 번. */
private const val SEND_INTERVAL_MILLIS = 500L

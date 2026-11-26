package kr.ac.cnu.balmatchum

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kr.ac.cnu.balmatchum.core.IndoorPose
import java.util.Locale

/** 지금 켜져 있는 카메라 추적. 앱이 뒤로 가거나 돌아올 때 MainActivity가 멈추고 다시 켠다. */
object IndoorTracking {
    @Volatile
    var active: IndoorTracker? = null
}

/**
 * 실내 시연 화면. 휴대폰 카메라로 이동을 추적해 워치로 위치를 보낸다.
 *
 * 쓰는 순서
 * 1. 출발선에 서서 휴대폰 카메라가 복도 앞쪽을 보게 든다(가슴에 고정해도 된다)
 * 2. "여기가 출발선"을 누른다. 이때의 위치와 방향이 기준이 된다
 * 3. 워치에서 "실내 시연 · 카메라"를 눌러 출발한다
 */
@Composable
fun IndoorTrackingScreen(modifier: Modifier = Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val watchLink = remember { WatchLink(context) }

    var hasCamera by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var error by remember { mutableStateOf<String?>(null) }
    var tracking by remember { mutableStateOf(false) }
    var canAlign by remember { mutableStateOf(false) }
    var pose by remember { mutableStateOf<IndoorPose?>(null) }
    var watches by remember { mutableStateOf(0) }

    val tracker = remember {
        var lastShownAt = 0L
        var lastSentAt = 0L
        IndoorTracker(activity) { update ->
            // 화면을 그리는 스레드에서 1초에 수십 번 불린다. 화면 갱신과 워치 전송은 간격을 두고 한다.
            val now = System.currentTimeMillis()
            if (now - lastShownAt >= SHOW_INTERVAL_MILLIS) {
                lastShownAt = now
                tracking = update.tracking
                canAlign = update.canAlign
                pose = update.pose
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

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("실내 시연 · 카메라 추적", style = MaterialTheme.typography.titleLarge)
        Text(
            "실내에서는 GPS 대신 카메라로 이동을 추적합니다. 출발선에서 복도 앞쪽을 비추고 \"여기가 출발선\"을 누른 뒤, " +
                "워치에서 \"실내 시연 · 카메라\"를 누르세요.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (hasCamera) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { viewContext -> GLSurfaceView(viewContext).also { tracker.attach(it) } },
            )
        } else {
            Text("카메라 권한을 기다리는 중입니다.", modifier = Modifier.weight(1f))
        }

        val status = when {
            error != null -> error.orEmpty()
            !hasCamera -> "카메라 권한 필요"
            !tracking -> "주변을 인식하는 중… 휴대폰을 천천히 좌우로 움직여 주세요"
            pose == null && !canAlign -> "휴대폰을 세워 카메라가 앞을 보게 해 주세요"
            pose == null -> "추적 중. 출발선에 서서 \"여기가 출발선\"을 누르세요"
            else -> "워치로 보내는 중 (연결된 워치 ${watches}대)"
        }
        Text(status, style = MaterialTheme.typography.titleMedium)

        val current = pose
        Text(
            if (current == null) {
                "앞으로 — · 옆으로 — · 방향 —"
            } else {
                String.format(
                    Locale.US, "앞으로 %.1fm · 옆으로 %+.1fm · 방향 %+.0f°",
                    current.forwardM, current.lateralM, current.headingDeg,
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = tracking && canAlign, onClick = { tracker.markStart() }) {
                Text(if (pose == null) "여기가 출발선" else "출발선 다시 잡기")
            }
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
    }
}

private const val SHOW_INTERVAL_MILLIS = 200L

/** 워치로 위치를 보내는 간격. 1초에 두 번. */
private const val SEND_INTERVAL_MILLIS = 500L

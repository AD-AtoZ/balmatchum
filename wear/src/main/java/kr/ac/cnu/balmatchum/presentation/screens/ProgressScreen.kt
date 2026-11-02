package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.core.ArrivalState
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme
import kr.ac.cnu.balmatchum.presentation.theme.color

/**
 * 안내 중 화면 (TODOLIST T5). 사용자가 손목을 들어 볼 때만 보는 화면이라 한눈에 상태가 보여야 한다.
 * [update]가 null이면 아직 첫 위치를 받기 전이다.
 */
@Composable
fun ProgressScreen(
    destinationName: String,
    update: CoachUpdate?,
    speeds: PersonalSpeeds,
    onStop: () -> Unit,
) {
    val state = update?.state
    // 노란 배경에서는 검은 글자로 대비를 확보한다.
    val contentColor = if (state == ArrivalState.HURRY) Color.Black else Color.White
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(state?.color() ?: Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        if (update == null) {
            Text("위치를 찾는 중…", color = contentColor, textAlign = TextAlign.Center)
            Text(destinationName, color = contentColor, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            ProgressStateLabel(update.state, contentColor)
            if (update.state == ArrivalState.TOO_LATE) {
                Text("뛰어도 약 ${Format.duration(update.lateAtRunSec)} 지각", color = contentColor, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("남은 시간", color = contentColor, fontSize = 12.sp)
                Text(
                    if (update.remainingTimeSec <= 0) "목표 시각 지남" else Format.duration(update.remainingTimeSec),
                    color = contentColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Text("남은 거리 ${Format.distance(update.remainingM)}", color = contentColor, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SpeedReading("필요 속도", update.requiredSpeedMps, contentColor)
                SpeedReading("지금 속도", update.movingSpeedMps, contentColor)
            }
            Text(
                "내 보통 걸음 %.1f m/s".format(speeds.walkMps),
                color = contentColor,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onStop, modifier = Modifier.padding(top = 6.dp)) { Text("안내 끝내기", fontSize = 12.sp) }
    }
}

/** 글자는 계속 읽을 수 있게 유지하고, 뛰기 상태에서만 천천히 밝기를 바꾼다. */
@Composable
private fun ProgressStateLabel(state: ArrivalState, color: Color) {
    val alpha = if (state == ArrivalState.RUN && !LocalInspectionMode.current) {
        val transition = rememberInfiniteTransition(label = "run emphasis")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "state brightness",
        )
        pulse
    } else {
        1f
    }
    Text(
        state.label,
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        color = color,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SpeedReading(label: String, speedMps: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 12.sp)
        Text(
            if (speedMps.isFinite()) "%.1f".format(speedMps.coerceIn(0.0, 99.0)) else "—",
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text("m/s", color = color, fontSize = 10.sp)
    }
}

@WearPreviewDevices
@Composable
private fun ProgressLoadingPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", null, FakeData.speeds, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressRelaxedPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.RELAXED), FakeData.speeds, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressHurryPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.HURRY), FakeData.speeds, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressRunPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.RUN), FakeData.speeds, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressTooLatePreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.TOO_LATE), FakeData.speeds, onStop = {}) }
}

package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(state?.color() ?: Color.Black)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (update == null) {
            Text("위치를 찾는 중…", textAlign = TextAlign.Center)
            Text(destinationName, fontSize = 12.sp, textAlign = TextAlign.Center)
        } else {
            Text(update.state.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (update.state == ArrivalState.TOO_LATE) {
                Text("약 ${Format.duration(update.lateAtRunSec)} 늦을 수 있어요", fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Text("남은 시간 ${Format.duration(update.remainingTimeSec)}", fontSize = 13.sp)
            Text("남은 거리 ${Format.distance(update.remainingM)}", fontSize = 13.sp)
            Text(
                "필요 %.1f · 지금 %.1f m/s".format(update.requiredSpeedMps.coerceAtMost(99.0), update.movingSpeedMps),
                fontSize = 11.sp,
            )
            Text("내 보통 걸음 %.1f m/s".format(speeds.walkMps), fontSize = 11.sp)
        }
        Button(onClick = onStop, modifier = Modifier.padding(top = 6.dp)) { Text("안내 끝내기", fontSize = 12.sp) }
    }
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

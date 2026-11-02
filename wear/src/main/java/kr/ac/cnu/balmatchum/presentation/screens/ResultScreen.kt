package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.guidance.GuidanceResult
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme
import kotlin.math.abs
import kotlin.math.roundToLong

/** 도착 결과 화면 (TODOLIST T6). */
@Composable
fun ResultScreen(result: GuidanceResult, onDone: () -> Unit) {
    // 화면에 표시하는 초 단위로 비교해 "0초 일찍/늦게"를 피한다.
    val earlySec = result.earlySec.roundToLong()
    val summary = when {
        !result.arrived -> "도착 전\n안내를 끝냈어요"
        earlySec > 0 -> "${Format.duration(earlySec.toDouble())}\n일찍 도착"
        earlySec < 0 -> "${Format.duration(abs(earlySec).toDouble())}\n늦게 도착"
        else -> "목표 시각에\n도착했어요"
    }
    val summaryColor = when {
        !result.arrived -> MaterialTheme.colorScheme.onBackground
        earlySec < 0 -> Color(0xFFFFB4AB)
        else -> Color(0xFFA5D6A7)
    }
    ListScreen(title = if (result.arrived) "도착!" else "안내 종료") { spec ->
        item {
            Column(
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = summary,
                    color = summaryColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(text = result.destinationName, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        item {
            ResultReading(
                label = if (result.arrived) "도착 시각" else "종료 시각",
                value = Format.clock(result.endedAtMillis),
                modifier = Modifier.transformedHeight(this, spec),
            )
        }
        item {
            ResultReading("목표 시각", Format.clock(result.deadlineMillis), Modifier.transformedHeight(this, spec))
        }
        item {
            ResultReading(
                "걸린 시간",
                Format.duration((result.endedAtMillis - result.startedAtMillis) / 1000.0),
                Modifier.transformedHeight(this, spec),
            )
        }
        item {
            ResultReading("평균 속도", Format.speed(result.averageSpeedMps), Modifier.transformedHeight(this, spec))
        }
        buttonItem(spec, "처음으로", onDone)
    }
}

@Composable
private fun ResultReading(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@WearPreviewDevices
@Composable
private fun ResultEarlyPreview() {
    BalmatchumTheme { ResultScreen(FakeData.result, onDone = {}) }
}

@WearPreviewDevices
@Composable
private fun ResultLatePreview() {
    BalmatchumTheme {
        ResultScreen(FakeData.result.copy(deadlineMillis = FakeData.result.endedAtMillis - 125_000), onDone = {})
    }
}

@WearPreviewDevices
@Composable
private fun ResultOnTimePreview() {
    BalmatchumTheme {
        ResultScreen(FakeData.result.copy(deadlineMillis = FakeData.result.endedAtMillis), onDone = {})
    }
}

@WearPreviewDevices
@Composable
private fun ResultStoppedPreview() {
    BalmatchumTheme { ResultScreen(FakeData.result.copy(arrived = false), onDone = {}) }
}

@WearPreviewDevices
@Composable
private fun ResultStoppedImmediatelyPreview() {
    BalmatchumTheme {
        ResultScreen(
            FakeData.result.copy(arrived = false, endedAtMillis = FakeData.result.startedAtMillis, averageSpeedMps = 0.0),
            onDone = {},
        )
    }
}

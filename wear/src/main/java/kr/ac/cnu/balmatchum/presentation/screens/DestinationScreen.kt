package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme
import java.util.Locale

/** 목록에 보일 목적지 한 줄. */
data class DestinationItem(
    val name: String,
    val distanceM: Double,
    /** TMAP 예상 소요 시간(초). */
    val tmapSec: Double,
    /** 휴대폰이 보내 준 목적지면 true. */
    val fromPhone: Boolean,
)

/**
 * 첫 화면: 목적지 고르기 (TODOLIST T3).
 * 맨 위 "내 속도" 줄을 누르면 속도 측정 화면으로 간다.
 */
@Composable
fun DestinationScreen(
    items: List<DestinationItem>,
    speeds: PersonalSpeeds?,
    simulateLabel: String?,
    onCalibrate: () -> Unit,
    onCycleSimulate: () -> Unit,
    onPick: (index: Int) -> Unit,
) {
    ListScreen(title = "발맞춤") { spec ->
        item {
            Button(
                onClick = onCalibrate,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("내 속도", fontWeight = FontWeight.Bold)
                    if (speeds == null) {
                        Text("기본값 사용 중", fontSize = 12.sp)
                        Text("눌러서 내 속도 재기", fontSize = 12.sp)
                    } else {
                        Text("보통  ${speedValue(speeds.walkMps)}", fontSize = 12.sp)
                        Text("빠름  ${speedValue(speeds.briskMps)}", fontSize = 12.sp)
                        Text("뛰기  ${speedValue(speeds.runMps)}", fontSize = 12.sp)
                    }
                }
            }
        }
        textItem(spec, "목적지 선택")
        items.forEachIndexed { i, item ->
            item {
                Button(
                    onClick = { onPick(i) },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item.fromPhone) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (item.fromPhone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (item.fromPhone) "📱 휴대폰에서 받음" else "시연 경로",
                            fontSize = 12.sp,
                            fontWeight = if (item.fromPhone) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("거리  ${Format.distance(item.distanceM)}", fontSize = 12.sp)
                        Text("예상  약 ${Format.duration(item.tmapSec)}", fontSize = 12.sp)
                    }
                }
            }
        }
        if (items.isEmpty()) textItem(spec, "목적지가 없습니다\n휴대폰에서 목적지를 보내 주세요")
        if (simulateLabel != null) buttonItem(spec, simulateLabel, onCycleSimulate)
    }
}

private fun speedValue(mps: Double): String = "%.1f m/s".format(Locale.KOREA, mps)

@WearPreviewDevices
@Composable
private fun DestinationScreenPreview() {
    BalmatchumTheme {
        DestinationScreen(
            items = FakeData.destinations.map { (name, m, sec) -> DestinationItem(name, m, sec, fromPhone = false) },
            speeds = FakeData.speeds,
            simulateLabel = "가상 걷기: 끔",
            onCalibrate = {}, onCycleSimulate = {}, onPick = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun DestinationFromPhonePreview() {
    DestinationPreview(
        items = listOf(DestinationItem("충남대학교 공과대학 5호관", 1250.0, 960.0, fromPhone = true)) +
            FakeData.destinations.map { (name, m, sec) -> DestinationItem(name, m, sec, fromPhone = false) },
    )
}

@WearPreviewDevices
@Composable
private fun DestinationDefaultSpeedsPreview() {
    DestinationPreview(
        items = FakeData.destinations.map { (name, m, sec) -> DestinationItem(name, m, sec, fromPhone = false) },
        speeds = null,
    )
}

@WearPreviewDevices
@Composable
private fun DestinationEmptyPreview() {
    DestinationPreview(items = emptyList())
}

@Composable
private fun DestinationPreview(items: List<DestinationItem>, speeds: PersonalSpeeds? = FakeData.speeds) {
    BalmatchumTheme {
        DestinationScreen(
            items = items,
            speeds = speeds,
            simulateLabel = null,
            onCalibrate = {}, onCycleSimulate = {}, onPick = {},
        )
    }
}

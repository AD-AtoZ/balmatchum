package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme

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
        val speedText = speeds?.let { "내 속도  보통 %.1f · 빠름 %.1f · 뛰기 %.1f".format(it.walkMps, it.briskMps, it.runMps) }
            ?: "내 속도 재기 (아직 기본값)"
        buttonItem(spec, speedText, onCalibrate)
        items.forEachIndexed { i, item ->
            val prefix = if (item.fromPhone) "📱 " else ""
            buttonItem(spec, "$prefix${item.name}\n${Format.distance(item.distanceM)} · 약 ${Format.duration(item.tmapSec)}") { onPick(i) }
        }
        if (items.isEmpty()) textItem(spec, "목적지가 없습니다")
        if (simulateLabel != null) buttonItem(spec, simulateLabel, onCycleSimulate)
    }
}

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

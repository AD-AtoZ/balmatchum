package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.guidance.GuidanceResult
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme

/** 도착 결과 화면 (TODOLIST T6). */
@Composable
fun ResultScreen(result: GuidanceResult, onDone: () -> Unit) {
    ListScreen(title = if (result.arrived) "도착!" else "안내 종료") { spec ->
        textItem(spec, result.destinationName)
        textItem(spec, "도착 ${Format.clock(result.endedAtMillis)} · 목표 ${Format.clock(result.deadlineMillis)}")
        val early = result.earlySec
        textItem(spec, if (early >= 0) "목표보다 ${Format.duration(early)} 일찍" else "목표보다 ${Format.duration(-early)} 늦게")
        textItem(spec, "걸린 시간 ${Format.duration((result.endedAtMillis - result.startedAtMillis) / 1000.0)}")
        textItem(spec, "평균 속도 ${Format.speed(result.averageSpeedMps)}")
        buttonItem(spec, "처음으로", onDone)
    }
}

@WearPreviewDevices
@Composable
private fun ResultScreenPreview() {
    BalmatchumTheme { ResultScreen(FakeData.result, onDone = {}) }
}

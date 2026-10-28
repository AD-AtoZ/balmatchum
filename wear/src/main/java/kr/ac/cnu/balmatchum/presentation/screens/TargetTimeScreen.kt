package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme

/**
 * 도착 목표 시각 고르기 (TODOLIST T4). 지금은 ±1분 버튼으로만 바꾼다.
 * 기본값은 "지금 + TMAP 예상 시간 + 2분"이고 MainActivity 쪽에서 정해 넘겨준다.
 */
@Composable
fun TargetTimeScreen(
    destinationName: String,
    deadlineMillis: Long,
    onChange: (newDeadlineMillis: Long) -> Unit,
    onStart: () -> Unit,
) {
    ListScreen(title = "몇 시까지?") { spec ->
        textItem(spec, destinationName)
        textItem(spec, "도착 목표  ${Format.clock(deadlineMillis)}")
        buttonItem(spec, "+1분") { onChange(deadlineMillis + 60_000) }
        buttonItem(spec, "-1분") { onChange(deadlineMillis - 60_000) }
        buttonItem(spec, "출발", onStart)
    }
}

@WearPreviewDevices
@Composable
private fun TargetTimeScreenPreview() {
    BalmatchumTheme {
        TargetTimeScreen("공대 5호관 → 경영경제연구소", 1_761_000_320_000, onChange = {}, onStart = {})
    }
}

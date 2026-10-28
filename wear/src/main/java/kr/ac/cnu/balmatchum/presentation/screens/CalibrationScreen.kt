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

/**
 * 내 속도 측정 화면 (TODOLIST T1·T2).
 * 지금은 저장된 값을 보여 주고 기본값으로 되돌리는 것만 된다. 30초 측정과 저장은 T1·T2에서 만든다.
 * 저장은 MainActivity가 넘겨주는 SpeedStore.save(PersonalSpeeds)로 한다.
 */
@Composable
fun CalibrationScreen(
    saved: PersonalSpeeds?,
    onResetToDefault: () -> Unit,
    onDone: () -> Unit,
) {
    val shown = saved ?: PersonalSpeeds.DEFAULT
    ListScreen(title = "내 속도") { spec ->
        textItem(spec, if (saved == null) "아직 재지 않아 기본값을 씁니다" else "저장된 내 속도")
        textItem(spec, "보통 걸음  ${Format.speed(shown.walkMps)}")
        textItem(spec, "빠른 걸음  ${Format.speed(shown.briskMps)}")
        textItem(spec, "뛰기  ${Format.speed(shown.runMps)}")
        textItem(spec, "측정 기능 준비 중")
        if (saved != null) buttonItem(spec, "기본값으로 되돌리기", onResetToDefault)
        buttonItem(spec, "완료", onDone)
    }
}

@WearPreviewDevices
@Composable
private fun CalibrationScreenPreview() {
    BalmatchumTheme { CalibrationScreen(saved = FakeData.speeds, onResetToDefault = {}, onDone = {}) }
}

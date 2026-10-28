package kr.ac.cnu.balmatchum.presentation.theme

import androidx.compose.ui.graphics.Color
import kr.ac.cnu.balmatchum.core.ArrivalState

/** 상태별 화면 색. 진행 화면(T5)에서 배경색으로 쓴다. */
fun ArrivalState.color(): Color = when (this) {
    ArrivalState.RELAXED -> Color(0xFF2E7D32)
    ArrivalState.HURRY -> Color(0xFFF9A825)
    ArrivalState.RUN -> Color(0xFFC62828)
    ArrivalState.TOO_LATE -> Color(0xFF616161)
}

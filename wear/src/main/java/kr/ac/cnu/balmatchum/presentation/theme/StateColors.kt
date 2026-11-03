package kr.ac.cnu.balmatchum.presentation.theme

import androidx.compose.ui.graphics.Color
import kr.ac.cnu.balmatchum.core.ArrivalState

/** 상태별 강조색. 진행 화면(T5)의 검은 바탕 위 고리·글자·방향 카드에 쓴다. */
fun ArrivalState.color(): Color = when (this) {
    ArrivalState.RELAXED -> Color(0xFF34C759)
    ArrivalState.HURRY -> Color(0xFFFFCC00)
    ArrivalState.RUN -> Color(0xFFFF453A)
    ArrivalState.TOO_LATE -> Color(0xFF8E8E93)
}

/** 워치 화면용 짧은 상태 이름. 문서·포스터의 정식 이름은 [ArrivalState.label]("여유 있음" 등). */
fun ArrivalState.shortLabel(): String = when (this) {
    ArrivalState.RELAXED -> "여유"
    ArrivalState.HURRY -> "서둘러"
    ArrivalState.RUN -> "뛰어"
    ArrivalState.TOO_LATE -> "늦음"
}

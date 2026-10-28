package kr.ac.cnu.balmatchum.presentation

import kr.ac.cnu.balmatchum.core.ArrivalState
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.guidance.GuidanceResult

/**
 * 화면 미리보기(@Preview)와 화면 작업용 가짜 데이터.
 * 실제 위치나 워치 없이도 각 화면이 어떻게 보이는지 Android Studio 미리보기 창에서 바로 확인할 수 있다.
 */
object FakeData {

    /** 목적지 목록 화면용: (이름, 거리 m, TMAP 예상 초) */
    val destinations = listOf(
        Triple("빠삐용 → 공대 5호관", 949.0, 727.0),
        Triple("공대 5호관 → 경영경제연구소", 266.0, 224.0),
    )

    val speeds = PersonalSpeeds(walkMps = 1.3, briskMps = 1.8, runMps = 2.9)

    /** 진행 화면용: 상태마다 그럴듯한 값 하나씩. */
    fun update(state: ArrivalState): CoachUpdate {
        val (remainingM, remainingSec, required) = when (state) {
            ArrivalState.RELAXED -> Triple(400.0, 420.0, 0.95)
            ArrivalState.HURRY -> Triple(400.0, 260.0, 1.54)
            ArrivalState.RUN -> Triple(400.0, 170.0, 2.35)
            ArrivalState.TOO_LATE -> Triple(400.0, 90.0, 4.44)
        }
        return CoachUpdate(
            remainingM = remainingM,
            remainingTimeSec = remainingSec,
            movingSpeedMps = 1.3,
            requiredSpeedMps = required,
            slackAtWalkSec = remainingSec - remainingM / speeds.walkMps,
            lateAtRunSec = (remainingM / speeds.runMps - remainingSec).coerceAtLeast(0.0),
            observedState = state,
            state = state,
            stateChanged = false,
            offRoute = false,
            offRouteChanged = false,
            arrived = false,
        )
    }

    /** 결과 화면용: 목표보다 2분 일찍 도착. */
    val result = GuidanceResult(
        destinationName = "공대 5호관 → 경영경제연구소",
        startedAtMillis = 1_761_000_000_000,
        endedAtMillis = 1_761_000_200_000,
        deadlineMillis = 1_761_000_320_000,
        arrived = true,
        averageSpeedMps = 1.33,
        csvPath = null,
    )
}

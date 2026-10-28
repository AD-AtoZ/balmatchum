package kr.ac.cnu.balmatchum.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.wear.compose.material3.AppScaffold
import kotlinx.coroutines.launch
import kr.ac.cnu.balmatchum.BuildConfig
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.data.PhoneDestinationStore
import kr.ac.cnu.balmatchum.data.SpeedStore
import kr.ac.cnu.balmatchum.guidance.GuidanceRequest
import kr.ac.cnu.balmatchum.guidance.GuidanceSession
import kr.ac.cnu.balmatchum.guidance.GuidanceState
import kr.ac.cnu.balmatchum.presentation.screens.CalibrationScreen
import kr.ac.cnu.balmatchum.presentation.screens.DestinationItem
import kr.ac.cnu.balmatchum.presentation.screens.DestinationScreen
import kr.ac.cnu.balmatchum.presentation.screens.ProgressScreen
import kr.ac.cnu.balmatchum.presentation.screens.ResultScreen
import kr.ac.cnu.balmatchum.presentation.screens.TargetTimeScreen
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme

/** 앱의 화면 순서: 목적지 → 목표 시각 → 안내 중 → 결과. 속도 측정은 목적지 화면에서 들어간다. */
enum class Screen { DESTINATIONS, CALIBRATION, TARGET_TIME, PROGRESS, RESULT }

/** 가상 걷기 선택지(디버그 빌드에서만 보임). null은 실제 위치. */
private val SIMULATION_OPTIONS = listOf<Pair<String, Double?>>(
    "가상 걷기: 끔 (실제 위치)" to null,
    "가상 걷기: 1.3m/s" to 1.3,
    "가상 걷기: 느리게 0.8m/s" to 0.8,
    "가상 걷기: 빠르게 2.0m/s" to 2.0,
)

/**
 * 화면 이동과 화면 사이 데이터 전달만 맡는다. 각 화면의 모양은 screens/ 아래 파일에서 바꾼다.
 *
 * @param startGuidance 안내 시작. 위치 권한 확인과 서비스 시작은 MainActivity가 한다.
 */
@Composable
fun BalmatchumApp(
    demoDestinations: List<Destination>,
    speedStore: SpeedStore,
    startGuidance: (GuidanceRequest) -> Unit,
    stopGuidance: () -> Unit,
) {
    val guidance by GuidanceSession.state.collectAsState()
    val phoneDestination by PhoneDestinationStore.latest.collectAsState()
    val savedSpeeds by speedStore.speeds.collectAsState(initial = null)
    val speeds = savedSpeeds ?: PersonalSpeeds.DEFAULT
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(if (guidance is GuidanceState.Running) Screen.PROGRESS else Screen.DESTINATIONS) }
    var selected by remember { mutableStateOf<Destination?>(null) }
    var deadline by remember { mutableLongStateOf(0L) }
    var simulationIndex by remember { mutableStateOf(0) }

    val destinations = listOfNotNull(phoneDestination) + demoDestinations

    // 서비스가 안내를 시작하거나 끝내면 화면을 따라 바꾼다.
    LaunchedEffect(guidance) {
        when (guidance) {
            is GuidanceState.Running -> screen = Screen.PROGRESS
            is GuidanceState.Finished -> screen = Screen.RESULT
            GuidanceState.Idle -> if (screen == Screen.PROGRESS || screen == Screen.RESULT) screen = Screen.DESTINATIONS
        }
    }

    BackHandler(enabled = screen == Screen.CALIBRATION || screen == Screen.TARGET_TIME) {
        screen = Screen.DESTINATIONS
    }

    BalmatchumTheme {
        AppScaffold {
            when (screen) {
                Screen.DESTINATIONS -> DestinationScreen(
                    items = destinations.map {
                        DestinationItem(it.name, it.tmap.totalDistanceM, it.tmap.totalTimeSec, it.source == Destination.Source.PHONE)
                    },
                    speeds = savedSpeeds,
                    simulateLabel = if (BuildConfig.DEBUG) SIMULATION_OPTIONS[simulationIndex].first else null,
                    onCalibrate = { screen = Screen.CALIBRATION },
                    onCycleSimulate = { simulationIndex = (simulationIndex + 1) % SIMULATION_OPTIONS.size },
                    onPick = { i ->
                        val d = destinations[i]
                        selected = d
                        deadline = defaultDeadline(d)
                        screen = Screen.TARGET_TIME
                    },
                )

                Screen.CALIBRATION -> CalibrationScreen(
                    saved = savedSpeeds,
                    onResetToDefault = { scope.launch { speedStore.clear() } },
                    onDone = { screen = Screen.DESTINATIONS },
                )

                Screen.TARGET_TIME -> TargetTimeScreen(
                    destinationName = selected?.name.orEmpty(),
                    deadlineMillis = deadline,
                    onChange = { deadline = it },
                    onStart = {
                        val d = selected ?: return@TargetTimeScreen
                        startGuidance(GuidanceRequest(d, deadline, speeds, SIMULATION_OPTIONS[simulationIndex].second))
                    },
                )

                Screen.PROGRESS -> {
                    val running = guidance as? GuidanceState.Running
                    ProgressScreen(
                        destinationName = running?.request?.destination?.name.orEmpty(),
                        update = running?.latest,
                        speeds = running?.request?.speeds ?: speeds,
                        onStop = stopGuidance,
                    )
                }

                Screen.RESULT -> {
                    val finished = guidance as? GuidanceState.Finished
                    if (finished != null) {
                        ResultScreen(finished.result, onDone = { GuidanceSession.reset() })
                    }
                }
            }
        }
    }
}

/** 목표 시각 기본값: 지금 + TMAP 예상 시간 + 2분, 분 단위로 올림. */
private fun defaultDeadline(d: Destination): Long {
    val raw = System.currentTimeMillis() + (d.tmap.totalTimeSec * 1000).toLong() + 120_000
    return (raw + 59_999) / 60_000 * 60_000
}

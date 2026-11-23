package kr.ac.cnu.balmatchum.presentation.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kr.ac.cnu.balmatchum.core.PersonalSpeeds
import kr.ac.cnu.balmatchum.core.SpeedCalibrator
import kr.ac.cnu.balmatchum.location.FusedLocationSource
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme
import kotlin.time.Duration.Companion.milliseconds

/** 재는 속도의 종류. */
private enum class SpeedKind(val label: String) {
    WALK("보통 걸음"),
    BRISK("빠른 걸음"),
    RUN("뛰기"),
}

private const val MEASURE_SECONDS = 30

/**
 * 내 속도 측정 화면 (TODOLIST T1).
 * 버튼을 누르면 30초 동안 위치 속도를 모아 대표값을 구하고, 세 가지를 다 재면 저장한다.
 * 대표값 계산은 T2의 SpeedCalibrator를 사용한다.
 */
@Composable
fun CalibrationScreen(
    saved: PersonalSpeeds?,
    onSave: (PersonalSpeeds) -> Unit,
    onResetToDefault: () -> Unit,
    onDone: () -> Unit,
    simulate: Boolean = false,
) {
    val context = LocalContext.current
    val view = LocalView.current

    var measuring by remember { mutableStateOf<SpeedKind?>(null) }
    var secondsLeft by remember { mutableIntStateOf(MEASURE_SECONDS) }
    var message by remember { mutableStateOf<String?>(null) }
    val measured = remember { mutableStateMapOf<SpeedKind, Double>() }

    // 측정 중에는 화면이 꺼지지 않게 한다.
    DisposableEffect(measuring != null) {
        view.keepScreenOn = measuring != null
        onDispose { view.keepScreenOn = false }
    }

    // 측정: 위치 속도를 모으고 30초 세기. 화면을 나가거나 취소하면 이 블록이 취소되어 finally에서 위치 수신을 멈춘다.
    LaunchedEffect(measuring) {
        val kind = measuring ?: return@LaunchedEffect
        val calibrator = SpeedCalibrator()
        // 가상 걷기(디버그)일 때는 실제 위치 대신 종류별 가짜 속도를 1초마다 넣는다.
        val source = if (simulate) null else FusedLocationSource(context)
        try {
            source?.start { sample ->
                sample.speedMps?.let { calibrator.add(sample.timeMillis, it) }
            }
            for (left in MEASURE_SECONDS downTo 1) {
                secondsLeft = left
                if (simulate) calibrator.add(System.currentTimeMillis(), simulatedSpeed(kind))
                delay(1_000.milliseconds)
            }
            val result = calibrator.result()
            if (result == null) {
                message = "측정에 실패했어요. 계속 걸으며 다시 재 주세요"
            } else {
                measured[kind] = result
                message = null
            }
            measuring = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "위치 측정을 시작하지 못했어요. 위치 권한과 설정을 확인해 주세요"
            measuring = null
        } finally {
            source?.stop()
        }
    }

    fun start(kind: SpeedKind) {
        val granted = simulate ||
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            message = "위치 권한이 필요해요. 설정에서 허용해 주세요"
            return
        }
        message = null
        secondsLeft = MEASURE_SECONDS
        measuring = kind
    }

    // 상태는 여기서 읽어 두고, 아래 목록 안에서는 일반 값만 쓴다.
    val kind = measuring
    val left = secondsLeft
    val msg = message
    val walk = measured[SpeedKind.WALK]
    val brisk = measured[SpeedKind.BRISK]
    val run = measured[SpeedKind.RUN]
    val candidate = if (walk != null && brisk != null && run != null) PersonalSpeeds(walk, brisk, run) else null
    val shown = saved ?: PersonalSpeeds.DEFAULT

    ListScreen(title = "내 속도") { spec ->
        if (simulate) textItem(spec, "가상 속도 측정: 실제 GPS를 사용하지 않습니다")
        if (kind != null) {
            textItem(spec, "${kind.label} 재는 중")
            textItem(spec, "${left}초 남음. 계속 걸어 주세요")
            buttonItem(spec, "취소") { measuring = null }
        } else {
            textItem(spec, if (saved == null) "아직 재지 않아 기본값을 씁니다" else "저장된 내 속도")
            textItem(spec, "보통 걸음  ${Format.speed(shown.walkMps)}")
            textItem(spec, "빠른 걸음  ${Format.speed(shown.briskMps)}")
            textItem(spec, "뛰기  ${Format.speed(shown.runMps)}")

            textItem(spec, "새로 재기 (각 ${MEASURE_SECONDS}초)")
            SpeedKind.entries.forEach { k ->
                val value = measured[k]
                val label = if (value == null) k.label else "${k.label}  ${Format.speed(value)}"
                buttonItem(spec, label) { start(k) }
            }

            if (candidate != null) {
                if (candidate.isOrdered) {
                    buttonItem(spec, "저장") {
                        onSave(candidate)
                        measured.clear()
                        message = "저장했어요"
                    }
                } else {
                    textItem(spec, "보통 < 빠른 < 뛰기 순서가 아니에요. 다시 재 주세요")
                }
            }
            msg?.let { textItem(spec, it) }

            if (saved != null) buttonItem(spec, "기본값으로 되돌리기", onResetToDefault)
            buttonItem(spec, "완료", onDone)
        }
    }
}

/** 가상 걷기(디버그)에서 쓰는 가짜 속도. 기본값(1.3/1.7/2.5)과 달라서 저장 결과를 구분할 수 있다. */
private fun simulatedSpeed(kind: SpeedKind): Double = when (kind) {
    SpeedKind.WALK -> 1.1
    SpeedKind.BRISK -> 1.6
    SpeedKind.RUN -> 2.8
}

@WearPreviewDevices
@Composable
private fun CalibrationScreenPreview() {
    BalmatchumTheme {
        CalibrationScreen(saved = FakeData.speeds, onSave = {}, onResetToDefault = {}, onDone = {})
    }
}

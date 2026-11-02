package kr.ac.cnu.balmatchum.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.components.ListScreen
import kr.ac.cnu.balmatchum.presentation.components.buttonItem
import kr.ac.cnu.balmatchum.presentation.components.textItem
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme

/**
 * 날짜와 시·분을 고르고, 선택 확인과 출발 시점에 지난 시각인지 검사한다.
 * 기본값은 "지금 + TMAP 예상 시간 + 2분"이고 BalmatchumApp에서 넘겨준다.
 */
@Composable
fun TargetTimeScreen(
    destinationName: String,
    deadlineMillis: Long,
    onChange: (newDeadlineMillis: Long) -> Unit,
    onStart: () -> Unit,
    currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var selectionError by rememberSaveable { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = currentTimeMillis()
            delay(1_000)
        }
    }

    val zone = ZoneId.systemDefault()
    val deadline = Instant.ofEpochMilli(deadlineMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val targetDate = deadline.toLocalDate()
    val dayLabel = when (targetDate) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> "${targetDate.monthValue}월 ${targetDate.dayOfMonth}일"
    }
    val isFuture = deadlineMillis > nowMillis

    BackHandler(enabled = showPicker) { showPicker = false }
    if (showPicker) {
        TimePicker(
            initialTime = deadline.toLocalTime(),
            timePickerType = TimePickerType.HoursMinutes24H,
            onTimePicked = { time ->
                val candidate = futureTargetTimeMillis(targetDate, time, currentTimeMillis(), zone)
                selectionError = candidate == null
                if (candidate != null) onChange(candidate)
                showPicker = false
            },
        )
    } else {
        ListScreen(title = "몇 시까지?") { spec ->
            textItem(spec, destinationName)
            textItem(spec, "도착 목표  $dayLabel ${Format.clock(deadlineMillis)}")
            buttonItem(spec, "시각 고르기") {
                selectionError = false
                showPicker = true
            }
            buttonItem(spec, if (targetDate == today) "내일 도착으로 변경" else "오늘 도착으로 변경") {
                val newDate = if (targetDate == today) today.plusDays(1) else today
                val candidate = futureTargetTimeMillis(newDate, deadline.toLocalTime(), currentTimeMillis(), zone)
                // 오늘의 같은 시각이 지났으면 다음 분부터 다시 고른다.
                val fallback = (currentTimeMillis() / 60_000 + 1) * 60_000
                onChange(candidate ?: fallback)
                selectionError = false
            }
            if (selectionError) {
                textItem(spec, "지난 시각은 선택할 수 없어요. 현재보다 늦은 시각을 골라 주세요.")
            } else if (!isFuture) {
                textItem(spec, "목표 시각이 지났어요. 시각을 다시 골라 주세요.")
            }
            item {
                Button(
                    enabled = isFuture && !selectionError,
                    onClick = {
                        nowMillis = currentTimeMillis()
                        if (deadlineMillis > nowMillis) onStart() else selectionError = true
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                ) { Text("출발") }
            }
        }
    }
}

private val previewNow = java.time.ZonedDateTime.of(2026, 11, 2, 14, 0, 0, 0, ZoneId.systemDefault())
    .toInstant().toEpochMilli()

@WearPreviewDevices
@Composable
private fun TargetTimeScreenPreview() {
    BalmatchumTheme {
        TargetTimeScreen("공대 5호관 → 경영경제연구소", previewNow + 900_000,
            onChange = {}, onStart = {}, currentTimeMillis = { previewNow })
    }
}

@WearPreviewDevices
@Composable
private fun TargetTimeExpiredPreview() {
    BalmatchumTheme {
        TargetTimeScreen("공대 5호관", previewNow - 60_000,
            onChange = {}, onStart = {}, currentTimeMillis = { previewNow })
    }
}

@WearPreviewDevices
@Composable
private fun TargetTimeTomorrowPreview() {
    BalmatchumTheme {
        TargetTimeScreen("공대 5호관", previewNow + 86_400_000,
            onChange = {}, onStart = {}, currentTimeMillis = { previewNow })
    }
}

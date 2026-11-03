package kr.ac.cnu.balmatchum.presentation.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import kr.ac.cnu.balmatchum.core.ArrivalState
import kr.ac.cnu.balmatchum.core.CoachUpdate
import kr.ac.cnu.balmatchum.core.RouteTurn
import kr.ac.cnu.balmatchum.core.UpcomingTurn
import kr.ac.cnu.balmatchum.presentation.FakeData
import kr.ac.cnu.balmatchum.presentation.Format
import kr.ac.cnu.balmatchum.presentation.theme.BalmatchumTheme
import kr.ac.cnu.balmatchum.presentation.theme.color
import kr.ac.cnu.balmatchum.presentation.theme.shortLabel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 보조 글자색(남은 거리, 속도 등). */
private val Muted = Color(0xFFAEAEB2)

/**
 * 안내 중 화면 (TODOLIST T5). 사용자가 손목을 들어 볼 때만 보는 화면이라 한눈에 상태가 보여야 한다.
 * 검은 바탕에 상태 색 하나만 쓴다: 가장자리 고리(경로를 얼마나 왔는지), 상태 글자, 방향 카드.
 * [update]가 null이면 아직 첫 위치를 받기 전이다.
 */
@Composable
fun ProgressScreen(
    destinationName: String,
    update: CoachUpdate?,
    /** 경로 전체 길이(m). 가장자리 고리를 채우는 데 쓴다. */
    routeDistanceM: Double,
    nextTurn: UpcomingTurn? = null,
    onStop: () -> Unit,
) {
    val accent = update?.state?.color() ?: Muted
    // 안내 지점이 가까우면(30m 안) 방향 카드를 크게 키운다.
    val nearTurn = nextTurn?.takeIf { it.isNear }?.turn
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (update != null && routeDistanceM > 0) {
            ProgressRing((1 - update.remainingM / routeDistanceM).toFloat(), accent)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            if (update == null) {
                Text("위치를 찾는 중…", color = Color.White, textAlign = TextAlign.Center)
                Text(destinationName, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else if (nearTurn != null) {
                StateWord(update.state, accent, 18.sp)
                TurnCard(nextTurn, accent, big = true)
                Text(
                    "${remainingTime(update)} · ${Format.distance(update.remainingM)} 남음",
                    color = Muted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                StateWord(update.state, accent, 32.sp)
                if (update.state == ArrivalState.TOO_LATE) {
                    Text("뛰어도 ${Format.duration(update.lateAtRunSec)} 지각", color = accent, fontSize = 12.sp)
                }
                if (nextTurn != null) TurnCard(nextTurn, accent, big = false)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(remainingTime(update), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${Format.distance(update.remainingM)} 남음", color = Muted, fontSize = 13.sp)
                }
                Text(
                    "필요 ${speedText(update.requiredSpeedMps)} · 지금 ${speedText(update.movingSpeedMps)} m/s",
                    color = Muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            // 끝내기는 가끔 쓰므로 첫 화면에서 빼고, 아래로 스크롤하면 나오게 한다.
            FilledTonalButton(onClick = onStop, modifier = Modifier.padding(top = 48.dp)) { Text("안내 끝내기", fontSize = 12.sp) }
        }
    }
}

private fun remainingTime(update: CoachUpdate): String =
    if (update.remainingTimeSec <= 0) "목표 시각 지남" else Format.duration(update.remainingTimeSec)

private fun speedText(mps: Double): String = if (mps.isFinite()) "%.1f".format(mps.coerceIn(0.0, 99.0)) else "—"

/** 화면 가장자리 고리. 경로를 온 만큼 위에서부터 시계 방향으로 채운다. */
@Composable
private fun ProgressRing(progress: Float, color: Color) {
    val sweep by animateFloatAsState(progress.coerceIn(0f, 1f) * 360f, label = "route progress")
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2 + 3.dp.toPx()
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** "여유"처럼 짧은 상태 글자. 뛰기 상태에서만 천천히 밝기를 바꾼다. */
@Composable
private fun StateWord(state: ArrivalState, color: Color, fontSize: TextUnit) {
    val alpha = if (state == ArrivalState.RUN && !LocalInspectionMode.current) {
        val transition = rememberInfiniteTransition(label = "run emphasis")
        val pulse by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "state brightness",
        )
        pulse
    } else {
        1f
    }
    Text(
        state.shortLabel(),
        modifier = Modifier.graphicsLayer { this.alpha = alpha },
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

/**
 * 다음에 꺾을 곳 카드. 꺾는 모양 화살표 + 큰 거리 숫자 + "후 좌회전".
 * 가까우면([big]) 같은 카드를 크게 그려서, 화면이 바뀌어도 어디를 봐야 할지 같다.
 */
@Composable
private fun TurnCard(next: UpcomingTurn, accent: Color, big: Boolean) {
    val turn = next.turn
    val now = turn != null && next.distanceM < 5
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(if (big) 32.dp else 22.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = if (big) 20.dp else 14.dp, vertical = if (big) 14.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (big) 14.dp else 10.dp),
    ) {
        TurnArrow(turn?.arrowDegrees ?: 0f, accent, if (big) 60.dp else 34.dp)
        Column {
            Text(
                if (now) "지금" else Format.distance(next.distanceM),
                color = Color.White,
                fontSize = if (big) 32.sp else 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    turn == null -> "후 도착"
                    now -> turn.label
                    else -> "후 ${turn.label}"
                },
                color = Muted,
                fontSize = if (big) 15.sp else 12.sp,
            )
        }
    }
}

/**
 * 내비게이션처럼 "앞으로 가다가 꺾는" 모양의 화살표. [degrees]는 꺾는 방향(0 = 직진, -90 = 왼쪽, 90 = 오른쪽).
 * 방향이 바뀌면 팔이 부드럽게 돌아간다. 유턴(150도 넘음)은 U자로 따로 그린다.
 */
@Composable
private fun TurnArrow(degrees: Float, color: Color, size: Dp) {
    val angle by animateFloatAsState(degrees, label = "turn arrow")
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val line = Stroke(width = w * 0.14f, join = StrokeJoin.Round)
        val headLen = w * 0.22f
        val headHalf = w * 0.18f
        // 화살촉: tip에서 dir 방향으로 뾰족한 삼각형.
        fun head(tip: Offset, dir: Offset) {
            val side = Offset(-dir.y, dir.x)
            drawPath(
                Path().apply {
                    moveTo(tip.x + dir.x * headLen, tip.y + dir.y * headLen)
                    lineTo(tip.x + side.x * headHalf, tip.y + side.y * headHalf)
                    lineTo(tip.x - side.x * headHalf, tip.y - side.y * headHalf)
                    close()
                },
                color,
            )
        }
        if (abs(angle) > 150f) {
            // 유턴: 오른쪽 기둥으로 올라가 반원을 돌아 왼쪽으로 내려온다.
            val r = w * 0.2f
            drawPath(
                Path().apply {
                    moveTo(w * 0.7f, w * 0.95f)
                    lineTo(w * 0.7f, w * 0.4f)
                    arcTo(Rect(w * 0.3f, w * 0.4f - r, w * 0.7f, w * 0.4f + r), 0f, -180f, false)
                    lineTo(w * 0.3f, w * 0.6f)
                },
                color,
                style = line,
            )
            head(Offset(w * 0.3f, w * 0.6f), Offset(0f, 1f))
            return@Canvas
        }
        val rad = Math.toRadians(angle.toDouble())
        val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
        // 꺾는 쪽 반대로 기둥을 조금 옮겨 전체가 가운데 오게 한다.
        val bend = Offset(w * (0.5f - 0.18f * dir.x), w * 0.55f)
        val tip = Offset(bend.x + dir.x * w * 0.3f, bend.y + dir.y * w * 0.3f)
        drawPath(
            Path().apply {
                moveTo(bend.x, w * 0.97f)
                lineTo(bend.x, bend.y)
                lineTo(tip.x, tip.y)
            },
            color,
            style = line,
        )
        head(tip, dir)
    }
}

@WearPreviewDevices
@Composable
private fun ProgressLoadingPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", null, routeDistanceM = 266.0, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressRelaxedPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.RELAXED), routeDistanceM = 266.0, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressHurryPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.HURRY), routeDistanceM = 266.0, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressRunPreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.RUN), routeDistanceM = 266.0, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressTooLatePreview() {
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.TOO_LATE), routeDistanceM = 266.0, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressTurnFarPreview() {
    val next = UpcomingTurn(RouteTurn.fromTmap(200.0, 12, ""), distanceM = 120.0, isNear = false)
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.RELAXED), routeDistanceM = 266.0, next, onStop = {}) }
}

@WearPreviewDevices
@Composable
private fun ProgressTurnNearPreview() {
    val next = UpcomingTurn(RouteTurn.fromTmap(200.0, 12, ""), distanceM = 25.0, isNear = true)
    BalmatchumTheme { ProgressScreen("경영경제연구소", FakeData.update(ArrivalState.HURRY), routeDistanceM = 266.0, next, onStop = {}) }
}

package kr.ac.cnu.balmatchum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.ac.cnu.balmatchum.core.IndoorDemo
import kr.ac.cnu.balmatchum.core.IndoorPose
import kr.ac.cnu.balmatchum.core.WallMap
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 위에서 내려다본 복도 지도에 지금 위치를 보여 준다. 복도 앞쪽(출발점 → 도착점)이 화면 위쪽이다.
 *
 * - 진회색: 스캔의 벽·물체 (좌우를 뒤집어 실제 건물에 맞춘 것), 연두: 위치가 잡힐 수 있는 곳(걸을 수 있는 곳)
 * - 회색 선: 시연 경로, 초록·빨강 가로선: 출발점·도착점
 * - **노란 점(테두리 있음)**: 카메라가 본 점 중 스캔 벽과 맞은 것. 많고 벽 위에 줄지어 있을수록 위치가 맞다
 * - 옅은 빨간 점: 카메라가 봤지만 벽과 맞지 않은 점(사람·잡음, 또는 위치가 틀어졌을 때 늘어난다)
 * - 하늘색 선: 지나온 길, 파란 점과 부채꼴: 지금 위치와 휴대폰이 보는 방향
 *
 * 오른쪽 위 글자를 누르면 "내 주변 확대"와 "경로 전체"를 오간다. [onPick]이 있으면(시작점 고르기) 지도를 누른 자리를 알려 준다.
 *
 * @param seenPoints 점마다 (forward, lateral, 가장 가까운 벽까지 거리 m) 3개씩
 */
@Composable
fun IndoorMiniMap(
    map: WallMap,
    pose: IndoorPose?,
    seenPoints: FloatArray?,
    modifier: Modifier = Modifier,
    trail: FloatArray? = null,
    startF: Double = 0.0,
    startL: Double = 0.0,
    onPick: ((forward: Double, lateral: Double) -> Unit)? = null,
) {
    val image = remember(map) { wallImage(map) }
    var closeUp by remember { mutableStateOf(true) }
    // 시작점을 고를 때는 경로 전체를 보여 준다.
    val zoomed = closeUp && pose != null && onPick == null
    val currentPick by rememberUpdatedState(onPick)
    val view = View(zoomed, pose, startF)
    val currentView by rememberUpdatedState(view)

    val matched = seenPoints?.let { pts -> (0 until pts.size / 3).count { pts[3 * it + 2] < MATCH_M } } ?: 0
    val total = (seenPoints?.size ?: 0) / 3

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xF0FFFFFF))
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    val pick = currentPick ?: return@detectTapGestures
                    val v = currentView
                    val scale = size.height / v.spanM.toFloat()
                    val f = v.centerF - (tap.y - size.height / 2f) / scale
                    val l = v.centerL + (tap.x - size.width / 2f) / scale
                    pick(f, l)
                }
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scale = size.height / view.spanM.toFloat() // 1m 당 화면 픽셀

            fun toScreen(f: Double, l: Double) = Offset(
                x = size.width / 2 + ((l - view.centerL) * scale).toFloat(),
                y = size.height / 2 - ((f - view.centerF) * scale).toFloat(),
            )

            clipRect {
                // 벽 지도: 그림의 가로 = lateral, 세로 = forward(위가 앞쪽)
                val cellPx = (map.cellM * scale).toFloat()
                val topLeft = toScreen(map.forwardMax, map.lateralMin)
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
                    dstSize = IntSize((image.width * cellPx).roundToInt(), (image.height * cellPx).roundToInt()),
                    filterQuality = FilterQuality.None,
                )

                // 경로와 출발점·도착점
                val endF = startF + IndoorDemo.ROUTE_LENGTH_M
                drawLine(Color(0xFF9AA4B2), toScreen(startF, 0.0), toScreen(endF, 0.0), strokeWidth = 3f)
                drawLine(Color(0xFF2E9E5B), toScreen(startF, -1.3), toScreen(startF, 1.3), strokeWidth = 5f)
                drawLine(Color(0xFFD64545), toScreen(endF, -1.3), toScreen(endF, 1.3), strokeWidth = 5f)
                drawCircle(Color(0xFF2E9E5B), radius = 6f, center = toScreen(startF, startL))

                // 지나온 길
                trail?.let { t ->
                    if (t.size >= 4) {
                        val path = Path()
                        for (i in 0 until t.size / 2) {
                            val p = toScreen(t[2 * i].toDouble(), t[2 * i + 1].toDouble())
                            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        }
                        drawPath(path, Color(0xAA4FC3F7), style = Stroke(width = 4f))
                    }
                }

                // 카메라 점: 맞지 않은 점을 먼저 작게, 맞은 점을 위에 크게
                seenPoints?.let { pts ->
                    for (i in 0 until pts.size / 3) {
                        if (pts[3 * i + 2] < MATCH_M) continue
                        drawCircle(Color(0x88E53935), radius = 2.2f, center = toScreen(pts[3 * i].toDouble(), pts[3 * i + 1].toDouble()))
                    }
                    for (i in 0 until pts.size / 3) {
                        if (pts[3 * i + 2] >= MATCH_M) continue
                        val c = toScreen(pts[3 * i].toDouble(), pts[3 * i + 1].toDouble())
                        drawCircle(Color(0xFF3E2723), radius = 5.5f, center = c)
                        drawCircle(Color(0xFFFFE600), radius = 4f, center = c)
                    }
                }

                // 지금 위치와 보는 방향
                if (pose != null) drawMe(toScreen(pose.forwardM, pose.lateralM), pose.headingDeg)
            }
        }
        Text(
            when {
                onPick != null -> "출발점으로 쓸 곳을 누르세요"
                total > 0 -> "노랑 $matched / 점 $total"
                else -> "카메라 점 없음"
            },
            fontSize = 10.sp,
            color = Color(0xFF333333),
            modifier = Modifier.align(Alignment.TopStart).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp, vertical = 2.dp),
        )
        if (onPick == null && pose != null) {
            Text(
                if (closeUp) "전체" else "확대",
                fontSize = 11.sp,
                color = Color(0xFF2F6FED),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clickable { closeUp = !closeUp }
                    .background(Color(0xCCFFFFFF))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** 미니맵이 보여 주는 범위: 가운데 자리(forward, lateral)와 화면 세로에 들어오는 거리(m). */
private class View(zoomed: Boolean, pose: IndoorPose?, startF: Double) {
    val spanM: Double = if (zoomed) CLOSE_SPAN_M else IndoorDemo.ROUTE_LENGTH_M + 8.0
    val centerF: Double = if (zoomed && pose != null) pose.forwardM else startF + IndoorDemo.ROUTE_LENGTH_M / 2
    val centerL: Double = if (zoomed && pose != null) pose.lateralM else 0.0
}

/** 파란 점과, 보는 방향으로 벌어진 부채꼴. 방향 0도가 화면 위쪽(복도 앞쪽), 오른쪽으로 돌면 양수. */
private fun DrawScope.drawMe(at: Offset, headingDeg: Double) {
    val a = Math.toRadians(headingDeg)
    val spread = Math.toRadians(30.0)
    val length = 34f
    fun tip(angle: Double) = Offset(at.x + (sin(angle) * length).toFloat(), at.y - (cos(angle) * length).toFloat())
    val cone = Path().apply {
        moveTo(at.x, at.y)
        lineTo(tip(a - spread).x, tip(a - spread).y)
        lineTo(tip(a + spread).x, tip(a + spread).y)
        close()
    }
    drawPath(cone, Color(0x552F6FED))
    drawCircle(Color.White, radius = 9f, center = at)
    drawCircle(Color(0xFF2F6FED), radius = 7f, center = at)
    drawCircle(Color(0xFF2F6FED), radius = 9f, center = at, style = Stroke(width = 1.5f))
}

/** 벽 지도를 그림으로 만든다. 가로 = lateral(왼쪽 → 오른쪽), 세로 = forward(위가 앞쪽). */
private fun wallImage(map: WallMap): ImageBitmap {
    val w = map.lateralCells
    val h = map.forwardCells
    val pixels = IntArray(w * h)
    for (fi in 0 until h) {
        val row = h - 1 - fi
        for (li in 0 until w) {
            val d = map.cellDistance(fi, li)
            pixels[row * w + li] = when {
                d == 0 -> 0xFF4A5059.toInt()
                map.hasWalkable && map.cellWalkable(fi, li) -> 0xFFDDF2DF.toInt()
                d <= 2 -> 0xFFC9CED6.toInt()
                else -> 0x00000000
            }
        }
    }
    return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** 확대했을 때 화면 세로에 들어오는 거리(m). */
private const val CLOSE_SPAN_M = 16.0

/** 벽에서 이 거리(m) 안의 카메라 점을 "맞은 점"(노랑)으로 그린다. 지도 맞추기의 일치 기준([kr.ac.cnu.balmatchum.core.MatchConfig.inlierM])과 같다. */
private const val MATCH_M = 0.25f

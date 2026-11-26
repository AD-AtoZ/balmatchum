package kr.ac.cnu.balmatchum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.ac.cnu.balmatchum.core.IndoorDemo
import kr.ac.cnu.balmatchum.core.IndoorPose
import kr.ac.cnu.balmatchum.core.WallMap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * 위에서 내려다본 복도 지도에 지금 위치를 보여 준다. 복도 앞쪽(출발선 → 도착선)이 화면 위쪽이다.
 *
 * - 회색: 스캔의 벽·물체 (좌우를 뒤집어 실제 건물에 맞춘 것)
 * - 흰 선: 실내 시연 경로, 초록·빨강 가로선: 출발선·도착선
 * - 파란 점과 부채꼴: 지금 위치와 휴대폰이 보는 방향
 * - 주황 점: 마지막 지도 맞추기에 쓴 카메라 점. 벽에 겹쳐 있을수록 잘 맞은 것이다
 *
 * 누르면 "내 주변 확대"와 "경로 전체"를 오간다.
 */
@Composable
fun IndoorMiniMap(
    map: WallMap,
    pose: IndoorPose?,
    seenPoints: FloatArray?,
    modifier: Modifier = Modifier,
) {
    val image = remember(map) { wallImage(map) }
    var closeUp by remember { mutableStateOf(true) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE6FFFFFF))
            .clickable { closeUp = !closeUp },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // 화면에 보일 범위(m): 확대면 세로 16m, 전체면 경로 전체가 들어오게.
            val routeLength = IndoorDemo.ROUTE_LENGTH_M
            val spanM = if (closeUp && pose != null) CLOSE_SPAN_M else routeLength + 8.0
            val scale = size.height / spanM.toFloat() // 1m 당 화면 픽셀
            val centerF = if (closeUp && pose != null) pose.forwardM else routeLength / 2
            val centerL = if (closeUp && pose != null) pose.lateralM else 0.0

            fun toScreen(f: Double, l: Double) = Offset(
                x = size.width / 2 + ((l - centerL) * scale).toFloat(),
                y = size.height / 2 - ((f - centerF) * scale).toFloat(),
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

                // 경로와 출발선·도착선
                drawLine(Color(0xFF9AA4B2), toScreen(0.0, 0.0), toScreen(routeLength, 0.0), strokeWidth = 3f)
                drawLine(Color(0xFF2E9E5B), toScreen(0.0, -1.3), toScreen(0.0, 1.3), strokeWidth = 5f)
                drawLine(Color(0xFFD64545), toScreen(routeLength, -1.3), toScreen(routeLength, 1.3), strokeWidth = 5f)

                // 카메라 점
                seenPoints?.let { pts ->
                    for (i in 0 until pts.size / 2) {
                        drawCircle(Color(0xCCFF8F00), radius = 2.5f, center = toScreen(pts[2 * i].toDouble(), pts[2 * i + 1].toDouble()))
                    }
                }

                // 지금 위치와 보는 방향
                if (pose != null) drawMe(toScreen(pose.forwardM, pose.lateralM), pose.headingDeg)
            }
        }
        Text(
            if (closeUp && pose != null) "내 주변 (누르면 전체)" else "경로 전체 (누르면 확대)",
            fontSize = 10.sp,
            color = Color(0xFF555555),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp),
        )
    }
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
                d == 0 -> 0xFF5F6670.toInt()
                d <= 2 -> 0xFFC9CED6.toInt()
                else -> 0x00000000
            }
        }
    }
    return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** 확대했을 때 화면 세로에 들어오는 거리(m). */
private const val CLOSE_SPAN_M = 16.0

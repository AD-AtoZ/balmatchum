package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TurnGuideTest {

    private fun turn(along: Double, type: Int) = RouteTurn.fromTmap(along, type, "")!!

    private val guide = TurnGuide(listOf(turn(100.0, 12), turn(200.0, 13)), totalDistanceM = 300.0)

    @Test
    fun farTurnIsShownSmall() {
        val next = guide.next(20.0)
        assertEquals(12, next.turn!!.turnType)
        assertEquals(80.0, next.distanceM, 1e-9)
        assertFalse(next.isNear)
    }

    @Test
    fun nearTurnIsShownBig() {
        val next = guide.next(75.0)
        assertEquals(12, next.turn!!.turnType)
        assertTrue(next.isNear)
    }

    @Test
    fun staysOnTurnUntilPassedThenMovesOn() {
        // 지나고 10m 안에서는 아직 좌회전(0m)을 보여 준다.
        val justPassed = guide.next(105.0)
        assertEquals(12, justPassed.turn!!.turnType)
        assertEquals(0.0, justPassed.distanceM, 1e-9)
        // 10m를 넘게 지나면 다음 우회전으로.
        val moved = guide.next(111.0)
        assertEquals(13, moved.turn!!.turnType)
        assertEquals(89.0, moved.distanceM, 1e-9)
    }

    @Test
    fun closeTurnsSwitchHalfway() {
        // 좌회전 바로 6m 뒤에 횡단보도가 있으면 가운데(3m 지점)에서 넘어간다.
        val g = TurnGuide(listOf(turn(100.0, 12), turn(106.0, 211)), totalDistanceM = 300.0)
        assertEquals(12, g.next(102.0).turn!!.turnType)
        assertEquals(211, g.next(104.0).turn!!.turnType)
    }

    @Test
    fun afterLastTurnShowsDestination() {
        val next = guide.next(250.0)
        assertNull(next.turn)
        assertEquals(50.0, next.distanceM, 1e-9)
        assertFalse(next.isNear)
    }

    @Test
    fun straightStartAndEndAreNotGuidance() {
        assertNull(RouteTurn.fromTmap(0.0, 11, ""))
        assertNull(RouteTurn.fromTmap(0.0, 200, ""))
        assertNull(RouteTurn.fromTmap(0.0, 201, ""))
        assertNull(RouteTurn.fromTmap(0.0, 999, ""))
        assertEquals(-90f, RouteTurn.fromTmap(0.0, 12, "")!!.arrowDegrees)
        assertEquals("오른쪽 횡단보도", RouteTurn.fromTmap(0.0, 213, "")!!.label)
    }

    @Test
    fun demoRoutesHaveTurnsWhereTmapSaysSo() {
        // 앱에 넣은 시연 경로. TMAP 구간 거리(LineString의 distance)를 앞에서부터 더한 곳에 안내 지점이 있어야 한다.
        // 안내 문장의 "○m 이동"만 더하면 안 된다. 빠삐용 경로는 안내 지점 없이 이어지는 385m 구간이 있다.
        val dir = File("../wear/src/main/assets/routes")
        val meri = TmapRoute.parse(dir.resolve("eng5_to_meri.json").readText())
        assertEquals(listOf(12, 13), meri.turns.map { it.turnType })
        assertEquals(84.0, meri.turns[0].distanceAlongM, 5.0)
        assertEquals(196.0, meri.turns[1].distanceAlongM, 5.0)

        val papillon = TmapRoute.parse(dir.resolve("papillon_to_eng5.json").readText())
        assertEquals(listOf(13, 12, 13, 13, 12), papillon.turns.map { it.turnType })
        val expected = listOf(34.0, 76.0, 97.0, 446.0, 865.0)
        papillon.turns.zip(expected).forEach { (t, e) -> assertEquals(e, t.distanceAlongM, 10.0) }
    }
}

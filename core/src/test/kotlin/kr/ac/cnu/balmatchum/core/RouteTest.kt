package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {

    @Test
    fun totalDistanceOfStraightRoute() {
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0)))
        assertEquals(100.0, route.totalDistance, 0.1)
    }

    @Test
    fun projectsPointBesideRoute() {
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0)))
        val p = route.project(at(10.0, 40.0))
        assertEquals(40.0, p.distanceAlong, 0.1)
        assertEquals(60.0, p.remaining, 0.1)
        assertEquals(10.0, p.offset, 0.1)
    }

    @Test
    fun remainingFollowsPathNotStraightLine() {
        // 북쪽 100m 갔다가 동쪽 80m. 출발점에서 도착점까지 직선은 약 128m지만 경로로는 180m.
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0), at(80.0, 100.0)))
        assertEquals(180.0, route.project(at(0.0, 0.0)).remaining, 0.2)
        assertEquals(40.0, route.project(at(40.0, 100.0)).remaining, 0.2)
    }

    @Test
    fun clampsBeforeStartAndAfterEnd() {
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0)))
        assertEquals(0.0, route.project(at(0.0, -20.0)).distanceAlong, 0.1)
        assertEquals(0.0, route.project(at(0.0, 130.0)).remaining, 0.1)
    }

    @Test
    fun pointAtWalksAlongThePath() {
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0), at(80.0, 100.0)))
        assertEquals(0.0, distanceMeters(route.pointAt(0.0), at(0.0, 0.0)), 0.1)
        assertEquals(0.0, distanceMeters(route.pointAt(50.0), at(0.0, 50.0)), 0.2)
        assertEquals(0.0, distanceMeters(route.pointAt(140.0), at(40.0, 100.0)), 0.2)
        assertEquals(0.0, distanceMeters(route.pointAt(999.0), at(80.0, 100.0)), 0.1) // 끝을 넘으면 도착점
        // 경로 위의 점을 다시 투영하면 같은 진행 거리가 나와야 한다.
        assertEquals(140.0, route.project(route.pointAt(140.0)).distanceAlong, 0.3)
    }

    @Test
    fun hintKeepsProgressOnCloseParallelLegs() {
        // 북쪽 100m → 동쪽 10m → 남쪽 100m. 가는 길과 오는 길이 10m 떨어져 있다.
        val route = Route(listOf(at(0.0, 0.0), at(0.0, 100.0), at(10.0, 100.0), at(10.0, 0.0)))
        // 가는 중인데 GPS가 오는 길에 더 가깝게(6m 대 4m) 찍혀도 가는 길에 남는다.
        assertEquals(50.0, route.project(at(6.0, 50.0), hintAlong = 45.0).distanceAlong, 0.2)
        // 오는 중이면 반대로 오는 길에 남는다.
        assertEquals(160.0, route.project(at(4.0, 50.0), hintAlong = 155.0).distanceAlong, 0.2)
        // 힌트가 없으면 그냥 가장 가까운 길.
        assertEquals(160.0, route.project(at(6.0, 50.0)).distanceAlong, 0.2)
    }
}

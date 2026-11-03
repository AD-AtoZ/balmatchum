package kr.ac.cnu.balmatchum

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kr.ac.cnu.balmatchum.core.GeoPoint
import kr.ac.cnu.balmatchum.core.TmapApi
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PhoneRouteRequestTest {
    private val start = GeoPoint(36.36, 127.34)
    private val poi = TmapApi.Poi("목적지", "주소", GeoPoint(36.37, 127.35))
    private val json = """{"features":[{"geometry":{"type":"LineString","coordinates":[[127.34,36.36],[127.35,36.37]]}}]}"""

    @Test fun usesCurrentLocationAndPreservesDestinationAndArrival() = runBlocking {
        val destination = preparePhoneDestination(poi, 100, { start }, { origin, end ->
            assertEquals(start, origin)
            assertEquals(poi.entrance, end.entrance)
            json
        }, { 0 })
        assertEquals(poi.name, destination.name)
        assertEquals(json, destination.tmapJson)
        assertEquals(100L, destination.arrivalTimeMillis)
    }

    @Test fun expiredTimeDoesNotRequestLocationOrRoute() = runBlocking {
        try {
            preparePhoneDestination(poi, 100, { error("위치 요청 금지") }, { _, _ -> error("경로 요청 금지") }, { 100 })
            fail("만료된 도착 시각")
        } catch (_: ArrivalTimeExpiredException) { }
    }

    @Test fun timeExpiringDuringLocationDoesNotRequestRoute() = runBlocking {
        var now = 0L
        try {
            preparePhoneDestination(poi, 100, { now = 100; start }, { _, _ -> error("경로 요청 금지") }, { now })
            fail("위치 대기 중 만료")
        } catch (_: ArrivalTimeExpiredException) { }
    }

    @Test fun timeExpiringDuringRouteCannotBeSent() = runBlocking {
        var now = 0L
        try {
            preparePhoneDestination(poi, 100, { start }, { _, _ -> now = 100; json }, { now })
            fail("경로 대기 중 만료")
        } catch (_: ArrivalTimeExpiredException) { }
    }

    @Test fun invalidRouteCannotBeSent() = runBlocking {
        try {
            preparePhoneDestination(poi, 100, { start }, { _, _ -> """{"features":[]}""" }, { 0 })
            fail("보행 경로 없음")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun locationFailureDoesNotRequestRoute() = runBlocking {
        val failure = IOException("위치 실패")
        try {
            preparePhoneDestination(poi, 100, { throw failure }, { _, _ -> error("경로 요청 금지") }, { 0 })
            fail("위치 실패")
        } catch (e: IOException) { assertSame(failure, e) }
    }

    @Test fun cancellationPropagatesWithoutRequestingRoute() = runBlocking {
        try {
            preparePhoneDestination(poi, 100, { throw CancellationException() }, { _, _ -> error("경로 요청 금지") }, { 0 })
            fail("취소")
        } catch (_: CancellationException) { }
    }
}

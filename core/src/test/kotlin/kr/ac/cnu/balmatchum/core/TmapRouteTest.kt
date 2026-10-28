package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TmapRouteTest {

    private val sample = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[127.3440,36.3670]},
           "properties":{"index":0,"pointType":"SP","totalDistance":180,"totalTime":150}},
          {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127.3440,36.3670],[127.3440,36.3679]]},
           "properties":{"index":1,"lineIndex":0,"distance":100,"time":80}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[127.3440,36.3679]},
           "properties":{"index":2,"pointType":"GP"}},
          {"type":"Feature","geometry":{"type":"LineString","coordinates":[[127.3440,36.3679],[127.3449,36.3679]]},
           "properties":{"index":3,"lineIndex":1,"distance":80,"time":70}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[127.3449,36.3679]},
           "properties":{"index":4,"pointType":"EP"}}
        ]}
    """.trimIndent()

    @Test
    fun readsTotalsAndJoinsLineStrings() {
        val r = TmapRoute.parse(sample)
        assertEquals(180.0, r.totalDistanceM, 1e-9)
        assertEquals(150.0, r.totalTimeSec, 1e-9)
        assertEquals(1.2, r.tmapSpeedMps!!, 1e-9)
        // 두 선이 공유하는 꺾이는 점은 한 번만 들어간다.
        assertEquals(3, r.route.points.size)
        assertEquals(GeoPoint(36.3670, 127.3440), r.route.points.first())
        assertEquals(GeoPoint(36.3679, 127.3449), r.route.points.last())
    }

    @Test
    fun demoRoutesFromAssetsMatchTmapTotal() {
        // 앱에 넣은 시연 경로. 경로 선 길이가 TMAP 총거리와 2% 안에서 맞아야 한다.
        val files = File("../wear/src/main/assets/routes").listFiles { f -> f.extension == "json" }!!
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val r = TmapRoute.parse(file.readText())
            val diff = kotlin.math.abs(r.route.totalDistance - r.totalDistanceM) / r.totalDistanceM
            assertTrue("${file.name}: 경로 선 ${r.route.totalDistance}m vs TMAP ${r.totalDistanceM}m", diff < 0.02)
        }
    }
}

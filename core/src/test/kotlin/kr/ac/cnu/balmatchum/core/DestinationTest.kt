package kr.ac.cnu.balmatchum.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DestinationTest {

    @Test
    fun survivesPhoneToWatchJson() {
        // 휴대폰이 보낸 JSON을 워치가 그대로 되살릴 수 있어야 한다.
        val tmapJson = File("../wear/src/main/assets/routes/eng5_to_meri.json").readText()
        val sent = Destination("공대 5호관 → 경영경제연구소", tmapJson, Destination.Source.DEMO)
        val received = Destination.fromJson(sent.toJson())
        assertEquals(sent.name, received.name)
        assertEquals(Destination.Source.PHONE, received.source)
        assertEquals(sent.tmap.totalDistanceM, received.tmap.totalDistanceM, 1e-9)
        assertEquals(sent.tmap.route.points.size, received.tmap.route.points.size)
    }
}

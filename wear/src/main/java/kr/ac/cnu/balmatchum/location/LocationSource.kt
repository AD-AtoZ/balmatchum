package kr.ac.cnu.balmatchum.location

import kr.ac.cnu.balmatchum.core.LocationSample

/**
 * 위치 표본을 1초쯤마다 내보내는 곳. 실제 위치([FusedLocationSource])와 가상 걷기([ReplayLocationSource])가
 * 같은 모양이라, 안내 서비스는 어느 쪽인지 몰라도 된다.
 */
interface LocationSource {
    fun start(onSample: (LocationSample) -> Unit)
    fun stop()
}

package kr.ac.cnu.balmatchum.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.ac.cnu.balmatchum.core.LocationSample
import kr.ac.cnu.balmatchum.core.Route

/**
 * 가상 걷기. 경로를 따라 [speedMps]로 걷는 것처럼 1초마다 가짜 위치를 낸다.
 * 실외에 나가지 않고 에뮬레이터에서 상태 변화와 진동 시점을 확인할 때 쓴다.
 * 시간은 실제 시계를 그대로 쓰므로, 상태 변화를 빨리 보려면 목표 시각을 빠듯하게 잡는다.
 */
class ReplayLocationSource(
    private val route: Route,
    private val speedMps: Double,
    private val scope: CoroutineScope,
) : LocationSource {

    private var job: Job? = null

    override fun start(onSample: (LocationSample) -> Unit) {
        val startedAt = System.currentTimeMillis()
        job = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val walked = speedMps * (now - startedAt) / 1000.0
                onSample(
                    LocationSample(
                        timeMillis = now,
                        point = route.pointAt(walked),
                        speedMps = if (walked < route.totalDistance) speedMps else 0.0,
                        accuracyM = 5.0,
                    ),
                )
                delay(1_000)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }
}

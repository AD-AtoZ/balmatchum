package kr.ac.cnu.balmatchum.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kr.ac.cnu.balmatchum.core.GeoPoint
import kr.ac.cnu.balmatchum.core.LocationSample

/**
 * 실제 위치. Android의 Fused Location이 GPS·Wi-Fi·기지국을 알아서 합쳐 준다.
 * 교내처럼 Wi-Fi가 많은 곳에서는 Wi-Fi가 위치를 보정하는 데 쓰인다(설정 → 위치 → Wi-Fi 검색이 켜져 있어야 함).
 * 위치 권한은 안내를 시작하기 전에 화면에서 받아 둔다.
 *
 * 요청 설정
 * - 정밀 위치만 받는다(Granularity FINE). 대략 위치로 떨어뜨리지 않게
 * - 정확한 위치가 잡힐 때까지 잠깐 기다린다(setWaitForAccurateLocation). 안내 시작 직후 튀는 첫 위치를 줄인다
 * - 위치를 모아서 늦게 주지 않는다(setMaxUpdateDelayMillis 0). 1초마다 바로 받는다
 */
class FusedLocationSource(context: Context) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(onSample: (LocationSample) -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setWaitForAccurateLocation(true)
            .setMaxUpdateDelayMillis(0)
            .build()
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { onSample(it.toSample()) }
            }
        }
        callback = cb
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    /**
     * GPS를 미리 깨운다. 앱 화면이 열릴 때 한 번 불러 두면, 안내를 시작할 때 이미 위성이 잡혀 있어
     * 첫 위치가 덜 튄다. 결과 위치는 쓰지 않는다. 권한이 없으면 아무것도 하지 않는다.
     */
    @SuppressLint("MissingPermission")
    fun warmUp() {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setGranularity(Granularity.GRANULARITY_FINE)
            .setDurationMillis(WARM_UP_MILLIS)
            .build()
        runCatching { client.getCurrentLocation(request, null) }
    }

    private fun Location.toSample() = LocationSample(
        timeMillis = time,
        point = GeoPoint(latitude, longitude),
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
        const val WARM_UP_MILLIS = 30_000L
    }
}

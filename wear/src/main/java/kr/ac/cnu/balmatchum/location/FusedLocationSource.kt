package kr.ac.cnu.balmatchum.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
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
 */
class FusedLocationSource(context: Context) : LocationSource {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start(onSample: (LocationSample) -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS)
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

    private fun Location.toSample() = LocationSample(
        timeMillis = time,
        point = GeoPoint(latitude, longitude),
        speedMps = if (hasSpeed()) speed.toDouble() else null,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )

    private companion object {
        const val INTERVAL_MILLIS = 1_000L
    }
}

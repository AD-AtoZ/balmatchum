package kr.ac.cnu.balmatchum

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kr.ac.cnu.balmatchum.core.GeoPoint
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PhoneLocationException(message: String) : Exception(message)

/** 전송할 때만 한 번 측정한다. 화면이 닫히거나 제한 시간이 지나면 요청도 취소한다. */
class PhoneLocation(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPrecisePermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // 호출 직전에 권한 확인, 철회 시 SecurityException은 화면에서 처리.
    suspend fun currentPoint(): GeoPoint {
        if (!hasPrecisePermission()) {
            throw PhoneLocationException("현재 위치에서 출발하려면 위치 권한에서 ‘정확한 위치’를 허용해 주세요.")
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            throw PhoneLocationException("휴대폰의 위치 서비스가 꺼져 있어요. 위치 설정을 켜고 다시 보내 주세요.")
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(10_000)
            .setDurationMillis(20_000)
            .build()
        val location = withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val token = CancellationTokenSource()
                continuation.invokeOnCancellation { token.cancel() }
                // 위에서 권한을 확인하며, 측정 중 철회된 권한은 호출 측에서 안내한다.
                client.getCurrentLocation(request, token.token)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } ?: throw PhoneLocationException("현재 위치를 찾지 못했어요. 하늘이 보이는 곳에서 다시 보내 주세요.")
        return GeoPoint(location.latitude, location.longitude)
    }
}

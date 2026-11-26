package kr.ac.cnu.balmatchum.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kr.ac.cnu.balmatchum.data.DemoRoutes
import kr.ac.cnu.balmatchum.data.PhoneDestinationStore
import kr.ac.cnu.balmatchum.data.SpeedStore
import kr.ac.cnu.balmatchum.guidance.GuidanceRequest
import kr.ac.cnu.balmatchum.guidance.GuidanceService
import kr.ac.cnu.balmatchum.location.FusedLocationSource

/**
 * 워치 앱 시작점. 권한 요청, 시연 경로 읽기, 안내 서비스 시작·종료만 하고 화면은 [BalmatchumApp]에 맡긴다.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        warmUpGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        PhoneDestinationStore.load(this)
        val demo = DemoRoutes.load(this)
        val speedStore = SpeedStore(applicationContext)
        requestMissingPermissions()

        setContent {
            BalmatchumApp(
                demoDestinations = demo,
                speedStore = speedStore,
                startGuidance = ::startGuidance,
                stopGuidance = { GuidanceService.stop(this) },
                confirmCheckpoint = { GuidanceService.checkpoint(this) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        warmUpGps()
    }

    /** 화면이 열릴 때 GPS를 미리 깨워 둔다. 안내 시작 직후 첫 위치가 튀는 것을 줄인다. */
    private fun warmUpGps() {
        if (hasLocationPermission()) FusedLocationSource(applicationContext).warmUp()
    }

    private fun startGuidance(request: GuidanceRequest) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "위치 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            requestMissingPermissions()
            return
        }
        GuidanceService.start(this, request)
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestMissingPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    private companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            // 걸음 감지 센서. GPS가 나쁜 구간을 걸음 수로 메울 때 쓴다. 거절해도 안내는 된다
            Manifest.permission.ACTIVITY_RECOGNITION,
        )
    }
}

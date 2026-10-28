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

/**
 * 워치 앱 시작점. 권한 요청, 시연 경로 읽기, 안내 서비스 시작·종료만 하고 화면은 [BalmatchumApp]에 맡긴다.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

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
            )
        }
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
        )
    }
}

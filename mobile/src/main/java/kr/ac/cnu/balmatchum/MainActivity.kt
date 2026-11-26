package kr.ac.cnu.balmatchum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kr.ac.cnu.balmatchum.ui.theme.BalmatchumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalmatchumTheme {
                // 실내 시연(카메라 추적) 화면을 보고 있는지.
                var indoor by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = indoor) { indoor = false }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (indoor) {
                        IndoorTrackingScreen(Modifier.padding(innerPadding), onClose = { indoor = false })
                    } else {
                        Column(Modifier.padding(innerPadding)) {
                            TextButton(onClick = { indoor = true }) { Text("실내 시연 (카메라로 위치 추적)") }
                            PlaceSearchScreen(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // 카메라 추적은 앱이 화면에 보일 때만 돌린다.
    override fun onResume() {
        super.onResume()
        IndoorTracking.active?.resume()
    }

    override fun onPause() {
        IndoorTracking.active?.pause()
        super.onPause()
    }
}

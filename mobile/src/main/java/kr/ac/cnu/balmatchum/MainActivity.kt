package kr.ac.cnu.balmatchum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.GeoPoint
import kr.ac.cnu.balmatchum.core.TmapApi
import kr.ac.cnu.balmatchum.ui.theme.BalmatchumTheme

/**
 * 휴대폰 앱. 지금은 워치 연결 확인과 "시험 목적지 보내기"만 있다.
 * 장소 검색(TODOLIST T9)과 현재 위치에서 경로 받아 보내기(T10)는 이 위에 만든다.
 * 쓸 도구: [TmapApi.searchPois], [TmapApi.pedestrianRouteJson], [WatchLink.sendDestination]
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalmatchumTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LinkTestScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/** 공대 5호관 입구 → 경영경제연구소 남쪽 입구. 시험용 고정 좌표. */
private val TEST_START = GeoPoint(36.36621027, 127.34446567)
private val TEST_END = GeoPoint(36.3669704632505, 127.346337519953)

@Composable
private fun LinkTestScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val link = remember { WatchLink(context) }
    val tmap = remember { TmapApi(BuildConfig.TMAP_APP_KEY) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("발맞춤 (휴대폰)")
        Button(onClick = {
            link.connectedWatches { status = if (it.isEmpty()) "연결된 워치 없음" else "연결된 워치: ${it.joinToString()}" }
        }) { Text("연결된 워치 확인") }
        Button(onClick = {
            status = "경로 받는 중…"
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) { tmap.pedestrianRouteJson(TEST_START, TEST_END, "공대 5호관", "경영경제연구소") }
                }
                result.onSuccess { json ->
                    link.sendDestination(Destination("시험: 공대 5호관 → 경영경제연구소", json)) { n ->
                        status = if (n > 0) "워치 ${n}대로 보냈습니다" else "보내지 못했습니다 (워치 연결 확인)"
                    }
                }.onFailure { status = "경로 받기 실패: ${it.message}" }
            }
        }) { Text("시험 목적지 워치로 보내기") }
        if (BuildConfig.TMAP_APP_KEY.isBlank()) Text("local.properties에 TMAP_APP_KEY가 없습니다")
        Text(status)
    }
}

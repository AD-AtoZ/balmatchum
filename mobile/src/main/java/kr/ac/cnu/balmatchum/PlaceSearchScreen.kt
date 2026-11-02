package kr.ac.cnu.balmatchum

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.GeoPoint
import kr.ac.cnu.balmatchum.core.TmapApi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** T10에서 현재 위치를 연결하기 전까지 사용하는 시험 출발지. */
private val DEMO_START = GeoPoint(36.36621027, 127.34446567)

private val PoiListSaver = listSaver<List<TmapApi.Poi>, String>(
    save = { pois -> pois.flatMap { listOf(it.name, it.address, it.entrance.lat.toString(), it.entrance.lon.toString()) } },
    restore = { values -> values.chunked(4).map { TmapApi.Poi(it[0], it[1], GeoPoint(it[2].toDouble(), it[3].toDouble())) } },
)

@Composable
fun PlaceSearchScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val link = remember { WatchLink(context.applicationContext) }
    val tmap = remember { TmapApi(BuildConfig.TMAP_APP_KEY) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by rememberSaveable(stateSaver = PoiListSaver) { mutableStateOf<List<TmapApi.Poi>>(emptyList()) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val selected = results.getOrNull(selectedIndex)
    var searched by rememberSaveable { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var deadline by rememberSaveable { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zone = ZoneId.systemDefault()
    val busy = searching || sending
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(3 + results.size)
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    fun search() {
        if (busy || query.isBlank()) return
        keyboard?.hide()
        searching = true
        searched = false
        selectedIndex = -1
        results = emptyList()
        searchError = null
        status = ""
        val keyword = query.trim()
        scope.launch {
            try {
                results = withContext(Dispatchers.IO) { tmap.searchPois(keyword) }
                searched = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                searchError = "검색하지 못했어요. 인터넷 연결을 확인하고 다시 검색해 주세요."
            } finally {
                searching = false
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
    ) {
        item {
            Text("발맞춤", style = MaterialTheme.typography.headlineMedium)
            Text("어디까지 걸어갈까요?", style = MaterialTheme.typography.titleMedium)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    results = emptyList()
                    selectedIndex = -1
                    searched = false
                    searchError = null
                    status = ""
                },
                label = { Text("장소 이름") },
                placeholder = { Text("충남대학교 공과대학5호관") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = { search() }, enabled = !busy && query.isNotBlank() && BuildConfig.TMAP_APP_KEY.isNotBlank()) {
                Text("장소 검색")
            }
            if (BuildConfig.TMAP_APP_KEY.isBlank()) Text("TMAP 앱 키를 설정해 주세요.")
            if (searching) {
                CircularProgressIndicator()
                Text("장소를 찾는 중…")
            }
            searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (searched && results.isEmpty()) Text("검색 결과가 없어요. 다른 장소 이름으로 검색해 주세요.")
            if (results.isNotEmpty()) Text("검색 결과 ${results.size}개 · 목적지를 골라 주세요")
        }
        itemsIndexed(results) { index, poi ->
            Card(onClick = {
                selectedIndex = index
                deadline = ((System.currentTimeMillis() + 30 * 60_000 + 59_999) / 60_000) * 60_000
                status = ""
                keyboard?.hide()
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (selectedIndex == index) "✓ ${poi.name}" else poi.name, style = MaterialTheme.typography.titleMedium)
                    Text(poi.address.ifBlank { "주소 정보 없음" })
                }
            }
        }
        selected?.let { poi ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("목적지: ${poi.name}", style = MaterialTheme.typography.titleLarge)
                    Text("도착 시각", style = MaterialTheme.typography.titleMedium)
                    val arrival = Instant.ofEpochMilli(deadline).atZone(zone)
                    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("오늘" to today, "내일" to today.plusDays(1)).forEach { (label, date) ->
                            FilterChip(
                                selected = arrival.toLocalDate() == date,
                                enabled = !busy,
                                onClick = {
                                    deadline = date.atTime(arrival.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                                    status = ""
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    TextButton(enabled = !busy, onClick = {
                        TimePickerDialog(context, { _, hour, minute ->
                            deadline = arrival.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                            status = ""
                        }, arrival.hour, arrival.minute, true).show()
                    }) { Text(arrival.format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))) }
                    if (deadline <= now) Text("현재보다 나중인 도착 시각을 골라 주세요.", color = MaterialTheme.colorScheme.error)
                    Text("시험 경로: 출발지는 공대 5호관 입구로 고정됩니다. 현재 위치 연결은 T10에서 추가합니다.")
                    Button(enabled = !busy && deadline > now, onClick = {
                        if (deadline <= System.currentTimeMillis()) {
                            status = "도착 시각이 지났어요. 다시 골라 주세요."
                            return@Button
                        }
                        sending = true
                        status = "시험 경로 받는 중…"
                        val target = deadline
                        scope.launch {
                            try {
                                val json = withContext(Dispatchers.IO) {
                                    tmap.pedestrianRouteJson(DEMO_START, poi.entrance, "공대 5호관 입구", poi.name)
                                }
                                if (target <= System.currentTimeMillis()) {
                                    status = "도착 시각이 지났어요. 다시 골라 주세요."
                                    sending = false
                                } else {
                                    status = "워치로 보내는 중…"
                                    link.sendDestination(Destination(poi.name, json, arrivalTimeMillis = target)) { count ->
                                        status = if (count > 0) "워치 ${count}대로 보냈어요. 워치에서 목적지를 선택해 주세요."
                                        else "보내지 못했어요. 워치 연결을 확인하고 다시 보내 주세요."
                                        sending = false
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                status = "경로를 받지 못했어요. 인터넷 연결을 확인하고 다시 보내 주세요."
                                sending = false
                            }
                        }
                    }) { Text("시험 경로 워치로 보내기") }
                }
            }
        }
        item {
            if (sending) CircularProgressIndicator()
            Text(status)
            TextButton(enabled = !busy, onClick = {
                link.connectedWatches { names ->
                    status = if (names.isEmpty()) "연결된 워치가 없어요."
                    else "연결된 워치: ${names.joinToString()}"
                }
            }) { Text("연결된 워치 확인") }
        }
    }
}

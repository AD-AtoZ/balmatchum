package kr.ac.cnu.balmatchum.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ac.cnu.balmatchum.core.Destination
import java.io.File

/**
 * 휴대폰이 마지막으로 보내 준 목적지 하나. 파일로도 남겨서 워치 앱을 다시 켜도 목록에 보인다.
 * 받는 쪽은 [kr.ac.cnu.balmatchum.datalayer.PhoneMessageService].
 */
object PhoneDestinationStore {

    private val _latest = MutableStateFlow<Destination?>(null)
    val latest: StateFlow<Destination?> = _latest

    private fun file(context: Context) = File(context.filesDir, "phone_destination.json")

    /** 앱 시작 때 한 번 부른다. */
    fun load(context: Context) {
        val f = file(context)
        if (_latest.value == null && f.exists()) {
            _latest.value = runCatching { Destination.fromJson(f.readText()) }.getOrNull()
        }
    }

    fun save(context: Context, destination: Destination) {
        file(context).writeText(destination.toJson())
        _latest.value = destination
    }
}

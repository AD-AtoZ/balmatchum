package kr.ac.cnu.balmatchum.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kr.ac.cnu.balmatchum.core.PersonalSpeeds

private val Context.speedDataStore by preferencesDataStore(name = "personal_speeds")

/**
 * 속도 측정 화면에서 잰 내 속도를 저장한다. 앱을 껐다 켜도 남는다.
 * 아직 안 쟀으면 [speeds]가 null을 내보낸다(화면에서는 [PersonalSpeeds.DEFAULT]를 쓴다).
 */
class SpeedStore(private val context: Context) {

    private val walk = doublePreferencesKey("walk_mps")
    private val brisk = doublePreferencesKey("brisk_mps")
    private val run = doublePreferencesKey("run_mps")

    val speeds: Flow<PersonalSpeeds?> = context.speedDataStore.data.map { p ->
        val w = p[walk]
        val b = p[brisk]
        val r = p[run]
        if (w != null && b != null && r != null) PersonalSpeeds(w, b, r) else null
    }

    suspend fun save(speeds: PersonalSpeeds) {
        context.speedDataStore.edit {
            it[walk] = speeds.walkMps
            it[brisk] = speeds.briskMps
            it[run] = speeds.runMps
        }
    }

    suspend fun clear() {
        context.speedDataStore.edit { it.clear() }
    }
}

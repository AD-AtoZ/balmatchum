package kr.ac.cnu.balmatchum.data

import android.content.Context
import kr.ac.cnu.balmatchum.core.Destination

/**
 * 앱에 미리 넣어 둔 시연 경로(`assets/routes` 폴더의 json 파일들, TMAP 응답 원문).
 * 인터넷이 안 돼도 시연할 수 있게 하려고 넣었다. 새 경로를 넣으면 [NAMES]에 표시 이름도 추가한다.
 */
object DemoRoutes {

    private const val DIR = "routes"

    /** 파일 이름(확장자 제외) → 목록에 보일 이름. 순서대로 보인다. */
    private val NAMES = linkedMapOf(
        "papillon_to_eng5" to "빠삐용 → 공대 5호관",
        "eng5_to_meri" to "공대 5호관 → 경영경제연구소",
    )

    fun load(context: Context): List<Destination> {
        val files = context.assets.list(DIR).orEmpty().filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }
        val ordered = NAMES.keys.filter { it in files } + files.filter { it !in NAMES }
        return ordered.map { id ->
            val json = context.assets.open("$DIR/$id.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            Destination(name = NAMES[id] ?: id, tmapJson = json, source = Destination.Source.DEMO)
        }
    }
}

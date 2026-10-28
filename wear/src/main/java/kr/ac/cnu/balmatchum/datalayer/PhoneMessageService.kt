package kr.ac.cnu.balmatchum.datalayer

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.data.PhoneDestinationStore

/**
 * 휴대폰 앱이 보낸 메시지를 받는다. 워치 앱이 꺼져 있어도 시스템이 이 서비스를 깨워 준다.
 *
 * | 경로 | 내용 |
 * |---|---|
 * | `/balmatchum/destination` | [Destination.toJson] (목적지 이름 + TMAP 경로 JSON) |
 *
 * 휴대폰 쪽 보내는 코드는 mobile 모듈의 `WatchLink`.
 */
class PhoneMessageService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_DESTINATION -> {
                val destination = Destination.fromJson(event.data.toString(Charsets.UTF_8))
                PhoneDestinationStore.save(this, destination)
            }
        }
    }

    companion object {
        const val PATH_DESTINATION = "/balmatchum/destination"
    }
}

package kr.ac.cnu.balmatchum.location

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kr.ac.cnu.balmatchum.core.IndoorDemo
import kr.ac.cnu.balmatchum.core.IndoorPose
import kr.ac.cnu.balmatchum.core.LocationSample

/**
 * 휴대폰 카메라 추적이 보내 주는 위치. 실내 시연에서 GPS 자리에 끼운다.
 *
 * 휴대폰은 "출발선에서 앞으로·오른쪽으로 몇 m"([IndoorPose])를 보내고, 여기서 실내 시연 경로와 같은 좌표계의
 * 위치로 바꿔 내보낸다. 그래서 안내 서비스와 판단 로직은 GPS 위치를 받을 때와 똑같이 동작한다.
 * 시각은 휴대폰과 워치 시계가 어긋날 수 있어 워치가 받은 시각을 쓴다.
 */
class PhoneLocationSource(context: Context) : LocationSource {

    private val messages = Wearable.getMessageClient(context.applicationContext)
    private var listener: MessageClient.OnMessageReceivedListener? = null

    override fun start(onSample: (LocationSample) -> Unit) {
        val l = MessageClient.OnMessageReceivedListener { event ->
            if (event.path != IndoorPose.PATH) return@OnMessageReceivedListener
            val pose = IndoorPose.decode(event.data.toString(Charsets.UTF_8)) ?: return@OnMessageReceivedListener
            onSample(
                LocationSample(
                    timeMillis = System.currentTimeMillis(),
                    point = IndoorDemo.pointAt(pose.forwardM, pose.lateralM),
                    accuracyM = IndoorDemo.CAMERA_ACCURACY_M,
                ),
            )
        }
        listener = l
        messages.addListener(l)
    }

    override fun stop() {
        listener?.let { messages.removeListener(it) }
        listener = null
    }
}

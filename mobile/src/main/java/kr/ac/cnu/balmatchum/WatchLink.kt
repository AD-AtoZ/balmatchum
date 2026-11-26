package kr.ac.cnu.balmatchum

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kr.ac.cnu.balmatchum.core.Destination
import kr.ac.cnu.balmatchum.core.IndoorPose

/**
 * 휴대폰 → 워치 메시지 보내기. 받는 쪽은 wear 모듈의 `PhoneMessageService`.
 * 워치가 블루투스로 연결돼 있고, 두 앱의 패키지 이름이 같아야(kr.ac.cnu.balmatchum) 전달된다.
 */
class WatchLink(context: Context) {

    private val nodes = Wearable.getNodeClient(context)
    private val messages = Wearable.getMessageClient(context)

    /** 연결된 워치 이름 목록. 없으면 빈 목록. */
    fun connectedWatches(onResult: (List<String>) -> Unit) {
        nodes.connectedNodes
            .addOnSuccessListener { list -> onResult(list.map { it.displayName }) }
            .addOnFailureListener { onResult(emptyList()) }
    }

    /**
     * 목적지(이름 + TMAP 경로)를 연결된 모든 워치로 보낸다.
     * @param onResult 보낸 워치 수. 0이면 연결된 워치가 없거나 실패.
     */
    fun sendDestination(destination: Destination, onResult: (Int) -> Unit) {
        val bytes = destination.toJson().toByteArray(Charsets.UTF_8)
        nodes.connectedNodes
            .addOnSuccessListener { list ->
                if (list.isEmpty()) return@addOnSuccessListener onResult(0)
                var done = 0
                var sent = 0
                list.forEach { node ->
                    messages.sendMessage(node.id, PATH_DESTINATION, bytes)
                        .addOnSuccessListener { sent++ }
                        .addOnCompleteListener { if (++done == list.size) onResult(sent) }
                }
            }
            .addOnFailureListener { onResult(0) }
    }

    /** 위치를 보낼 워치 목록. 매번 찾으면 느려서 [NODE_REFRESH_MILLIS]마다 다시 찾는다. */
    @Volatile
    private var poseNodeIds: List<String> = emptyList()
    private var nodesCheckedAt = 0L

    /** 지금 위치를 받고 있는 워치 수. */
    val poseWatchCount: Int get() = poseNodeIds.size

    /**
     * 실내 시연에서 카메라로 잰 위치를 연결된 워치로 보낸다. 1초에 두 번쯤 부른다.
     * 받는 쪽은 wear 모듈의 `PhoneLocationSource`.
     */
    fun sendIndoorPose(pose: IndoorPose) {
        val now = System.currentTimeMillis()
        if (now - nodesCheckedAt > NODE_REFRESH_MILLIS) {
            nodesCheckedAt = now
            nodes.connectedNodes.addOnSuccessListener { list -> poseNodeIds = list.map { it.id } }
        }
        val bytes = pose.encode().toByteArray(Charsets.UTF_8)
        poseNodeIds.forEach { id -> messages.sendMessage(id, IndoorPose.PATH, bytes) }
    }

    companion object {
        const val PATH_DESTINATION = "/balmatchum/destination"
        private const val NODE_REFRESH_MILLIS = 5_000L
    }
}

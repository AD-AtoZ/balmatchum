package kr.ac.cnu.balmatchum

import android.app.Activity
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kr.ac.cnu.balmatchum.core.IndoorFrame
import kr.ac.cnu.balmatchum.core.IndoorPose
import kr.ac.cnu.balmatchum.core.MapMatcher
import kr.ac.cnu.balmatchum.core.MapTransform
import kr.ac.cnu.balmatchum.core.MatchResult
import kr.ac.cnu.balmatchum.core.TurnCoverage
import kr.ac.cnu.balmatchum.core.WallMap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.hypot

/** 실내 위치를 잡는 단계. */
enum class LocateStep {
    /** 아직 지도에서 어디인지 모른다. */
    NONE,

    /** 제자리에서 한 바퀴 돌며 주변 점을 모으는 중. */
    SCANNING,

    /** 모은 점을 지도 전체에 대 보는 중. */
    SEARCHING,

    /** 후보를 찾았지만 다른 자리와 헷갈린다. 사용자가 쓸지 정한다. */
    UNSURE,

    /** 지도에서 위치를 찾았다. 워치로 보낸다. */
    LOCATED,
}

/** [IndoorTracker]가 화면마다 알려 주는 상태. */
data class TrackerUpdate(
    /** 카메라가 지금 움직임을 추적하고 있는지. 벽만 보이거나 너무 어두우면 false가 된다. */
    val tracking: Boolean,
    /** 지도 기준 위치. 아직 위치를 못 찾았거나 추적이 끊기면 null. */
    val pose: IndoorPose?,
    /** 카메라가 충분히 앞을 보고 있어 손으로 출발선을 정할 수 있는지. */
    val canAlign: Boolean,
    val step: LocateStep = LocateStep.NONE,
    /** 한 바퀴 돌기에서 지금까지 둘러본 각도. */
    val scanDegrees: Int = 0,
    /** 지금 모아 둔 주변 점 개수. */
    val pointCount: Int = 0,
    /** 마지막 지도 맞추기 결과. */
    val lastMatch: MatchResult? = null,
    /** 마지막으로 지도 맞추기에 성공한 시각(ms). */
    val lastMatchAt: Long = 0,
    /** 마지막 지도 맞추기에 쓴 카메라 점을 지도 좌표로 옮긴 것(forward, lateral 순서로 이어 붙임). 미니맵에 그린다. */
    val seenPoints: FloatArray? = null,
    /** 직전 위치 찾기가 실패했으면 true. 한 바퀴 더 돌라고 안내할 때 쓴다. */
    val lastSearchFailed: Boolean = false,
)

/**
 * 실내 시연용 카메라 추적. 실내에서는 GPS가 잡히지 않아, 휴대폰 카메라가 "눈" 역할을 한다.
 *
 * 1. ARCore가 카메라 영상과 움직임 센서로 휴대폰이 얼마나, 어느 쪽으로 움직였는지 추적하고, 여러 장의 사진에서
 *    벽 모서리·무늬 같은 주변 지점의 3차원 위치(점)를 계산한다.
 * 2. 처음에 제자리에서 한 바퀴 돌며 모은 점을 스캔 벽 지도([WallMap]) 전체에 대 보고, 가장 잘 겹치는 자리를 찾는다([MapMatcher.localize]).
 * 3. 걷는 동안 1초마다 최근 점을 지도 근처에 다시 대 보고, 추적이 틀어진 만큼 조금씩 바로잡는다([MapMatcher.refine]).
 * 4. 지도 기준 위치를 [IndoorPose]로 [onUpdate]에 알려 준다. 워치로 보내는 일은 화면이 한다.
 *
 * ARCore는 화면을 그리는 스레드에서만 갱신할 수 있어서 [GLSurfaceView.Renderer]로 만든다.
 * 지도 맞추기는 무거워서 별도 스레드에서 하고, [onUpdate]는 그리기 스레드에서 불린다.
 */
class IndoorTracker(
    private val activity: Activity,
    private val wallMap: WallMap?,
    private val onUpdate: (TrackerUpdate) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile
    private var session: Session? = null

    /** 추적 좌표 → 지도 좌표. 위치를 찾기 전에는 null. */
    @Volatile
    private var transform: MapTransform? = null

    @Volatile
    private var step = LocateStep.NONE

    @Volatile
    private var pendingResult: MatchResult? = null

    @Volatile
    private var lastMatch: MatchResult? = null

    @Volatile
    private var lastMatchAt = 0L

    @Volatile
    private var seenPoints: FloatArray? = null

    @Volatile
    private var lastSearchFailed = false

    /** 걷는 중 지도로 바로잡기를 할지. 끄면 처음 찾은 위치에서 카메라 이동량만 쓴다. */
    @Volatile
    var refineEnabled = true

    @Volatile
    private var markStartRequested = false

    @Volatile
    private var matching = false

    @Volatile
    private var scanResetRequested = false

    /** 둘러본 각도만 비운다(모은 점은 둔다). 위치 찾기에 실패했을 때. */
    @Volatile
    private var coverageResetRequested = false

    @Volatile
    private var searchRequested = false

    private val matcher = wallMap?.let { MapMatcher(it) }
    private val worker = Executors.newSingleThreadExecutor()
    private val coverage = TurnCoverage()

    /** 주변 점. ARCore가 점마다 붙여 주는 번호로 묶어 가장 최근 위치만 둔다. 그리기 스레드에서만 만진다. */
    private val points = HashMap<Int, StoredPoint>()
    private var lastPointCloudTime = 0L
    private var lastRefineAt = 0L

    private var installRequested = false
    private var surface: GLSurfaceView? = null
    private val background = CameraBackground()
    private var cameraTexture = -1
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportChanged = false

    private class StoredPoint(val x: Float, val y: Float, val z: Float, val seenAt: Long)

    /** 화면에 넣을 뷰가 만들어지면 알려 준다. 카메라 영상이 여기에 그려진다. */
    fun attach(view: GLSurfaceView) {
        view.preserveEGLContextOnPause = true
        view.setEGLContextClientVersion(2)
        view.setRenderer(this)
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view.keepScreenOn = true
        surface = view
    }

    /**
     * 추적을 시작하거나 이어 간다. 카메라 권한을 받은 뒤에 부른다.
     * @return 시작하지 못했으면 사용자에게 보여 줄 이유, 잘 됐으면 null
     */
    fun resume(): String? {
        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return "Google Play AR 서비스를 설치한 뒤 다시 열어 주세요"
                    }
                    else -> Unit
                }
                val created = Session(activity)
                created.configure(
                    Config(created).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                        // 이동 추적과 주변 점만 쓰므로 바닥·벽 찾기와 조명 추정은 끈다.
                        planeFindingMode = Config.PlaneFindingMode.DISABLED
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                    },
                )
                session = created
            }
            session?.resume()
            surface?.onResume()
            return null
        } catch (e: Exception) {
            session = null
            return "카메라 추적을 시작할 수 없습니다: ${e.javaClass.simpleName}"
        }
    }

    /** 앱이 뒤로 가거나 화면을 떠날 때. */
    fun pause() {
        surface?.onPause()
        session?.pause()
    }

    /** 다 썼을 때. 카메라를 놓아준다. */
    fun close() {
        session?.close()
        session = null
        transform = null
        worker.shutdownNow()
    }

    /** 제자리에서 한 바퀴 돌며 출발 위치 찾기를 시작한다. 지도가 없으면 아무것도 하지 않는다. */
    fun startScan() {
        if (matcher == null) return
        transform = null
        pendingResult = null
        lastSearchFailed = false
        step = LocateStep.SCANNING
        scanResetRequested = true
    }

    /** 한 바퀴를 다 돌기 전이라도 지금까지 모은 점으로 찾는다. */
    fun searchNow() {
        if (step == LocateStep.SCANNING) searchRequested = true
    }

    /** 헷갈린다고 나온 후보를 그래도 쓴다. */
    fun acceptPending() {
        val result = pendingResult ?: return
        transform = result.transform
        pendingResult = null
        step = LocateStep.LOCATED
    }

    /** 지금 서 있는 곳과 바라보는 방향을 출발선(앞으로 0m, 옆으로 0m)으로 삼는다. 지도 없이도 쓸 수 있는 예비 방법. */
    fun markStart() {
        markStartRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        cameraTexture = background.create()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val current = session ?: return
        try {
            if (viewportChanged) {
                current.setDisplayGeometry(activity.display?.rotation ?: Surface.ROTATION_0, viewportWidth, viewportHeight)
                viewportChanged = false
            }
            current.setCameraTextureName(cameraTexture)
            val frame = current.update()
            background.draw(frame)

            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                onUpdate(status(tracking = false, pose = null, canAlign = false))
                return
            }
            val now = System.currentTimeMillis()
            val cam = camera.pose
            val camX = cam.tx().toDouble()
            val camY = cam.ty()
            val camZ = cam.tz().toDouble()
            // 카메라 좌표계에서 -z가 바라보는 방향이다. 그 방향의 수평 성분(x, z)만 쓴다.
            val zAxis = cam.zAxis
            val viewX = -zAxis[0].toDouble()
            val viewZ = -zAxis[2].toDouble()
            val canAlign = IndoorFrame.canAlign(viewX, viewZ)

            if (scanResetRequested) {
                scanResetRequested = false
                points.clear()
                coverage.clear()
            }
            if (coverageResetRequested) {
                coverageResetRequested = false
                coverage.clear()
            }
            collectPoints(frame, now)
            if (step == LocateStep.SCANNING) {
                coverage.add(viewX, viewZ)
                if ((coverage.enough || searchRequested) && points.size >= MIN_SCAN_POINTS) {
                    searchRequested = false
                    startLocalize(camX, camY, camZ, now)
                }
            }
            if (markStartRequested && canAlign) {
                markStartRequested = false
                // 바라보는 방향이 지도의 앞쪽(forward)이 되도록 돌리고, 지금 자리를 출발선으로 놓는다.
                transform = MapTransform.placing(camX, camZ, 0.0, 0.0, -atan2(viewZ, viewX))
                pendingResult = null
                step = LocateStep.LOCATED
            }
            if (step == LocateStep.LOCATED && refineEnabled && matcher != null && !matching &&
                now - lastRefineAt >= REFINE_INTERVAL_MILLIS
            ) {
                lastRefineAt = now
                startRefine(camX, camY, camZ, now)
            }

            val t = transform
            onUpdate(status(tracking = true, pose = t?.toPose(camX, camZ, viewX, viewZ, now), canAlign = canAlign))
        } catch (e: Exception) {
            // 화면이 꺼지는 순간 등 카메라를 잠깐 쓸 수 없을 때. 다음 화면에서 다시 시도한다.
        }
    }

    private fun status(tracking: Boolean, pose: IndoorPose?, canAlign: Boolean) = TrackerUpdate(
        tracking = tracking,
        pose = pose,
        canAlign = canAlign,
        step = step,
        scanDegrees = coverage.degrees,
        pointCount = points.size,
        lastMatch = pendingResult ?: lastMatch,
        lastMatchAt = lastMatchAt,
        seenPoints = seenPoints,
        lastSearchFailed = lastSearchFailed,
    )

    /** 이번 화면의 주변 점을 모은다. 오래된 점은 버린다(한 바퀴 돌기 중에는 모두 둔다). */
    private fun collectPoints(frame: Frame, now: Long) {
        frame.acquirePointCloud().use { cloud ->
            if (cloud.timestamp == lastPointCloudTime) return
            lastPointCloudTime = cloud.timestamp
            val xyzc = cloud.points
            val ids = cloud.ids
            val n = ids.remaining()
            for (i in 0 until n) {
                val confidence = xyzc.get(i * 4 + 3)
                if (confidence < MIN_CONFIDENCE) continue
                points[ids.get(i)] = StoredPoint(xyzc.get(i * 4), xyzc.get(i * 4 + 1), xyzc.get(i * 4 + 2), now)
            }
        }
        if (step != LocateStep.SCANNING && step != LocateStep.SEARCHING) {
            points.values.removeAll { now - it.seenAt > KEEP_MILLIS }
        }
        if (points.size > MAX_STORED_POINTS) {
            val oldest = points.entries.sortedBy { it.value.seenAt }.take(points.size - MAX_STORED_POINTS).map { it.key }
            oldest.forEach { points.remove(it) }
        }
    }

    /** 지도 맞추기에 쓸 점: 카메라와 비슷한 높이(바닥·천장 제외), 가까운 것만. */
    private fun snapshot(camX: Double, camY: Float, camZ: Double): DoubleArray {
        val out = ArrayList<Double>(points.size * 2)
        for (p in points.values) {
            val dy = p.y - camY
            if (dy < HEIGHT_BELOW || dy > HEIGHT_ABOVE) continue
            if (hypot(p.x - camX, p.z - camZ) > MAX_RANGE_M) continue
            out += p.x.toDouble()
            out += p.z.toDouble()
        }
        return out.toDoubleArray()
    }

    private fun startLocalize(camX: Double, camY: Float, camZ: Double, now: Long) {
        val m = matcher ?: return
        val pts = snapshot(camX, camY, camZ)
        step = LocateStep.SEARCHING
        matching = true
        worker.execute {
            val result = runCatching { m.localize(pts, camX, camZ) }.getOrNull()
            if (result != null) seenPoints = toMap(pts, result.transform)
            matching = false
            when {
                result == null || result.score < ACCEPT_SCORE -> {
                    // 점이 모자라거나 잘 맞는 자리가 없다. 모은 점은 두고, 한 바퀴를 새로 돌아야 다시 찾게 한다.
                    // 둘러본 각도를 비우지 않으면 다음 화면에서 곧바로 다시 찾느라 무거운 계산이 계속 돈다.
                    lastMatch = result
                    lastSearchFailed = true
                    coverageResetRequested = true
                    step = LocateStep.SCANNING
                }
                result.ambiguous -> {
                    lastSearchFailed = false
                    pendingResult = result
                    step = LocateStep.UNSURE
                }
                else -> {
                    lastSearchFailed = false
                    lastMatch = result
                    lastMatchAt = now
                    transform = result.transform
                    step = LocateStep.LOCATED
                }
            }
        }
    }

    private fun startRefine(camX: Double, camY: Float, camZ: Double, now: Long) {
        val m = matcher ?: return
        val current = transform ?: return
        val pts = snapshot(camX, camY, camZ)
        matching = true
        worker.execute {
            val result = runCatching { m.refine(pts, current, camX, camZ) }.getOrNull()
            matching = false
            if (result != null) {
                seenPoints = toMap(pts, result.transform)
                lastMatch = result
                lastMatchAt = now
                // 그사이 사용자가 다시 찾기를 눌렀으면 덮어쓰지 않는다. 한 번에 튀지 않게 절반만 옮긴다.
                if (step == LocateStep.LOCATED && transform === current) {
                    transform = current.blend(result.transform, REFINE_WEIGHT)
                }
            }
        }
    }

    /** 추적 좌표의 점(x, z)을 지도 좌표(forward, lateral)로 옮긴다. 미니맵에 많이 그리지 않게 [MAX_SHOWN_POINTS]개까지만. */
    private fun toMap(xz: DoubleArray, t: MapTransform): FloatArray {
        val n = minOf(xz.size / 2, MAX_SHOWN_POINTS)
        val stride = maxOf(1, (xz.size / 2) / n)
        return FloatArray(n * 2).also { out ->
            for (i in 0 until n) {
                val x = xz[2 * i * stride]
                val z = xz[2 * i * stride + 1]
                out[2 * i] = t.forward(x, z).toFloat()
                out[2 * i + 1] = t.lateral(x, z).toFloat()
            }
        }
    }

    private companion object {
        const val MAX_SHOWN_POINTS = 300

        /** ARCore 점의 확신도(0~1)가 이보다 낮으면 버린다. */
        const val MIN_CONFIDENCE = 0.2f

        /** 한 바퀴 돌기에서 이만큼은 모여야 찾기를 시작한다. */
        const val MIN_SCAN_POINTS = 120

        /** 걷는 중에는 이 시간(ms) 안에 본 점만 둔다. */
        const val KEEP_MILLIS = 15_000L
        const val MAX_STORED_POINTS = 4_000

        /** 카메라 높이 기준으로 이 사이의 점만 쓴다(m). 지도는 바닥에서 0.4~2.0m 높이의 벽으로 만들었다. */
        const val HEIGHT_BELOW = -1.0f
        const val HEIGHT_ABOVE = 0.8f
        const val MAX_RANGE_M = 12.0

        const val REFINE_INTERVAL_MILLIS = 1_000L
        const val REFINE_WEIGHT = 0.5

        /** 처음 위치 찾기 결과를 받아들이는 최소 점수. [kr.ac.cnu.balmatchum.core.MatchConfig.acceptScore]와 같게 둔다. */
        const val ACCEPT_SCORE = 0.45
    }
}

/** 카메라 영상을 화면 가득 그린다. ARCore가 카메라 영상을 넣어 줄 텍스처도 여기서 만든다. */
private class CameraBackground {

    private var program = 0
    private var texture = 0
    private var texCoordsReady = false
    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords: FloatBuffer = floatBuffer(FloatArray(8))

    /** @return ARCore에 넘길 카메라 텍스처 이름 */
    fun create(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(program)
        texCoordsReady = false
        return texture
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged() || !texCoordsReady) {
            quad.position(0)
            texCoords.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, texCoords,
            )
            texCoordsReady = true
        }
        if (frame.timestamp == 0L) return // 아직 카메라 영상이 한 장도 오지 않았다

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        quad.position(0)
        texCoords.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(texCoord)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private companion object {
        const val VERTEX = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }
    }
}

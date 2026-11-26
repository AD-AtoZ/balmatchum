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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** [IndoorTracker]가 화면마다 알려 주는 상태. */
data class TrackerUpdate(
    /** 카메라가 지금 움직임을 추적하고 있는지. 벽만 보이거나 너무 어두우면 false가 된다. */
    val tracking: Boolean,
    /** 출발선을 정한 뒤의 위치. 아직 출발선을 정하지 않았거나 추적이 끊기면 null. */
    val pose: IndoorPose?,
    /** 카메라가 충분히 앞을 보고 있어 출발선을 정할 수 있는지. */
    val canAlign: Boolean,
)

/**
 * 실내 시연용 카메라 추적. 실내에서는 GPS가 잡히지 않아, 휴대폰 카메라가 "눈" 역할을 한다.
 *
 * ARCore가 카메라 영상과 움직임 센서로 휴대폰이 얼마나, 어느 쪽으로 움직였는지 미터 단위로 추적한다.
 * 출발선에 서서 복도를 바라보고 [markStart]를 부르면 그 위치와 방향이 기준이 되고,
 * 그 뒤로는 "출발선에서 앞으로·오른쪽으로 몇 m"([IndoorPose])를 [onUpdate]로 알려 준다.
 *
 * ARCore는 화면을 그리는 스레드에서만 갱신할 수 있어서 [GLSurfaceView.Renderer]로 만든다.
 * [onUpdate]도 그 스레드에서 불린다.
 */
class IndoorTracker(
    private val activity: Activity,
    private val onUpdate: (TrackerUpdate) -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile
    private var session: Session? = null

    @Volatile
    private var frame: IndoorFrame? = null

    @Volatile
    private var markStartRequested = false

    private var installRequested = false
    private var surface: GLSurfaceView? = null
    private val background = CameraBackground()
    private var cameraTexture = -1
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var viewportChanged = false

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
                        // 위치 추적만 쓰므로 바닥·벽 찾기와 조명 추정은 끈다.
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
        frame = null
    }

    /** 지금 서 있는 곳과 바라보는 방향을 출발선으로 삼는다. 다음 화면부터 적용된다. */
    fun markStart() {
        markStartRequested = true
    }

    /** 출발선을 지우고 위치 보내기를 멈춘다. */
    fun clearStart() {
        frame = null
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
            val arFrame = current.update()
            background.draw(arFrame)

            val camera = arFrame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                onUpdate(TrackerUpdate(tracking = false, pose = null, canAlign = false))
                return
            }
            // 카메라 좌표계에서 -z가 바라보는 방향이다. 그 방향의 수평 성분(x, z)만 쓴다.
            val pose = camera.pose
            val zAxis = pose.zAxis
            val viewX = -zAxis[0].toDouble()
            val viewZ = -zAxis[2].toDouble()
            val canAlign = IndoorFrame.canAlign(viewX, viewZ)
            if (markStartRequested && canAlign) {
                frame = IndoorFrame(pose.tx().toDouble(), pose.tz().toDouble(), viewX, viewZ)
                markStartRequested = false
            }
            onUpdate(
                TrackerUpdate(
                    tracking = true,
                    pose = frame?.toPose(pose.tx().toDouble(), pose.tz().toDouble(), viewX, viewZ, System.currentTimeMillis()),
                    canAlign = canAlign,
                ),
            )
        } catch (e: Exception) {
            // 화면이 꺼지는 순간 등 카메라를 잠깐 쓸 수 없을 때. 다음 화면에서 다시 시도한다.
        }
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

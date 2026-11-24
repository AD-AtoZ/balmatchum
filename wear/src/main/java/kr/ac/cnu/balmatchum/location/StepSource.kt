package kr.ac.cnu.balmatchum.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * 걸음 감지 센서(TYPE_STEP_DETECTOR). 한 걸음마다 [start]에 준 함수를 부른다.
 * GPS가 나쁜 구간에서 걸음 수로 진행 거리를 메울 때 쓴다(T22 4번).
 *
 * 걸음 센서가 없거나 신체 활동 권한(ACTIVITY_RECOGNITION)이 없으면 아무것도 하지 않는다. 그래도 안내는 된다.
 */
class StepSource(context: Context) {

    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(SensorManager::class.java)
    private var listener: SensorEventListener? = null

    /** 걸음 센서를 실제로 쓰고 있으면 true. */
    val isActive: Boolean get() = listener != null

    fun start(onStep: (count: Int) -> Unit) {
        if (appContext.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) return
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onStep(event.values.firstOrNull()?.toInt()?.coerceAtLeast(1) ?: 1)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        sensors.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
    }
}

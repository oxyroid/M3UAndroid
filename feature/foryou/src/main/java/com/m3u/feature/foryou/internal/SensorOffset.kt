package com.m3u.feature.foryou.internal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun produceSensorOffset(
    context: Context = LocalContext.current
): State<Offset> = produceState(
    initialValue = Offset.Zero
) {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val (x, y, _) = event.values
            value = Offset(x, y)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    manager.registerListener(
        listener,
        sensor,
        SensorManager.SENSOR_DELAY_NORMAL
    )
    awaitDispose {
        manager.unregisterListener(listener)
    }
}
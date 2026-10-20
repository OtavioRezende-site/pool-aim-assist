package com.poolaim.app
import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import com.poolaim.calibration.CalibrationStore
import com.poolaim.physics.*
import kotlin.math.*

enum class Screen { HOME, SIMULATOR, CALIBRATION, SETTINGS, DEMO, CAPTURE, ANALYSIS, VISUAL_CALIBRATION, VISUAL_TEST }
class PoolViewModel(app: Application): AndroidViewModel(app) {
 private val store=CalibrationStore(app)
 var screen by mutableStateOf(Screen.HOME)
 var angle by mutableFloatStateOf(0f)
 var radius by mutableFloatStateOf(store.radius)
  private set
 var reflections by mutableIntStateOf(store.reflections)
  private set
 var target by mutableStateOf(Vec(1.35f,.5f))
 val cue = Vec(.45f,.5f)
 fun updateRadius(value: Float) {
  radius=value.coerceIn(.02f,.06f)
  val adjusted=Vec(target.x.coerceIn(radius,2-radius),target.y.coerceIn(radius,1-radius))
  target=if((adjusted-cue).dot(adjusted-cue)>4*radius*radius) adjusted else Vec(1.35f,.5f)
  store.radius=radius
 }
 fun updateReflections(value: Int) { reflections=value.coerceIn(0,2); store.reflections=reflections }
 fun aim(point: Vec) { angle = Math.toDegrees(atan2((point.y-cue.y).toDouble(),(point.x-cue.x).toDouble())).toFloat() }
 fun path() = Trajectory.trace(cue,Vec(cos(Math.toRadians(angle.toDouble())).toFloat(),sin(Math.toRadians(angle.toDouble())).toFloat()),target,radius,reflections)
}

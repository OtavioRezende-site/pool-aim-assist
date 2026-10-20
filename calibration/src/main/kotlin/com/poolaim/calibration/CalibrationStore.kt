package com.poolaim.calibration
import android.content.Context
/** Local simulator calibration; not a camera perspective calibration. */
class CalibrationStore(context: Context) {
 private val prefs=context.getSharedPreferences("calibration",Context.MODE_PRIVATE)
 var radius: Float
  get()=prefs.getFloat("radius",.035f).coerceIn(.02f,.06f)
  set(value) { prefs.edit().putFloat("radius",value.coerceIn(.02f,.06f)).apply() }
 var reflections: Int
  get()=prefs.getInt("reflections",2).coerceIn(0,2)
  set(value) { prefs.edit().putInt("reflections",value.coerceIn(0,2)).apply() }
}

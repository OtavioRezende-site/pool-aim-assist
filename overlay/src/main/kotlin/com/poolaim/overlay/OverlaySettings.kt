package com.poolaim.overlay

import android.content.Context

data class FloatingSettings(val x: Float=.96f,val y: Float=.02f,val sizeDp: Int=48,val opacity: Float=.85f) {
 fun sanitized()=copy(x=if(x.isFinite()) x.coerceIn(0f,1f) else .96f,
  y=if(y.isFinite()) y.coerceIn(0f,1f) else .02f,sizeDp=sizeDp.coerceIn(32,72),
  opacity=if(opacity.isFinite()) opacity.coerceIn(.3f,1f) else .85f)
}
class OverlaySettings(context: Context) {
 private val prefs=context.getSharedPreferences("floating_controls",Context.MODE_PRIVATE)
 fun read()=FloatingSettings(prefs.getFloat("x",.96f),prefs.getFloat("y",.02f),prefs.getInt("size",48),prefs.getFloat("opacity",.85f)).sanitized()
 fun observe(changed: ()->Unit): ()->Unit {
  val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed() }
  prefs.registerOnSharedPreferenceChangeListener(listener)
  return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
 }
 fun write(value: FloatingSettings) {
  val s=value.sanitized()
  prefs.edit().putFloat("x",s.x).putFloat("y",s.y).putInt("size",s.sizeDp).putFloat("opacity",s.opacity).apply()
 }
}

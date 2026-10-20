package com.poolaim.calibration

import android.content.Context
import com.poolaim.vision.*
import org.json.JSONArray
import org.json.JSONObject

class VisualCalibrationStore(context: Context) {
 private val prefs=context.getSharedPreferences("visual-calibration",Context.MODE_PRIVATE)
 fun read(): VisualSettings=try {
  val root=JSONObject(prefs.getString("settings","{}")!!)
  val raw=root.optJSONObject("manual")
  val manual=raw?.let {
   val region=TableRegion(it.getDouble("left").toFloat(),it.getDouble("top").toFloat(),it.getDouble("right").toFloat(),it.getDouble("bottom").toFloat())
   require(region.valid())
   val list=it.getJSONArray("balls"); require(list.length() in 0..16)
   val balls=(0 until list.length()).map { index -> val b=list.getJSONObject(index)
    ObservedBall(ImagePoint(b.getDouble("x").toFloat(),b.getDouble("y").toFloat()),b.getDouble("radius").toFloat()) }
   require(balls.all { b -> b.center.x in 0f..1f && b.center.y in 0f..1f && b.radius in .001f.. .1f })
   require(it.getDouble("aspect").isFinite() && it.getDouble("aspect")>0 && it.getInt("rotation") in setOf(0,90,180,270))
   ManualScene(region,balls,(0 until list.length()).map { list.getJSONObject(it).getInt("color") },it.getDouble("aspect").toFloat(),it.getInt("rotation"))
  }
  VisualSettings(manual,root.optBoolean("direction"),root.optDouble("angle",0.0).toFloat(),root.optDouble("opacity",.9).toFloat()).sanitized()
 } catch(_: Exception) { VisualSettings() }
 fun write(settings: VisualSettings) {
  val value=settings.sanitized()
  val root=JSONObject().put("direction",value.manualDirection).put("angle",value.angleDegrees).put("opacity",value.opacity)
  value.manual?.let { m ->
   val balls=JSONArray()
   m.balls.forEachIndexed { index,b -> balls.put(JSONObject().put("x",b.center.x).put("y",b.center.y).put("radius",b.radius).put("color",m.colors[index])) }
   root.put("manual",JSONObject().put("left",m.region.left).put("top",m.region.top).put("right",m.region.right).put("bottom",m.region.bottom)
    .put("aspect",m.sourceAspect).put("rotation",m.rotation).put("balls",balls))
  }
  prefs.edit().putString("settings",root.toString()).apply()
 }
}

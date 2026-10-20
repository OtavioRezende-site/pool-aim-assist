package com.poolaim.physics

import kotlin.math.sqrt

data class Vec(val x: Float, val y: Float) {
 operator fun plus(v: Vec) = Vec(x+v.x,y+v.y)
 operator fun minus(v: Vec) = Vec(x-v.x,y-v.y)
 operator fun times(s: Float) = Vec(x*s,y*s)
 fun dot(v: Vec) = x*v.x+y*v.y
 fun finite() = x.isFinite() && y.isFinite()
 fun normalized(): Vec {
  require(finite()) { "Direction must be finite" }
  val length = sqrt(x.toDouble()*x+y.toDouble()*y)
  require(length > 0) { "Direction must be nonzero" }
  return Vec((x/length).toFloat(),(y/length).toFloat())
 }
}
/** Compatibility adapter for the training UI. GeometryEngine exposes typed contact events. */
data class Segment(val from: Vec, val to: Vec, val objectBall: Boolean = false, val reflection: Int = 0)
object Trajectory {
 fun trace(start: Vec, direction: Vec, target: Vec?, radius: Float = .035f, reflections: Int = 2): List<Segment> =
  GeometryEngine().predict(Ball("cue",start,radius),direction,
   target?.let { listOf(Ball("target",it,radius)) } ?: emptyList(),reflections)
   .segments.map { Segment(it.from,it.to,it.actor==PathActor.TARGET,it.reflection) }
}

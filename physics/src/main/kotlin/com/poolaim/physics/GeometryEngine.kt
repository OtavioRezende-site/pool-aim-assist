package com.poolaim.physics

import kotlin.math.*

/** Dimensions are in table-height units by default; UV coordinates are always in [0,1]. */
data class Table(val width: Float = 2f, val height: Float = 1f) {
 init { require(width.isFinite() && height.isFinite() && width > 0 && height > 0) }
 fun fromNormalized(uv: Vec): Vec {
  require(uv.finite() && uv.x in 0f..1f && uv.y in 0f..1f)
  return Vec(uv.x * width, uv.y * height)
 }
 fun toNormalized(point: Vec): Vec {
  require(point.finite() && point.x in 0f..width && point.y in 0f..height)
  return Vec(point.x / width, point.y / height)
 }
 fun directionFromNormalized(delta: Vec) = Vec(delta.x*width,delta.y*height).normalized()
 fun contains(ball: Ball) = ball.radius * 2 <= min(width, height) &&
  ball.center.x in ball.radius..(width-ball.radius) && ball.center.y in ball.radius..(height-ball.radius)
}

data class Ball(val id: String, val center: Vec, val radius: Float) {
 init { require(id.isNotBlank() && center.finite() && radius.isFinite() && radius > 0) }
}

/** Axis-aligned screen rectangle of the playing surface, excluding the cushion border.
 * This transform preserves UV calibration, not perspective; projective calibration is future work.
 */
data class ScreenCalibration(val left: Float, val top: Float, val width: Float, val height: Float) {
 init { require(listOf(left,top,width,height).all { it.isFinite() } && width > 0 && height > 0) }
 fun toScreen(uv: Vec): Vec { require(uv.finite()); return Vec(left+uv.x*width,top+uv.y*height) }
 fun fromScreen(point: Vec): Vec { require(point.finite()); return Vec((point.x-left)/width,(point.y-top)/height) }
}

sealed interface Hit {
 val distance: Float
 val center: Vec
 data class Cushion(override val distance: Float, override val center: Vec, val vertical: Boolean, val horizontal: Boolean): Hit
 data class BallContact(override val distance: Float, override val center: Vec, val ball: Ball, val point: Vec, val normal: Vec, val incomingDirection: Vec, val grazing: Boolean): Hit
}

enum class PathActor { CUE, TARGET }
data class GeometricSegment(val from: Vec, val to: Vec, val actor: PathActor, val reflection: Int)
data class Prediction(val segments: List<GeometricSegment>, val firstHit: Hit, val contact: Hit.BallContact?)

/** Pure geometry, not Miniclip physics. Ideal normal transfer determines only the initial
 * target direction. No force, travel distance, friction, spin, restitution or pocket model is
 * inferred. These belong in a future calibrated motion model consuming these contact events.
 * After a second ball contact, the target path stops; no multi-ball dynamics are claimed.
 */
class GeometryEngine(val table: Table = Table()) {
 private val epsilon = 1e-6f

 fun firstHit(moving: Ball, direction: Vec, obstacles: List<Ball>): Hit {
  validate(moving, obstacles)
  return nearest(moving, direction.normalized(), obstacles)
 }

 fun predict(cue: Ball, direction: Vec, obstacles: List<Ball>, maxReflections: Int = 2): Prediction {
  require(maxReflections in 0..2) { "Supported reflection budget: 0..2 per ball path" }
  validate(cue, obstacles)
  val d = direction.normalized()
  val first = nearest(cue,d,obstacles)
  val segments = mutableListOf<GeometricSegment>()
  val contact = follow(cue,d,obstacles,PathActor.CUE,maxReflections,segments)
  if(contact != null && !contact.grazing && contact.incomingDirection.dot(contact.normal) > epsilon) {
   // Ideal equal-mass normal transfer: direction only, not speed or distance.
   follow(contact.ball,contact.normal,obstacles.filter { it.id != contact.ball.id },PathActor.TARGET,maxReflections,segments)
  }
  return Prediction(segments,first,contact)
 }

 private fun follow(ball: Ball, initial: Vec, others: List<Ball>, actor: PathActor, budget: Int, segments: MutableList<GeometricSegment>): Hit.BallContact? {
  var current = ball
  var direction = initial
  for(bounce in 0..budget) {
   val hit = nearest(current,direction,others)
   segments += GeometricSegment(current.center,hit.center,actor,bounce)
   if(hit is Hit.BallContact) return hit
   hit as Hit.Cushion
   direction = Vec(if(hit.vertical) -direction.x else direction.x,if(hit.horizontal) -direction.y else direction.y)
   current = current.copy(center=hit.center)
  }
  return null
 }

 private fun validate(moving: Ball, obstacles: List<Ball>) {
  val all = listOf(moving)+obstacles
  require(all.map { it.id }.distinct().size == all.size) { "Ball IDs must be unique" }
  require(all.all(table::contains)) { "Ball outside playable bounds" }
  for(i in all.indices) for(j in i+1 until all.size) {
   val delta = all[i].center-all[j].center
   val sum = all[i].radius+all[j].radius
   require(delta.dot(delta).toDouble() >= sum.toDouble()*sum-1e-7) { "Overlapping balls" }
  }
 }

 private fun nearest(moving: Ball, direction: Vec, obstacles: List<Ball>): Hit {
  val p=moving.center; val r=moving.radius
  val tx = when { direction.x > 0 -> (table.width-r-p.x)/direction.x; direction.x < 0 -> (r-p.x)/direction.x; else -> Float.POSITIVE_INFINITY }
  val ty = when { direction.y > 0 -> (table.height-r-p.y)/direction.y; direction.y < 0 -> (r-p.y)/direction.y; else -> Float.POSITIVE_INFINITY }
  val distance=min(tx,ty).coerceAtLeast(0f)
  // Clamp small rounding drift so a reflected ball remains inside the legal center rectangle.
  val end = p+direction*distance
  var result: Hit = Hit.Cushion(distance,Vec(end.x.coerceIn(r,table.width-r),end.y.coerceIn(r,table.height-r)),abs(tx-distance)<epsilon,abs(ty-distance)<epsilon)
  for(ball in obstacles) {
   val rel=p-ball.center
   val b=rel.x.toDouble()*direction.x+rel.y.toDouble()*direction.y
   val sum=r.toDouble()+ball.radius
   val c=rel.x.toDouble()*rel.x+rel.y.toDouble()*rel.y-sum*sum
   val a=direction.x.toDouble()*direction.x+direction.y.toDouble()*direction.y
   val disc=b*b-a*c
   if(b >= 0 || disc < -1e-8) continue // A ball behind us or a separating touching pair.
   // Float scene coordinates introduce small discriminant noise at tangency.
   val grazing=abs(disc)<=1e-8
   val t=((-b-sqrt(if(grazing) 0.0 else max(0.0,disc)))/a).toFloat()
   // A cushion wins coincident contacts. A grazing contact is detected but transfers no motion.
   if(t >= -epsilon && t < result.distance-epsilon) {
    val center=p+direction*t.coerceAtLeast(0f)
    val normal=(ball.center-center).normalized()
    result=Hit.BallContact(t.coerceAtLeast(0f),center,ball,center+normal*r,normal,direction,grazing)
   }
  }
  return result
 }
}

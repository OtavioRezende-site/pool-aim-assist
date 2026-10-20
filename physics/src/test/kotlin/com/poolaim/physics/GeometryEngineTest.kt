package com.poolaim.physics

import org.junit.Assert.*
import org.junit.Test

class GeometryEngineTest {
 private val engine=GeometryEngine()
 private fun cue(p: Vec=Vec(.4f,.5f), r: Float=.04f)=Ball("cue",p,r)
 private fun ball(id: String="target",p: Vec=Vec(1f,.5f),r: Float=.04f)=Ball(id,p,r)
 private fun near(a: Float,b: Float)=assertEquals(a,b,1e-5f)

 @Test fun findsNearestBallRegardlessOfListOrder() {
  val hit=engine.firstHit(cue(),Vec(1f,0f),listOf(ball("far",Vec(1.6f,.5f)),ball())) as Hit.BallContact
  assertEquals("target",hit.ball.id); near(.52f,hit.distance)
 }
 @Test fun physicalContactIsBetweenSurfacesWithDifferentRadii() {
  val hit=engine.firstHit(cue(r=.03f),Vec(10f,0f),listOf(ball(r=.06f))) as Hit.BallContact
  near(.91f,hit.center.x); near(.94f,hit.point.x)
  near(.06f,(hit.ball.center-hit.point).x); near(1f,hit.normal.x)
 }
 @Test fun wallBeforeBallOnAnotherRay() {
  assertTrue(engine.firstHit(cue(),Vec(-1f,0f),listOf(ball())) is Hit.Cushion)
 }
 @Test fun targetInitialDirectionFollowsLineOfCentersAtImpact() {
  val result=engine.predict(cue(),Vec(1f,0f),listOf(ball(p=Vec(1f,.54f))))
  val contact=result.contact!!
  val segment=result.segments.first { it.actor==PathActor.TARGET }
  val outgoing=(segment.to-segment.from).normalized()
  near(contact.normal.x,outgoing.x); near(contact.normal.y,outgoing.y)
  assertTrue(outgoing.y>0)
 }
 @Test fun touchingPairApproachingTransfersDirection() {
  val p=engine.predict(cue(Vec(.92f,.5f)),Vec(1f,0f),listOf(ball()),0)
  assertNotNull(p.contact); assertTrue(p.segments.any { it.actor==PathActor.TARGET })
 }
 @Test fun touchingPairSeparatingDoesNotCollide() {
  assertTrue(engine.firstHit(cue(Vec(.92f,.5f)),Vec(-1f,0f),listOf(ball())) is Hit.Cushion)
 }
 @Test fun detectsTangentContactWithoutInventingTargetMotion() {
  val p=engine.predict(cue(),Vec(1f,0f),listOf(ball(p=Vec(1f,.58f))),0)
  assertNotNull(p.contact)
  assertFalse(p.segments.any { it.actor==PathActor.TARGET })
 }
 @Test fun atMostTwoReflectionsPerPath() {
  val p=engine.predict(cue(),Vec(1f,.3f),emptyList(),2)
  assertEquals(3,p.segments.size)
  assertEquals(listOf(0,1,2),p.segments.map { it.reflection })
 }
 @Test fun reflectionPreservesAngleMagnitudeAndChangesNormalSign() {
  val p=engine.predict(cue(),Vec(1f,.1f),emptyList(),1)
  val incoming=(p.segments[0].to-p.segments[0].from).normalized()
  val outgoing=(p.segments[1].to-p.segments[1].from).normalized()
  near(-incoming.x,outgoing.x); near(incoming.y,outgoing.y)
 }
 @Test fun stopsTargetAtNextBallWithoutClaimingMultiBallDynamics() {
  val p=engine.predict(cue(),Vec(1f,0f),listOf(ball(),ball("second",Vec(1.5f,.5f))),0)
  val target=p.segments.last()
  assertEquals(PathActor.TARGET,target.actor); near(1.42f,target.to.x)
 }
 @Test fun trajectoriesStayInsideRadiusAdjustedBounds() {
  for(angle in 0..359 step 3) {
   val radians=Math.toRadians(angle.toDouble())
   val p=engine.predict(cue(),Vec(kotlin.math.cos(radians).toFloat(),kotlin.math.sin(radians).toFloat()),emptyList())
   p.segments.forEach { assertTrue(engine.table.contains(cue(it.to))); assertTrue(it.to.finite()) }
  }
 }
 @Test fun boundaryStartOutwardReflectsWithoutLooping() {
  val p=engine.predict(cue(Vec(.04f,.5f)),Vec(-1f,0f),emptyList(),2)
  assertEquals(3,p.segments.size); near(.04f,p.segments.first().to.x)
  assertTrue(p.segments[1].to.x > p.segments[1].from.x)
 }
 @Test fun normalizedCoordinatesRoundTripOnDifferentAspectRatio() {
  val table=Table(2.2f,1.1f); val uv=Vec(.25f,.75f)
  assertEquals(uv,table.toNormalized(table.fromNormalized(uv)))
  near(.55f,table.fromNormalized(uv).x)
 }
 @Test fun normalizedDirectionRespectsTableAspectRatio() {
  val direction=Table().directionFromNormalized(Vec(1f,1f))
  near(2f,direction.x/direction.y)
 }
 @Test fun calibrationAdaptsToDifferentScreenRectangles() {
  val uv=Vec(.2f,.8f)
  for(rect in listOf(ScreenCalibration(20f,30f,1000f,500f),ScreenCalibration(140f,80f,2400f,1200f))) {
   val restored=rect.fromScreen(rect.toScreen(uv)); near(uv.x,restored.x); near(uv.y,restored.y)
  }
 }
 @Test fun changingDirectionImmediatelyChangesFirstHit() {
  assertTrue(engine.firstHit(cue(),Vec(1f,0f),listOf(ball())) is Hit.BallContact)
  assertTrue(engine.firstHit(cue(),Vec(0f,1f),listOf(ball())) is Hit.Cushion)
 }
 @Test fun largerRadiusContactsCushionSooner() {
  val small=engine.firstHit(cue(r=.02f),Vec(1f,0f),emptyList())
  val large=engine.firstHit(cue(r=.06f),Vec(1f,0f),emptyList())
  near(.04f,small.distance-large.distance)
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsOverlap() { engine.firstHit(cue(),Vec(1f,0f),listOf(ball(p=Vec(.41f,.5f)))) }
 @Test(expected=IllegalArgumentException::class) fun rejectsOutOfBounds() { engine.firstHit(cue(Vec(0f,.5f)),Vec(1f,0f),emptyList()) }
 @Test(expected=IllegalArgumentException::class) fun rejectsZeroDirection() { engine.firstHit(cue(),Vec(0f,0f),emptyList()) }
 @Test(expected=IllegalArgumentException::class) fun rejectsNonFiniteDirection() { engine.firstHit(cue(),Vec(Float.NaN,1f),emptyList()) }
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalidRadius() { Ball("bad",Vec(1f,.5f),0f) }
 @Test(expected=IllegalArgumentException::class) fun rejectsThreeReflections() { engine.predict(cue(),Vec(1f,0f),emptyList(),3) }
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalidTable() { Table(Float.POSITIVE_INFINITY,1f) }
}

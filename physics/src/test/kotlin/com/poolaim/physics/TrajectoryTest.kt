package com.poolaim.physics
import org.junit.Assert.*
import org.junit.Test
class TrajectoryTest {
 @Test fun reflectsAtRightCushion() {
  val path = Trajectory.trace(Vec(.5f,.5f),Vec(1f,0f),null, reflections=1)
  assertEquals(1.965f,path[0].to.x,.0001f)
  assertEquals(.035f,path[1].to.x,.0001f)
 }
 @Test fun predictsHeadOnCollision() {
  val path = Trajectory.trace(Vec(.5f,.5f),Vec(1f,0f),Vec(1f,.5f), reflections=0)
  assertEquals(.93f,path[0].to.x,.0001f)
  assertTrue(path[1].objectBall)
 }
 @Test fun missesBallBehindCueBeforeAnyReflection() {
  assertFalse(Trajectory.trace(Vec(1f,.5f),Vec(1f,0f),Vec(.5f,.5f),reflections=0).any { it.objectBall })
 }
 @Test fun ballBehindCueCanBeHitAfterReflection() {
  assertTrue(Trajectory.trace(Vec(1f,.5f),Vec(1f,0f),Vec(.5f,.5f),reflections=1).any { it.objectBall })
 }
 @Test fun tangentDoesNotTransferMotion() {
  assertFalse(Trajectory.trace(Vec(.5f,.5f),Vec(1f,0f),Vec(1f,.57f),reflections=0).any { it.objectBall })
 }
 @Test fun cornerReflectsBothAxes() {
  val path=Trajectory.trace(Vec(1f,.5f),Vec(.965f,.465f),null,reflections=1)
  assertTrue(path[1].to.x < path[1].from.x)
  assertTrue(path[1].to.y < path[1].from.y)
 }
}

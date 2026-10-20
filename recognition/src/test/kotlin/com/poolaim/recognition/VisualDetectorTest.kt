package com.poolaim.recognition

import com.poolaim.vision.*
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

class VisualDetectorTest {
 companion object {
  fun scene(angle: Float=0f,whiteAim: Boolean=true,secondWhite: Boolean=false,width: Int=720,height: Int=400): VideoFrame {
   val bytes=ByteArray(width*height*4)
   fun pixel(x: Int,y: Int,r: Int,g: Int,b: Int) { val o=(y*width+x)*4; bytes[o]=r.toByte(); bytes[o+1]=g.toByte(); bytes[o+2]=b.toByte(); bytes[o+3]=255.toByte() }
   val left=width*.06f; val top=height*.1f; val tw=width*.88f; val th=tw/2
   val cx=left+tw*.25f; val cy=top+th*.5f; val radius=th*.03f
   for(y in 0 until height) for(x in 0 until width) {
    pixel(x,y,12,20,30)
    if(x>=left && x<left+tw && y>=top && y<top+th) pixel(x,y,15,99,81)
   }
   if(whiteAim) {
    val a=angle*PI/180
    for(d in (radius*2).toInt() until (tw*.38).toInt()) {
     val x=(cx+cos(a)*d).roundToInt(); val y=(cy+sin(a)*d).roundToInt()
     if(x>left+2 && x<left+tw-2 && y>top+2 && y<top+th-2) for(offset in -1..1) pixel(x,y+offset,240,240,240)
    }
   }
   for(y in top.toInt().coerceAtLeast(0) until (top+th).toInt().coerceAtMost(height)) for(x in left.toInt() until (left+tw).toInt()) {
    if(hypot(x-cx,y-cy)<radius) pixel(x,y,250,250,250)
    if(hypot(x-(left+tw*.68f),y-cy)<radius) pixel(x,y,if(secondWhite)250 else 245,if(secondWhite)250 else 190,if(secondWhite)250 else 20)
    if(hypot(x-(left+tw*.8f),y-(top+th*.75f))<radius) pixel(x,y,210,25,30)
   }
   return VideoFrame(FrameMetadata(7,width,height,width,height,160,0,System.nanoTime(),0),bytes)
  }
 }
 @Test fun detectsTableBallsRadiiAndWhiteAim() {
  scene().use { f ->
   val d=VisualDetector().detect(f,VisualSettings())
   assertNotNull(d.message,d.region); assertEquals(d.message,3,d.balls.size); assertNotNull(d.message,d.cue)
   assertNotNull(d.message,d.angle); assertEquals(0f,d.angle!!,3f)
   assertEquals(.06f,d.region!!.left,.01f); assertEquals(.03f,d.balls[d.cue!!].radius/d.region!!.height,.008f)
  }
 }
 @Test fun changedAimIsEstimatedWithoutGameParameters() {
  scene(30f).use { val d=VisualDetector().detect(it,VisualSettings()); assertNotNull(d.message,d.angle); assertEquals(30f,d.angle!!,3f) }
 }
 @Test fun ambiguousWhiteBallsSuppressPrediction() {
  scene(secondWhite=true).use { val d=VisualDetector().detect(it,VisualSettings()); assertNull(d.angle); assertEquals(0f,d.confidence,0f) }
 }
 @Test fun noAimOffersManualDirection() {
  scene(whiteAim=false).use {
   assertNull(VisualDetector().detect(it,VisualSettings()).angle)
   assertEquals(41f,VisualDetector().detect(it,VisualSettings(manualDirection=true,angleDegrees=41f)).angle!!,0f)
  }
 }
 @Test fun blackFrameNeverCreatesTable() {
  val f=VideoFrame(FrameMetadata(7,100,80,100,80,160,0,System.nanoTime(),0),ByteArray(100*80*4))
  f.use { assertNull(VisualDetector().detect(it,VisualSettings()).region) }
 }
 @Test fun processorConnectsVisionToExistingGeometryAndClearsOnLoss() {
  AnalysisFeed.authorize(7); AnalysisFeed.start(); AnalysisFeed.ownUiVisible=false; AnalysisFeed.configure(VisualSettings())
  val processor=VisualProcessor()
  try {
   scene().use(processor::process); assertTrue(AnalysisFeed.snapshot.lines.isEmpty())
   scene().use(processor::process); assertTrue(AnalysisFeed.snapshot.message,AnalysisFeed.snapshot.lines.isNotEmpty())
   assertTrue(AnalysisFeed.snapshot.lines.any { it.target }); assertTrue(AnalysisFeed.snapshot.lines.any { it.reflection>0 })
   val blank=VideoFrame(FrameMetadata(7,720,400,720,400,160,0,System.nanoTime(),0),ByteArray(720*400*4))
   blank.use(processor::process); assertTrue(AnalysisFeed.snapshot.lines.isEmpty())
  } finally { AnalysisFeed.stop() }
 }
 @Test fun revisionRejectsResultFromBeforePauseOrNewSettings() {
  AnalysisFeed.authorize(7); AnalysisFeed.start(); val revision=AnalysisFeed.revision
  AnalysisFeed.pause(); AnalysisFeed.publish(VisualSnapshot(sessionId=7,message="invalid"),revision)
  assertEquals("Análise pausada",AnalysisFeed.snapshot.message); AnalysisFeed.stop()
 }
 @Test fun visualPredictionUsesSelectedCushionLimitAfterSettingsChange() {
  val saved=AnalysisFeed.settings
  AnalysisFeed.authorize(7); AnalysisFeed.start(); AnalysisFeed.ownUiVisible=false
  val processor=VisualProcessor()
  try {
   for(limit in listOf(2,0,1)) {
    AnalysisFeed.configure(VisualSettings(manualDirection=true,angleDegrees=90f,maxReflections=limit))
    scene(whiteAim=false).use(processor::process)
    assertTrue("Ajuste deve invalidar a previsão anterior",AnalysisFeed.snapshot.lines.isEmpty())
    scene(whiteAim=false).use(processor::process)
    val lines=AnalysisFeed.snapshot.lines
    assertTrue(AnalysisFeed.snapshot.message,lines.isNotEmpty())
    assertEquals("O motor deve respeitar a quantidade escolhida",limit,lines.maxOf { it.reflection })
   }
  } finally { AnalysisFeed.stop(); AnalysisFeed.configure(saved) }
 }
 @Test fun manualGeometryRequiresSameOrientationAndMatchingBalls() {
  scene().use { f ->
   val detected=VisualDetector().detect(f,VisualSettings())
   val ordered=listOf(detected.balls[detected.cue!!])+detected.balls.filterIndexed { i,_->i!=detected.cue }
   val colors=ordered.map { ball -> val o=((ball.center.y*400).toInt()*720+(ball.center.x*720).toInt())*4
    (255 shl 24) or ((f.rgba.get(o).toInt() and 255) shl 16) or ((f.rgba.get(o+1).toInt() and 255) shl 8) or (f.rgba.get(o+2).toInt() and 255) }
   val manual=ManualScene(detected.region!!,ordered,colors,1.8f,0)
   assertNotNull(VisualDetector().detect(f,VisualSettings(manual,true,0f)).angle)
   assertNull(VisualDetector().detect(f,VisualSettings(manual.copy(rotation=90),true,0f)).angle)
   scene(whiteAim=false,secondWhite=true).use { assertNull(VisualDetector().detect(it,VisualSettings(manual,true,0f)).angle) }
  }
 }
 @Test fun staleSnapshotIsNeverDrawable() { assertFalse(VisualSnapshot(receivedAtNanos=System.nanoTime()-800_000_000).fresh()) }
 @Test fun controlsOverTableSuppressAnalysisButOutsideControlsDoNot() {
  scene().use { frame ->
   assertNull(VisualDetector().detect(frame,VisualSettings(),TableRegion(.4f,.4f,.5f,.5f)).angle)
   assertNotNull(VisualDetector().detect(frame,VisualSettings(),TableRegion(.95f,0f,1f,.05f)).angle)
  }
 }
 @Test fun malformedManualCoordinatesAreRejectedWithoutCrashingPipeline() {
  scene().use { frame ->
   val m=ManualScene(TableRegion(.06f,.1f,.94f,.89f),listOf(ObservedBall(ImagePoint(Float.NaN,.5f),.02f)),listOf(-1),1.8f,0)
   assertNull(VisualDetector().detect(frame,VisualSettings(m,true)).angle)
  }
 }
 @Test fun colorSignatureAveragesPrintedNumberInsideBall() {
  val pixels=IntArray(21*21){ 0xfff5be14.toInt() }
  for(y in 9..11) for(x in 9..11) pixels[y*21+x]=0xff000000.toInt()
  val average=ImageColors.averageBall(pixels,21,21,ImagePoint(.5f,.5f),.45f)
  assertTrue((average shr 16 and 255)>200)
  assertTrue((average shr 8 and 255)>150)
 }
}

package com.poolaim.recognition

import com.poolaim.vision.*
import com.poolaim.physics.*
import kotlin.math.*

/** Runs on the existing latest-frame worker. GeometryEngine remains unchanged. */
class VisualProcessor(private val detector: VisualDetector=VisualDetector()): FrameProcessor {
 private var previous: VisualSnapshot?=null
 private var previousRevision=-1L
 override fun process(frame: VideoFrame) {
  if(!AnalysisFeed.enabled || AnalysisFeed.paused || AnalysisFeed.controlsOpen) { previous=null; return }
  val revision=AnalysisFeed.revision
  val meta=frame.metadata
  if(meta.sessionId!=AnalysisFeed.authorizedSession) return
  if(AnalysisFeed.ownUiVisible && !AnalysisFeed.trainingVisible) {
   previous=null
   AnalysisFeed.publish(AnalysisFeed.snapshot.copy(lines=emptyList(),message="Abra a mesa no outro aplicativo para analisar",sessionId=meta.sessionId),revision)
   return
  }
  if(System.nanoTime()-meta.receivedAtNanos>500_000_000L) { previous=null; return }
  val settings=AnalysisFeed.settings
  val result=detector.detect(frame,settings,AnalysisFeed.buttonBlock)
  var snapshot=VisualSnapshot(meta.sessionId,meta.receivedAtNanos,meta.sourceWidth,meta.sourceHeight,meta.displayRotationDegrees,
   result.message,result.confidence,result.region,result.balls,result.cue,result.angle,preview=result.preview,
   manual=settings.manual!=null || settings.manualDirection,aimEnd=result.aimEnd)
  val region=result.region
  if(region!=null && result.cue!=null && result.angle!=null && result.confidence>=.68f) {
   val old=previous
   val stable=old!=null && previousRevision==revision && old.sessionId==meta.sessionId && meta.receivedAtNanos-old.receivedAtNanos in 0..2_000_000_000L &&
    old.sourceWidth==meta.sourceWidth && old.sourceHeight==meta.sourceHeight && old.rotation==meta.displayRotationDegrees &&
    old.region?.let { abs(it.left-region.left)+abs(it.top-region.top)+abs(it.right-region.right)+abs(it.bottom-region.bottom)<.035f }==true &&
    old.cueIndex!=null && old.balls.size==result.balls.size &&
    hypot(old.balls[old.cueIndex!!].center.x-result.balls[result.cue].center.x,old.balls[old.cueIndex!!].center.y-result.balls[result.cue].center.y)<.012f &&
    old.directionDegrees?.let { abs(((it-result.angle+540)%360)-180)<8 }==true
   if(stable) {
    try {
     val ratio=region.width*meta.sourceWidth/(region.height*meta.sourceHeight)
     val table=Table(ratio,1f)
     fun ball(index: Int): Ball {
      val b=result.balls[index]
      return Ball("ball-$index",Vec((b.center.x-region.left)/region.width*ratio,(b.center.y-region.top)/region.height),b.radius/region.height)
     }
     val angle=result.angle*PI/180
     val prediction=GeometryEngine(table).predict(ball(result.cue),Vec(cos(angle).toFloat(),sin(angle).toFloat()),result.balls.indices.filter { it!=result.cue }.map(::ball),settings.maxReflections)
     fun uv(v: Vec)=ImagePoint(region.left+v.x/ratio*region.width,region.top+v.y*region.height)
     snapshot=snapshot.copy(lines=prediction.segments.map { VisualLine(uv(it.from),uv(it.to),it.actor==PathActor.TARGET,it.reflection) },
      message=if(settings.manualDirection) "Direção manual • estimativa geométrica" else "Mira visual • estimativa geométrica")
    } catch(_: IllegalArgumentException) { snapshot=snapshot.copy(confidence=0f,message="Bolas sobrepostas ou fora da mesa: recalibre") }
   } else snapshot=snapshot.copy(message="Confirmando mesa e direção em novo quadro")
  }
  // Stability needs geometry only; never retain captured pixels in the long-lived processor.
  previous=snapshot.copy(preview=null); previousRevision=revision
  if(!Thread.currentThread().isInterrupted && snapshot.fresh()) AnalysisFeed.publish(snapshot,revision)
 }
}

package com.poolaim.recognition

import com.poolaim.vision.*
import java.util.concurrent.atomic.AtomicReference

data class ProbeResult(val sessionId: Long,val greenFraction: Float,val samples: Int)
/** Diagnostics only: samples green pixels of the training table. Does not detect balls or games. */
object TrainingFrameProbe: FrameProcessor {
 private val result=AtomicReference<ProbeResult?>(null)
 val latest: ProbeResult? get()=result.get()
 override fun process(frame: VideoFrame) {
  val meta=frame.metadata; var green=0; var total=0
  for(y in 0 until meta.height step 8) {
   if(Thread.currentThread().isInterrupted) return
   for(x in 0 until meta.width step 8) {
    val offset=(y*meta.width+x)*4
    val r=frame.rgba.get(offset).toInt() and 255
    val g=frame.rgba.get(offset+1).toInt() and 255
    val b=frame.rgba.get(offset+2).toInt() and 255
    if(g>40 && g>r*1.2 && g>b*1.05) green++
    total++
   }
  }
  if(!Thread.currentThread().isInterrupted) result.set(ProbeResult(meta.sessionId,if(total>0) green.toFloat()/total else 0f,total))
 }
}

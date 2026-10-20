package com.poolaim.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.poolaim.vision.FrameProcessor

object CaptureController {
 internal const val AUTH="projection_authorization"
 internal const val CODE="projection_result_code"
 internal const val STOP="com.poolaim.capture.STOP"
 @Volatile internal var processor: FrameProcessor=FrameProcessor { }
 fun configureProcessor(value: FrameProcessor) {
  check(!CaptureMonitor.snapshot.inProgress) { "Configure the processor before starting a session" }
  processor=value
 }
 /** Called exactly once for a fresh ActivityResult. Never persist or replay authorization. */
 fun start(context: Context,resultCode: Int,authorization: Intent): Boolean {
  if(resultCode!=Activity.RESULT_OK) return false
  val session=CaptureMonitor.reserve() ?: return false
  com.poolaim.vision.AnalysisFeed.authorize(session.id)
  try {
   context.startForegroundService(Intent(context,CaptureService::class.java)
    .putExtra(CODE,resultCode).putExtra(AUTH,authorization))
  } catch(error: RuntimeException) {
   CaptureMonitor.finish(session.id,"O Android não permitiu iniciar a captura. Solicite novo consentimento.")
   throw error
  }
  return true
 }
 fun stop(context: Context) {
  com.poolaim.vision.AnalysisFeed.stop()
  CaptureMonitor.session?.cancel() // Reject frames immediately, before lifecycle teardown.
  if(CaptureMonitor.snapshot.inProgress) CaptureMonitor.publish(CaptureMonitor.snapshot.copy(phase=CapturePhase.STOPPING,message="Encerrando captura"))
  context.stopService(Intent(context,CaptureService::class.java))
 }
}

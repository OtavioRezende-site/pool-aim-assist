package com.poolaim.app

import android.content.Context
import com.poolaim.calibration.VisualCalibrationStore
import com.poolaim.capture.CaptureController
import com.poolaim.capture.CaptureMonitor
import com.poolaim.overlay.OverlayController
import com.poolaim.recognition.TrainingFrameProbe
import com.poolaim.recognition.VisualProcessor
import com.poolaim.vision.AnalysisFeed

object VisualRuntime {
 private var configured=false
 @Synchronized fun initialize(context: Context) {
  if(configured) return
  val app=context.applicationContext
  val store=VisualCalibrationStore(app)
  AnalysisFeed.configure(store.read())
  AnalysisFeed.onSettingsChanged=store::write
  if(!CaptureMonitor.snapshot.inProgress) {
   val visual=VisualProcessor()
   CaptureController.configureProcessor { frame -> if(!AnalysisFeed.enabled || AnalysisFeed.paused) TrainingFrameProbe.process(frame); visual.process(frame) }
  }
  OverlayController.onStopAnalysis={ CaptureController.stop(app) }
  configured=true
 }
}

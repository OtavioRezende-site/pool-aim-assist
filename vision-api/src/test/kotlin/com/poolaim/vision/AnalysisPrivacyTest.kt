package com.poolaim.vision

import org.junit.Assert.*
import org.junit.Test

class AnalysisPrivacyTest {
 private fun publishFrame(session: Long) {
  AnalysisFeed.publish(VisualSnapshot(sessionId=session,receivedAtNanos=System.nanoTime(),
   preview=PixelPreview(2,2,IntArray(4))),AnalysisFeed.revision)
 }
 @Test fun stopDiscardsLiveAndFrozenPixelsAndRejectsInFlightResults() {
  AnalysisFeed.authorize(100); AnalysisFeed.start()
  try {
   publishFrame(100); AnalysisFeed.freezeForCalibration()
   assertNotNull(AnalysisFeed.snapshot.preview); assertNotNull(AnalysisFeed.calibrationFrame?.preview)
   val revision=AnalysisFeed.revision
   AnalysisFeed.stop()
   assertNull(AnalysisFeed.snapshot.preview); assertNull(AnalysisFeed.calibrationFrame)
   assertTrue(AnalysisFeed.snapshot.lines.isEmpty()); assertFalse(AnalysisFeed.enabled)
   assertEquals(0L,AnalysisFeed.authorizedSession)
   AnalysisFeed.publish(VisualSnapshot(sessionId=100,preview=PixelPreview(2,2,IntArray(4))),revision)
   assertNull(AnalysisFeed.snapshot.preview)
  } finally { AnalysisFeed.stop() }
 }
 @Test fun newAuthorizationDoesNotRetainPreviousCalibrationImage() {
  AnalysisFeed.authorize(101); AnalysisFeed.start()
  try {
   publishFrame(101); AnalysisFeed.freezeForCalibration()
   assertNotNull(AnalysisFeed.calibrationFrame)
   AnalysisFeed.authorize(102)
   assertEquals(102L,AnalysisFeed.authorizedSession)
   assertNull(AnalysisFeed.snapshot.preview); assertNull(AnalysisFeed.calibrationFrame)
  } finally { AnalysisFeed.stop() }
 }
}

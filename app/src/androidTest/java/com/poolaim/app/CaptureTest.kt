package com.poolaim.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.*
import com.poolaim.capture.*
import com.poolaim.overlay.OverlayController
import com.poolaim.recognition.TrainingFrameProbe
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Uses the real system consent dialog; never grants PROJECT_MEDIA through appops. */
@RunWith(AndroidJUnit4::class)
class CaptureTest {
 @get:Rule val notifications: TestRule=if(Build.VERSION.SDK_INT>=33)
  GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else TestRule { base,_->base }
 @Test fun authorizedFramesRotateStopAndRequireFreshConsent() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  CaptureController.stop(context)
  ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra(OverlayController.SCREEN,"CAPTURE")).use {
   try {
    device.wait(Until.findObject(By.text("Autorizar captura de tela")),15000).click()
    // API 34+ may offer app-only or whole-display capture. Select whole display for rotation.
    val selector=device.wait(Until.findObject(By.res("com.android.systemui","screen_share_mode_spinner")),2000)
    if(selector!=null) {
     selector.click()
     device.wait(Until.findObject(By.textContains("Entire screen")),3000)?.click()
    }
    clickCaptureConsent(device)
    assertTrue("Frames autorizados não chegaram: ${CaptureMonitor.snapshot}",eventually(25000) {
     CaptureMonitor.snapshot.phase==CapturePhase.RUNNING && CaptureMonitor.snapshot.processedFrames>=3
    })
    val first=CaptureMonitor.snapshot
    assertTrue(first.width>0 && first.height>0 && first.densityDpi>0)
    assertTrue(first.bufferBytes<=3L*first.width*first.height*4)
    assertEquals(first.sessionId,TrainingFrameProbe.latest!!.sessionId)
    if(first.rotation==90 || first.rotation==270) device.setOrientationNatural() else device.setOrientationLeft()
    assertTrue("Resolução não acompanhou a rotação",eventually(20000) {
     val now=CaptureMonitor.snapshot
     now.sourceWidth!=first.sourceWidth && now.processedFrames>first.processedFrames
    })
    // Stop from the visible UI; the controller cancels delivery before service teardown.
    device.wait(Until.findObject(By.text("Parar captura")),10000).click()
    assertTrue(eventually(10000) { !CaptureMonitor.snapshot.inProgress })
    assertTrue("Notificação de captura permaneceu após parar",eventually(10000) {
     context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.none { it.id==42 }
    })
    assertTrue("Threads de captura/processamento não encerraram",eventually(10000) {
     Thread.getAllStackTraces().keys.none { thread -> thread.isAlive && thread.name in setOf("capture-images","vision-latest") }
    })
    assertNull(com.poolaim.vision.AnalysisFeed.snapshot.preview)
    assertNull(com.poolaim.vision.AnalysisFeed.calibrationFrame)
    val stopped=CaptureMonitor.snapshot
    assertEquals(CapturePhase.IDLE,stopped.phase)
    SystemClock.sleep(1200)
    assertEquals(stopped.processedFrames,CaptureMonitor.snapshot.processedFrames)
    device.wait(Until.findObject(By.text("Autorizar captura de tela")),10000).click()
    assertNotNull(device.wait(Until.findObject(By.res("android","button1")),10000))
    device.pressBack() // Cancelling a new consent must never restart the previous session.
    assertFalse(CaptureMonitor.snapshot.inProgress)
    assertEquals(stopped.sessionId,CaptureMonitor.snapshot.sessionId)
   } finally { CaptureController.stop(context); device.unfreezeRotation() }
  }
 }
 private fun eventually(timeout: Long,test: ()->Boolean): Boolean {
  val end=SystemClock.elapsedRealtime()+timeout
  while(SystemClock.elapsedRealtime()<end) { if(test()) return true; SystemClock.sleep(100) }
  return test()
 }
}

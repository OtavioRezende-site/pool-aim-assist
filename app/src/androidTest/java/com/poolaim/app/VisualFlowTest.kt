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
import com.poolaim.overlay.*
import com.poolaim.vision.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import kotlin.math.abs

/** Full display consent -> real Canvas pixels -> vision -> geometry -> touch-through overlay. */
@RunWith(AndroidJUnit4::class)
class VisualFlowTest {
 @get:Rule val notifications: TestRule=if(Build.VERSION.SDK_INT>=33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else TestRule { base,_->base }
 @Test fun visualDirectionUpdatesPredictionAndManualFallbackRemainsFunctional() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext; val device=UiDevice.getInstance(instrumentation)
  val priorMode=Regex("SYSTEM_ALERT_WINDOW: (allow|deny|ignore|default|foreground)").find(device.executeShellCommand("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
  val previous=AnalysisFeed.settings
  val floating=OverlaySettings(context); val oldFloating=floating.read()
  floating.write(oldFloating.copy(x=.96f,y=0f))
  CaptureController.stop(context)
  device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
  ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra(OverlayController.SCREEN,"VISUAL_TEST")).use {
   var completed=false
   try {
    device.wait(Until.findObject(By.text("Iniciar teste visual")),20000).click()
    clickCaptureConsent(device)
    val predicted=eventually(45000) { AnalysisFeed.snapshot.lines.isNotEmpty() && AnalysisFeed.snapshot.fresh() }
    assertTrue("Sem previsão: ${AnalysisFeed.snapshot}",predicted)
    val first=AnalysisFeed.snapshot
    assertEquals(3,first.balls.size); assertNotNull(first.cueIndex)
    assertTrue(first.lines.any { line -> line.target }); assertTrue(first.lines.any { line -> line.reflection>0 })
    assertTrue(OverlayController.running)
    // Real View.onDraw diagnostic avoids a screenshot binder call slower than the 700 ms
    // expiry on a software-only emulator. Pixel placement is verified by TrajectoryRendererTest.
    val rendered=eventually(15000) { OverlayController.lastRenderedFrameNanos>0L }
    assertTrue("A janela real não desenhou: ${OverlayController.renderState}",rendered)
    val slider=device.wait(Until.findObject(By.desc("Direção da mira de teste")),10000)
    assertNotNull(slider); val bounds=slider.visibleBounds
    assertTrue(device.click(bounds.left+(bounds.width()*.7f).toInt(),bounds.centerY()))
    assertTrue("Direção visual não atualizou",eventually(25000) {
     val s=AnalysisFeed.snapshot
     s.lines.isNotEmpty() && s.directionDegrees?.let { angle -> abs(angle-(first.directionDegrees ?: 0f))>12f }==true
    })
    AnalysisFeed.pause(); assertTrue(AnalysisFeed.snapshot.lines.isEmpty())
    assertEquals(CapturePhase.RUNNING,CaptureMonitor.snapshot.phase)
    AnalysisFeed.configure(AnalysisFeed.settings.copy(manualDirection=true,angleDegrees=-25f)); AnalysisFeed.start()
    assertTrue("Direção manual não aplicada",eventually(25000) { AnalysisFeed.snapshot.lines.isNotEmpty() && AnalysisFeed.snapshot.directionDegrees==-25f })
    AnalysisFeed.freezeForCalibration(); assertNotNull(AnalysisFeed.calibrationFrame?.preview); assertTrue(AnalysisFeed.snapshot.lines.isEmpty())
    CaptureController.stop(context)
    assertTrue(eventually(10000) { !CaptureMonitor.snapshot.inProgress })
    assertFalse(AnalysisFeed.enabled)
    completed=true
   } finally {
    if(!completed) {
     device.takeScreenshot(java.io.File(context.cacheDir,"visual-failure.png"))
     java.io.File(context.cacheDir,"visual-failure.txt").writeText(AnalysisFeed.snapshot.toString()+"\n"+OverlayController.buttonBounds+"\n"+OverlayController.renderState)
    }
    CaptureController.stop(context); OverlayController.stop(context)
    AnalysisFeed.configure(previous); floating.write(oldFloating)
    device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $priorMode")
   }
  }
 }
 private fun eventually(timeout: Long,test: ()->Boolean): Boolean {
  val end=SystemClock.elapsedRealtime()+timeout
  while(SystemClock.elapsedRealtime()<end) { if(test()) return true; SystemClock.sleep(100) }
  return test()
 }
}

package com.poolaim.app

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.poolaim.capture.CaptureController
import com.poolaim.capture.CaptureMonitor
import com.poolaim.capture.CapturePhase
import com.poolaim.overlay.OverlayController
import com.poolaim.overlay.OverlaySettings
import com.poolaim.vision.AnalysisFeed
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Real launcher -> compact overlay -> system consent -> stop, without the laboratory UI. */
@RunWith(AndroidJUnit4::class)
class FloatingLaunchTest {
 @get:Rule val notifications: TestRule=if(Build.VERSION.SDK_INT>=33)
  GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else TestRule { base,_->base }
 @Test fun iconOpensCompactPanelAndCaptureReturnsToPreviousApp() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val previousMode=Regex("SYSTEM_ALERT_WINDOW: (allow|deny|ignore|default|foreground)")
   .find(device.executeShellCommand("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
  val previous=AnalysisFeed.settings
  val store=OverlaySettings(context); val floating=store.read()
  try {
   CaptureController.stop(context); OverlayController.stop(context)
   device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
   store.write(floating.copy(x=.96f,y=0f))
   device.pressHome()
   val launcher=context.packageManager.getLaunchIntentForPackage(context.packageName)!!
   assertEquals(FloatingActivity::class.java.name,launcher.component!!.className)
   context.startActivity(launcher)
   assertNotNull(device.wait(Until.findObject(By.text("Iniciar")),15000))
   assertFalse("O laboratório não deve abrir pelo ícone",device.hasObject(By.text("POOL AIM ASSIST")))
   assertTrue(OverlayController.running)
   val cushions=device.wait(Until.findObject(By.desc("Quantidade de tabelas")),5000)!!
   val bounds=cushions.visibleBounds
   device.click(bounds.left+bounds.width()/2,bounds.centerY())
   assertTrue(eventually { AnalysisFeed.settings.maxReflections==1 })
   val transparency=device.findObject(By.desc("Transparência das linhas"))!!
   val alphaBounds=transparency.visibleBounds
   device.click(alphaBounds.left+alphaBounds.width()/2,alphaBounds.centerY())
   assertTrue(eventually { AnalysisFeed.settings.opacity in .4f.. .7f })
   device.findObject(By.text("Iniciar")).click()
   val consent=device.wait(Until.findObject(By.res("android","button1")),15000)
   assertNotNull("Consentimento de captura ausente",consent); consent.click()
   assertTrue("Captura não iniciou: ${CaptureMonitor.snapshot}",eventually(25000) {
    CaptureMonitor.snapshot.phase==CapturePhase.RUNNING && CaptureMonitor.snapshot.processedFrames>=2
   })
   assertTrue(eventually { !AnalysisFeed.ownUiVisible })
   assertFalse(device.hasObject(By.text("POOL AIM ASSIST")))
   assertTrue(eventually { !AnalysisFeed.controlsOpen })
   val bubble=OverlayController.buttonBounds!!
   device.click(bubble.left+bubble.width/2,bubble.top+bubble.height/2)
   device.wait(Until.findObject(By.text("Parar")),10000).click()
   assertTrue(eventually { !CaptureMonitor.snapshot.inProgress && !AnalysisFeed.enabled })
   val manager=context.getSystemService(NotificationManager::class.java)
   assertTrue(eventually { manager.activeNotifications.none { it.id==42 } })
   assertTrue("Parar análise mantém PA disponível",OverlayController.running)
  } finally {
   CaptureController.stop(context); OverlayController.stop(context)
   AnalysisFeed.configure(previous); store.write(floating)
   device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $previousMode")
  }
 }
 private fun eventually(timeout: Long=10000,test: ()->Boolean): Boolean {
  val end=SystemClock.elapsedRealtime()+timeout
  while(SystemClock.elapsedRealtime()<end) { if(test()) return true; SystemClock.sleep(100) }
  return test()
 }
}

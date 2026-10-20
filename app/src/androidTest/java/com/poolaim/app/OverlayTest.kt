package com.poolaim.app

import android.app.NotificationManager
import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.rules.TestRule
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.poolaim.overlay.OverlayController
import com.poolaim.overlay.OverlaySettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Grants the special permission through the test shell only; production always uses Settings. */
@RunWith(AndroidJUnit4::class)
class OverlayTest {
 @get:Rule val notificationPermission: TestRule = if(Build.VERSION.SDK_INT>=33)
  GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
 else TestRule { base, _ -> base }
 @Test fun floatingControlsPassTouchesDragRotateAndStop() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
  val previousMode=Regex("SYSTEM_ALERT_WINDOW: (allow|deny|ignore|default|foreground)")
   .find(device.executeShellCommand("appops get ${context.packageName} SYSTEM_ALERT_WINDOW"))?.groupValues?.get(1) ?: "default"
  val store=OverlaySettings(context); val previousSettings=store.read()
  var completed=false
  try {
   device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
   store.write(com.poolaim.overlay.FloatingSettings())
   context.startActivity(Intent(context,MainActivity::class.java).putExtra(OverlayController.SCREEN,"DEMO").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
   assertNotNull(device.wait(Until.findObject(By.text("Exibir linhas de demonstração")),15000))
   OverlayController.start(context,demo=true)
   val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
   fun floatingRoot(): android.view.accessibility.AccessibilityNodeInfo? = automation.windows
    .mapNotNull { it.root }.firstOrNull { it.refresh() && hasFloatingButton(it) }
   fun bubbleBounds(): android.graphics.Rect {
    val end=SystemClock.elapsedRealtime()+15000
    while(SystemClock.elapsedRealtime()<end) {
     val position=OverlayController.buttonBounds
     if(position!=null && position.width>0 && position.height>0)
      return android.graphics.Rect(position.left,position.top,position.left+position.width,position.top+position.height)
     SystemClock.sleep(100)
    }
    throw AssertionError("Janela do botão flutuante ausente")
   }
   fun tapBubble() {
    val bounds=bubbleBounds()
    assertTrue(device.click(bounds.centerX(),bounds.centerY()))
   }
   // Query the actual overlay root bounds: API 28 may flag a moved WindowManager root
   // as invisible in UiObject2 even while it is drawn. Every tap is verified by menu state.
   assertFalse(bubbleBounds().isEmpty)
   assertTrue(eventually { OverlayController.running })
   val notifications=context.getSystemService(NotificationManager::class.java)
   assertTrue(eventually { notifications.activeNotifications.any { it.id==41 } })

   // A full-screen drawing window is present. Input must reach the underlying Compose button.
   val control=device.wait(Until.findObject(By.text("Controle abaixo da camada: 0 toques")),15000)
   assertNotNull(control); control.click()
   assertNotNull(device.wait(Until.findObject(By.text("Controle abaixo da camada: 1 toques")),10000))

   val original=bubbleBounds()
   device.swipe(original.centerX(),original.centerY(),device.displayWidth/8,device.displayHeight/8,20)
   assertTrue(eventually { store.read().x < .5f })
   assertTrue(OverlayController.running)
   assertFalse(device.hasObject(By.text("Iniciar análise"))) // Drag must not become a click.
   tapBubble()
   assertNotNull(device.wait(Until.findObject(By.text("Ocultar linhas")),10000))
   // Opening a menu must not cover PA; a second tap closes it.
   tapBubble()
   assertTrue(device.wait(Until.gone(By.text("Ocultar linhas")),10000))
   tapBubble()
   device.wait(Until.findObject(By.text("Ocultar linhas")),10000).click()
   tapBubble()
   assertNotNull(device.wait(Until.findObject(By.text("Mostrar linhas")),10000))
   device.findObject(By.text("Mostrar linhas")).click()

   // The demonstration permits portrait/landscape so this exercises a real bounds change.
   device.setOrientationLeft(); device.waitForIdle()
   assertTrue(eventually {
    val b=bubbleBounds()
     b.width()>0 && b.left>=0 && b.top>=0 && b.right<=device.displayWidth && b.bottom<=device.displayHeight
   })
   device.pressHome()
   assertFalse(bubbleBounds().isEmpty) // independent of Activity visibility
   tapBubble()
   // Release the test's artificial orientation lock before returning to a screen
   // that requests landscape. Android 15 honors this lock across Activity launches.
   device.unfreezeRotation()
   device.wait(Until.findObject(By.text("Configurações")),10000).click()
   assertNotNull(device.wait(Until.findObject(By.textStartsWith("Reflexões máximas por trajetória:")),10000))
   // Settings returns to landscape; wait for the real configuration transition before
   // opening a window, because onConfigurationChanged intentionally dismisses stale menus.
   assertTrue(eventually { device.displayWidth>device.displayHeight })
   var exit: androidx.test.uiautomator.UiObject2?=null
   for(attempt in 0 until 4) {
    tapBubble()
    exit=device.wait(Until.findObject(By.text("Encerrar aplicativo")),2000)
    if(exit!=null) break
   }
   assertNotNull("Menu ausente após estabilizar a orientação",exit)
   exit!!.click()
   assertTrue(eventually { floatingRoot()==null })
   assertTrue(eventually { !OverlayController.running })
   assertTrue(eventually { notifications.activeNotifications.none { it.id==41 } })
   assertTrue(eventually { context.getSystemService(android.app.ActivityManager::class.java).appTasks.isEmpty() })
   completed=true
  } finally {
   if(!completed) {
    device.takeScreenshot(java.io.File(context.cacheDir,"overlay-failure.png"))
    device.dumpWindowHierarchy(java.io.File(context.cacheDir,"overlay-failure.xml"))
   }
   OverlayController.stop(context)
   context.sendBroadcast(Intent(OverlayController.EXIT).setPackage(context.packageName))
   device.unfreezeRotation()
   store.write(previousSettings)
   device.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $previousMode")
  }
 }
 private fun hasFloatingButton(node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
  if(node.contentDescription?.toString()=="Abrir menu Pool Aim Assist") return true
  for(index in 0 until node.childCount) {
   val child=node.getChild(index) ?: continue
   if(hasFloatingButton(child)) return true
  }
  return false
 }
 private fun eventually(check: ()->Boolean): Boolean {
  val end=SystemClock.elapsedRealtime()+10000
  while(SystemClock.elapsedRealtime()<end) {
   if(check()) return true
   SystemClock.sleep(100)
  }
  return check()
 }
}

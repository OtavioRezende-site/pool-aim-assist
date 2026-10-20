package com.poolaim.app

import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** Android 15 may recreate the consent window between accessibility lookup and tap. */
internal fun clickCaptureConsent(device: UiDevice,timeout: Long=15000) {
 val end=SystemClock.elapsedRealtime()+timeout
 while(SystemClock.elapsedRealtime()<end) {
  val remaining=end-SystemClock.elapsedRealtime()
  val button=device.wait(Until.findObject(By.res("android","button1").enabled(true)),remaining.coerceIn(1,2000))
  if(button!=null) try {
   button.click()
   return
  } catch(_: StaleObjectException) {
   // Look up the real system button again; never grant PROJECT_MEDIA through appops.
   SystemClock.sleep(100)
  }
 }
 throw AssertionError("Diálogo de consentimento do Android ausente ou instável")
}

package com.poolaim.overlay

import android.content.Context
import android.content.Intent

data class FloatingBounds(val left: Int,val top: Int,val width: Int,val height: Int)

object OverlayController {
 /** Source timestamp of the last fresh frame painted by the real overlay View. */
 @Volatile var renderState="Sem desenho"
  internal set
 @Volatile var lastRenderedFrameNanos=0L
  internal set
 /** Last laid-out screen bounds, for placement diagnostics; null while stopped. */
 @Volatile var buttonBounds: FloatingBounds?=null
  internal set
 @Volatile var onStopAnalysis: (() -> Unit)?=null
 const val STOP="com.poolaim.overlay.STOP"
 const val DEMO="com.poolaim.overlay.DEMO"
 const val UPDATE="com.poolaim.overlay.UPDATE"
 const val OPEN_PANEL="com.poolaim.overlay.OPEN_PANEL"
 const val REQUEST_ANALYSIS="com.poolaim.overlay.REQUEST_ANALYSIS"
 const val EXIT="com.poolaim.overlay.EXIT"
 const val SCREEN="overlay_screen"
 @Volatile var running=false
  internal set
 fun start(context: Context, demo: Boolean=false,openMenu: Boolean=false) {
  check(OverlayPermission.granted(context)) { "Permissão de sobreposição necessária" }
  context.startForegroundService(Intent(context,OverlayService::class.java).setAction(if(demo) DEMO else if(openMenu) OPEN_PANEL else "START"))
 }
 fun stop(context: Context) { context.stopService(Intent(context,OverlayService::class.java)) }
 fun refresh(context: Context) {
  if(running) context.startService(Intent(context,OverlayService::class.java).setAction(UPDATE))
 }
}

package com.poolaim.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.poolaim.physics.Trajectory
import com.poolaim.physics.Vec
import com.poolaim.vision.AnalysisFeed
import android.view.WindowManager
import android.os.Build

/** Draws either the explicit demonstration or fresh trajectories from the analysis pipeline. */
class OverlayLinesView(context: Context): View(context) {
 var demonstration=true
 private var unsubscribe: (() -> Unit)?=null
 override fun onAttachedToWindow() { super.onAttachedToWindow(); unsubscribe=AnalysisFeed.observe { postInvalidateOnAnimation() } }
 override fun onDetachedFromWindow() { unsubscribe?.invoke(); unsubscribe=null; super.onDetachedFromWindow() }
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 var showLines=true
  set(value) { field=value; invalidate() }
 override fun onDraw(canvas: Canvas) {
  super.onDraw(canvas)
  OverlayController.renderState="show=$showLines demo=$demonstration active=${AnalysisFeed.enabled} paused=${AnalysisFeed.paused} own=${AnalysisFeed.ownUiVisible} training=${AnalysisFeed.trainingVisible}"
  if(!showLines) return
  if(!demonstration) {
   if(!AnalysisFeed.enabled || AnalysisFeed.paused || (AnalysisFeed.ownUiVisible && !AnalysisFeed.trainingVisible)) return
   val snapshot=AnalysisFeed.snapshot
   val wm=context.getSystemService(WindowManager::class.java)
   val metrics=android.util.DisplayMetrics()
   @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
   val location=IntArray(2); getLocationOnScreen(location)
   @Suppress("DEPRECATION") val rotation=wm.defaultDisplay.rotation*90
   OverlayController.renderState+=" source=${snapshot.sourceWidth}x${snapshot.sourceHeight}/${snapshot.rotation} display=${metrics.widthPixels}x${metrics.heightPixels}/$rotation ageMs=${(System.nanoTime()-snapshot.receivedAtNanos)/1_000_000} segments=${snapshot.lines.size} canvas=${width}x${height}"
   if(snapshot.rotation!=rotation) return
   if(LiveTrajectoryRenderer.draw(canvas,snapshot,AnalysisFeed.settings,metrics.widthPixels,metrics.heightPixels,location[0],location[1],3*resources.displayMetrics.density))
    OverlayController.lastRenderedFrameNanos=snapshot.receivedAtNanos
   return
  }
  val scale=minOf(width*.42f,height*.65f)
  val left=(width-2*scale)/2; val top=(height-scale)/2
  paint.style=Paint.Style.STROKE; paint.strokeWidth=3*resources.displayMetrics.density
  Trajectory.trace(Vec(.45f,.5f),Vec(1f,.12f),Vec(1.35f,.6f),reflections=2).forEach {
   paint.color=when { it.reflection>0 -> Color.CYAN; it.objectBall -> Color.YELLOW; else -> Color.WHITE }
   canvas.drawLine(left+it.from.x*scale,top+it.from.y*scale,left+it.to.x*scale,top+it.to.y*scale,paint)
  }
  paint.style=Paint.Style.FILL; paint.color=Color.WHITE; paint.textSize=16*resources.displayMetrics.scaledDensity
  canvas.drawText("DEMONSTRAÇÃO • sem captura ou análise automática",left,top-12*resources.displayMetrics.density,paint)
 }
}

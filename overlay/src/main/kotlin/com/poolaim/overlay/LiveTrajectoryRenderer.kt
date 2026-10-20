package com.poolaim.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.poolaim.vision.*
import kotlin.math.*

/** Screen-normalized coordinates; preserve observed balls and native aim pixels in captures. */
object LiveTrajectoryRenderer {
 fun draw(canvas: Canvas,snapshot: VisualSnapshot,settings: VisualSettings,sourceWidth: Int,sourceHeight: Int,
  offsetX: Int=0,offsetY: Int=0,strokeWidth: Float=3f): Boolean {
  if(!snapshot.fresh() || snapshot.lines.isEmpty() || !settings.linesVisible ||
   snapshot.sourceWidth!=sourceWidth || snapshot.sourceHeight!=sourceHeight) return false
  val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; this.strokeWidth=strokeWidth }
  val balls=Path().apply {
   snapshot.balls.forEach { ball -> addCircle(ball.center.x*sourceWidth-offsetX,ball.center.y*sourceHeight-offsetY,ball.radius*sourceHeight+strokeWidth,Path.Direction.CW) }
  }
  canvas.save(); canvas.clipOutPath(balls)
  // Preserve the native aim for EVERY segment, including target/reflections crossing it.
  snapshot.aimEnd?.takeIf { !settings.manualDirection }?.let { end ->
   val origin=snapshot.cueIndex?.let { snapshot.balls.getOrNull(it) }
   if(origin!=null) {
    val ax=origin.center.x*sourceWidth-offsetX; val ay=origin.center.y*sourceHeight-offsetY
    val bx=end.x*sourceWidth-offsetX; val by=end.y*sourceHeight-offsetY
    val length=hypot(bx-ax,by-ay)
    if(length>0) {
     val margin=max(strokeWidth*2,origin.radius*sourceHeight*.6f)
     val px=-(by-ay)/length*margin; val py=(bx-ax)/length*margin
     val corridor=Path().apply {
      moveTo(ax+px,ay+py); lineTo(bx+px,by+py); lineTo(bx-px,by-py); lineTo(ax-px,ay-py); close()
     }
     canvas.clipOutPath(corridor)
    }
   }
  }
  try {
   snapshot.lines.forEach { line ->
    var from=line.from
    val aimEnd=snapshot.aimEnd
    if(!line.target && line.reflection==0 && aimEnd!=null && !settings.manualDirection) {
     val dx=(line.to.x-line.from.x)*sourceWidth; val dy=(line.to.y-line.from.y)*sourceHeight
     val length=hypot(dx,dy)
     if(length>0) {
      val projected=((aimEnd.x-line.from.x)*sourceWidth*dx+(aimEnd.y-line.from.y)*sourceHeight*dy)/(length*length)
      val margin=(snapshot.cueIndex?.let { snapshot.balls.getOrNull(it)?.radius } ?: .01f)*sourceHeight
      val fraction=(projected+margin/length).coerceAtLeast(0f)
      if(fraction>=1f) return@forEach // Native aim already covers this segment.
      from=ImagePoint(line.from.x+(line.to.x-line.from.x)*fraction,line.from.y+(line.to.y-line.from.y)*fraction)
     }
    }
    // Saturated colors never count as the neutral white native aiming line.
    paint.color=when { line.reflection>0 -> Color.rgb(40,150,255); line.target -> Color.rgb(255,160,25); else -> Color.MAGENTA }
    paint.alpha=(settings.opacity*255).toInt()
    canvas.drawLine(from.x*sourceWidth-offsetX,from.y*sourceHeight-offsetY,line.to.x*sourceWidth-offsetX,line.to.y*sourceHeight-offsetY,paint)
   }
  } finally { canvas.restore() }
  return true
 }
}

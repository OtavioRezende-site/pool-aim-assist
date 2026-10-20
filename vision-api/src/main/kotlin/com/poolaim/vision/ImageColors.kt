package com.poolaim.vision

import kotlin.math.*

object ImageColors {
 /** Mean over an inner disk tolerates printed numbers/stripes better than a center pixel. */
 fun averageBall(pixels: IntArray,width: Int,height: Int,center: ImagePoint,radius: Float): Int {
  require(width>0 && height>0 && pixels.size==width*height)
  val cx=(center.x*width).roundToInt().coerceIn(0,width-1); val cy=(center.y*height).roundToInt().coerceIn(0,height-1)
  val rad=max(1,(radius*height*.65f).roundToInt())
  var red=0L; var green=0L; var blue=0L; var count=0
  for(dy in -rad..rad) for(dx in -rad..rad) {
   val x=cx+dx; val y=cy+dy
   if(dx*dx+dy*dy>rad*rad || x !in 0 until width || y !in 0 until height) continue
   val c=pixels[y*width+x]; red+=c shr 16 and 255; green+=c shr 8 and 255; blue+=c and 255; count++
  }
  require(count>0)
  return (255 shl 24) or ((red/count).toInt() shl 16) or ((green/count).toInt() shl 8) or (blue/count).toInt()
 }
}

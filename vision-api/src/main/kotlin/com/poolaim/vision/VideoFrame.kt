package com.poolaim.vision

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

data class FrameSize(val width: Int,val height: Int) {
 init { require(width>0 && height>0) }
 val rgbaBytes: Int get()=Math.multiplyExact(Math.multiplyExact(width,height),4)
 companion object {
  fun fit(sourceWidth: Int,sourceHeight: Int,maxDimension: Int=1280): FrameSize {
   require(sourceWidth>0 && sourceHeight>0 && maxDimension in 64..1920)
   val scale=min(1.0,maxDimension.toDouble()/maxOf(sourceWidth,sourceHeight))
   return FrameSize((sourceWidth*scale).roundToInt().coerceIn(1,maxDimension),(sourceHeight*scale).roundToInt().coerceIn(1,maxDimension))
  }
 }
}
data class FrameMetadata(val sessionId: Long,val width: Int,val height: Int,
 val sourceWidth: Int,val sourceHeight: Int,val densityDpi: Int,val displayRotationDegrees: Int,
 val receivedAtNanos: Long,val sourceTimestampNanos: Long) {
 val landscape: Boolean get()=sourceWidth>=sourceHeight
}
/** RGBA bytes, tightly packed. Valid only during FrameProcessor.process; copy explicitly if
 * retaining data. No Android types. Closing releases the buffer once, including on failure/drop.
 */
class VideoFrame(val metadata: FrameMetadata,bytes: ByteArray,private val release: ()->Unit={}): AutoCloseable {
 val rgba: ByteBuffer=ByteBuffer.wrap(bytes).asReadOnlyBuffer()
 private val closed=AtomicBoolean()
 val byteCount: Int get()=rgba.capacity()
 override fun close() { if(closed.compareAndSet(false,true)) release() }
}
/** Executes on a background worker; must cooperate with interruption and not retain the lease. */
fun interface FrameProcessor { fun process(frame: VideoFrame) }

object RgbaPixels {
 fun copy(source: ByteBuffer,width: Int,height: Int,rowStride: Int,pixelStride: Int,destination: ByteArray) {
  require(width>0 && height>0 && pixelStride>=4 && rowStride.toLong()>=(width-1L)*pixelStride+4)
  require(destination.size.toLong()==width.toLong()*height*4)
  val start=source.position()
  require((height-1L)*rowStride+(width-1L)*pixelStride+4<=source.remaining().toLong())
  val input=source.duplicate()
  for(y in 0 until height) {
   if(Thread.currentThread().isInterrupted) throw InterruptedException("RGBA copy cancelled")
   val offset=start+y*rowStride
   if(pixelStride==4) { input.position(offset); input.get(destination,y*width*4,width*4) }
   else for(x in 0 until width) for(c in 0..3) destination[(y*width+x)*4+c]=input.get(offset+x*pixelStride+c)
  }
 }
}

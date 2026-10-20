package com.poolaim.recognition
import com.poolaim.vision.*
import org.junit.Test
import org.junit.Assert.*
class TrainingFrameProbeTest {
 @Test fun simulatedGreenTableAndWhiteBallProduceDiagnostic() {
  val size=64; val bytes=ByteArray(size*size*4)
  for(y in 0 until size) for(x in 0 until size) {
   val white=(x-32)*(x-32)+(y-32)*(y-32)<100
   val p=(y*size+x)*4
   bytes[p]=if(white) 255.toByte() else 15
   bytes[p+1]=if(white) 255.toByte() else 99
   bytes[p+2]=if(white) 255.toByte() else 81
   bytes[p+3]=255.toByte()
  }
  val frame=VideoFrame(FrameMetadata(17,size,size,size,size,160,0,0,0),bytes)
  TrainingFrameProbe.process(frame)
  val result=TrainingFrameProbe.latest!!
  assertEquals(17,result.sessionId); assertEquals(64,result.samples)
  assertTrue(result.greenFraction in .8f.. .99f); frame.close()
 }
 @Test fun blackFrameDoesNotPretendToRecognizeTable() {
  val frame=VideoFrame(FrameMetadata(18,8,8,8,8,160,0,0,0),ByteArray(256))
  TrainingFrameProbe.process(frame); assertEquals(0f,TrainingFrameProbe.latest!!.greenFraction,0f); frame.close()
 }
}

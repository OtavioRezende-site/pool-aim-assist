package com.poolaim.vision
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
class PipelineTest {
 private fun frame(id: Long,release: ()->Unit={})=VideoFrame(FrameMetadata(id,1,1,1,1,160,0,System.nanoTime(),0),ByteArray(4),release)
 @Test fun slowConsumerGetsNewestAndReleasesAllFrames() {
  val entered=CountDownLatch(1); val resume=CountDownLatch(1); val done=CountDownLatch(2)
  val releases=AtomicInteger(); val seen=CopyOnWriteArrayList<Long>()
  val pipe=LatestFramePipeline({ f ->
   seen.add(f.metadata.sessionId)
   if(f.metadata.sessionId==1L) { entered.countDown(); check(resume.await(3,TimeUnit.SECONDS)) }
   done.countDown()
  })
  try {
   pipe.offer(frame(1){releases.incrementAndGet()}); assertTrue(entered.await(3,TimeUnit.SECONDS))
   pipe.offer(frame(2){releases.incrementAndGet()}); pipe.offer(frame(3){releases.incrementAndGet()})
   assertEquals(1,pipe.dropped.get()); resume.countDown(); assertTrue(done.await(3,TimeUnit.SECONDS))
  } finally { resume.countDown(); pipe.close(); assertTrue(pipe.awaitTermination(3,TimeUnit.SECONDS)) }
  assertEquals(listOf(1L,3L),seen); assertEquals(3,releases.get())
 }
 @Test fun cancellationInterruptsConsumerAndReleasesPendingAndRejected() {
  val entered=CountDownLatch(1); val releases=AtomicInteger()
  val pipe=LatestFramePipeline({ entered.countDown(); CountDownLatch(1).await() })
  pipe.offer(frame(1){releases.incrementAndGet()}); assertTrue(entered.await(3,TimeUnit.SECONDS))
  pipe.offer(frame(2){releases.incrementAndGet()}); pipe.close()
  assertFalse(pipe.offer(frame(3){releases.incrementAndGet()}))
  assertTrue(pipe.awaitTermination(3,TimeUnit.SECONDS)); assertEquals(3,releases.get()); assertEquals(0,pipe.processed.get())
 }
 @Test fun consumerFailureClosesPipelineAndFrame() {
  val error=CountDownLatch(1); val release=CountDownLatch(1)
  val pipe=LatestFramePipeline({ throw IllegalStateException("test") },{error.countDown()})
  pipe.offer(frame(1){release.countDown()})
  assertTrue(error.await(3,TimeUnit.SECONDS)); assertTrue(release.await(3,TimeUnit.SECONDS))
  assertTrue(pipe.awaitTermination(3,TimeUnit.SECONDS)); assertFalse(pipe.offer(frame(2)))
 }
 @Test fun leaseClosesExactlyOnceAndIsReadOnly() {
  val releases=AtomicInteger(); val f=frame(1){releases.incrementAndGet()}
  assertTrue(f.rgba.isReadOnly); f.close(); f.close(); assertEquals(1,releases.get())
 }
 @Test fun poolIsBoundedAndDoesNotRecycleLeases() {
  val p=FrameBufferPool(); val a=p.borrow(4)!!; val b=p.borrow(4)!!; val c=p.borrow(4)!!
  assertNull(p.borrow(4)); assertEquals(12,p.ownedBytes)
  p.release(a); assertSame(a,p.borrow(4)); p.release(a); p.release(b); p.release(c)
  val d=p.borrow(8)!!; assertEquals(16,p.ownedBytes); p.close(); assertEquals(8,p.ownedBytes)
  assertNull(p.borrow(4)); p.release(d); assertEquals(0,p.ownedBytes)
 }
 @Test fun paddedRowsAndSourceOffsetAreCopiedWithoutPadding() {
  val src=ByteBuffer.wrap(byteArrayOf(99,1,2,3,4,5,6,7,8,99,99,9,10,11,12,13,14,15,16));src.position(1)
  val output=ByteArray(16); RgbaPixels.copy(src,2,2,10,4,output)
  assertArrayEquals(ByteArray(16){(it+1).toByte()},output); assertEquals(1,src.position())
 }
 @Test fun pixelStrideIsRespected() {
  val output=ByteArray(8)
  RgbaPixels.copy(ByteBuffer.wrap(byteArrayOf(1,2,3,4,99,99,5,6,7,8)),2,1,10,6,output)
  assertArrayEquals(ByteArray(8){(it+1).toByte()},output)
 }
 @Test(expected=IllegalArgumentException::class) fun truncatedImageRejected() {
  RgbaPixels.copy(ByteBuffer.allocate(7),2,1,8,4,ByteArray(8))
 }
 @Test fun aspectRatioAndRotationSizesFitBound() {
  assertEquals(FrameSize(1280,800),FrameSize.fit(2800,1750))
  assertEquals(FrameSize(800,1280),FrameSize.fit(1750,2800))
  assertEquals(FrameSize(640,480),FrameSize.fit(640,480))
  assertEquals(4096000,FrameSize.fit(2800,1750).rgbaBytes)
 }
 @Test(expected=IllegalArgumentException::class) fun invalidDimensionsRejected() { FrameSize.fit(0,800) }
 @Test(expected=ArithmeticException::class) fun allocationOverflowRejected() { FrameSize(Int.MAX_VALUE,2).rgbaBytes }
}

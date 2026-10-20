package com.poolaim.vision

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** One running callback and one replaceable pending frame. No unbounded executor work queue. */
class LatestFramePipeline(private val processor: FrameProcessor,private val onError: (Throwable)->Unit={}): AutoCloseable {
 private val lock=Any()
 private var pending: VideoFrame?=null
 private var draining=false
 @Volatile private var closed=false
 private val executor=Executors.newSingleThreadExecutor { action -> Thread(action,"vision-latest").apply { isDaemon=true; priority=Thread.NORM_PRIORITY-1 } }
 val processed=AtomicLong()
 val dropped=AtomicLong()
 val latencyNanos=AtomicLong()
 fun offer(frame: VideoFrame): Boolean = synchronized(lock) {
  if(closed) { frame.close(); return false }
  pending?.let { it.close(); dropped.incrementAndGet() }
  pending=frame
  if(!draining) { draining=true; executor.execute(::drain) }
  true
 }
 private fun drain() {
  while(true) {
   val frame=synchronized(lock) {
    if(closed) { draining=false; return }
    pending.also { pending=null } ?: run { draining=false; return }
   }
   try {
    processor.process(frame)
    if(!closed) {
     latencyNanos.set((System.nanoTime()-frame.metadata.receivedAtNanos).coerceAtLeast(0))
     processed.incrementAndGet()
    }
   } catch(error: Throwable) {
    if(!closed) try { onError(error) } finally { close() }
   } finally { frame.close() }
  }
 }
 override fun close() {
  synchronized(lock) {
   if(closed) return
   closed=true; pending?.close(); pending=null
  }
  executor.shutdownNow()
 }
 fun awaitTermination(timeout: Long,unit: TimeUnit)=executor.awaitTermination(timeout,unit)
}

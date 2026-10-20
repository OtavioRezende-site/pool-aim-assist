package com.poolaim.vision

/** At most three arrays: producer, one pending, one processor. Never recycles a live lease. */
class FrameBufferPool(private val maxBuffers: Int=3): AutoCloseable {
 private val idle=java.util.ArrayDeque<ByteArray>()
 private var count=0
 private var bytes=0L
 private var closed=false
 init { require(maxBuffers>0) }
 val ownedBytes: Long @Synchronized get()=bytes
 @Synchronized fun borrow(size: Int): ByteArray? {
  require(size>0)
  if(closed) return null
  val same=idle.firstOrNull { it.size==size }
  if(same!=null) { idle.remove(same); return same }
  if(idle.isNotEmpty()) { val old=idle.removeFirst(); count--; bytes-=old.size }
  if(count>=maxBuffers) return null
  return ByteArray(size).also { count++; bytes+=size }
 }
 @Synchronized fun release(buffer: ByteArray) {
  if(closed) { count--; bytes-=buffer.size } else idle.addLast(buffer)
 }
 @Synchronized override fun close() {
  if(closed) return
  closed=true
  for(buffer in idle) { count--; bytes-=buffer.size }
  idle.clear()
 }
}

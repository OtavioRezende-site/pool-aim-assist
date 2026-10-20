package com.poolaim.capture

import android.os.Handler
import android.os.Looper
import com.poolaim.vision.LatestFramePipeline
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArraySet

enum class CapturePhase(val label: String) { IDLE("Inativa"),STARTING("Iniciando"),RUNNING("Ativa e autorizada"),STOPPING("Encerrando"),ERROR("Erro") }
data class CaptureSnapshot(val phase: CapturePhase=CapturePhase.IDLE,val sessionId: Long=0,val message: String="Nenhuma sessão de captura",
 val width: Int=0,val height: Int=0,val sourceWidth: Int=0,val sourceHeight: Int=0,val densityDpi: Int=0,val rotation: Int=0,
 val captureFps: Double=0.0,val processingFps: Double=0.0,val latencyMs: Double=0.0,val processedFrames: Long=0,
 val dropped: Long=0,val throttled: Long=0,val bufferBytes: Long=0,val javaHeapBytes: Long=0,val pssKb: Long=0,val contentVisible: Boolean=true) {
 val inProgress: Boolean get()=phase==CapturePhase.STARTING || phase==CapturePhase.RUNNING || phase==CapturePhase.STOPPING
}
internal class CaptureSession(val id: Long) {
 val active=AtomicBoolean(true)
 @Volatile var pipeline: LatestFramePipeline?=null
 fun cancel() { active.set(false); pipeline?.close() }
}
object CaptureMonitor {
 private val main=Handler(Looper.getMainLooper())
 private val listeners=CopyOnWriteArraySet<(CaptureSnapshot)->Unit>()
 @Volatile var snapshot=CaptureSnapshot()
  private set
 @Volatile internal var session: CaptureSession?=null
 private var nextId=0L
 @Synchronized internal fun reserve(): CaptureSession? {
  if(session!=null) return null
  val fresh=CaptureSession(++nextId); session=fresh
  publish(CaptureSnapshot(CapturePhase.STARTING,fresh.id,"Aguardando inicialização da sessão autorizada"))
  return fresh
 }
 internal fun publish(value: CaptureSnapshot) {
  snapshot=value
  if(Looper.myLooper()==Looper.getMainLooper()) listeners.forEach { it(value) }
  else main.post { listeners.forEach { it(snapshot) } }
 }
 @Synchronized internal fun finish(id: Long,error: String?=null) {
  if(session?.id!=id) return
  session?.cancel(); session=null
  com.poolaim.vision.AnalysisFeed.stop()
  publish(snapshot.copy(phase=if(error==null) CapturePhase.IDLE else CapturePhase.ERROR,
   message=error ?: "Sessão encerrada; novo consentimento será necessário",captureFps=0.0,processingFps=0.0))
 }
 fun observe(listener: (CaptureSnapshot)->Unit): ()->Unit {
  listeners.add(listener); listener(snapshot)
  return { listeners.remove(listener) }
 }
}

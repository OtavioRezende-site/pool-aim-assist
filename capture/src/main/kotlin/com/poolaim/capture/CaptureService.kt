package com.poolaim.capture

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.*
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.Display
import android.view.WindowManager
import com.poolaim.vision.*
import java.util.concurrent.atomic.AtomicLong

/** A single authorization -> one MediaProjection -> one VirtualDisplay. No sticky restart. */
class CaptureService: Service() {
 private data class Screen(val width: Int,val height: Int,val density: Int,val rotation: Int)
 private data class Target(val reader: ImageReader,val size: FrameSize,val sourceWidth: Int,val sourceHeight: Int,val density: Int)
 private val main=Handler(Looper.getMainLooper())
 private lateinit var ioThread: HandlerThread
 private lateinit var io: Handler
 private lateinit var displays: DisplayManager
 private val imageLock=Any()
 private val pool=FrameBufferPool(3)
 private var session: CaptureSession?=null
 private var projection: MediaProjection?=null
 private var display: VirtualDisplay?=null
 @Volatile private var target: Target?=null
 @Volatile private var screen=Screen(1,1,160,0)
 @Volatile private var contentVisible=true
 private var listening=false
 private var released=false
 private var terminalError: String?=null
 private val accepted=AtomicLong()
 private val throttled=AtomicLong()
 private val poolDrops=AtomicLong()
 private var lastAccepted=0L
 private var metricTime=0L
 private var previousAccepted=0L
 private var previousProcessed=0L
 private val maxFps=15
 private val displayListener=object: DisplayManager.DisplayListener {
  override fun onDisplayAdded(id: Int) { }
  override fun onDisplayRemoved(id: Int) { if(id==Display.DEFAULT_DISPLAY) shutdown("A tela deixou de estar disponível") }
  override fun onDisplayChanged(id: Int) { if(id==Display.DEFAULT_DISPLAY) screenChanged() }
 }
 private val projectionCallback=object: MediaProjection.Callback() {
  override fun onStop() { if(session?.active?.get()==true) shutdown(null) }
  override fun onCapturedContentResize(width: Int,height: Int) {
   if(Build.VERSION.SDK_INT>=34 && width>0 && height>0 && session?.active?.get()==true) resize(width,height)
  }
  override fun onCapturedContentVisibilityChanged(isVisible: Boolean) { contentVisible=isVisible }
 }
 override fun onBind(intent: Intent?) = null
 override fun onCreate() {
  super.onCreate()
  displays=getSystemService(DisplayManager::class.java)
  ioThread=HandlerThread("capture-images",Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
  io=Handler(ioThread.looper)
 }
 override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
  if(intent?.action==CaptureController.STOP) { shutdown(null); return START_NOT_STICKY }
  if(projection!=null) { intent?.removeExtra(CaptureController.AUTH); return START_NOT_STICKY }
  val fresh=CaptureMonitor.session
  session=fresh
  val authorization=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra(CaptureController.AUTH,Intent::class.java)
   else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(CaptureController.AUTH)
  val resultCode=intent?.getIntExtra(CaptureController.CODE,Activity.RESULT_CANCELED)
  intent?.removeExtra(CaptureController.AUTH) // Never retain/redeliver the authorization Intent.
  if(fresh==null || !fresh.active.get() || resultCode!=Activity.RESULT_OK || authorization==null) {
   shutdown(if(fresh?.active?.get()==true) "Novo consentimento de captura necessário" else null)
   return START_NOT_STICKY
  }
  try {
   foreground()
   screen=readScreen()
   fresh.pipeline=LatestFramePipeline(CaptureController.processor) { error ->
    android.util.Log.e("PoolCapture","Erro no consumidor de frames",error)
    main.post { if(fresh.active.get()) shutdown("O processamento falhou; a captura foi encerrada") }
   }
   val manager=getSystemService(MediaProjectionManager::class.java)
   projection=manager.getMediaProjection(resultCode,authorization)
   val activeProjection=checkNotNull(projection)
   activeProjection.registerCallback(projectionCallback,main) // Required before display creation on API 34+.
   val initial=createTarget(screen.width,screen.height)
   synchronized(imageLock) {
    target=initial
    display=checkNotNull(activeProjection.createVirtualDisplay("Pool Aim Assist authorized session",initial.size.width,initial.size.height,
     screen.density,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,initial.reader.surface,null,main))
   }
   displays.registerDisplayListener(displayListener,main); listening=true
   metricTime=System.nanoTime()
   CaptureMonitor.publish(CaptureMonitor.snapshot.copy(phase=CapturePhase.RUNNING,message="Captura autorizada; pipeline de processamento ativo"))
   io.post(metrics)
  } catch(error: RuntimeException) {
   android.util.Log.e("PoolCapture","Falha ao iniciar sessão autorizada",error)
   shutdown("Não foi possível iniciar a captura. Solicite novo consentimento.")
  }
  return START_NOT_STICKY
 }
 private fun foreground() {
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("capture","Captura de tela autorizada",NotificationManager.IMPORTANCE_LOW))
  val open=Intent().setClassName(packageName,"$packageName.MainActivity").putExtra("overlay_screen","CAPTURE")
  val pending=PendingIntent.getActivity(this,42,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,43,Intent(this,CaptureService::class.java).setAction(CaptureController.STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_camera)
   .setContentTitle("Pool Aim Assist • captura ativa").setContentText("Captura autorizada em andamento. Toque para diagnósticos.")
   .setContentIntent(pending).setOngoing(true)
   .addAction(Notification.Action.Builder(null,"Parar captura",stop).build()).build()
  if(Build.VERSION.SDK_INT>=29) startForeground(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  else startForeground(42,notification)
 }
 private fun readScreen(): Screen {
  val density=resources.configuration.densityDpi
  val rotation=when(displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
   android.view.Surface.ROTATION_90 -> 90
   android.view.Surface.ROTATION_180 -> 180
   android.view.Surface.ROTATION_270 -> 270
   else -> 0
  }
  if(Build.VERSION.SDK_INT>=30) {
   val bounds=getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
   return Screen(bounds.width(),bounds.height(),density,rotation)
  }
  val metrics=android.util.DisplayMetrics()
  @Suppress("DEPRECATION") displays.getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(metrics)
  return Screen(metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,rotation)
 }
 private fun screenChanged() {
  if(session?.active?.get()!=true) return
  val old=screen; screen=readScreen()
  if(Build.VERSION.SDK_INT<34) resize(screen.width,screen.height)
  else if(screen.density!=old.density) target?.let { resize(it.sourceWidth,it.sourceHeight) }
 }
 override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); screenChanged() }
 private fun createTarget(sourceWidth: Int,sourceHeight: Int): Target {
  val size=FrameSize.fit(sourceWidth,sourceHeight)
  val reader=ImageReader.newInstance(size.width,size.height,PixelFormat.RGBA_8888,3)
  val next=Target(reader,size,sourceWidth,sourceHeight,screen.density)
  reader.setOnImageAvailableListener({ readLatest(next) },io)
  return next
 }
 private fun resize(sourceWidth: Int,sourceHeight: Int) {
  if(session?.active?.get()!=true) return
  val old=target ?: return
  if(old.sourceWidth==sourceWidth && old.sourceHeight==sourceHeight && old.density==screen.density) return
  try {
   synchronized(imageLock) {
    if(session?.active?.get()!=true) return
    val next=createTarget(sourceWidth,sourceHeight)
    try {
     // Resize the existing VirtualDisplay; a fresh createVirtualDisplay call would reuse authorization.
     display?.surface=null
     display?.resize(next.size.width,next.size.height,screen.density)
     display?.surface=next.reader.surface
     // Transfer ownership only after the new surface is accepted. On failure the old
     // reader remains reachable by releaseResources(), rather than leaking it.
     target=next
     safely { old.reader.setOnImageAvailableListener(null,null) }
     safely { old.reader.close() }
    } catch(error: RuntimeException) { next.reader.close(); throw error }
   }
  } catch(error: RuntimeException) {
   android.util.Log.e("PoolCapture","Falha no redimensionamento",error)
   shutdown("A captura foi encerrada durante mudança de resolução; autorize uma nova sessão")
  }
 }
 private fun readLatest(expected: Target) {
  val active=session ?: return
  try {
   synchronized(imageLock) {
    if(!active.active.get() || target!==expected) return
    val image=expected.reader.acquireLatestImage() ?: return
    image.use {
     if(!active.active.get() || !contentVisible) return
     val now=System.nanoTime()
     if(now-lastAccepted<1_000_000_000L/maxFps) { throttled.incrementAndGet(); return }
     val bytes=pool.borrow(expected.size.rgbaBytes) ?: run { poolDrops.incrementAndGet(); return }
     var handedOff=false
     try {
      val plane=image.planes[0]
      RgbaPixels.copy(plane.buffer,expected.size.width,expected.size.height,plane.rowStride,plane.pixelStride,bytes)
      if(!active.active.get()) return
      val meta=FrameMetadata(active.id,expected.size.width,expected.size.height,expected.sourceWidth,expected.sourceHeight,
       screen.density,screen.rotation,now,image.timestamp)
      val frame=VideoFrame(meta,bytes) { pool.release(bytes) }
      handedOff=true // The pipeline owns and releases it, including on rejection.
      if(active.pipeline?.offer(frame)==true) { accepted.incrementAndGet(); lastAccepted=now }
      else frame.close()
     } finally { if(!handedOff) pool.release(bytes) }
    }
   }
  } catch(error: RuntimeException) {
   if(active.active.get()) main.post { shutdown("Falha ao receber frames; solicite novo consentimento") }
  } catch(_: InterruptedException) { /* cancellation; image and borrowed buffer are released by finally */ }
 }
 private val metrics=object: Runnable {
  override fun run() {
   val active=session ?: return
   if(!active.active.get()) return
   val now=System.nanoTime(); val elapsed=(now-metricTime).coerceAtLeast(1)/1e9
   val count=accepted.get(); val processed=active.pipeline?.processed?.get() ?: 0
   val current=target
   val runtime=Runtime.getRuntime()
   val next=CaptureSnapshot(CapturePhase.RUNNING,active.id,"Sessão autorizada; latência medida após recebimento do ImageReader",
    current?.size?.width ?: 0,current?.size?.height ?: 0,current?.sourceWidth ?: 0,current?.sourceHeight ?: 0,screen.density,screen.rotation,
    (count-previousAccepted)/elapsed,(processed-previousProcessed)/elapsed,(active.pipeline?.latencyNanos?.get() ?: 0)/1e6,
    processed,poolDrops.get()+(active.pipeline?.dropped?.get() ?: 0),throttled.get(),pool.ownedBytes,
    runtime.totalMemory()-runtime.freeMemory(),Debug.getPss(),contentVisible)
   metricTime=now; previousAccepted=count; previousProcessed=processed
   main.post { if(active.active.get() && CaptureMonitor.session===active) CaptureMonitor.publish(next) }
   if(active.active.get()) io.postDelayed(this,1000)
  }
 }
 private fun shutdown(error: String?) { terminalError=error; releaseResources(); stopSelf() }
 private fun releaseResources() {
  if(released) return
  released=true
  session?.cancel()
  if(listening) safely { displays.unregisterDisplayListener(displayListener) }
  listening=false
  io.removeCallbacksAndMessages(null)
  // Wait only for the bounded pixel copy, never for a recognition callback. Keep Image memory valid.
  synchronized(imageLock) {
   safely { target?.reader?.setOnImageAvailableListener(null,null) }
   safely { display?.surface=null }; safely { display?.release() }; display=null
   safely { target?.reader?.close() }; target=null
  }
  safely { projection?.unregisterCallback(projectionCallback) }
  safely { projection?.stop() }; projection=null
  pool.close(); ioThread.quitSafely()
  session?.let { CaptureMonitor.finish(it.id,terminalError) }
  stopForeground(STOP_FOREGROUND_REMOVE)
 }
 private inline fun safely(action: ()->Unit) {
  try { action() } catch(error: RuntimeException) { android.util.Log.w("PoolCapture","Liberação de recurso de captura",error) }
 }
 override fun onDestroy() { releaseResources(); main.removeCallbacksAndMessages(null); super.onDestroy() }
}

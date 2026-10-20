package com.poolaim.overlay

import android.app.*
import android.app.AppOpsManager
import android.content.*
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import kotlin.math.abs
import com.poolaim.vision.AnalysisFeed
import com.poolaim.vision.TableRegion

/** Owned entirely by the service, not an Activity. OS may stop or hide these windows. */
class OverlayService: Service() {
 private lateinit var wm: WindowManager
 private lateinit var store: OverlaySettings
 private var button: View?=null
 private var menu: View?=null
 private var lines: OverlayLinesView?=null
 private var buttonParams: WindowManager.LayoutParams?=null
 private var demo=false
 private val main=Handler(Looper.getMainLooper())
 private val analysisTick=object: Runnable {
  override fun run() {
   if(!OverlayController.running) return
   if(AnalysisFeed.enabled && !AnalysisFeed.paused) { if(demo) lines?.showLines=true; demo=false; if(lines==null) try { addLines() } catch(_: RuntimeException) { stopSelf(); return } }
   if(!demo && !AnalysisFeed.enabled) removeLines()
   lines?.let { it.demonstration=demo; it.invalidate() }
   main.postDelayed(this,100)
  }
 }
 private val opsListener=AppOpsManager.OnOpChangedListener { _, _ ->
  main.post { if(!OverlayPermission.granted(this)) stopSelf() }
 }
 override fun onBind(intent: Intent?) = null
 override fun onCreate() {
  super.onCreate()
  wm=getSystemService(WindowManager::class.java); store=OverlaySettings(this)
  getSystemService(AppOpsManager::class.java).startWatchingMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,packageName,opsListener)
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if(intent?.action==OverlayController.STOP) { stopSelf(); return START_NOT_STICKY }
  if(!OverlayPermission.granted(this)) { stopSelf(); return START_NOT_STICKY }
  try {
   if(!OverlayController.running) foreground()
   if(button==null) addButton()
   OverlayController.running=true
   main.removeCallbacks(analysisTick); main.post(analysisTick)
   when(intent?.action) {
    OverlayController.DEMO -> { AnalysisFeed.pause(); demo=true; addLines(); lines?.showLines=true }
    OverlayController.UPDATE -> { removeMenu(); applySettings() }
   }
  } catch(e: RuntimeException) {
   android.util.Log.e("PoolOverlay","Não foi possível abrir a sobreposição",e)
   Toast.makeText(this,"O Android não permitiu iniciar a sobreposição",Toast.LENGTH_LONG).show()
   stopSelf()
  }
  return START_NOT_STICKY
 }
 private fun foreground() {
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("floating","Controles flutuantes",NotificationManager.IMPORTANCE_LOW))
  val open=packageManager.getLaunchIntentForPackage(packageName)!!
  val content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val stop=PendingIntent.getService(this,1,Intent(this,OverlayService::class.java).setAction(OverlayController.STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(this,"floating").setSmallIcon(android.R.drawable.ic_menu_compass)
   .setContentTitle("Pool Aim Assist • botão flutuante")
   .setContentText("Controles de análise visual e calibração")
   .setContentIntent(content).setOngoing(true)
   .addAction(Notification.Action.Builder(null,"Encerrar sobreposição",stop).build()).build()
  if(Build.VERSION.SDK_INT>=34) startForeground(41,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
  else startForeground(41,notification)
 }
 private fun dp(n: Int)=(n*resources.displayMetrics.density).toInt()
 private fun bounds(): Pair<Int,Int> {
  if(Build.VERSION.SDK_INT>=30) {
   val metrics=wm.currentWindowMetrics
   val insets=metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
   return (metrics.bounds.width()-insets.left-insets.right) to (metrics.bounds.height()-insets.top-insets.bottom)
  }
  val metrics=android.util.DisplayMetrics()
  @Suppress("DEPRECATION") wm.defaultDisplay.getMetrics(metrics)
  return metrics.widthPixels to metrics.heightPixels
 }
 private fun params(width: Int,height: Int,flags: Int)=WindowManager.LayoutParams(width,height,
  WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT }
 private fun background(color: Int, radius: Float)=GradientDrawable().apply { setColor(color); cornerRadius=radius }
 private fun addButton() {
  val view=TextView(this).apply {
   text="PA"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.rgb(10,30,36))
   background=background(Color.rgb(93,228,199),dp(100).toFloat())
   contentDescription="Abrir menu Pool Aim Assist"; isClickable=true; elevation=dp(6).toFloat()
   setOnClickListener { if(menu==null) showMenu() else removeMenu() }
  }
  // Keep the interactive child in a stable local coordinate system when moving the window.
  val root=FrameLayout(this).apply {
   clipChildren=false
   addView(view,FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT))
  }
  root.viewTreeObserver.addOnGlobalLayoutListener { publishBounds(root) }
  button=root; val size=dp(store.read().sizeDp)
  buttonParams=params(size,size,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
  applySettings(update=false)
  var downX=0f; var downY=0f; var startX=0; var startY=0; var dragged=false
  val slop=ViewConfiguration.get(this).scaledTouchSlop
  view.setOnTouchListener { v,event ->
   val p=buttonParams ?: return@setOnTouchListener false
   when(event.actionMasked) {
    MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; startX=p.x; startY=p.y; dragged=false; true }
    MotionEvent.ACTION_MOVE -> {
     val dx=event.rawX-downX; val dy=event.rawY-downY
     if(abs(dx)>slop || abs(dy)>slop) dragged=true
     if(dragged) {
      removeMenu()
      val (w,h)=bounds(); p.x=(startX+dx.toInt()).coerceIn(0,(w-p.width).coerceAtLeast(0)); p.y=(startY+dy.toInt()).coerceIn(0,(h-p.height).coerceAtLeast(0))
      update(root,p)
     }; true
    }
    MotionEvent.ACTION_UP -> {
     if(dragged) {
      removeMenu()
      val (w,h)=bounds()
      store.write(store.read().copy(x=p.x.toFloat()/(w-p.width).coerceAtLeast(1),y=p.y.toFloat()/(h-p.height).coerceAtLeast(1)))
      refreshAccessibility(root)
     } else v.performClick()
     true
    }
    MotionEvent.ACTION_CANCEL -> true
    else -> false
   }
  }
  wm.addView(root,buttonParams)
 }
 private fun applySettings(update: Boolean=true) {
  val p=buttonParams ?: return
  val s=store.read(); val (w,h)=bounds()
  p.width=dp(s.sizeDp).coerceAtMost(w); p.height=dp(s.sizeDp).coerceAtMost(h)
  p.x=(s.x*(w-p.width)).toInt(); p.y=(s.y*(h-p.height)).toInt()
  p.alpha=s.opacity
  if(update) button?.let { update(it,p); refreshAccessibility(it) }
 }
 private fun publishBounds(view: View) {
  if(!view.isAttachedToWindow) return
  val location=IntArray(2); view.getLocationOnScreen(location)
  OverlayController.buttonBounds=FloatingBounds(location[0],location[1],view.width,view.height)
  val metrics=android.util.DisplayMetrics()
  @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
  AnalysisFeed.setButtonBlock(TableRegion(location[0].toFloat()/metrics.widthPixels,location[1].toFloat()/metrics.heightPixels,
   (location[0]+view.width).toFloat()/metrics.widthPixels,(location[1]+view.height).toFloat()/metrics.heightPixels))
 }
 private fun refreshAccessibility(view: View) {
  // Moving a WindowManager root does not move its local child bounds. Announce the
  // subtree after layout so accessibility clients invalidate cached screen coordinates.
  view.post {
   if(view.isAttachedToWindow) {
    publishBounds(view)
    val event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    event.contentChangeTypes=AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
    view.sendAccessibilityEventUnchecked(event)
   }
  }
 }
 private fun update(view: View,p: WindowManager.LayoutParams) {
  try { wm.updateViewLayout(view,p) } catch(_: RuntimeException) { stopSelf() }
 }
 private fun showMenu() {
  val detectionStatus=AnalysisFeed.snapshot.statusText
  AnalysisFeed.setControlsOpen(true)
  val (w,h)=bounds()
  val content=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding(dp(8),dp(8),dp(8),dp(8))
   background=background(Color.rgb(21,40,56),dp(16).toFloat())
  }
  content.addView(TextView(this).apply { text=detectionStatus; setTextColor(Color.LTGRAY); setPadding(dp(8),dp(4),dp(8),dp(4)); textSize=12f })
  fun item(label: String,action: ()->Unit) {
   content.addView(Button(this).apply { text=label; isAllCaps=false; minHeight=dp(40); setOnClickListener { removeMenu(); action() } })
  }
  item("Iniciar análise") { if(AnalysisFeed.authorizedSession!=0L) AnalysisFeed.start() else open("ANALYSIS") }
  item(if(AnalysisFeed.paused) "Retomar análise" else "Pausar análise") { if(AnalysisFeed.paused) AnalysisFeed.start() else AnalysisFeed.pause() }
  item("Parar análise") { OverlayController.onStopAnalysis?.invoke(); demo=false; removeLines() }
  item(if(if(demo) lines?.showLines==true else AnalysisFeed.settings.linesVisible) "Ocultar linhas" else "Mostrar linhas") {
   if(demo) lines?.let { it.showLines=!it.showLines }
   else AnalysisFeed.configure(AnalysisFeed.settings.copy(linesVisible=!AnalysisFeed.settings.linesVisible))
  }
  item(if(AnalysisFeed.settings.manualDirection) "Usar mira automática" else "Usar direção manual") {
   AnalysisFeed.configure(AnalysisFeed.settings.copy(manualDirection=!AnalysisFeed.settings.manualDirection))
  }
  if(AnalysisFeed.settings.manualDirection) {
   val label=TextView(this).apply { text="Direção: %.0f°".format(AnalysisFeed.settings.angleDegrees); setTextColor(Color.WHITE) }
   content.addView(label)
   content.addView(SeekBar(this).apply { max=360; progress=(AnalysisFeed.settings.angleDegrees+180).toInt()
    setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
     override fun onStartTrackingTouch(bar: SeekBar?) { }
     override fun onStopTrackingTouch(bar: SeekBar?) { }
     override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean) {
      if(fromUser) { AnalysisFeed.configure(AnalysisFeed.settings.copy(angleDegrees=value-180f)); label.text="Direção: ${value-180}°" }
     }
    })
   })
  }
  item("Calibrar mesa") { AnalysisFeed.freezeForCalibration(); open("VISUAL_CALIBRATION") }
  item("Configurações") { open("SETTINGS") }
  item("Encerrar aplicativo") {
   OverlayController.onStopAnalysis?.invoke()
   sendBroadcast(Intent(OverlayController.EXIT).setPackage(packageName))
   getSystemService(ActivityManager::class.java).appTasks.forEach { it.finishAndRemoveTask() }
   stopSelf()
  }
  // Only this compact window consumes touches. No full-screen touch-dismiss surface is installed.
  val scroll=ScrollView(this).apply { addView(content); isFillViewport=false }
  menu=scroll
  val width=dp(272).coerceAtMost(w)
  val p=params(width,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
  scroll.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((h-dp(16)).coerceAtLeast(1),View.MeasureSpec.AT_MOST))
  p.height=scroll.measuredHeight.coerceAtMost((h-dp(16)).coerceAtLeast(1))
  val bubble=buttonParams!!; val gap=dp(8)
  p.x=when {
   bubble.x>=width+gap -> bubble.x-width-gap
   bubble.x+bubble.width+gap+width<=w -> bubble.x+bubble.width+gap
   else -> bubble.x.coerceIn(0,(w-width).coerceAtLeast(0))
  }
  p.y=bubble.y.coerceIn(0,(h-p.height).coerceAtLeast(0))
  // Keep PA reachable even on a narrow display: stack a scrollable menu above/below it.
  if(p.x < bubble.x+bubble.width && p.x+width > bubble.x) {
   val below=(h-bubble.y-bubble.height-gap).coerceAtLeast(0)
   val above=(bubble.y-gap).coerceAtLeast(0)
   if(below>=above) {
    p.height=p.height.coerceAtMost(below.coerceAtLeast(1)); p.y=(bubble.y+bubble.height+gap).coerceAtMost(h-p.height)
   } else {
    p.height=p.height.coerceAtMost(above.coerceAtLeast(1)); p.y=(bubble.y-gap-p.height).coerceAtLeast(0)
   }
  }
  try { wm.addView(scroll,p) } catch(_: RuntimeException) { menu=null; stopSelf() }
 }
 private fun open(screen: String) {
  val intent=packageManager.getLaunchIntentForPackage(packageName) ?: return
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(OverlayController.SCREEN,screen)
  try { startActivity(intent) } catch(_: RuntimeException) { Toast.makeText(this,"Abra o Pool Aim Assist para continuar",Toast.LENGTH_LONG).show() }
 }
 private fun addLines() {
  if(lines!=null) return
  val view=OverlayLinesView(this)
  val p=params(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
   WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
  // Android 12+ rejects touches through opaque untrusted windows. Stay below its threshold.
  p.alpha=if(Build.VERSION.SDK_INT>=31) minOf(.35f,getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch*.5f) else .35f
  lines=view
  wm.addView(view,p)
  // Keep the button above the newly added drawing window.
  button?.let { wm.removeView(it); wm.addView(it,buttonParams) }
 }
 private fun remove(view: View?) { if(view!=null) try { wm.removeViewImmediate(view) } catch(e: RuntimeException) { android.util.Log.w("PoolOverlay","Janela já indisponível durante encerramento",e) } }
 private fun removeMenu() { remove(menu); menu=null; AnalysisFeed.setControlsOpen(false) }
 private fun removeLines() { remove(lines); lines=null }
 override fun onConfigurationChanged(newConfig: Configuration) {
  super.onConfigurationChanged(newConfig)
  removeMenu()
  // Reattach after rotation so drawing, input and accessibility share a fresh window frame.
  remove(button); button=null; buttonParams=null; OverlayController.buttonBounds=null
  if(OverlayController.running) try { addButton() } catch(_: RuntimeException) { stopSelf() }
  lines?.invalidate()
 }
 override fun onDestroy() {
  OverlayController.buttonBounds=null
  OverlayController.lastRenderedFrameNanos=0L
  AnalysisFeed.setButtonBlock(null)
  main.removeCallbacksAndMessages(null)
  getSystemService(AppOpsManager::class.java).stopWatchingMode(opsListener)
  removeMenu(); removeLines(); remove(button); button=null; buttonParams=null
  OverlayController.running=false
  stopForeground(STOP_FOREGROUND_REMOVE)
  super.onDestroy()
 }
}

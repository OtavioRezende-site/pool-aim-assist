package com.poolaim.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.poolaim.capture.CaptureController
import com.poolaim.capture.CaptureMonitor
import com.poolaim.overlay.OverlayController
import com.poolaim.overlay.OverlayPermission
import com.poolaim.vision.AnalysisFeed

/** Only hosts Android permission dialogs. The floating service owns the actual controls. */
class FloatingActivity : ComponentActivity() {
 private var flowStarted=false
 private var startAnalysis=false
 private val overlayPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
  if(OverlayPermission.granted(this)) notificationThenContinue()
  else failure("Permita aparecer sobre outros aplicativos para usar o botão flutuante.")
 }
 private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()) { continueFlow() }
 private val consent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val authorization=result.data
  if(result.resultCode==RESULT_OK && authorization!=null) {
   try {
    if(!CaptureController.start(this,result.resultCode,authorization) && !CaptureMonitor.snapshot.inProgress)
     throw IllegalStateException("Sessão indisponível")
    finish()
   } catch(_: RuntimeException) { AnalysisFeed.stop(); failure("Não foi possível iniciar a captura. Tente autorizar uma nova sessão.") }
  } else {
   AnalysisFeed.stop()
   Toast.makeText(this,"Captura não autorizada",Toast.LENGTH_SHORT).show()
   showControls()
  }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  VisualRuntime.initialize(this)
  flowStarted=savedInstanceState?.getBoolean("flowStarted") ?: false
  startAnalysis=savedInstanceState?.getBoolean("startAnalysis") ?: (intent.action==OverlayController.REQUEST_ANALYSIS)
  content("Abrindo controles flutuantes…")
  if(!flowStarted) window.decorView.post { beginFlow() }
 }
 override fun onSaveInstanceState(outState: Bundle) {
  outState.putBoolean("flowStarted",flowStarted); outState.putBoolean("startAnalysis",startAnalysis)
  super.onSaveInstanceState(outState)
 }
 private fun beginFlow() {
  if(flowStarted) return
  flowStarted=true
  try {
   if(OverlayPermission.granted(this)) notificationThenContinue()
   else overlayPermission.launch(OverlayPermission.settings(this))
  } catch(_: RuntimeException) { failure("Não foi possível abrir as permissões neste dispositivo.") }
 }
 private fun notificationThenContinue() {
  if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
  else continueFlow()
 }
 private fun continueFlow() {
  if(!startAnalysis) { showControls(); return }
  try {
   OverlayController.start(this)
   AnalysisFeed.start()
   if(CaptureMonitor.snapshot.inProgress) { finish(); return }
   val manager=getSystemService(MediaProjectionManager::class.java)
   val request=if(Build.VERSION.SDK_INT>=34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
   consent.launch(request)
  } catch(_: RuntimeException) { AnalysisFeed.stop(); failure("O Android não permitiu iniciar a análise. Tente novamente.") }
 }
 private fun showControls() {
  try { OverlayController.start(this,openMenu=true); finish() }
  catch(_: RuntimeException) { failure("Não foi possível abrir a sobreposição. Confira a permissão.") }
 }
 private fun content(message: String,retry: Boolean=false) {
  val density=resources.displayMetrics.density
  val box=LinearLayout(this).apply {
   orientation=LinearLayout.VERTICAL; setPadding((24*density).toInt(),(24*density).toInt(),(24*density).toInt(),(24*density).toInt())
   setBackgroundColor(Color.rgb(21,40,56))
   addView(TextView(this@FloatingActivity).apply { text="Pool Aim Assist"; textSize=20f; setTextColor(Color.rgb(93,228,199)) })
   addView(TextView(this@FloatingActivity).apply { text=message; textSize=16f; setTextColor(Color.WHITE) })
   if(retry) {
    addView(Button(this@FloatingActivity).apply { text="Tentar novamente"; setOnClickListener { flowStarted=false; beginFlow() } })
    addView(Button(this@FloatingActivity).apply { text="Fechar"; setOnClickListener { finish() } })
   }
  }
  setContentView(box)
  window.setLayout((320*density).toInt(),ViewGroup.LayoutParams.WRAP_CONTENT)
 }
 private fun failure(message: String) { content(message,retry=true) }
}

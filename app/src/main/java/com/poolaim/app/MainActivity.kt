package com.poolaim.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.poolaim.overlay.OverlayController
import com.poolaim.overlay.OverlaySettings
import com.poolaim.overlay.FloatingSettings
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.poolaim.capture.*
import com.poolaim.vision.AnalysisFeed
import android.media.projection.MediaProjectionConfig
import com.poolaim.recognition.TrainingFrameProbe
import android.media.projection.MediaProjectionManager
import kotlinx.coroutines.delay
import com.poolaim.recognition.RecognitionStatus
import com.poolaim.overlay.OverlayPermission
import com.poolaim.physics.Vec
import com.poolaim.physics.Table
import com.poolaim.physics.ScreenCalibration

class MainActivity : ComponentActivity() {
 private val exitReceiver=object: android.content.BroadcastReceiver() {
  override fun onReceive(context: android.content.Context?,intent: Intent?) { if(intent?.action==OverlayController.EXIT) finish() }
 }
 override fun onDestroy() { unregisterReceiver(exitReceiver); super.onDestroy() }
 private var requestedScreen by mutableStateOf<Screen?>(null)
 private var demoRequested=false
 private var analysisRequested=false
 override fun onResume() { super.onResume(); AnalysisFeed.ownUiVisible=true }
 override fun onStop() { AnalysisFeed.ownUiVisible=false; super.onStop() }
 private fun beginAnalysis() { AnalysisFeed.start(); analysisRequested=true; beginFloating(false) }
 private val captureConsent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
  val authorization=result.data
  if(result.resultCode==RESULT_OK && authorization!=null) {
   try { CaptureController.start(this,result.resultCode,authorization) }
   catch(_: RuntimeException) { Toast.makeText(this,"Falha ao iniciar captura; solicite novo consentimento",Toast.LENGTH_LONG).show() }
  } else { AnalysisFeed.stop(); Toast.makeText(this,"Captura não autorizada",Toast.LENGTH_SHORT).show() }
 }
 private val captureNotification=registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestCaptureConsent() }
 private fun requestCaptureConsent() {
  if(!CaptureMonitor.snapshot.inProgress) {
   val manager=getSystemService(MediaProjectionManager::class.java)
   val request=if(Build.VERSION.SDK_INT>=34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
   captureConsent.launch(request)
  }
 }
 private fun beginCapture() {
  if(CaptureMonitor.snapshot.inProgress) return
  if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   captureNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
  else requestCaptureConsent()
 }
 private val permissionResult=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
  if(OverlayPermission.granted(this)) notificationThenStart()
  else { analysisRequested=false; AnalysisFeed.pause(); Toast.makeText(this,"Permissão de sobreposição não concedida",Toast.LENGTH_LONG).show() }
 }
 private val notificationResult=registerForActivityResult(ActivityResultContracts.RequestPermission()) { startFloating() }
 private fun beginFloating(demo: Boolean=false) {
  demoRequested=demo
  if(OverlayPermission.granted(this)) notificationThenStart()
  else try { permissionResult.launch(OverlayPermission.settings(this)) }
  catch(_: android.content.ActivityNotFoundException) { Toast.makeText(this,"Permissão indisponível neste dispositivo",Toast.LENGTH_LONG).show() }
 }
 private fun notificationThenStart() {
  if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   notificationResult.launch(Manifest.permission.POST_NOTIFICATIONS)
  else startFloating()
 }
 private fun startFloating() {
  try { OverlayController.start(this,demoRequested); if(analysisRequested) { analysisRequested=false; beginCapture() } }
  catch(_: RuntimeException) { analysisRequested=false; AnalysisFeed.pause(); Toast.makeText(this,"Não foi possível iniciar; confira a permissão e tente com o aplicativo aberto",Toast.LENGTH_LONG).show() }
 }
 private fun destination(intent: Intent?) {
  requestedScreen=intent?.getStringExtra(OverlayController.SCREEN)?.let { value -> Screen.entries.find { it.name==value } }
 }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); destination(intent) }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  ContextCompat.registerReceiver(this,exitReceiver,android.content.IntentFilter(OverlayController.EXIT),ContextCompat.RECEIVER_NOT_EXPORTED)
  demoRequested=savedInstanceState?.getBoolean("demoRequested") ?: false
  analysisRequested=savedInstanceState?.getBoolean("analysisRequested") ?: false
  destination(intent)
  VisualRuntime.initialize(this)
  setContent {
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF5DE4C7), background=Color(0xFF0B1723), surface=Color(0xFF152838))) {
    Surface(Modifier.fillMaxSize()) { PoolApp() }
   }
  }
 }
 override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("demoRequested",demoRequested); outState.putBoolean("analysisRequested",analysisRequested); super.onSaveInstanceState(outState) }
 @Composable private fun PoolApp(vm: PoolViewModel = viewModel()) {
  LaunchedEffect(vm.screen) {
   AnalysisFeed.trainingVisible=vm.screen==Screen.VISUAL_TEST
   requestedOrientation=if(vm.screen==Screen.DEMO || vm.screen==Screen.CAPTURE) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
  }
  LaunchedEffect(requestedScreen) { requestedScreen?.let { vm.screen=it; requestedScreen=null } }
  var capture by remember { mutableStateOf(CaptureMonitor.snapshot) }
  DisposableEffect(Unit) { val stop=CaptureMonitor.observe { capture=it }; onDispose { stop() } }
  var granted by remember { mutableStateOf(OverlayPermission.granted(this)) }
  var overlayDialog by remember { mutableStateOf(false) }
  DisposableEffect(Unit) {
   val observer=LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_RESUME) granted=OverlayPermission.granted(this@MainActivity) }
   lifecycle.addObserver(observer)
   onDispose { lifecycle.removeObserver(observer) }
  }
  BackHandler(vm.screen != Screen.HOME) { vm.screen=Screen.HOME }
  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
    Column { Text("POOL AIM ASSIST",style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
     Text("Laboratório de trajetórias • versão 0.2",style=MaterialTheme.typography.bodyMedium) }
    Column {
     TextButton(onClick={ if(OverlayPermission.granted(this@MainActivity)) { OverlayController.start(this@MainActivity,openMenu=true); finish() } else beginFloating() }) { Text("Voltar ao botão flutuante") }
     if(vm.screen!=Screen.HOME) TextButton(onClick={vm.screen=Screen.HOME}) { Text("Voltar ao início") }
    }
   }
   Spacer(Modifier.height(16.dp))
   when(vm.screen) {
    Screen.HOME -> Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
     Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("Explore a próxima jogada",style=MaterialTheme.typography.headlineSmall)
      Text("Simulação manual com colisões ideais e reflexões nas tabelas.")
      Button(onClick={vm.screen=Screen.ANALYSIS},modifier=Modifier.fillMaxWidth()) { Text("Iniciar assistência visual") }
      Button(onClick={vm.screen=Screen.SIMULATOR},modifier=Modifier.fillMaxWidth()) { Text("Iniciar simulador") }
      OutlinedButton(onClick={overlayDialog=true},modifier=Modifier.fillMaxWidth()) { Text("Ativar sobreposição") }
      OutlinedButton(onClick={vm.screen=Screen.CALIBRATION},modifier=Modifier.fillMaxWidth()) { Text("Calibrar mesa") }
      OutlinedButton(onClick={vm.screen=Screen.CAPTURE},modifier=Modifier.fillMaxWidth()) { Text("Captura e diagnósticos") }
      OutlinedButton(onClick={vm.screen=Screen.DEMO},modifier=Modifier.fillMaxWidth()) { Text("Testar sobreposição") }
      OutlinedButton(onClick={vm.screen=Screen.SETTINGS},modifier=Modifier.fillMaxWidth()) { Text("Configurações") }
     }
     Card(Modifier.weight(1f)) { Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
      Text("Permissões e recursos",style=MaterialTheme.typography.titleLarge)
      Text("Sobreposição: "+if(granted) "permissão concedida" else "permissão não concedida")
      Text("Captura: ${capture.phase.label}")
      Text(CaptureStatus.description)
      Text(RecognitionStatus.description)
      Text("O simulador funciona sem permissões especiais e sem conexão com a internet.")
     } }
    }
    Screen.ANALYSIS -> AnalysisPanel(capture,onStart={beginAnalysis()},onStop={CaptureController.stop(this@MainActivity)},
     onCalibrate={AnalysisFeed.freezeForCalibration(); vm.screen=Screen.VISUAL_CALIBRATION},onTest={vm.screen=Screen.VISUAL_TEST})
    Screen.VISUAL_CALIBRATION -> VisualCalibrationPanel {
     if(AnalysisFeed.authorizedSession!=0L) AnalysisFeed.start()
     finish()
    }
    Screen.VISUAL_TEST -> VisualTrainingPanel(onStart={AnalysisFeed.configure(AnalysisFeed.settings.copy(manual=null,manualDirection=false)); beginAnalysis()},onStop={CaptureController.stop(this@MainActivity)})
    Screen.SIMULATOR -> {
     Text("Toque na mesa para mirar. Pressione e segure para mover a bola amarela.")
     Table(vm,Modifier.weight(1f).fillMaxWidth().padding(vertical=12.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
      Text("Ângulo: %.0f°".format(vm.angle),Modifier.width(120.dp))
      Slider(value=vm.angle,onValueChange={vm.angle=it},valueRange=-180f..180f,modifier=Modifier.weight(1f))
      TextButton(onClick={vm.angle=0f; vm.target=Vec(1.35f,.5f)}) { Text("Reiniciar") }
     }
     Text("Branco: bola branca • Amarelo: bola-alvo • Azul: reflexões. Sem efeito, atrito ou caçapas; trajetória residual da branca não calculada.",style=MaterialTheme.typography.bodySmall)
    }
    Screen.CALIBRATION -> Column(Modifier.verticalScroll(rememberScrollState())) {
     Text("Calibração manual do simulador",style=MaterialTheme.typography.headlineSmall)
     Text("Mesa normalizada 2:1. Ajuste o tamanho relativo das bolas; salvo neste dispositivo. Calibração por câmera ainda não implementada.")
     Text("Raio: %.3f da altura da mesa".format(vm.radius))
     Slider(value=vm.radius,onValueChange=vm::updateRadius,valueRange=.02f.. .06f)
     Table(vm,Modifier.fillMaxWidth().height(180.dp))
    }
    Screen.DEMO -> Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Text("Demonstração da sobreposição",style=MaterialTheme.typography.headlineSmall)
     Text("Linhas de treinamento fixas, sem captura. Arraste o botão PA, toque para abrir o menu e alterne de aplicativo quando permitido. O Android pode ocultar estas janelas.")
     var taps by rememberSaveable { mutableIntStateOf(0) }
     Button(onClick={beginFloating(true)}) { Text("Exibir linhas de demonstração") }
     Button(onClick={taps++},modifier=Modifier.fillMaxWidth().height(72.dp)) { Text("Controle abaixo da camada: $taps toques") }
     OutlinedButton(onClick={OverlayController.stop(this@MainActivity)}) { Text("Encerrar sobreposição") }
     Text("Teste manual: toque neste controle com linhas visíveis, arraste PA, abra o menu, gire o dispositivo e encerre pelo menu ou pela notificação.")
    }
    Screen.CAPTURE -> Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text("Captura autorizada • ${capture.phase.label}",style=MaterialTheme.typography.headlineSmall)
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      Button(onClick={beginCapture()},enabled=!capture.inProgress) { Text("Autorizar captura de tela") }
      OutlinedButton(onClick={CaptureController.stop(this@MainActivity)},enabled=capture.inProgress) { Text("Parar captura") }
     }
     Text(CaptureStatus.description)
     Text(capture.message)
     Text("Fonte: ${capture.sourceWidth} × ${capture.sourceHeight} • RGBA: ${capture.width} × ${capture.height} • ${capture.densityDpi} dpi • rotação ${capture.rotation}°")
     Text("FPS recebidos: %.1f • processados: %.1f • latência: %.1f ms".format(capture.captureFps,capture.processingFps,capture.latencyMs))
     Text("Frames: ${capture.processedFrames} • descartados: ${capture.dropped} • limitados a 15 FPS: ${capture.throttled}")
     Text("Memória: buffers %.1f MiB • heap Java %.1f MiB • PSS %.1f MiB".format(capture.bufferBytes/1048576.0,capture.javaHeapBytes/1048576.0,capture.pssKb/1024.0))
     val probe=TrainingFrameProbe.latest?.takeIf { it.sessionId==capture.sessionId }
     Text("Diagnóstico de verde: "+(probe?.let { "%.1f%% (%d amostras)".format(it.greenFraction*100,it.samples) } ?: "aguardando frames"))
     Text("Teste de pixels; acesse Assistência visual para detecção experimental de mesa, bolas e mira. Latência após recebimento, sem medir o atraso do compositor. Memória PSS inclui o processo completo.",style=MaterialTheme.typography.bodySmall)
     var animate by rememberSaveable { mutableStateOf(false) }
     LaunchedEffect(animate) { while(animate) { delay(100); vm.angle=if(vm.angle>=179f) -180f else vm.angle+2f } }
     TextButton(onClick={animate=!animate}) { Text(if(animate) "Parar animação" else "Animar mesa de teste") }
     Table(vm,Modifier.fillMaxWidth().height(180.dp))
    }
    Screen.SETTINGS -> Column(Modifier.verticalScroll(rememberScrollState())) {
     Text("Configurações",style=MaterialTheme.typography.headlineSmall)
     var reflections by remember { mutableIntStateOf(AnalysisFeed.settings.maxReflections) }
     Text("Reflexões máximas por trajetória: $reflections")
     Slider(value=reflections.toFloat(),onValueChange={
      reflections=it.toInt(); vm.updateReflections(reflections)
      AnalysisFeed.configure(AnalysisFeed.settings.copy(maxReflections=reflections))
     },valueRange=0f..2f,steps=1)
     Text("Preferências salvas automaticamente. Orientação paisagem com suporte a tablets, incluindo Galaxy Tab S9 Plus.")
     Text(RecognitionStatus.description)
     FloatingOptions()
    }
   }
  }
  if(overlayDialog) AlertDialog(onDismissRequest={overlayDialog=false},title={Text("Permissão de sobreposição")},text={Text(OverlayPermission.status)},confirmButton={TextButton(onClick={
   overlayDialog=false
   beginFloating()
  }) { Text(if(granted) "Iniciar botão flutuante" else "Conceder permissão") }},dismissButton={TextButton(onClick={overlayDialog=false}) { Text("Fechar") }})
 }
 @Composable private fun FloatingOptions() {
  val store=remember { OverlaySettings(this) }
  var settings by remember { mutableStateOf(store.read()) }
  DisposableEffect(store) {
   val stop=store.observe { settings=store.read() }
   onDispose { stop() }
  }
  fun save(value: FloatingSettings) {
   settings=value.sanitized(); store.write(settings)
   try { OverlayController.refresh(this) } catch(_: RuntimeException) { Toast.makeText(this,"Reabra a sobreposição para aplicar",Toast.LENGTH_SHORT).show() }
  }
  Spacer(Modifier.height(16.dp))
  Text("Botão flutuante",style=MaterialTheme.typography.titleLarge)
  Text("Posição horizontal: ${(settings.x*100).toInt()}%")
  Slider(settings.x,{save(settings.copy(x=it))},valueRange=0f..1f)
  Text("Posição vertical: ${(settings.y*100).toInt()}%")
  Slider(settings.y,{save(settings.copy(y=it))},valueRange=0f..1f)
  Text("Tamanho: ${settings.sizeDp} dp")
  Slider(settings.sizeDp.toFloat(),{save(settings.copy(sizeDp=it.toInt()))},valueRange=32f..72f)
  Text("Opacidade: ${(settings.opacity*100).toInt()}%")
  Slider(settings.opacity,{save(settings.copy(opacity=it))},valueRange=.3f..1f)
  Button(onClick={beginFloating()}) { Text("Iniciar botão flutuante") }
  OutlinedButton(onClick={OverlayController.stop(this@MainActivity)}) { Text("Encerrar sobreposição") }
  Text("Notificação persistente enquanto o serviço estiver ativo. Se notificações forem negadas, o Android pode exibir o serviço apenas no gerenciador de aplicativos ativos. O sistema pode interromper o serviço; não há reinício forçado.")
 }
}

@Composable private fun Table(vm: PoolViewModel, modifier: Modifier) {
 // Android Canvas coordinates are fitted inside the Compose host, preserving table aspect ratio.
 Canvas(modifier.background(Color(0xFF101E28)).pointerInput(vm.radius) {
  fun point(x: Float,y: Float): Vec {
   val scale=minOf((size.width-32f)/2f,size.height-32f)
   val left=(size.width-2*scale)/2; val top=(size.height-scale)/2
   val uv=ScreenCalibration(left,top,2*scale,scale).fromScreen(Vec(x,y))
   val metric=Table().fromNormalized(Vec(uv.x.coerceIn(0f,1f),uv.y.coerceIn(0f,1f)))
   return Vec(metric.x.coerceIn(vm.radius,2-vm.radius),metric.y.coerceIn(vm.radius,1-vm.radius))
  }
  detectTapGestures(onTap={vm.aim(point(it.x,it.y))},onLongPress={
   val p=point(it.x,it.y)
   if((p-vm.cue).dot(p-vm.cue)>4*vm.radius*vm.radius) vm.target=p
  })
 }) {
  val scale=minOf((size.width-32f)/2f,size.height-32f)
  if(scale<=0) return@Canvas
  val left=(size.width-2*scale)/2; val top=(size.height-scale)/2
  val calibration=ScreenCalibration(left,top,2*scale,scale)
  fun screen(point: Vec)=calibration.toScreen(Table().toNormalized(point))
  val canvas=drawContext.canvas.nativeCanvas
  val paint=Paint(Paint.ANTI_ALIAS_FLAG)
  paint.color=android.graphics.Color.rgb(104,72,48)
  canvas.drawRoundRect(left-12,top-12,left+2*scale+12,top+scale+12,16f,16f,paint)
  paint.color=android.graphics.Color.rgb(15,99,81)
  canvas.drawRect(left,top,left+2*scale,top+scale,paint)
  paint.strokeWidth=3f
  vm.path().forEach {
   paint.color=when { it.reflection > 0 -> android.graphics.Color.CYAN; it.objectBall -> android.graphics.Color.YELLOW; else -> android.graphics.Color.WHITE }
   val a=screen(it.from); val b=screen(it.to)
   canvas.drawLine(a.x,a.y,b.x,b.y,paint)
  }
  paint.color=android.graphics.Color.WHITE
  val cue=screen(vm.cue)
  canvas.drawCircle(cue.x,cue.y,vm.radius*scale,paint)
  paint.color=android.graphics.Color.rgb(255,205,67)
  val target=screen(vm.target)
  canvas.drawCircle(target.x,target.y,vm.radius*scale,paint)
 }
}

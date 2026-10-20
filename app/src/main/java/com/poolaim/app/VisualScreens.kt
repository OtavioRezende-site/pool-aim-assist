package com.poolaim.app

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.poolaim.vision.*
import com.poolaim.capture.CaptureSnapshot
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable fun AnalysisPanel(capture: CaptureSnapshot,onStart: ()->Unit,onStop: ()->Unit,onCalibrate: ()->Unit,onTest: ()->Unit) {
 var status by remember { mutableStateOf(AnalysisFeed.snapshot) }
 var settings by remember { mutableStateOf(AnalysisFeed.settings) }
 var paused by remember { mutableStateOf(AnalysisFeed.paused) }
 LaunchedEffect(Unit) { while(true) { status=AnalysisFeed.snapshot; settings=AnalysisFeed.settings; paused=AnalysisFeed.paused; delay(200) } }
 Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Assistência visual",style=MaterialTheme.typography.headlineSmall)
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Button(onClick=onStart) { Text("Iniciar") }
   OutlinedButton(onClick=onStop) { Text("Parar") }
   TextButton(onClick={ if(paused) AnalysisFeed.start() else AnalysisFeed.pause() },enabled=capture.inProgress) { Text(if(paused) "Retomar" else "Pausar") }
  }
  Text("Captura: ${capture.phase.label} • ${status.statusText}")
  Text("Bolas: ${status.balls.size} • qualidade heurística: ${(status.confidence*100).toInt()}% • direção: "+(status.directionDegrees?.let { "%.1f°".format(it) } ?: "não disponível"))
  Text("Inicie, confirme as permissões do Android e abra a mesa no outro aplicativo. Nesta tela as previsões ficam suspensas. Use o botão PA para pausar, mudar direção ou calibrar.")
  Text("Magenta: branca • laranja: alvo • azul: reflexões. Estimativa geométrica sem força, atrito, efeito ou garantia de acerto.")
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   OutlinedButton(onClick=onCalibrate) { Text("Calibração manual") }
   TextButton(onClick=onTest) { Text("Mesa para testar análise") }
  }
  Row { Switch(checked=settings.manualDirection,onCheckedChange={ AnalysisFeed.configure(settings.copy(manualDirection=it)); settings=AnalysisFeed.settings }); Text("Direção manual",Modifier.padding(12.dp)) }
  if(settings.manualDirection) {
   Text("Ângulo: %.0f° (0° direita, 90° para baixo)".format(settings.angleDegrees))
   Slider(settings.angleDegrees,{ AnalysisFeed.configure(settings.copy(angleDegrees=it)); settings=AnalysisFeed.settings },valueRange=-180f..180f)
   Text("A direção também pode ser ajustada no menu flutuante sobre o jogo. Arraste PA para fora do pano para não ocultar bolas.")
  }
  Text("Transparência das linhas: ${(settings.opacity*100).toInt()}% de opacidade")
  Slider(settings.opacity,{ AnalysisFeed.configure(settings.copy(opacity=it)); settings=AnalysisFeed.settings },valueRange=.1f..1f)
  Text("A detecção automática é experimental para mesas retangulares vistas de cima, com pano verde/azul e mira clara. Se não houver confiança, as linhas somem; calibre mesa/bolas e use direção manual. Recalibre após as bolas se moverem ou o visual mudar.",style=MaterialTheme.typography.bodySmall)
 }
}

@Composable fun VisualCalibrationPanel(onSaved: ()->Unit) {
 val frozen=remember { AnalysisFeed.calibrationFrame }
 val preview=frozen?.preview
 if(frozen==null || preview==null) {
  Text("Sem quadro congelado. Inicie a captura, abra a mesa desejada e toque em PA → Calibrar mesa. O quadro anterior à abertura desta tela será preservado.")
  return
 }
 val bitmap=remember(preview) { Bitmap.createBitmap(preview.argb,preview.width,preview.height,Bitmap.Config.ARGB_8888).asImageBitmap() }
 var region by remember { mutableStateOf(frozen.region ?: TableRegion(.1f,.2f,.9f,.8f)) }
 var corner by remember { mutableStateOf<ImagePoint?>(null) }
 var mode by remember { mutableStateOf("mesa") }
 var balls by remember { mutableStateOf(frozen.balls.let { list -> frozen.cueIndex?.let { i -> listOf(list[i])+list.filterIndexed { j,_->j!=i } } ?: emptyList() }) }
 var radius by remember { mutableFloatStateOf(.03f) }
 var checked by remember { mutableStateOf(false) }
 var message by remember { mutableStateOf("") }
 fun save(includeBalls: Boolean) {
  if(!region.valid()) { message="Selecione os dois cantos internos da mesa"; return }
  val selected=if(includeBalls) balls else emptyList()
  if(includeBalls) {
   try {
    val ratio=region.width*frozen.sourceWidth/(region.height*frozen.sourceHeight)
    val physical=selected.mapIndexed { i,b -> com.poolaim.physics.Ball(i.toString(),com.poolaim.physics.Vec((b.center.x-region.left)/region.width*ratio,(b.center.y-region.top)/region.height),b.radius/region.height) }
    com.poolaim.physics.GeometryEngine(com.poolaim.physics.Table(ratio,1f)).firstHit(physical.first(),com.poolaim.physics.Vec(1f,0f),physical.drop(1))
   } catch(_: IllegalArgumentException) { message="Bolas sobrepostas ou fora dos limites; corrija centros e raios"; return }
  }
  val colors=selected.map { b -> ImageColors.averageBall(preview.argb,preview.width,preview.height,b.center,b.radius) }
  val scene=ManualScene(region,selected,colors,frozen.sourceWidth.toFloat()/frozen.sourceHeight,frozen.rotation)
  AnalysisFeed.configure(AnalysisFeed.settings.copy(manual=scene,manualDirection=includeBalls || AnalysisFeed.settings.manualDirection))
  onSaved()
 }
 Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Calibrar quadro congelado",style=MaterialTheme.typography.headlineSmall)
  Text("Mesa: toque no canto interno superior esquerdo e depois no inferior direito. Bolas: a primeira é a branca; inclua todas as bolas visíveis. Esta calibração vale para esta orientação e esta disposição.")
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   TextButton(onClick={ mode="mesa"; corner=null }) { Text("Selecionar mesa") }
   TextButton(onClick={ mode="bolas"; balls=emptyList(); checked=false }) { Text("Marcar branca e bolas") }
   TextButton(onClick={ if(balls.isNotEmpty()) balls=balls.dropLast(1); checked=false }) { Text("Remover última") }
  }
  Text(if(mode=="mesa") if(corner==null) "Toque no primeiro canto" else "Toque no segundo canto" else "Toque: "+if(balls.isEmpty()) "bola branca" else "próxima bola (${balls.size}/16)")
  Canvas(Modifier.fillMaxWidth().aspectRatio(preview.width.toFloat()/preview.height).pointerInput(mode,region,corner,balls,radius) {
   detectTapGestures { offset ->
    val point=ImagePoint((offset.x/size.width).coerceIn(0f,1f),(offset.y/size.height).coerceIn(0f,1f))
    checked=false
    if(mode=="mesa") {
     val first=corner
     if(first==null) corner=point else { region=TableRegion(min(first.x,point.x),min(first.y,point.y),max(first.x,point.x),max(first.y,point.y)); corner=null }
    } else if(balls.size<16) balls=balls+ObservedBall(point,radius*region.height)
   }
  }) {
   drawImage(bitmap,dstSize=IntSize(size.width.toInt(),size.height.toInt()))
   drawRect(Color.Cyan,Offset(region.left*size.width,region.top*size.height),androidx.compose.ui.geometry.Size(region.width*size.width,region.height*size.height),style=Stroke(2.dp.toPx()))
   val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.WHITE; textSize=14.dp.toPx() }
   balls.forEachIndexed { index,b ->
    drawCircle(if(index==0) Color.Magenta else Color.Yellow,b.radius*size.height,Offset(b.center.x*size.width,b.center.y*size.height),style=Stroke(2.dp.toPx()))
    drawContext.canvas.nativeCanvas.drawText(if(index==0) "B" else index.toString(),b.center.x*size.width,b.center.y*size.height,paint)
   }
   corner?.let { drawCircle(Color.Cyan,5.dp.toPx(),Offset(it.x*size.width,it.y*size.height)) }
  }
  Text("Raio relativo: %.3f da altura da mesa".format(radius))
  Slider(radius,{ radius=it; balls=balls.map { b -> b.copy(radius=radius*region.height) }; checked=false },valueRange=.015f.. .055f)
  Row { Checkbox(checked,{ checked=it }); Text("Conferi os limites, a branca e todas as bolas visíveis.",Modifier.padding(top=12.dp)) }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Button(onClick={ save(true) },enabled=checked && balls.isNotEmpty() && region.valid()) { Text("Salvar mesa e bolas") }
   OutlinedButton(onClick={ save(false) },enabled=region.valid()) { Text("Salvar só limites") }
  }
  TextButton(onClick={ AnalysisFeed.configure(AnalysisFeed.settings.copy(manual=null)); onSaved() }) { Text("Restaurar detecção automática") }
  Text(message)
  Text("Salvar só limites mantém a detecção automática das bolas. Na geometria manual, a cor nas posições marcadas é verificada a cada quadro; mudanças exigem recalibração. Perspectiva inclinada não é suportada.",style=MaterialTheme.typography.bodySmall)
 }
}

@Composable fun VisualTrainingPanel(onStart: ()->Unit,onStop: ()->Unit) {
 var angle by remember { mutableFloatStateOf(0f) }
 var status by remember { mutableStateOf(AnalysisFeed.snapshot) }
 LaunchedEffect(Unit) { while(true) { status=AnalysisFeed.snapshot; delay(100) } }
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Text("Mesa de teste da análise visual",style=MaterialTheme.typography.titleLarge)
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Button(onClick=onStart) { Text("Iniciar teste visual") }
   OutlinedButton(onClick=onStop) { Text("Parar teste") }
   Text("${status.statusText} • ${status.balls.size} bolas • ${status.lines.size} segmentos",modifier=Modifier.weight(1f),maxLines=1)
  }
  Slider(angle,{ angle=it },valueRange=-60f..60f,modifier=Modifier.semantics { contentDescription="Direção da mira de teste" })
  Canvas(Modifier.fillMaxWidth().weight(1f)) {
   val w=min(size.width*.9f,size.height*1.8f); val h=w/2
   val left=(size.width-w)/2; val top=(size.height-h)/2; val radius=h*.03f
   drawRect(Color(0xFF684830),Offset(left-10,top-10),androidx.compose.ui.geometry.Size(w+20,h+20))
   drawRect(Color(0xFF0F6351),Offset(left,top),androidx.compose.ui.geometry.Size(w,h))
   val cue=Offset(left+w*.25f,top+h*.5f)
   val a=angle*PI/180
   drawLine(Color.White,cue,Offset(cue.x+cos(a).toFloat()*w*.32f,cue.y+sin(a).toFloat()*w*.32f),4.dp.toPx())
   drawCircle(Color.White,radius,cue)
   drawCircle(Color(0xFFF5BE14),radius,Offset(left+w*.68f,top+h*.5f))
   drawCircle(Color(0xFFD2191E),radius,Offset(left+w*.8f,top+h*.75f))
  }
 }
}

package com.poolaim.vision

import java.util.concurrent.CopyOnWriteArraySet

data class ImagePoint(val x: Float,val y: Float)
data class TableRegion(val left: Float,val top: Float,val right: Float,val bottom: Float) {
 val width get()=right-left
 val height get()=bottom-top
 fun intersects(other: TableRegion)=left<other.right && right>other.left && top<other.bottom && bottom>other.top
 fun valid()=listOf(left,top,right,bottom).all { it.isFinite() && it in 0f..1f } && width>.1f && height>.08f
}
/** Radius is in fractions of the full image height, not width. */
data class ObservedBall(val center: ImagePoint,val radius: Float,val whiteFraction: Float=0f)
data class VisualLine(val from: ImagePoint,val to: ImagePoint,val target: Boolean,val reflection: Int)
data class PixelPreview(val width: Int,val height: Int,val argb: IntArray) { override fun toString()="PixelPreview(${width}x${height})" }
data class ManualScene(val region: TableRegion,val balls: List<ObservedBall>,val colors: List<Int>,
 val sourceAspect: Float,val rotation: Int)
data class VisualSettings(val manual: ManualScene?=null,val manualDirection: Boolean=false,val angleDegrees: Float=0f,
 val opacity: Float=.9f,val linesVisible: Boolean=true,val maxReflections: Int=2) {
 fun sanitized()=copy(angleDegrees=if(angleDegrees.isFinite()) ((angleDegrees+180)%360+360)%360-180 else 0f,
  opacity=if(opacity.isFinite()) opacity.coerceIn(.1f,1f) else .9f,maxReflections=maxReflections.coerceIn(0,2))
}
data class VisualSnapshot(val sessionId: Long=0,val receivedAtNanos: Long=0,val sourceWidth: Int=0,val sourceHeight: Int=0,
 val rotation: Int=0,val message: String="Análise parada",val confidence: Float=0f,val region: TableRegion?=null,
 val balls: List<ObservedBall> = emptyList(),val cueIndex: Int?=null,val directionDegrees: Float?=null,
 val lines: List<VisualLine> = emptyList(),val preview: PixelPreview?=null,val manual: Boolean=false,val aimEnd: ImagePoint?=null) {
 val statusText: String get()=if(lines.isNotEmpty() && !fresh()) "Sem quadro recente: linhas ocultas" else message
 fun fresh(now: Long=System.nanoTime())=receivedAtNanos>0 && now-receivedAtNanos in 0..700_000_000L
}

/** Shared immutable snapshots only. No frame lease or Android dependency crosses modules. */
object AnalysisFeed {
 private val listeners=CopyOnWriteArraySet<()->Unit>()
 @Volatile var settings=VisualSettings(); private set
 @Volatile var snapshot=VisualSnapshot(); private set
 @Volatile var enabled=false; private set
 @Volatile var paused=false; private set
 @Volatile var authorizedSession=0L; private set
 @Volatile var revision=0L; private set
 @Volatile var controlsOpen=false; private set
 @Volatile var buttonBlock: TableRegion?=null; private set
 @Synchronized fun setControlsOpen(value: Boolean) {
  if(controlsOpen==value) return
  controlsOpen=value; revision++; snapshot=snapshot.copy(lines=emptyList(),message=if(value) "Menu aberto: análise suspensa" else "Aguardando novo quadro"); notifyChanged()
 }
 @Synchronized fun setButtonBlock(value: TableRegion?) {
  if(buttonBlock==value) return
  buttonBlock=value; revision++; snapshot=snapshot.copy(lines=emptyList()); notifyChanged()
 }
 @Volatile var ownUiVisible=false
 @Volatile var trainingVisible=false
 @Volatile var calibrationFrame: VisualSnapshot?=null; private set
 var onSettingsChanged: ((VisualSettings)->Unit)?=null
 private fun notifyChanged() { listeners.forEach { it() } }
 @Synchronized fun configure(value: VisualSettings) {
  settings=value.sanitized(); revision++; snapshot=snapshot.copy(lines=emptyList(),message="Aguardando novo quadro após ajuste")
  onSettingsChanged?.invoke(settings); notifyChanged()
 }
 @Synchronized fun authorize(sessionId: Long) { authorizedSession=sessionId; revision++; calibrationFrame=null; snapshot=VisualSnapshot(message="Aguardando mesa") }
 @Synchronized fun start() { enabled=true; paused=false; revision++; snapshot=snapshot.copy(lines=emptyList(),message="Aguardando mesa confiável"); notifyChanged() }
 @Synchronized fun pause() { paused=true; revision++; snapshot=snapshot.copy(lines=emptyList(),message="Análise pausada"); notifyChanged() }
 @Synchronized fun stop() { enabled=false; paused=false; authorizedSession=0; revision++; calibrationFrame=null; snapshot=VisualSnapshot(message="Análise parada"); notifyChanged() }
 @Synchronized fun freezeForCalibration() { calibrationFrame=snapshot.takeIf { it.preview!=null }; pause() }
 @Synchronized fun publish(value: VisualSnapshot,expectedRevision: Long) {
  if(expectedRevision!=revision || !enabled || paused || value.sessionId!=authorizedSession) return
  snapshot=value; notifyChanged()
 }
 fun observe(listener: ()->Unit): ()->Unit { listeners.add(listener); return { listeners.remove(listener) } }
}

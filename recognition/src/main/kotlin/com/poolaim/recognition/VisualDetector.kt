package com.poolaim.recognition

import com.poolaim.vision.*
import kotlin.math.*

/** Conservative color/shape heuristics for an unobstructed, rectangular top-down table.
 * Confidence is a heuristic quality score, not a calibrated probability or game-specific model.
 */
class VisualDetector {
 data class Detection(val region: TableRegion?,val balls: List<ObservedBall>,val cue: Int?,val angle: Float?,
  val confidence: Float,val message: String,val preview: PixelPreview,val aimEnd: ImagePoint?=null)
 private class Pixels(frame: VideoFrame) {
  val step=max(1,ceil(frame.metadata.width/400.0).toInt())
  val w=(frame.metadata.width+step-1)/step
  val h=(frame.metadata.height+step-1)/step
  val rgb=IntArray(w*h)
  init {
   for(y in 0 until h) {
    if(Thread.currentThread().isInterrupted) throw InterruptedException()
    for(x in 0 until w) {
     val offset=((y*step).coerceAtMost(frame.metadata.height-1)*frame.metadata.width+(x*step).coerceAtMost(frame.metadata.width-1))*4
     rgb[y*w+x]=(255 shl 24) or ((frame.rgba.get(offset).toInt() and 255) shl 16) or
      ((frame.rgba.get(offset+1).toInt() and 255) shl 8) or (frame.rgba.get(offset+2).toInt() and 255)
    }
   }
  }
  fun at(x: Int,y: Int)=rgb[y.coerceIn(0,h-1)*w+x.coerceIn(0,w-1)]
  fun preview()=PixelPreview(w,h,rgb)

 }
 private data class Component(val left: Int,val top: Int,val right: Int,val bottom: Int,val count: Int,val cx: Float,val cy: Float)
 private fun components(mask: BooleanArray,w: Int,h: Int): List<Component> {
  val seen=BooleanArray(mask.size); val queue=IntArray(mask.size); val result=ArrayList<Component>()
  for(seed in mask.indices) {
   if(seed%w==0 && Thread.currentThread().isInterrupted) throw InterruptedException()
   if(!mask[seed] || seen[seed]) continue
   var read=0; var end=1; queue[0]=seed; seen[seed]=true
   var l=w; var r=0; var t=h; var b=0; var sx=0L; var sy=0L
   while(read<end) {
    val p=queue[read++]; val x=p%w; val y=p/w
    l=min(l,x); r=max(r,x); t=min(t,y); b=max(b,y); sx+=x; sy+=y
    fun visit(n: Int) { if(!seen[n] && mask[n]) { seen[n]=true; queue[end++]=n } }
    if(x>0) visit(p-1); if(x<w-1) visit(p+1); if(y>0) visit(p-w); if(y<h-1) visit(p+w)
   }
   if(end>=3) result+=Component(l,t,r,b,end,sx.toFloat()/end,sy.toFloat()/end)
  }
  return result
 }
 private fun r(c: Int)=c shr 16 and 255
 private fun g(c: Int)=c shr 8 and 255
 private fun b(c: Int)=c and 255
 private fun white(c: Int)=minOf(r(c),g(c),b(c))>170 && maxOf(r(c),g(c),b(c))-minOf(r(c),g(c),b(c))<38
 private fun cloth(c: Int): Boolean {
  val red=r(c); val green=g(c); val blue=b(c); val hi=maxOf(red,green,blue); val lo=minOf(red,green,blue)
  if(hi<65 || hi>235 || hi-lo<30) return false
  // Green, teal or blue cloth only. Other themes require manual calibration.
  return red<max(green,blue)*.82f && (green>blue*.65f || blue>green*1.15f)
 }
 private fun distance(a: Int,c: Int)=maxOf(abs(r(a)-r(c)),abs(g(a)-g(c)),abs(b(a)-b(c)))
 fun detect(frame: VideoFrame,settings: VisualSettings,obstruction: TableRegion?=null): Detection {
  val p=Pixels(frame); val preview=p.preview()
  fun fail(message: String,region: TableRegion?=null,balls: List<ObservedBall> = emptyList(),cue: Int?=null)=Detection(region,balls,cue,null,0f,message,preview)
  val manual=settings.manual
  var confidence=.85f
  val region=if(manual!=null) {
   val aspect=frame.metadata.sourceWidth.toFloat()/frame.metadata.sourceHeight
   if(abs(aspect-manual.sourceAspect)>.015f || frame.metadata.displayRotationDegrees!=manual.rotation)
    return fail("Resolução/orientação alterada: recalibre a mesa")
   manual.region.takeIf { it.valid() } ?: return fail("Calibração inválida")
  } else {
   val mask=BooleanArray(p.rgb.size) { cloth(p.rgb[it]) }
   val candidates=components(mask,p.w,p.h).filter {
    val w=it.right-it.left+1; val h=it.bottom-it.top+1; val ratio=w.toFloat()/h
    ratio in 1.55f..2.4f && w*h>p.w*p.h*.08f && it.count.toFloat()/(w*h)>.68f
   }.sortedByDescending { it.count }
   val c=candidates.firstOrNull() ?: return fail("Mesa não detectada: use calibração manual")
   if(candidates.size>1 && candidates[1].count>c.count*.65f) return fail("Mais de uma mesa possível")
   confidence=(c.count.toFloat()/((c.right-c.left+1)*(c.bottom-c.top+1))).coerceAtMost(.95f)
   TableRegion(c.left.toFloat()/p.w,c.top.toFloat()/p.h,(c.right+1f)/p.w,(c.bottom+1f)/p.h)
  }
  if(obstruction?.intersects(region)==true) return fail("Arraste PA para fora da mesa: controles ocultam a imagem",region)
  val l=(region.left*p.w).roundToInt().coerceIn(0,p.w-1); val t=(region.top*p.h).roundToInt().coerceIn(0,p.h-1)
  val rr=(region.right*p.w).roundToInt().coerceIn(l+1,p.w); val bb=(region.bottom*p.h).roundToInt().coerceIn(t+1,p.h)
  val tw=rr-l; val th=bb-t
  if(tw<80 || th<40) return fail("Mesa pequena demais no quadro",region)
  val balls: List<ObservedBall>
  val cue: Int
  if(manual!=null && manual.balls.isNotEmpty()) {
   if(manual.balls.size !in 1..16 || manual.balls.size!=manual.colors.size) return fail("Calibração de bolas inválida",region)
   if(manual.balls.any { !it.radius.isFinite() || it.radius !in .001f.. .1f || it.center.x !in 0f..1f || it.center.y !in 0f..1f }) return fail("Coordenadas manuais inválidas",region)
   if(manual.balls.indices.any { i ->
     val ball=manual.balls[i]
     distance(ImageColors.averageBall(p.rgb,p.w,p.h,ball.center,ball.radius),manual.colors[i])>55
    }) return fail("Bolas mudaram: calibre novamente",region)
   balls=manual.balls; cue=0; confidence=.8f
  } else {
   // Median cloth color; white aim lines and isolated objects do not dominate these samples.
   val colors=ArrayList<Int>()
   for(y in t until bb step max(1,th/20)) for(x in l until rr step max(1,tw/40)) if(cloth(p.at(x,y))) colors+=p.at(x,y)
   if(colors.size<30) return fail("Cor do pano não confiável",region)
   fun median(channel: (Int)->Int)=colors.map(channel).sorted()[colors.size/2]
   val base=(255 shl 24) or (median(::r) shl 16) or (median(::g) shl 8) or median(::b)
   val foreign=BooleanArray(p.rgb.size)
   for(y in t until bb) for(x in l until rr) foreign[y*p.w+x]=distance(p.at(x,y),base)>48
   // Opening suppresses thin aim/overlay lines before finding circular components.
   val kernel=max(1,(th*.012f).roundToInt()); val core=BooleanArray(foreign.size)
   val neighbors=IntArray(8) { d ->
    val angle=d*PI/4
    (sin(angle)*kernel).roundToInt()*p.w+(cos(angle)*kernel).roundToInt()
   }
   for(y in t+kernel until bb-kernel) {
    if(Thread.currentThread().isInterrupted) throw InterruptedException()
    for(x in l+kernel until rr-kernel) {
     if(!foreign[y*p.w+x]) continue
     var votes=0
     val index=y*p.w+x
     for(offset in neighbors) if(foreign[index+offset]) votes++
     core[y*p.w+x]=votes>=7
    }
   }
   val found=ArrayList<ObservedBall>(); var unresolved=false
   for(c in components(core,p.w,p.h)) {
    val cw=c.right-c.left+1; val ch=c.bottom-c.top+1
    if(c.count<max(4f,th*th*.00018f).toInt()) continue
    // Ignore the cushion/pockets only at the edge; ambiguous interior blobs suppress prediction.
    if(c.left-l<kernel || c.top-t<kernel || rr-1-c.right<kernel || bb-1-c.bottom<kernel) continue
    if(cw>th*.14f || ch>th*.14f || cw.toFloat()/ch !in .55f..1.8f) { unresolved=true; continue }
    val radii=FloatArray(16)
    for(d in radii.indices) {
     val a=d*2*PI/radii.size; var rad=1f
     while(rad<th*.09f && foreign[(c.cy+sin(a)*rad).roundToInt().coerceIn(t,bb-1)*p.w+(c.cx+cos(a)*rad).roundToInt().coerceIn(l,rr-1)]) rad+=.5f
     radii[d]=rad
    }
    radii.sort(); val radius=radii[7]
    if(radius/th !in .014f.. .06f || radii[12]-radii[3]>radius*.65f) { unresolved=true; continue }
    var whites=0; var samples=0
    for(y in -radius.toInt()..radius.toInt()) for(x in -radius.toInt()..radius.toInt()) {
     if(x*x+y*y>radius*radius*.5f) continue
     samples++; if(white(p.at((c.cx+x).roundToInt(),(c.cy+y).roundToInt()))) whites++
    }
    found+=ObservedBall(ImagePoint(c.cx/p.w,c.cy/p.h),radius/p.h,whites.toFloat()/samples.coerceAtLeast(1))
   }
   balls=found
   if(unresolved) return fail("Objetos ambíguos na mesa: calibre manualmente",region,balls)
   if(balls.isEmpty() || balls.size>16) return fail("Bolas não detectadas com segurança",region,balls)
   val whites=balls.indices.filter { balls[it].whiteFraction>.78f }
   if(whites.size!=1 || balls.indices.any { it!=whites.firstOrNull() && balls[it].whiteFraction>.62f })
    return fail("Bola branca ambígua: selecione manualmente",region,balls)
   cue=whites.single()
  }
  val aim=if(settings.manualDirection) null else estimateAim(p,region,balls,cue)
  val angle=if(settings.manualDirection) settings.angleDegrees else aim?.first
  if(angle==null) return fail("Mira não confiável: use direção manual",region,balls,cue)
  return Detection(region,balls,cue,angle,confidence,if(manual!=null) "Geometria manual verificada por cor" else "Mesa e bolas detectadas",preview,aim?.second)
 }
 private fun estimateAim(p: Pixels,region: TableRegion,balls: List<ObservedBall>,cue: Int): Pair<Float,ImagePoint>? {
  val origin=balls[cue]; val ox=origin.center.x*p.w; val oy=origin.center.y*p.h; val radius=origin.radius*p.h
  val scores=FloatArray(360); val extents=FloatArray(360)
  for(degree in 0..359) {
   if(Thread.currentThread().isInterrupted) throw InterruptedException()
   val angle=degree*PI/180; val dx=cos(angle).toFloat(); val dy=sin(angle).toFloat()
   var distance=radius*1.8f; var far=0f; var count=0
   while(distance<max(p.w,p.h)) {
    val x=ox+dx*distance; val y=oy+dy*distance
    if(x<region.left*p.w || x>=region.right*p.w || y<region.top*p.h || y>=region.bottom*p.h) break
    val inBall=balls.any { val bx=it.center.x*p.w-x; val by=it.center.y*p.h-y; bx*bx+by*by<it.radius*it.radius*p.h*p.h*1.8f }
    if(!inBall && white(p.at(x.roundToInt(),y.roundToInt()))) { count++; far=distance }
    distance+=1f
   }
   if(far>radius*5) { scores[degree]=count.toFloat(); extents[degree]=far }
  }
  val best=scores.indices.maxByOrNull { scores[it] } ?: return null
  fun delta(a: Int,b: Int)=abs((a-b+540)%360-180)
  val competitor=scores.indices.filter { delta(it,best)>12 }.maxOf { scores[it] }
  if(scores[best]<max(8f,radius*2.2f) || scores[best]<competitor*1.45f) return null
  val neighbors=(-2..2).map { (best+it+360)%360 }
  val x=neighbors.sumOf { cos(it*PI/180)*scores[it] }; val y=neighbors.sumOf { sin(it*PI/180)*scores[it] }
  val angle=atan2(y,x)
  return Math.toDegrees(angle).toFloat() to ImagePoint((ox+cos(angle).toFloat()*extents[best])/p.w,(oy+sin(angle).toFloat()*extents[best])/p.h)
 }
}

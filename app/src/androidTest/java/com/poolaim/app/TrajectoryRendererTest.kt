package com.poolaim.app

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.poolaim.overlay.LiveTrajectoryRenderer
import com.poolaim.vision.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrajectoryRendererTest {
 @Test fun sourceCoordinatesPreserveBallAndNativeAimWithWindowOffset() {
  val bitmap=Bitmap.createBitmap(280,180,Bitmap.Config.ARGB_8888)
  try {
   val canvas=Canvas(bitmap); canvas.drawColor(Color.rgb(15,99,81))
   val white=Paint().apply { color=Color.WHITE; strokeWidth=3f }
   canvas.drawLine(20f,80f,110f,80f,white); canvas.drawCircle(20f,80f,8f,white)
   val snapshot=VisualSnapshot(receivedAtNanos=System.nanoTime(),sourceWidth=300,sourceHeight=200,
    balls=listOf(ObservedBall(ImagePoint(.1f,.5f),.04f)),cueIndex=0,aimEnd=ImagePoint(.4f,.5f),
    lines=listOf(VisualLine(ImagePoint(.1f,.5f),ImagePoint(.9f,.5f),false,0),VisualLine(ImagePoint(.9f,.5f),ImagePoint(.1f,.5f),true,1)))
   assertTrue(LiveTrajectoryRenderer.draw(canvas,snapshot,VisualSettings(opacity=1f),300,200,10,20,3f))
   assertEquals("Bola deve permanecer branca",Color.WHITE,bitmap.getPixel(20,80))
   assertEquals("Mira original não deve ser encoberta por reflexões",Color.WHITE,bitmap.getPixel(80,80))
   assertEquals("Linha deve usar coordenadas da tela menos o deslocamento da janela",Color.rgb(40,150,255),bitmap.getPixel(200,80))
   assertEquals(Color.rgb(15,99,81),bitmap.getPixel(200,60))
  } finally { bitmap.recycle() }
 }
 @Test fun staleHiddenAndWrongResolutionNeverDraw() {
  val bitmap=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888)
  try {
   val canvas=Canvas(bitmap); canvas.drawColor(Color.BLACK)
   val snapshot=VisualSnapshot(receivedAtNanos=System.nanoTime(),sourceWidth=300,sourceHeight=200,
    lines=listOf(VisualLine(ImagePoint(0f,.5f),ImagePoint(1f,.5f),false,0)))
   assertFalse(LiveTrajectoryRenderer.draw(canvas,snapshot.copy(receivedAtNanos=System.nanoTime()-800_000_000),VisualSettings(),300,200))
   assertFalse(LiveTrajectoryRenderer.draw(canvas,snapshot,VisualSettings(linesVisible=false),300,200))
   assertFalse(LiveTrajectoryRenderer.draw(canvas,snapshot,VisualSettings(),300,201))
   assertEquals(Color.BLACK,bitmap.getPixel(150,100))
  } finally { bitmap.recycle() }
 }
}

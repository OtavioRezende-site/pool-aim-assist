package com.poolaim.overlay
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
object OverlayPermission {
 fun granted(context: Context) = Settings.canDrawOverlays(context)
 fun settings(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+context.packageName))
 const val status = "Permita exibir sobre outros aplicativos para manter os controles flutuantes. O Android ou outro aplicativo pode ocultar a sobreposição. Não há captura nem análise automática."
}

package com.efrix.aurorago.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.efrix.aurorago.R

object SpritesheetHelper {
    /**
     * Carga los frames de un spritesheet.
     * Para 704x64px, con frames de 64x64px, obtendrá los 11 frames automáticamente.
     * @param targetSizePx Tamaño deseado para cada frame (ej: 128 para duplicar tamaño)
     */
    fun loadFrames(
        context: Context, 
        resourceId: Int, 
        frameWidth: Int = 64, 
        frameHeight: Int = 64,
        targetSizePx: Int = 100 // Tamaño un poco más grande que el original (64)
    ): List<Bitmap> {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: return emptyList()
        val frames = mutableListOf<Bitmap>()
        var x = 0
        while (x + frameWidth <= sheet.width) {
            val frame = Bitmap.createBitmap(sheet, x, 0, frameWidth, frameHeight)
            
            // Escalamos el frame al tamaño deseado
            // filter = true ayuda a mantener la calidad si no es un múltiplo exacto
            val scaledFrame = Bitmap.createScaledBitmap(frame, targetSizePx, targetSizePx, true)
            
            frames.add(scaledFrame)
            x += frameWidth
        }
        return frames
    }

    /**
     * Mapea el tipo de miedo al recurso drawable del spritesheet correspondiente.
     */
    fun getSheetResource(tipo: String): Int = when (tipo) {
        "sombra_social" -> R.drawable.spritesheet_social
        "nube_tormenta" -> R.drawable.spritesheet_ira
        "niebla_gris" -> R.drawable.spritesheet_tristeza
        "reloj_tembloroso" -> R.drawable.spritesheet_fracaso
        else -> R.drawable.ic_miedo_default
    }
}

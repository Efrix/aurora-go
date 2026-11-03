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
    ): List<Bitmap> {
        var x = 0
        while (x + frameWidth <= sheet.width) {
            val frame = Bitmap.createBitmap(sheet, x, 0, frameWidth, frameHeight)

            // Escalamos el frame al tamaño deseado
            // filter = true ayuda a mantener la calidad si no es un múltiplo exacto
            val scaledFrame = Bitmap.createScaledBitmap(frame, targetSizePx, targetSizePx, true)

            if (scaledFrame !== frame) {
                frame.recycle()
            }

            frames.add(scaledFrame)
            x += frameWidth
        }
        sheet.recycle()
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

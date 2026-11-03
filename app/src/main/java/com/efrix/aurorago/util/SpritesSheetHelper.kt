package com.efrix.aurorago.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import com.efrix.aurorago.R
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.util.concurrent.ConcurrentHashMap

object SpritesheetHelper {

    class Sprites(val androidFrames: List<org.mapsforge.core.graphics.Bitmap>) {
        val frameCount: Int get() = androidFrames.size
        val isEmpty: Boolean get() = androidFrames.isEmpty()
    }

    private val cache = ConcurrentHashMap<String, Sprites>()

    /**
     * Obtiene (o carga y almacena en caché) los sprites de un tipo de miedo.
     */
    fun getSprites(context: Context, tipo: String, targetSizeDp: Int = 48): Sprites {
        return cache.getOrPut(tipo) {
            val resId = getSheetResource(tipo)
            val frames = loadFrames(context, resId, targetSizeDp)
            Sprites(frames)
        }
    }

    /**
     * Carga los frames de un spritesheet como bitmaps de Mapsforge.
     * @param targetSizeDp Tamaño deseado para cada frame en DP.
     */
    fun loadFrames(
        context: Context,
        resourceId: Int,
        targetSizeDp: Int = 48
    ): List<org.mapsforge.core.graphics.Bitmap> {
        val drawable = ContextCompat.getDrawable(context, resourceId) ?: return emptyList()

        if (drawable is BitmapDrawable) {
            val sheet = drawable.bitmap
            if (sheet == null || sheet.isRecycled || sheet.height <= 0 || sheet.width <= 0) {
                return emptyList()
            }

            val frameHeight = sheet.height
            val frameWidth = sheet.height
            val targetSizePx = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
            val frames = mutableListOf<org.mapsforge.core.graphics.Bitmap>()

            var x = 0
            while (x + frameWidth <= sheet.width) {
                val frame = android.graphics.Bitmap.createBitmap(sheet, x, 0, frameWidth, frameHeight)
                val scaledFrame = android.graphics.Bitmap.createScaledBitmap(frame, targetSizePx, targetSizePx, true)
                if (scaledFrame !== frame) {
                    frame.recycle()
                }
                val mapsforgeBitmap = AndroidGraphicFactory.convertToBitmap(
                    BitmapDrawable(context.resources, scaledFrame)
                )
                if (mapsforgeBitmap != null) {
                    frames.add(mapsforgeBitmap)
                }
                x += frameWidth
            }
            return frames
        } else {
            val mapsforgeBitmap = AndroidGraphicFactory.convertToBitmap(drawable)
            return if (mapsforgeBitmap != null) listOf(mapsforgeBitmap) else emptyList()
        }
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

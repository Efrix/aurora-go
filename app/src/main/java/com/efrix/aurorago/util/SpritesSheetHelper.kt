package com.efrix.aurorago.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.efrix.aurorago.R
import kotlinx.coroutines.delay
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.util.concurrent.ConcurrentHashMap

object SpritesheetHelper {

    class Sprites(val androidFrames: List<org.mapsforge.core.graphics.Bitmap>) {
        val frameCount: Int get() = androidFrames.size
        val isEmpty: Boolean get() = androidFrames.isEmpty()
    }

    private val cache = ConcurrentHashMap<String, Sprites>()
    private val androidCache = ConcurrentHashMap<String, List<android.graphics.Bitmap>>()

    /**
     * Obtiene (o carga y almacena en caché) los sprites de un tipo de miedo para Mapsforge.
     */
    fun getSprites(context: Context, tipo: String, targetSizeDp: Int = 48): Sprites {
        return cache.getOrPut(tipo) {
            val resId = getSheetResource(tipo)
            val frames = loadFrames(context, resId, targetSizeDp)
            Sprites(frames)
        }
    }

    /**
     * Carga los frames de un spritesheet como Android Bitmaps para usar en Jetpack Compose.
     */
    fun loadAndroidBitmapFrames(
        context: Context,
        tipo: String,
        targetSizeDp: Int = 64
    ): List<android.graphics.Bitmap> {
        return androidCache.getOrPut(tipo) {
            val resourceId = getSheetResource(tipo)
            val drawable = ContextCompat.getDrawable(context, resourceId) ?: return@getOrPut emptyList()

            if (drawable is BitmapDrawable) {
                val sheet = drawable.bitmap
                if (sheet == null || sheet.isRecycled || sheet.height <= 0 || sheet.width <= 0) {
                    return@getOrPut emptyList()
                }

                val frameHeight = sheet.height
                val frameWidth = sheet.height
                val targetSizePx = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val frames = mutableListOf<android.graphics.Bitmap>()

                var x = 0
                while (x + frameWidth <= sheet.width) {
                    val frame = android.graphics.Bitmap.createBitmap(sheet, x, 0, frameWidth, frameHeight)
                    val scaledFrame = android.graphics.Bitmap.createScaledBitmap(frame, targetSizePx, targetSizePx, true)
                    if (scaledFrame !== frame) {
                        frame.recycle()
                    }
                    frames.add(scaledFrame)
                    x += frameWidth
                }
                frames
            } else {
                val targetSizePx = (targetSizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                val bitmap = android.graphics.Bitmap.createBitmap(targetSizePx, targetSizePx, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                listOf(bitmap)
            }
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

/**
 * Componente Composable para mostrar un sprite animado frame a frame.
 */
@Composable
fun AnimatedSprite(
    tipo: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    frameDurationMs: Long = 100L
) {
    val context = LocalContext.current
    val frames = remember(tipo) {
        SpritesheetHelper.loadAndroidBitmapFrames(context, tipo)
    }
    var currentFrameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(frames) {
        if (frames.size > 1) {
            while (true) {
                delay(frameDurationMs)
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    if (frames.isNotEmpty()) {
        val currentBitmap = frames.getOrNull(currentFrameIndex) ?: frames.first()
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

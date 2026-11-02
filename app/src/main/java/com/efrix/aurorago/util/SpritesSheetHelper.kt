package com.efrix.aurorago.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.efrix.aurorago.R
import org.mapsforge.map.android.graphics.AndroidBitmap

/**
 * Carga y cachea los sprites de los monstruos.
 *
 * Optimizaciones:
 * - El spritesheet solo se decodifica y escala UNA vez por tipo.
 * - Los wrappers [AndroidBitmap] se crean una sola vez y se reutilizan en cada frame,
 *   evitando asignaciones en el bucle de animación.
 * - El mapa y la pantalla de check-in comparten la misma caché (mismos Bitmaps).
 */
object SpritesheetHelper {
    private const val FRAME_WIDTH = 64
    private const val FRAME_HEIGHT = 64

    /** Tamaño en dp de cada frame ya escalado. */
    private const val TARGET_DP = 48

    /** Tope de seguridad para no reservar bitmaps demasiado grandes en pantallas de alta densidad. */
    private const val MAX_TARGET_PX = 192

    data class Sprites(
        val frames: List<Bitmap>,
        val androidFrames: List<AndroidBitmap>
    ) {
        val frameCount: Int get() = frames.size
        val isEmpty: Boolean get() = frames.isEmpty()
    }

    private val cache = HashMap<Long, Sprites>()

    /**
     * Devuelve los sprites de un tipo de miedo, usando caché en memoria.
     */
    @Synchronized
    fun getSprites(
        context: Context,
        tipo: String,
        targetSizePx: Int = defaultTargetSize(context)
    ): Sprites {
        val resourceId = getSheetResource(tipo)
        val key = cacheKey(resourceId, targetSizePx)
        cache[key]?.let { return it }

        val frames = loadFrames(
            context = context,
            resourceId = resourceId,
            targetSizePx = targetSizePx
        )
        val sprites = Sprites(
            frames = frames,
            androidFrames = frames.map { AndroidBitmap(it) }
        )
        cache[key] = sprites
        return sprites
    }

    fun defaultTargetSize(context: Context): Int {
        val density = context.resources.displayMetrics.density
        return (TARGET_DP * density).toInt().coerceIn(32, MAX_TARGET_PX)
    }

    /**
     * Carga los frames de un spritesheet.
     * Para 704x64px, con frames de 64x64px, obtendrá los 11 frames automáticamente.
     *
     * @param targetSizePx Tamaño deseado para cada frame (ej: 128 para duplicar tamaño)
     */
    fun loadFrames(
        context: Context,
        resourceId: Int,
        frameWidth: Int = FRAME_WIDTH,
        frameHeight: Int = FRAME_HEIGHT,
        targetSizePx: Int = 100
    ): List<Bitmap> {
        val sheet = decodeBitmap(context, resourceId) ?: return emptyList()

        // El icono de fallback es una imagen suelta, no una hoja de frames.
        if (resourceId == R.drawable.ic_miedo_default) {
            return listOf(scaleWholeBitmap(sheet, targetSizePx))
        }

        val frames = ArrayList<Bitmap>()
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

    private fun decodeBitmap(context: Context, resourceId: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, resourceId, options)?.let { return it }

        // Algunos drawables (p. ej. vectores) no se pueden decodificar con BitmapFactory.
        val drawable = ContextCompat.getDrawable(context, resourceId) ?: return null
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 512
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 512
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
    }

    private fun scaleWholeBitmap(source: Bitmap, targetSizePx: Int): Bitmap {
        val largestSide = maxOf(source.width, source.height)
        if (largestSide <= 0) return source
        val scale = targetSizePx.toFloat() / largestSide
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
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

    /**
     * Libera toda la caché de sprites. Debe llamarse al cerrar sesión.
     */
    @Synchronized
    fun clearCache() {
        cache.values.forEach { sprites ->
            sprites.frames.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        cache.clear()
    }

    private fun cacheKey(resourceId: Int, targetSizePx: Int): Long =
        (resourceId.toLong() shl 32) or (targetSizePx.toLong() and 0xFFFFFFFFL)
}

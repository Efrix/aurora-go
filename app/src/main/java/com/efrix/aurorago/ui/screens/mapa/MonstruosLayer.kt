package com.efrix.aurorago.ui.screens.mapa

import android.content.Context
import com.efrix.aurorago.data.model.MiedoEnMapa
import com.efrix.aurorago.util.SpritesheetHelper
import org.mapsforge.core.graphics.Bitmap
import org.mapsforge.core.graphics.Canvas
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.core.model.Point
import org.mapsforge.core.model.Rectangle
import org.mapsforge.core.model.Rotation
import org.mapsforge.core.util.MercatorProjection
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.Layer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Capa única que dibuja todos los monstruos del mapa en una sola pasada.
 *
 * ¿Por qué una capa y no un [org.mapsforge.map.layer.overlay.Marker] por monstruo?
 * - [org.mapsforge.map.layer.overlay.Marker.setBitmap] decrementa el refCount del bitmap anterior,
 *   por lo que compartir los frames cacheados entre marcadores los destruiría.
 * - Una sola capa evita crear/eliminar decenas de capas en cada refresco y evita
 *   asignar un bitmap nuevo por marcador y por frame.
 *
 * La animación es un simple índice por tipo: todos los monstruos del mismo tipo
 * comparten frame y se dibujan con el [android.graphics.Bitmap] ya decodificado.
 */
class MonstruosLayer(
    private val context: Context,
    private val mapView: MapView
) : Layer() {

    private class Monstruo(
        val id: String,
        val tipo: String,
        val latLong: LatLong
    )

    private class Animacion(
        val sprites: SpritesheetHelper.Sprites,
        var frameIndex: Int = 0
    ) {
        fun currentFrame(): Bitmap? =
            sprites.androidFrames.getOrNull(frameIndex % sprites.frameCount)
    }

    private val monstruos = CopyOnWriteArrayList<Monstruo>()
    private val animaciones = HashMap<String, Animacion>()

    /** Callback invocado al tocar un monstruo. Se asigna desde la pantalla del mapa. */
    @Volatile
    var onMonstruoTap: ((miedoId: String, tipo: String) -> Unit)? = null

    /**
     * Sincroniza la lista de monstruos visibles.
     * Solo se llama cuando cambia la lista del ViewModel, no en cada frame.
     */
    @Synchronized
    fun sync(miedos: List<MiedoEnMapa>) {
        // Precarga (o reutiliza desde caché) los sprites de cada tipo presente.
        miedos.asSequence()
            .map { it.tipo }
            .distinct()
            .forEach { tipo ->
                if (animaciones[tipo] == null) {
                    val sprites = SpritesheetHelper.getSprites(context, tipo)
                    if (!sprites.isEmpty) {
                        // Desfase inicial para que no todos los tipos animen al unísono.
                        val startFrame = (animaciones.size * 3) % sprites.frameCount
                        animaciones[tipo] = Animacion(sprites, startFrame)
                    }
                }
            }

        val nuevos = miedos.map { miedo ->
            Monstruo(
                id = miedo.id,
                tipo = miedo.tipo,
                latLong = LatLong(miedo.latitud, miedo.longitud)
            )
        }
        monstruos.clear()
        monstruos.addAll(nuevos)

        requestRedraw()
    }

    /**
     * Avanza un frame todas las animaciones y solicita el redibujado de la capa.
     * Se ejecuta desde el reloj de animación de la pantalla (10 FPS).
     */
    fun advanceFrame() {
        val hayAnimaciones = synchronized(this) {
            if (animaciones.isEmpty()) {
                return@synchronized false
            }
            animaciones.values.forEach { animacion ->
                animacion.frameIndex = (animacion.frameIndex + 1) % animacion.sprites.frameCount
            }
            true
        }
        if (hayAnimaciones) {
            requestRedraw()
        }
    }

    override fun draw(
        boundingBox: BoundingBox,
        zoomLevel: Byte,
        canvas: Canvas,
        topLeftPoint: Point,
        rotation: Rotation
    ) {
        val model = displayModel ?: return
        if (monstruos.isEmpty()) return

        val mapSize = MercatorProjection.getMapSize(zoomLevel, model.tileSize)
        val conRotacion = !Rotation.noRotation(rotation)
        val canvasWidth = canvas.width
        val canvasHeight = canvas.height

        synchronized(this) {
            monstruos.forEach { monstruo ->
                // Descarta lo que queda fuera de la vista antes de calcular píxeles.
                if (!boundingBox.contains(monstruo.latLong)) return@forEach

                val frame = animaciones[monstruo.tipo]?.currentFrame() ?: return@forEach
                if (frame.isDestroyed) return@forEach

                val pixelX = MercatorProjection.longitudeToPixelX(monstruo.latLong.longitude, mapSize)
                val pixelY = MercatorProjection.latitudeToPixelY(monstruo.latLong.latitude, mapSize)
                val left = (pixelX - topLeftPoint.x - frame.width / 2).toInt()
                val top = (pixelY - topLeftPoint.y - frame.height / 2).toInt()

                // Culling de pantalla: evita rotaciones y drawBitmap innecesarios.
                if (left + frame.width < 0 || top + frame.height < 0 ||
                    left > canvasWidth || top > canvasHeight
                ) {
                    return@forEach
                }

                val pivotX = (pixelX - topLeftPoint.x).toFloat()
                val pivotY = (pixelY - topLeftPoint.y).toFloat()
                if (conRotacion) {
                    canvas.rotate(Rotation(-rotation.degrees, pivotX, pivotY))
                }
                canvas.drawBitmap(frame, left, top)
                if (conRotacion) {
                    canvas.rotate(Rotation(rotation.degrees, pivotX, pivotY))
                }
            }
        }
    }

    override fun onTap(tapLatLong: LatLong, layerXY: Point?, tapXY: Point?): Boolean {
        val tap = tapXY ?: return false
        val callback = onMonstruoTap ?: return false
        val model = displayModel ?: return false
        if (monstruos.isEmpty()) return false

        val projection = mapView.mapViewProjection
        val position = mapView.model.mapViewPosition
        // Mismo cálculo de escala que usa Marker.contains().
        val scaleFactor = Math.pow(2.0, position.zoom) / Math.pow(2.0, position.zoomLevel.toDouble())
        val touchSlop = 20.0 * model.scaleFactor

        synchronized(this) {
            // Se recorre al revés para respetar el orden de dibujado (el último está encima).
            for (index in monstruos.indices.reversed()) {
                val monstruo = monstruos[index]
                val center = projection.toPixels(monstruo.latLong, true) ?: continue
                val frame = animaciones[monstruo.tipo]?.currentFrame() ?: continue

                val width = maxOf(touchSlop, frame.width * scaleFactor)
                val height = maxOf(touchSlop, frame.height * scaleFactor)
                val area = Rectangle(
                    center.x - width / 2,
                    center.y - height / 2,
                    center.x + width / 2,
                    center.y + height / 2
                )
                if (area.contains(tap)) {
                    callback(monstruo.id, monstruo.tipo)
                    return true
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        monstruos.clear()
        synchronized(this) {
            animaciones.clear()
        }
        onMonstruoTap = null
        // Los bitmaps pertenecen a SpritesheetHelper: no se reciclan aquí.
    }
}

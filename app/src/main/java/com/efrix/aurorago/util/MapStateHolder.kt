package com.efrix.aurorago.util

import android.content.Context
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import com.efrix.aurorago.ui.screens.mapa.SafeMapsforgeView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.core.graphics.Style
import com.efrix.aurorago.ui.screens.mapa.MiedoMarker
import com.efrix.aurorago.ui.screens.mapa.MarkerAnimData
import org.mapsforge.map.android.graphics.AndroidBitmap
import com.efrix.aurorago.data.model.Miedo

/**
 * Singleton que mantiene el estado del mapa para que persista durante la navegación.
 */
object MapStateHolder {
    private var mapView: SafeMapsforgeView? = null
    private var tileCache: TileCache? = null
    private var downloadLayer: TileDownloadLayer? = null
    private var userLocationCircle: Circle? = null
    
    // Almacén de animaciones persistente
    val animDataMap = mutableMapOf<String, MarkerAnimData>()

    fun getMapView(context: Context): SafeMapsforgeView {
        if (mapView == null) {
            mapView = SafeMapsforgeView(context.applicationContext).apply {
                setZoomLevel(18)
                isClickable = true
                isFocusable = true
                setZoomLevelMin(18)
                setZoomLevelMax(19)
                mapScaleBar.isVisible = false
            }
            initLayers(context.applicationContext)
        }
        return requireNotNull(mapView) { "MapView no pudo ser inicializado" }
    }

    private fun initLayers(context: Context) {
        val map = mapView ?: return
        
        // Configurar Caché
        if (tileCache == null) {
            tileCache = AndroidUtil.createTileCache(
                context, 
                "mapcache", 
                map.model.displayModel.tileSize, 
                1.0f, 
                1.2
            )
        }

        // Configurar Capa de Descarga
        if (downloadLayer == null) {
            val tileSource = OpenStreetMapMapnik.INSTANCE
            tileSource.setUserAgent("AuroraGo/1.0")
            downloadLayer = TileDownloadLayer(
                tileCache, 
                map.model.mapViewPosition, 
                tileSource, 
                AndroidGraphicFactory.INSTANCE
            )
            // NO llamar a onResume aquí, causa NPE si el mapa no está listo
        }

        // Configurar Círculo de Ubicación
        if (userLocationCircle == null) {
            val paintFill = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(180, 0, 120, 255) // Más opaco
                setStyle(Style.FILL)
            }
            val paintStroke = AndroidGraphicFactory.INSTANCE.createPaint().apply {
                color = AndroidGraphicFactory.INSTANCE.createColor(255, 255, 255, 255) // Borde blanco para contraste
                setStyle(Style.STROKE)
                setStrokeWidth(3f)
            }
            userLocationCircle = Circle(LatLong(0.0, 0.0), 12f, paintFill, paintStroke)
        }

        // Asegurar que las capas base estén presentes en el orden correcto
        val layers = map.layerManager.layers
        if (!layers.contains(downloadLayer)) {
            layers.add(0, downloadLayer) // Mapa al fondo
        }
        if (!layers.contains(userLocationCircle)) {
            layers.add(userLocationCircle) // Usuario encima del mapa
        }
    }

    /**
     * Carga los frames de animación si no están cargados.
     */
    fun loadAnimDataIfNeeded(context: Context, miedos: List<Miedo>) {
        val density = context.resources.displayMetrics.density
        val targetSize = (60 * density).toInt()
        var tipoCounter = 0

        miedos.forEach { miedo ->
            if (!animDataMap.containsKey(miedo.tipo)) {
                val frames = SpritesheetHelper.loadFrames(
                    context = context,
                    resourceId = SpritesheetHelper.getSheetResource(miedo.tipo),
                    targetSizePx = targetSize
                )
                if (frames.isNotEmpty()) {
                    val startFrame = (tipoCounter * 3) % frames.size
                    animDataMap[miedo.tipo] = MarkerAnimData(frames, currentFrame = startFrame)
                    tipoCounter++
                }
            }
        }
    }

    fun getDownloadLayer() = downloadLayer
    fun getUserLocationCircle() = userLocationCircle
    fun getTileCache() = tileCache

    /**
     * Libera recursos de forma definitiva (solo si es realmente necesario, 
     * por ejemplo al cerrar la sesión).
     */
    fun clear() {
        downloadLayer?.onDestroy()
        tileCache?.destroy()
        mapView?.destroyAll()
        
        downloadLayer = null
        tileCache = null
        mapView = null
        userLocationCircle = null
        animDataMap.clear()
    }
}

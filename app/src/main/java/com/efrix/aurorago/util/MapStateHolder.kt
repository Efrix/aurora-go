package com.efrix.aurorago.util

import android.content.Context
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.download.TileDownloadLayer
import org.mapsforge.map.layer.download.tilesource.OpenStreetMapMapnik
import com.efrix.aurorago.ui.screens.mapa.SafeMapsforgeView
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.core.graphics.Style

/**
 * Singleton que mantiene el estado del mapa para que persista durante la navegación.
 */
object MapStateHolder {
    private var mapView: SafeMapsforgeView? = null
    private var tileCache: TileCache? = null
    private var downloadLayer: TileDownloadLayer? = null
    private var userLocationCircle: Circle? = null

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
        val v = requireNotNull(mapView) { "MapView no pudo ser inicializado" }
        (v.parent as? android.view.ViewGroup)?.removeView(v)
        return v
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
        if (downloadLayer != null && !layers.contains(downloadLayer)) {
            layers.add(0, downloadLayer) // Mapa al fondo
        }
        if (userLocationCircle != null && !layers.contains(userLocationCircle)) {
            layers.add(userLocationCircle)
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
    }
}

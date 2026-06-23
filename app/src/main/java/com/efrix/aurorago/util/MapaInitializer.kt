package com.efrix.aurorago.util

import android.app.Application
import android.content.Context
import android.util.Log
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.File

/**
 * Utilidad para inicializar Mapsforge y preparar el entorno de mapas.
 */
object MapaInitializer {
    private const val TAG = "MapaInitializer"
    private const val MAP_DIR_NAME = "mapsforge"

    /**
     * Inicializa la factoría de gráficos de Mapsforge y asegura la existencia
     * del directorio de caché para mapas.
     */
    fun inicializar(context: Context) {
        try {
            // Inicializar GraphicFactory de Mapsforge (requerido antes de usar cualquier vista de mapa)
            AndroidGraphicFactory.createInstance(context.applicationContext as Application?)
            
            // Preparar el directorio para mapas offline en el directorio de archivos persistentes
            // Usamos filesDir en lugar de cacheDir para evitar que el sistema borre los mapas descargados
            val mapDir = File(context.filesDir, MAP_DIR_NAME)
            if (!mapDir.exists()) {
                val created = mapDir.mkdirs()
                if (created) {
                    Log.d(TAG, "Directorio de mapas creado en: ${mapDir.absolutePath}")
                }
            } else {
                Log.d(TAG, "Directorio de mapas ya existe")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Mapsforge", e)
        }
    }

    /**
     * Obtiene la ruta al directorio de mapas.
     */
    fun getMapDirectory(context: Context): File {
        return File(context.filesDir, MAP_DIR_NAME)
    }
}

/**
 * Función de extensión opcional para compatibilidad con código existente
 */
fun inicializarMapsforge(context: Context) {
    MapaInitializer.inicializar(context)
}

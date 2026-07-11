package com.efrix.aurorago.util

import android.app.Application
import android.content.Context
import android.util.Log
import com.efrix.aurorago.BuildConfig
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import java.io.File

object MapaInitializer {
    private const val TAG = "MapaInitializer"
    private const val MAP_DIR_NAME = "mapsforge"

    fun inicializar(context: Context) {
        try {
            AndroidGraphicFactory.createInstance(context.applicationContext as Application?)
            
            val mapDir = File(context.filesDir, MAP_DIR_NAME)
            if (!mapDir.exists()) {
                val created = mapDir.mkdirs()
                if (created && BuildConfig.DEBUG) {
                    Log.d(TAG, "Directorio de mapas creado")
                }
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "Directorio de mapas ya existe")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Error inicializando Mapsforge", e)
        }
    }

    fun getMapDirectory(context: Context): File {
        return File(context.filesDir, MAP_DIR_NAME)
    }
}

fun inicializarMapsforge(context: Context) {
    MapaInitializer.inicializar(context)
}

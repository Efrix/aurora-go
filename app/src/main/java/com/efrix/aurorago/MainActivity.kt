package com.efrix.aurorago

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.efrix.aurorago.ui.navegation.Rutas
import com.efrix.aurorago.ui.screens.checkin.CheckinScreen
import com.efrix.aurorago.ui.screens.importarperfil.ImportPerfilScreen
import com.efrix.aurorago.ui.screens.login.LoginScreen
import com.efrix.aurorago.ui.screens.mapa.MapaScreen
import com.efrix.aurorago.ui.screens.reto.RetoScreen
import com.efrix.aurorago.ui.theme.AuroragoTheme
import com.efrix.aurorago.util.LocationWorker
import com.efrix.aurorago.util.inicializarMapsforge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inicializarMapsforge(this) // Inicializar Mapsforge antes del mapa

        setContent {
            AuroragoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = Rutas.LOGIN
                    ) {
                        composable(Rutas.LOGIN) {
                            LoginScreen(
                                onLoginSuccess = {
                                    navController.navigate(Rutas.MAPA) {
                                        popUpTo(Rutas.LOGIN) { inclusive = true }
                                    }
                                },
                                onImportPerfil = {
                                    navController.navigate(Rutas.IMPORTAR_PERFIL)
                                }
                            )
                        }

                        // Pantalla de importación de perfil
                        composable(Rutas.IMPORTAR_PERFIL) {
                            ImportPerfilScreen(
                                onNavigateToMap = {
                                    navController.navigate(Rutas.MAPA) {
                                        popUpTo(Rutas.IMPORTAR_PERFIL) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Pantalla del mapa
                        composable(Rutas.MAPA) {
                            MapaScreen(
                                onMiedoClick = { tipoMiedo, miedoId ->
                                    navController.navigate("reto/$tipoMiedo/$miedoId")
                                },
                                onCheckinClick = {
                                    navController.navigate(Rutas.CHECKIN)
                                },
                                onLogout = {
                                    LocationWorker.cancelLocationUpdates(this@MainActivity)
                                    navController.navigate(Rutas.LOGIN) {
                                        popUpTo(Rutas.MAPA) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Pantalla de reto (enfrentar miedo)
                        composable(
                            route = Rutas.RETO,
                            arguments = listOf(
                                navArgument("tipoMiedo") { type = NavType.StringType },
                                navArgument("miedoId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val tipoMiedo = backStackEntry.arguments?.getString("tipoMiedo") ?: "desconocido"
                            val miedoId = backStackEntry.arguments?.getString("miedoId") ?: ""
                            RetoScreen(
                                navController = navController,
                                tipoMiedo = tipoMiedo,
                                miedoId = miedoId
                            )
                        }

                        composable(Rutas.CHECKIN) {
                            CheckinScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
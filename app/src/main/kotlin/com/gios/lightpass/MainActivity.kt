package com.gios.lightpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gios.lightpass.ui.*
import com.gios.lightpass.ui.theme.LightPassTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LightPassTheme {
                val nav = rememberNavController()
                val vm: PassViewModel = viewModel()

                // Which key a scanned QR should set when the payload has no prefix.
                var scanTarget by remember { mutableStateOf("anthropic") }

                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) { vm.addFromUri(uri); nav.popBackStack("home", false) } }

                val scanQr = rememberLauncherForActivityResult(ScanContract()) { result ->
                    val raw = result.contents?.trim() ?: return@rememberLauncherForActivityResult
                    when {
                        raw.startsWith("tmdb:", true) -> vm.setTmdbKey(raw.substringAfter(":").trim())
                        raw.startsWith("anthropic:", true) -> vm.setApiKey(raw.substringAfter(":").trim())
                        scanTarget == "tmdb" -> vm.setTmdbKey(raw)
                        else -> vm.setApiKey(raw)
                    }
                }
                fun launchScan(target: String) {
                    scanTarget = target
                    scanQr.launch(ScanOptions().setBeepEnabled(false).setPrompt(
                        if (target == "tmdb") "Scan TMDb key QR" else "Scan API key QR"))
                }

                // After a pass is added, auto-open the movie picker (only if TMDb is set up).
                val justAdded by vm.justAdded.collectAsStateWithLifecycle()
                LaunchedEffect(justAdded) {
                    val id = justAdded ?: return@LaunchedEffect
                    if (vm.hasTmdbKey()) nav.navigate("picker/$id")
                    vm.clearJustAdded()
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(vm,
                            onOpen = { id -> nav.navigate("viewer/$id") },
                            onAdd = { nav.navigate("add") },
                            onSettings = { nav.navigate("settings") })
                    }
                    composable("add") {
                        AddScreen(
                            onCamera = { nav.navigate("camera") },
                            onAlbum = { pickImage.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onBack = { nav.popBackStack() })
                    }
                    composable("camera") {
                        CameraScreen(
                            newFile = { vm.newCaptureFile() },
                            onCaptured = { file -> vm.addFromFile(file); nav.popBackStack("home", false) })
                    }
                    composable(
                        "viewer/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        val id = entry.arguments!!.getString("id")!!
                        DetailScreen(vm, id,
                            onPickMovie = { nav.navigate("picker/$id") },
                            onBack = { nav.popBackStack() })
                    }
                    composable(
                        "picker/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        MoviePickerScreen(vm, entry.arguments!!.getString("id")!!) { nav.popBackStack() }
                    }
                    composable("settings") {
                        SettingsScreen(vm,
                            onScanApiQr = { launchScan("anthropic") },
                            onScanTmdbQr = { launchScan("tmdb") },
                            onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

package com.gios.lightpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
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

                // Album photo picker — no permission required (Android Photo Picker).
                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) { vm.addFromUri(uri); nav.popBackStack("home", false) } }

                // QR scanner for the API key.
                val scanQr = rememberLauncherForActivityResult(ScanContract()) { result ->
                    result.contents?.let { vm.setApiKey(it); nav.popBackStack("home", false) }
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm = vm,
                            onOpen = { id -> nav.navigate("viewer/$id") },
                            onAdd = { nav.navigate("add") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("add") {
                        AddScreen(
                            onCamera = { nav.navigate("camera") },
                            onAlbum = { pickImage.launch(PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("camera") {
                        CameraScreen(
                            newFile = { vm.newCaptureFile() },
                            onCaptured = { file -> vm.addFromFile(file); nav.popBackStack("home", false) },
                        )
                    }
                    composable(
                        "viewer/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                    ) { entry ->
                        ViewerScreen(vm, entry.arguments!!.getString("id")!!) { nav.popBackStack() }
                    }
                    composable("settings") {
                        SettingsScreen(
                            vm = vm,
                            onScanQr = { scanQr.launch(ScanOptions().setBeepEnabled(false).setPrompt("Scan API key QR")) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

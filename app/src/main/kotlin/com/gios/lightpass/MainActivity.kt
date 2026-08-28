package com.gios.lightpass

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
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
import com.gios.lightpass.hw.LightKey
import com.gios.lightpass.hw.LightKeys
import com.gios.lightpass.hw.LocalWheelBus
import com.gios.lightpass.hw.WheelBus
import com.gios.lightpass.ui.*
import com.gios.lightpass.ui.theme.LightPassTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.MutableStateFlow
import com.gios.lightpass.report.CrashLog
import com.gios.lightpass.report.ReportOverlay

class MainActivity : ComponentActivity() {

    /**
     * A ticket asked for from outside the app, via `lightpass://pass/<id>` — LightNotebook
     * links here from the day a film screens. Held in a flow rather than read straight off
     * the intent so a second tap while the app is already open still lands somewhere.
     */
    private val pendingPass = MutableStateFlow<String?>(null)

    /** Wheel notches on their way to whichever screen is up. */
    private val wheel = WheelBus()

    /**
     * Every hardware key arrives here first — `DecorView` calls the window callback before
     * it walks the view hierarchy — which is what lets the wheel beat a focused text field.
     * Both halves of a notch are eaten, because letting the UP through means the ticket's
     * title field receives it as a keypress.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (LightKeys.of(event)) {
            LightKey.WheelUp -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(1)
                return true
            }

            LightKey.WheelDown -> {
                if (event.action == KeyEvent.ACTION_DOWN) wheel.send(-1)
                return true
            }

            else -> Unit
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPass.value = passIdIn(intent)
    }

    private fun passIdIn(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "lightpass" || data.host != "pass") return null
        return data.lastPathSegment?.takeIf { it.isNotBlank() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First thing, before anything else can throw: the handler chains onto whatever is
        // already installed and only writes a file, so it is safe this early.
        CrashLog.install(this)
        pendingPass.value = passIdIn(intent)
        setContent {
            LightPassTheme {
                val nav = rememberNavController()
                val vm: PassViewModel = viewModel()

                // Which key a scanned QR should set when the payload has no prefix.
                var scanTarget by remember { mutableStateOf("anthropic") }

                // Non-null while the add flow was entered through ADD TICKET on an event:
                // the id of the pass the new photo should attach to. The + button clears it.
                var attachTarget by remember { mutableStateOf<String?>(null) }
                fun doneAdding() {
                    // An attached ticket returns you to the event it joined; a fresh one, home.
                    if (attachTarget != null) nav.popBackStack("viewer/{id}", false)
                    else nav.popBackStack("home", false)
                }

                val pickImage = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) { vm.addFromUri(uri, attachTarget); doneAdding() } }

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

                val requestedPass by pendingPass.collectAsStateWithLifecycle()
                LaunchedEffect(requestedPass) {
                    val id = requestedPass ?: return@LaunchedEffect
                    nav.navigate("viewer/$id")
                    pendingPass.value = null
                }

                // After a pass is added, auto-open the movie picker (only if TMDb is set up).
                val justAdded by vm.justAdded.collectAsStateWithLifecycle()
                LaunchedEffect(justAdded) {
                    val id = justAdded ?: return@LaunchedEffect
                    if (vm.hasTmdbKey()) nav.navigate("picker/$id")
                    vm.clearJustAdded()
                }

                // Every screen below can reach the wheel, so a notch scrolls whatever is up
                // rather than whatever the activity happens to know about.
                CompositionLocalProvider(LocalWheelBus provides wheel) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(vm,
                                onOpen = { id -> nav.navigate("viewer/$id") },
                                onAdd = { attachTarget = null; nav.navigate("add") },
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
                            // One visit to the camera is one burst: startBurst on arrival,
                            // endBurst on the way out, and every shot between them stacks.
                            LaunchedEffect(Unit) { vm.startBurst() }
                            val pending by vm.pending.collectAsStateWithLifecycle()
                            val progress by vm.burstProgress.collectAsStateWithLifecycle()
                            val canShoot by vm.canShoot.collectAsStateWithLifecycle()
                            CameraScreen(
                                shotsPending = pending,
                                burstProgress = progress,
                                canShoot = canShoot,
                                onShot = { bmp -> vm.captureShot(bmp, attachTarget) },
                                onDone = { vm.endBurst(); doneAdding() })
                        }
                        composable(
                            "viewer/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments!!.getString("id")!!
                            DetailScreen(vm, id,
                                // The pager may be sitting on a sibling, so both callbacks
                                // carry the ticket actually on screen, not the route's id.
                                onPickMovie = { pid -> nav.navigate("picker/$pid") },
                                onAddTicket = { pid -> attachTarget = pid; nav.navigate("add") },
                                onMerge = { pid -> nav.navigate("merge/$pid") },
                                onBack = { nav.popBackStack() })
                        }
                        composable(
                            "merge/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            MergeScreen(vm, entry.arguments!!.getString("id")!!) { nav.popBackStack() }
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
                // Shake to report, the crash offer on next launch, and the app's own noticed
                // failures. A sibling, not a wrapper — the sheet is its own window, so it covers
                // the app whether or not it contains it.
                ReportOverlay()
            }
        }
    }
}

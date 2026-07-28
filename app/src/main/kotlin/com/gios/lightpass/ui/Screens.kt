package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.util.Grayscale
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PassViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val passes by vm.passes.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passes") },
                navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (passes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No passes yet.\n\nTap + to photograph a ticket.\nSet your API key in Settings to auto-title them.",
                        style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(passes, key = { it.id }) { PassRow(it) { onOpen(it.id) } }
                }
            }
        }
    }
}

@Composable
private fun PassRow(pass: PassEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = File(pass.imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pass.movieTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(pass.theater, pass.date, pass.time, pass.seat?.let { "Seat $it" }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(vm: PassViewModel, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val pass by vm.observePass(id).collectAsStateWithLifecycle(initialValue = null)
    // Lift grayscale while viewing (needs WRITE_SECURE_SETTINGS; silent no-op otherwise).
    DisposableEffect(Unit) {
        Grayscale.colorOn(context)
        onDispose { Grayscale.restore(context) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pass?.movieTitle ?: "Pass") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    pass?.let { p -> IconButton(onClick = { vm.delete(p); onBack() }) { Icon(Icons.Default.Delete, "Delete") } }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color.White), Alignment.Center) {
            pass?.let {
                AsyncImage(
                    model = File(it.imagePath),
                    contentDescription = "Pass",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PassViewModel, onScanQr: () -> Unit, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(vm.apiKey()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API key") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Anthropic API key", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-ant-...") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.setApiKey(draft); onBack() }) { Text("Save") }
                OutlinedButton(onClick = onScanQr) { Text("Scan QR") }
            }
            Text("Scan a QR from the LightPass web page, or paste the key. Stored only on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

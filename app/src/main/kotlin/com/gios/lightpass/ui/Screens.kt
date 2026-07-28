package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gios.lightpass.data.PassEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Black, titleContentColor = Color.White,
    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: PassViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, onSettings: () -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Passes") },
                navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color(0xFF303030))
            if (lists.active.isEmpty() && lists.archived.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No passes yet.\n\nTap + to photograph a ticket.\nSet your API key in Settings to auto-title them.",
                        style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (lists.active.isNotEmpty()) {
                        item { SectionHeader("UPCOMING") }
                        items(lists.active, key = { it.id }) { PassRow(it) { onOpen(it.id) } }
                    }
                    if (lists.archived.isNotEmpty()) {
                        item { SectionHeader("ARCHIVED") }
                        items(lists.archived, key = { it.id }) { PassRow(it, dim = true) { onOpen(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A),
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PassRow(pass: PassEntity, dim: Boolean = false, onClick: () -> Unit) {
    val fg = if (dim) Color(0xFF888888) else Color.White
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = pass.posterUrl ?: File(pass.croppedPath ?: pass.imagePath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp, 72.dp).background(Color(0xFF1A1A1A)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(pass.movieTitle, style = MaterialTheme.typography.bodyLarge, color = fg,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(pass.theater, pass.date, pass.time, pass.seat?.let { "Seat $it" }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9A9A9A), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PassViewModel, onScanQr: () -> Unit, onBack: () -> Unit) {
    var draft by remember { mutableStateOf(vm.apiKey()) }
    var tmdb by remember { mutableStateOf(vm.tmdbKey()) }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(colors = barColors(), title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Anthropic API key", style = MaterialTheme.typography.bodyLarge, color = Color.White)
            KeyField(draft, "sk-ant-...") { draft = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.setApiKey(draft) }) { Text("Save key") }
                OutlinedButton(onClick = onScanQr) { Text("Scan QR") }
            }
            Spacer(Modifier.height(8.dp))
            Text("TMDb API key (optional — posters, runtime, synopsis)",
                style = MaterialTheme.typography.bodyLarge, color = Color.White)
            KeyField(tmdb, "TMDb v3 api key") { tmdb = it }
            Button(onClick = { vm.setTmdbKey(tmdb) }) { Text("Save TMDb key") }
            Text("Keys are stored only on this phone.", style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9A9A9A))
        }
    }
}

@Composable
private fun KeyField(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        ),
    )
}

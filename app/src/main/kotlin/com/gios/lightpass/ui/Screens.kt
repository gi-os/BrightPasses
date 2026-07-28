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
    var tab by remember { mutableStateOf(0) } // 0 = Upcoming, 1 = Archive
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Movie Tickets") },
                navigationIcon = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
                actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.White, trackColor = Color(0xFF303030))
            LightTabs(
                selected = tab,
                labels = listOf("UPCOMING", "ARCHIVE"),
                counts = listOf(lists.active.size, lists.archived.size),
                onSelect = { tab = it },
            )
            val shown = if (tab == 0) lists.active else lists.archived
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        if (tab == 0) "No upcoming tickets.\n\nTap + to photograph a ticket."
                        else "No archived tickets yet.",
                        style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { PassRow(it, dim = tab == 1) { onOpen(it.id) } }
                }
            }
        }
    }
}

@Composable
private fun LightTabs(selected: Int, labels: List<String>, counts: List<Int>, onSelect: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Text(
                    text = "$label  ${counts[i]}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) Color.White else Color(0xFF5E5E5E),
                    modifier = Modifier
                        .clickable { onSelect(i) }
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                )
            }
        }
        HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
    }
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
            val sub = listOfNotNull(pass.theater, com.gios.lightpass.util.PassTimes.humanDate(pass.date), pass.time, pass.seat?.let { "Seat $it" }).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9A9A9A), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PassViewModel, onScanApiQr: () -> Unit, onScanTmdbQr: () -> Unit, onBack: () -> Unit) {
    val savedApi by vm.apiKeyState.collectAsStateWithLifecycle()
    val savedTmdb by vm.tmdbKeyState.collectAsStateWithLifecycle()
    var draft by remember(savedApi) { mutableStateOf(savedApi) }
    var tmdb by remember(savedTmdb) { mutableStateOf(savedTmdb) }
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
                OutlinedButton(onClick = onScanApiQr) { Text("Scan QR") }
            }
            Spacer(Modifier.height(8.dp))
            Text("TMDb API key (optional — posters, runtime, synopsis)",
                style = MaterialTheme.typography.bodyLarge, color = Color.White)
            KeyField(tmdb, "TMDb v3 api key") { tmdb = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.setTmdbKey(tmdb) }) { Text("Save TMDb key") }
                OutlinedButton(onClick = onScanTmdbQr) { Text("Scan QR") }
            }
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

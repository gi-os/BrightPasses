package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.util.Grayscale
import com.gios.lightpass.util.PassTimes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: PassViewModel, id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val pass by vm.observePass(id).collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    // Lift grayscale while viewing (needs the one-time adb grant; silent no-op otherwise)
    DisposableEffect(Unit) { Grayscale.colorOn(context); onDispose { Grayscale.restore(context) } }

    val p = pass
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                title = { Text(p?.movieTitle ?: "Pass", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showOriginal = true }) { Icon(Icons.Default.Image, "Original photo") }
                    if (p != null) {
                        IconButton(onClick = {
                            if (editing) editing = false else editing = true
                        }) { Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, "Edit") }
                        IconButton(onClick = { vm.delete(p); onBack() }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        if (p == null) { Box(Modifier.padding(pad)) {}; return@Scaffold }

        if (editing) {
            EditForm(Modifier.padding(pad), p) { updated -> vm.save(updated); editing = false }
        } else {
            DetailBody(Modifier.padding(pad), p)
        }
    }

    if (showOriginal && p != null) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                ZoomableImage(File(p.imagePath))
                IconButton(onClick = { showOriginal = false }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DetailBody(modifier: Modifier, p: PassEntity) {
    Column(
        modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Poster hero (falls back to the cropped ticket if no poster)
        Box(Modifier.fillMaxWidth().height(360.dp).background(Color.Black), Alignment.Center) {
            AsyncImage(
                model = p.posterUrl ?: File(p.croppedPath ?: p.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(p.movieTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
        p.year?.let { Text(it, color = Color(0xFFB0B0B0)) }
        Spacer(Modifier.height(20.dp))

        InfoRow("BEGINS", PassTimes.beginsLabel(p) ?: p.time ?: "—", "ENDS", PassTimes.endsLabel(p) ?: "—")
        Spacer(Modifier.height(12.dp))
        InfoRow("THEATER", p.theater ?: "—", "SEAT", p.seat ?: "—")
        Spacer(Modifier.height(12.dp))
        InfoRow("DATE", p.date ?: "—", "PRICE", p.price ?: "—")

        p.overview?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCFCFCF),
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun InfoRow(l1: String, v1: String, l2: String, v2: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        InfoCell(l1, v1, Modifier.weight(1f))
        InfoCell(l2, v2, Modifier.weight(1f))
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditForm(modifier: Modifier, p: PassEntity, onSave: (PassEntity) -> Unit) {
    var title by remember { mutableStateOf(p.movieTitle) }
    var theater by remember { mutableStateOf(p.theater ?: "") }
    var date by remember { mutableStateOf(p.date ?: "") }
    var time by remember { mutableStateOf(p.time ?: "") }
    var seat by remember { mutableStateOf(p.seat ?: "") }
    var price by remember { mutableStateOf(p.price ?: "") }

    Column(
        modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Field("Title", title) { title = it }
        Field("Theater", theater) { theater = it }
        Field("Date (YYYY-MM-DD)", date) { date = it }
        Field("Time (h:mm AM/PM)", time) { time = it }
        Field("Seat", seat) { seat = it }
        Field("Price", price) { price = it }
        Button(
            onClick = {
                onSave(p.copy(
                    movieTitle = title.ifBlank { "Untitled" },
                    theater = theater.ifBlank { null }, date = date.ifBlank { null },
                    time = time.ifBlank { null }, seat = seat.ifBlank { null },
                    price = price.ifBlank { null },
                ))
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFFB0B0B0), unfocusedLabelColor = Color(0xFF8A8A8A),
        ),
    )
}

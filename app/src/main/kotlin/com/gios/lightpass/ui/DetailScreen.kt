package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import com.gios.lightpass.hw.WheelScroll
import com.gios.lightpass.util.Grayscale
import com.gios.lightpass.util.PassTimes
import com.gios.lightpass.util.TextUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: PassViewModel, id: String, onPickMovie: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val pass by vm.observePass(id).collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }

    // Edit fields, lifted here so the top-bar SAVE commits them.
    var title by remember { mutableStateOf("") }
    var theater by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    fun seedFrom(p: PassEntity) {
        title = p.movieTitle; theater = p.theater ?: ""; date = p.date ?: ""
        time = p.time ?: ""; seat = p.seat ?: ""; price = p.price ?: ""
    }
    fun doSave() {
        pass?.let {
            vm.save(it.copy(
                movieTitle = title.ifBlank { "Untitled" },
                theater = TextUtils.titleCaseVenue(theater),
                date = date.ifBlank { null }, time = time.ifBlank { null },
                seat = seat.ifBlank { null }, price = price.ifBlank { null },
            ))
        }
        editing = false
    }

    DisposableEffect(Unit) { Grayscale.colorOn(context); onDispose { Grayscale.restore(context) } }

    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Black, titleContentColor = Color.White,
        navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
    )
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = barColors,
                title = { Text(pass?.movieTitle ?: "Ticket", maxLines = 1) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("BACK", color = Color.White,
                        style = MaterialTheme.typography.labelLarge) }
                },
                actions = {
                    IconButton(onClick = { showTicket = true }) { Icon(Icons.Default.Image, "Ticket photo") }
                    pass?.let { p ->
                        TextButton(onClick = { if (editing) doSave() else { seedFrom(p); editing = true } }) {
                            Text(if (editing) "SAVE" else "EDIT", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(onClick = { vm.delete(p); onBack() }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                },
            )
        },
    ) { pad ->
        val p = pass ?: run { Box(Modifier.padding(pad)) {}; return@Scaffold }
        // The ticket photo is an overlay, not a separate window, so the page under it is
        // still composed and still listening. Without the gate one notch moves both.
        if (editing) {
            EditFields(Modifier.padding(pad),
                title, { title = it }, theater, { theater = it }, date, { date = it },
                time, { time = it }, seat, { seat = it }, price, { price = it }, ::doSave,
                wheelActive = !showTicket)
        } else {
            DetailBody(Modifier.padding(pad), p, onPickMovie, wheelActive = !showTicket)
        }
    }

    if (showTicket) {
        pass?.let { p ->
            var original by remember { mutableStateOf(false) }
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    ZoomableImage(File(if (original) p.imagePath else (p.croppedPath ?: p.imagePath)))
                    TextButton(onClick = { showTicket = false },
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        Text("CLOSE", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    if (p.croppedPath != null) {
                        TextButton(onClick = { original = !original },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                            Text(if (original) "TICKET" else "ORIGINAL", color = Color.White,
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBody(
    modifier: Modifier,
    p: PassEntity,
    onPickMovie: () -> Unit,
    wheelActive: Boolean = true,
) {
    val scroll = rememberScrollState()
    WheelScroll(scroll, active = wheelActive)
    Column(
        modifier.fillMaxSize().background(Color.Black).verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(360.dp).background(Color.Black), Alignment.Center) {
            AsyncImage(
                model = p.posterUrl ?: File(p.croppedPath ?: p.imagePath),
                contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(p.movieTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
        p.year?.let { Text(it, color = Color(0xFFB0B0B0)) }
        TextButton(onClick = onPickMovie) {
            Text(if (p.posterUrl == null) "PICK MOVIE" else "CHANGE MOVIE",
                color = Color(0xFF7FB0FF), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(12.dp))
        InfoRow("BEGINS", PassTimes.beginsLabel(p) ?: p.time ?: "—", "ENDS", PassTimes.endsLabel(p) ?: "—")
        Spacer(Modifier.height(12.dp))
        InfoRow("THEATER", p.theater ?: "—", "SEAT", p.seat ?: "—")
        Spacer(Modifier.height(12.dp))
        InfoRow("DATE", PassTimes.humanDate(p.date) ?: "—", "PRICE", p.price ?: "—")
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
        InfoCell(l1, v1, Modifier.weight(1f)); InfoCell(l2, v2, Modifier.weight(1f))
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditFields(
    modifier: Modifier,
    title: String, onTitle: (String) -> Unit,
    theater: String, onTheater: (String) -> Unit,
    date: String, onDate: (String) -> Unit,
    time: String, onTime: (String) -> Unit,
    seat: String, onSeat: (String) -> Unit,
    price: String, onPrice: (String) -> Unit,
    onSave: () -> Unit,
    wheelActive: Boolean = true,
) {
    val scroll = rememberScrollState()
    WheelScroll(scroll, active = wheelActive)
    Column(
        modifier.fillMaxSize().background(Color.Black).verticalScroll(scroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditField("Title", title, onTitle)
        EditField("Theater", theater, onTheater)
        EditField("Date (YYYY-MM-DD)", date, onDate)
        EditField("Time (h:mm AM/PM)", time, onTime)
        EditField("Seat", seat, onSeat)
        EditField("Price", price, onPrice)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("SAVE") }
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedLabelColor = Color(0xFFB0B0B0), unfocusedLabelColor = Color(0xFF8A8A8A),
        ),
    )
}

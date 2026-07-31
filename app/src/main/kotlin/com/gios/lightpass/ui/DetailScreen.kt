package com.gios.lightpass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.hw.WheelScroll
import com.gios.lightpass.util.BarcodeRender
import com.gios.lightpass.util.BookingCode
import com.gios.lightpass.util.Grayscale
import com.gios.lightpass.util.PassTimes
import com.gios.lightpass.util.Symbology
import com.gios.lightpass.util.TextUtils
import com.gios.lightpass.util.TicketDate
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: PassViewModel, id: String, onPickMovie: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val pass by vm.observePass(id).collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }

    // Edit fields, lifted here so the top-bar SAVE commits them.
    var title by remember { mutableStateOf("") }
    var theater by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var seat by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    fun seedFrom(p: PassEntity) {
        title = p.movieTitle; theater = p.theater ?: ""; date = p.date ?: ""
        time = p.time ?: ""; seat = p.seat ?: ""; price = p.price ?: ""
        code = p.code ?: ""
    }
    fun doSave() {
        pass?.let {
            vm.save(it.copy(
                movieTitle = title.ifBlank { "Untitled" },
                theater = TextUtils.titleCaseVenue(theater),
                // "12-18" typed in by hand gets a year the same way a parsed one does. A year
                // that was typed is left alone, including a long-past one — see TicketDate.
                date = TicketDate.resolveTyped(date), time = time.ifBlank { null },
                seat = seat.ifBlank { null }, price = price.ifBlank { null },
                code = code.ifBlank { null },
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
                    TextButton(onClick = { showTicket = true }) { Text("PHOTO", color = Color.White,
                        style = MaterialTheme.typography.labelSmall) }
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
        // Both overlays below are overlays, not separate windows, so the page under them is
        // still composed and still listening. Without the gate one notch moves both.
        val pageHasWheel = !showTicket && !showCode
        if (editing) {
            EditFields(Modifier.padding(pad),
                title, { title = it }, theater, { theater = it }, date, { date = it },
                time, { time = it }, seat, { seat = it }, price, { price = it },
                code, { code = it }, ::doSave,
                wheelActive = pageHasWheel)
        } else {
            DetailBody(Modifier.padding(pad), p, onPickMovie,
                onEnlargeCode = { showCode = true }, onShowPhoto = { showTicket = true },
                wheelActive = pageHasWheel)
        }
    }

    if (showCode) {
        pass?.let { p ->
            BookingCode.normalize(p.code)?.let { reference ->
                BookingCodeOverlay(
                    code = reference,
                    onShowPhoto = { showCode = false; showTicket = true },
                    onClose = { showCode = false },
                )
            }
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
    onEnlargeCode: () -> Unit,
    onShowPhoto: () -> Unit,
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
        // Bottom of the ticket, where every pass app puts the code and where a thumb already is.
        Spacer(Modifier.height(28.dp))
        BookingCodeSection(p.code, onEnlargeCode, onShowPhoto)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The generated code, at the foot of the ticket.
 *
 * It is generated, and the label says so, because a barcode is not information — it is whatever
 * string the cinema's own system encoded, and all we have is the reference a model read off a
 * photograph. Reproduce that string and a scanner that looks the reference up will very likely
 * take it; get one character wrong, or meet a cinema that encodes an internal id instead, and it
 * will not. So the photo of the actual barcode stays one tap away from here, and nothing on this
 * page promises anything.
 */
@Composable
private fun BookingCodeSection(rawCode: String?, onEnlarge: () -> Unit, onShowPhoto: () -> Unit) {
    val code = remember(rawCode) { BookingCode.normalize(rawCode) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BOOKING CODE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A))
        Spacer(Modifier.height(10.dp))
        if (code == null) {
            Text(
                "No code was read off this ticket. The barcode is on the photo.",
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8A8A8A),
                textAlign = TextAlign.Center,
            )
        } else {
            // Measured rather than fixed: the card has to fit inside the column's padding on a
            // 308dp-wide panel, and a hardcoded width that fits today clips on anything narrower.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                BarcodeCard(code, maxWidth - CARD_QUIET_ZONE * 2,
                    Modifier.clickable(onClick = onEnlarge))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Generated from the code above. Tap to enlarge and brighten. If the scanner won't " +
                    "take it, show the ticket photo.",
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8A8A8A),
                textAlign = TextAlign.Center,
            )
        }
        TextButton(onClick = onShowPhoto) {
            Text("TICKET PHOTO", color = Color(0xFF7FB0FF),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** White card: the code, then the reference beneath it in monospace, both on white. */
@Composable
private fun BarcodeCard(code: String, budget: Dp, modifier: Modifier = Modifier) {
    val generated = rememberGeneratedCode(code, budget)
    Surface(color = Color.White, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Column(
            Modifier.padding(CARD_QUIET_ZONE),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (generated == null) {
                Text("This code can't be drawn as a barcode", color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else {
                Image(
                    bitmap = generated.image,
                    contentDescription = "Booking code $code",
                    // Drawn at exactly the pixels it was generated at, with filtering off. Let
                    // the view resample it and the panel renders grey where a bar edge was.
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(generated.width, generated.height),
                )
                Spacer(Modifier.height(8.dp))
                Text(code, color = Color.Black, fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A code that has been drawn, and the size it actually came out at. See [rememberGeneratedCode]. */
private class GeneratedCode(val image: ImageBitmap, val width: Dp, val height: Dp)

/**
 * Generate the code, and report the size to draw it at rather than the size it was asked for.
 *
 * Those are not the same number, which is the trap here. ZXing scales a module by a whole number
 * and then does as it likes with the remainder: a PDF417 asked for 910x382 comes back 778x188, and
 * a QR asked for less room than its own matrix comes back larger than requested. Draw either at
 * the requested size and the view resamples the bars — undoing the entire reason for generating at
 * device pixels in the first place. So the request is a budget, and what gets drawn is whatever
 * came back, at its own pixels.
 */
@Composable
private fun rememberGeneratedCode(code: String, budget: Dp): GeneratedCode? {
    val symbology = remember(code) { BookingCode.symbologyFor(code) }
    val density = LocalDensity.current
    val widthPx = with(density) { budget.roundToPx() }
    val heightPx = with(density) { codeHeight(symbology, budget).roundToPx() }
    val image = remember(code, symbology, widthPx, heightPx) {
        BarcodeRender.bitmap(code, symbology, widthPx, heightPx)?.asImageBitmap()
    } ?: return null
    return with(density) { GeneratedCode(image, image.width.toDp(), image.height.toDp()) }
}

/**
 * A full screen of code, on white, at full brightness.
 *
 * Everything here exists for one moment: an usher's handheld pointed at a 3.9" greyscale panel
 * in a dark foyer. So the background is white rather than the app's black, the brightness is
 * pinned while this is up, and the way out to the real barcode on the photo is on screen rather
 * than back through the detail page.
 */
@Composable
private fun BookingCodeOverlay(code: String, onShowPhoto: () -> Unit, onClose: () -> Unit) {
    BrightWhileVisible()
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val generated = rememberGeneratedCode(code, maxWidth - OVERLAY_MARGIN * 2)
            if (generated != null) {
                ZoomableBitmap(generated.image, "Booking code $code",
                    generated.width, generated.height)
            }
            TextButton(onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Text("CLOSE", color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onShowPhoto,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Text("PHOTO", color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(code, color = Color.Black, fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp, letterSpacing = 1.5.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("GENERATED FROM THE BOOKING CODE", color = Color(0xFF6A6A6A),
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}

/** White around the code inside the card, on top of the quiet zone ZXing draws into the bitmap. */
private val CARD_QUIET_ZONE = 12.dp

private val OVERLAY_MARGIN = 12.dp

/**
 * How tall a code of this kind should be drawn.
 *
 * A QR and a PDF417 fix their own aspect from the data, so the height only has to be enough for
 * ZXing to scale the modules into; 1D bars have no natural height at all, and a Code 128 that is
 * too short is one a handheld's scan line keeps sliding off.
 */
private fun codeHeight(symbology: Symbology, width: Dp): Dp = when (symbology) {
    Symbology.QR -> width
    Symbology.CODE_128 -> width * 0.36f
    Symbology.PDF417 -> width * 0.42f
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
    code: String, onCode: (String) -> Unit,
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
        EditField("Date (YYYY-MM-DD, or MM-DD)", date, onDate)
        EditField("Time (h:mm AM/PM)", time, onTime)
        EditField("Seat", seat, onSeat)
        EditField("Price", price, onPrice)
        // Editable because the barcode is drawn from it: one wrong character and the code on the
        // ticket page is a picture of nothing, and this is the only way to fix that by hand.
        EditField("Booking code", code, onCode)
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

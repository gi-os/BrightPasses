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
import com.gios.lightpass.data.EventType
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.hw.WheelScroll
import com.gios.lightpass.nav.Directions
import androidx.compose.ui.text.style.TextOverflow
import com.google.zxing.BarcodeFormat
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
fun DetailScreen(
    vm: PassViewModel,
    id: String,
    onPickMovie: (String) -> Unit,
    onAddTicket: (String) -> Unit,
    onMerge: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Every ticket to this showing, not just the one that was tapped. The page shows one at
    // a time; the pager at the bottom steps through the rest.
    //
    // remember()ed because observeTickets() builds a new Flow each call: unremembered, every
    // recomposition (every keystroke in EDIT) tore the collection down and re-ran the queries
    // — the v1.11 lag.
    val ticketsFlow = remember(id) { vm.observeTickets(id) }
    val tickets by ticketsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedId by remember { mutableStateOf(id) }
    val pass = tickets.firstOrNull { it.id == selectedId } ?: tickets.firstOrNull()
    var editing by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(false) }
    var showType by remember { mutableStateOf(false) }

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
                        IconButton(onClick = {
                            // Deleting one ticket of several leaves you on the event, looking
                            // at a sibling; deleting the last one leaves the page too.
                            val remaining = tickets.filterNot { it.id == p.id }
                            vm.delete(p)
                            if (remaining.isEmpty()) onBack() else selectedId = remaining.first().id
                        }) { Icon(Icons.Default.Delete, "Delete") }
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
            DetailBody(Modifier.padding(pad), p,
                ticketIndex = tickets.indexOfFirst { it.id == p.id }.coerceAtLeast(0),
                ticketCount = tickets.size,
                onPickMovie = { onPickMovie(p.id) },
                onPickType = { showType = true },
                onAddTicket = { onAddTicket(p.id) },
                onMerge = { onMerge(p.id) },
                onUngroup = {
                    // Pulling the shown ticket out of the group: the page stays on it, and
                    // it is a lone pass again from the next emission.
                    vm.ungroup(p.id); selectedId = p.id
                },
                onSelectTicket = { ix -> tickets.getOrNull(ix)?.let { selectedId = it.id } },
                onEnlargeCode = { showCode = true }, onShowPhoto = { showTicket = true },
                wheelActive = pageHasWheel)
        }
    }

    if (showType) {
        pass?.let { p ->
            EventTypeDialog(
                current = p.eventType,
                onPick = { vm.setEventType(p.id, it); showType = false },
                onDismiss = { showType = false },
            )
        }
    }

    if (showCode) {
        pass?.let { p ->
            // The ticket's own code if it was read off the photograph, otherwise the reference
            // redrawn. Same overlay either way — what differs is whether it will scan.
            ticketCode(p)?.let { shown ->
                BookingCodeOverlay(
                    code = shown.content,
                    format = shown.format,
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
    ticketIndex: Int,
    ticketCount: Int,
    onPickMovie: () -> Unit,
    onPickType: () -> Unit,
    onAddTicket: () -> Unit,
    onMerge: () -> Unit,
    onUngroup: () -> Unit,
    onSelectTicket: (Int) -> Unit,
    onEnlargeCode: () -> Unit,
    onShowPhoto: () -> Unit,
    wheelActive: Boolean = true,
) {
    val isMovie = p.eventType == EventType.MOVIE
    val scroll = rememberScrollState()
    WheelScroll(scroll, active = wheelActive)
    Column(
        modifier.fillMaxSize().background(Color.Black).verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(360.dp).background(Color.Black), Alignment.Center) {
            AsyncImage(
                // Generated art (versus card / music note) first; a TMDb poster only for a
                // movie, so a stray match can't dress a game; the ticket photo as the floor.
                model = p.artPath?.let(::File)
                    ?: (if (isMovie) p.posterUrl else null)
                    ?: File(p.croppedPath ?: p.imagePath),
                contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(p.movieTitle, style = MaterialTheme.typography.titleLarge, color = Color.White)
        if (isMovie) p.year?.let { Text(it, color = Color(0xFFB0B0B0)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMovie) {
                TextButton(onClick = onPickMovie) {
                    Text(if (p.posterUrl == null) "PICK MOVIE" else "CHANGE MOVIE",
                        color = Color(0xFF7FB0FF), style = MaterialTheme.typography.labelSmall)
                }
            }
            // "Not a movie" lives here: tap the type to reclassify, on any pass, at any time.
            TextButton(onClick = onPickType) {
                Text(EventType.label(p.eventType).uppercase(),
                    color = Color(0xFF7FB0FF), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        InfoRow("BEGINS", PassTimes.beginsLabel(p) ?: p.time ?: "—", "ENDS", PassTimes.endsLabel(p) ?: "—")
        Spacer(Modifier.height(12.dp))
        InfoRow(EventType.venueLabel(p.eventType), p.theater ?: "—", "SEAT", p.seat ?: "—")
        DirectionsButton(p.theater)
        Spacer(Modifier.height(12.dp))
        InfoRow("DATE", PassTimes.humanDate(p.date) ?: "—", "PRICE", p.price ?: "—")
        if (isMovie) p.overview?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCFCFCF),
                modifier = Modifier.padding(horizontal = 20.dp))
        }
        // Bottom of the ticket, where every pass app puts the code and where a thumb already is.
        Spacer(Modifier.height(28.dp))
        if (ticketCount > 1) {
            TicketPager(ticketIndex, ticketCount, p.seat, onSelectTicket)
            Spacer(Modifier.height(8.dp))
        }
        BookingCodeSection(p, onEnlargeCode, onShowPhoto)
        TextButton(onClick = onAddTicket) {
            Text("ADD TICKET TO THIS EVENT", color = Color(0xFF7FB0FF),
                style = MaterialTheme.typography.labelSmall)
        }
        Row {
            // Retroactive grouping: fold another pass (added separately, maybe long ago)
            // into this event. Its whole group comes along.
            TextButton(onClick = onMerge) {
                Text("MERGE ANOTHER PASS", color = Color(0xFF7FB0FF),
                    style = MaterialTheme.typography.labelSmall)
            }
            if (ticketCount > 1) {
                // And the undo — for a merge that grabbed the wrong pass, or an auto-match
                // that was too clever.
                TextButton(onClick = onUngroup) {
                    Text("UNGROUP THIS TICKET", color = Color(0xFF8A8A8A),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * "TICKET 2 OF 3" with an arrow either side. Everyone at the door shows their own code, so
 * the whole page — seat, code, photo — follows the arrows, not just the barcode.
 */
@Composable
private fun TicketPager(index: Int, count: Int, seat: String?, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSelect((index - 1 + count) % count) }) {
            Text("PREV", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "TICKET ${index + 1} OF $count" + (seat?.let { " · SEAT $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0B0B0),
        )
        TextButton(onClick = { onSelect((index + 1) % count) }) {
            Text("NEXT", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Movie, sports or concert — the retroactive "not a movie" switch. Applies to the group. */
@Composable
private fun EventTypeDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        title = { Text("What is this ticket for?", color = Color.White) },
        text = {
            Column {
                EventType.ALL.forEach { type ->
                    Text(
                        EventType.label(type).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (type == current) Color.White else Color(0xFF8A8A8A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", color = Color.White) }
        },
    )
}

/**
 * What a code on this page is, and which of the three kinds you are looking at.
 *
 * The ticket's own code, read off the photograph, is the only one that reliably scans: it is the
 * cinema's payload byte for byte, in the symbology the cinema printed. The first version of this
 * feature had only the second kind — a reference a model read off the paper, re-encoded — which
 * works when the scanner looks that reference up and does nothing at all when it looks up an
 * internal id, which is most of the time. Both are shown, they are labelled differently, and the
 * photo of the real thing is one tap away from all three.
 */
private class TicketCode(
    val content: String,
    val format: BarcodeFormat,
    /** True when this came off the ticket rather than out of the parsed text. */
    val fromTicket: Boolean,
)

/** The best code available for this pass: the scanned one, else the reference, else none. */
private fun ticketCode(p: PassEntity): TicketCode? {
    val scanned = p.scannedCode?.trim()?.takeIf { it.isNotEmpty() }
    val scannedFormat = BarcodeRender.formatByName(p.scannedFormat)
    if (scanned != null && scannedFormat != null) {
        return TicketCode(scanned, scannedFormat, fromTicket = true)
    }
    val reference = BookingCode.normalize(p.code) ?: return null
    val format = when (BookingCode.symbologyFor(reference)) {
        Symbology.QR -> BarcodeFormat.QR_CODE
        Symbology.CODE_128 -> BarcodeFormat.CODE_128
        Symbology.PDF417 -> BarcodeFormat.PDF_417
    }
    return TicketCode(reference, format, fromTicket = false)
}

@Composable
private fun BookingCodeSection(p: PassEntity, onEnlarge: () -> Unit, onShowPhoto: () -> Unit) {
    val shown = remember(p.scannedCode, p.scannedFormat, p.code) { ticketCode(p) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (shown?.fromTicket == true) "TICKET CODE" else "BOOKING CODE",
            style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8A8A),
        )
        Spacer(Modifier.height(10.dp))
        if (shown == null) {
            Text(
                "No code was read off this ticket. The barcode is on the photo.",
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8A8A8A),
                textAlign = TextAlign.Center,
            )
        } else {
            // Measured rather than fixed: the card has to fit inside the column's padding on a
            // 308dp-wide panel, and a hardcoded width that fits today clips on anything narrower.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                BarcodeCard(shown, maxWidth - CARD_QUIET_ZONE * 2,
                    Modifier.clickable(onClick = onEnlarge))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (shown.fromTicket) {
                    "Read off your ticket, so this is the cinema's own code. Tap to enlarge and " +
                        "brighten."
                } else {
                    "Generated from the booking reference — it only scans if the cinema looks that " +
                        "reference up. If it won't take it, show the ticket photo."
                },
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
private fun BarcodeCard(shown: TicketCode, budget: Dp, modifier: Modifier = Modifier) {
    val generated = rememberGeneratedCode(shown, budget)
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
                    contentDescription = "Ticket code ${shown.content}",
                    // Drawn at exactly the pixels it was generated at, with filtering off. Let
                    // the view resample it and the panel renders grey where a bar edge was.
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(generated.width, generated.height),
                )
                Spacer(Modifier.height(8.dp))
                // The payload itself, which for a scanned 2D code can be long and is not meant to
                // be read — it is there so a human can check the machine agrees with the paper.
                Text(shown.content, color = Color.Black, fontFamily = FontFamily.Monospace,
                    fontSize = if (shown.content.length > 24) 10.sp else 14.sp,
                    letterSpacing = if (shown.content.length > 24) 0.sp else 1.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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
private fun rememberGeneratedCode(shown: TicketCode, budget: Dp): GeneratedCode? {
    val density = LocalDensity.current
    val widthPx = with(density) { budget.roundToPx() }
    val heightPx = with(density) { codeHeight(shown.format, budget).roundToPx() }
    val image = remember(shown.content, shown.format, widthPx, heightPx) {
        BarcodeRender.bitmap(shown.content, shown.format, widthPx, heightPx)?.asImageBitmap()
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
private fun BookingCodeOverlay(
    code: String,
    format: BarcodeFormat,
    onShowPhoto: () -> Unit,
    onClose: () -> Unit,
) {
    BrightWhileVisible()
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val shown = remember(code, format) { TicketCode(code, format, fromTicket = false) }
            val generated = rememberGeneratedCode(shown, maxWidth - OVERLAY_MARGIN * 2)
            if (generated != null) {
                ZoomableBitmap(generated.image, "Ticket code $code",
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
private fun codeHeight(format: BarcodeFormat, width: Dp): Dp = when {
    BarcodeRender.isTwoD(format) && format == BarcodeFormat.PDF_417 -> width * 0.42f
    BarcodeRender.isTwoD(format) -> width
    else -> width * 0.36f
}

/**
 * "DIRECTIONS TO THE VENUE", under the venue itself, which is where a thumb already is
 * when somebody has just worked out they are late. Nothing to type: the venue goes over
 * to BrightWay as text and lands on its search results — see [Directions] for why words
 * and not a route.
 *
 * Hidden rather than disabled when there is no venue on the pass or nothing on the phone
 * that takes a place: a button that cannot work is worse than no button.
 */
@Composable
private fun DirectionsButton(venue: String?) {
    val context = LocalContext.current
    if (venue.isNullOrBlank()) return
    val canOpen = remember { Directions.available(context) }
    if (!canOpen) return
    var failed by remember { mutableStateOf(false) }
    TextButton(onClick = { failed = !Directions.open(context, venue) }) {
        Text(
            if (failed) "NOTHING HERE SHOWS MAPS" else "DIRECTIONS TO THE VENUE",
            color = if (failed) Color(0xFF8A8A8A) else Color(0xFF7FB0FF),
            style = MaterialTheme.typography.labelSmall,
        )
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

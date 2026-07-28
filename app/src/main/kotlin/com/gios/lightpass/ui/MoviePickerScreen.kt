package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gios.lightpass.ai.MovieCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePickerScreen(vm: PassViewModel, passId: String, onDone: () -> Unit) {
    var title by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<MovieCandidate>?>(null) }

    LaunchedEffect(passId) {
        val p = vm.getPass(passId)
        title = p?.movieTitle
        results = p?.let { vm.searchMovies(it.movieTitle) } ?: emptyList()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
                title = { Text("Select movie") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = onDone) { Text("Skip", color = Color.White) } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            title?.let {
                Text("Matches for \u201C$it\u201D", color = Color(0xFF9A9A9A),
                    modifier = Modifier.padding(16.dp))
            }
            when {
                results == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                results!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No TMDb matches. Add a TMDb key in Settings, or Skip and edit details by hand.",
                        color = Color.White)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(results!!, key = { it.id }) { c ->
                        MovieRow(c) { vm.applyMovie(passId, c); onDone() }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieRow(c: MovieCandidate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = c.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp, 68.dp).background(Color(0xFF1A1A1A)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(buildString { append(c.title); c.year?.let { append("  ($it)") } },
                style = MaterialTheme.typography.bodyLarge, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            c.overview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8A8A8A),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gios.lightpass.ai.MovieCandidate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePickerScreen(vm: PassViewModel, passId: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf<String?>(null) } // null until pass loads
    var results by remember { mutableStateOf<List<MovieCandidate>?>(null) }

    fun runSearch(q: String) {
        results = null
        scope.launch { results = vm.searchMovies(q) }
    }

    LaunchedEffect(passId) {
        val p = vm.getPass(passId)
        val t = p?.movieTitle ?: ""
        query = t
        results = vm.searchMovies(t)
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
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("BACK", color = Color.White,
                        style = MaterialTheme.typography.labelLarge) }
                },
                actions = {
                    TextButton(onClick = onDone) { Text("SKIP", color = Color.White,
                        style = MaterialTheme.typography.labelLarge) }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            // Editable search title — correct Claude's guess and re-search.
            OutlinedTextField(
                value = query ?: "",
                onValueChange = { query = it },
                label = { Text("Search title") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { query?.let { runSearch(it) } }) {
                        Text("SEARCH", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { query?.let { runSearch(it) } }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFFB0B0B0), unfocusedLabelColor = Color(0xFF8A8A8A),
                ),
            )
            when {
                results == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                results!!.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No TMDb matches. Edit the title and search again, add a TMDb key in Settings, or Skip.",
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
            model = c.posterUrl, contentDescription = null, contentScale = ContentScale.Crop,
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

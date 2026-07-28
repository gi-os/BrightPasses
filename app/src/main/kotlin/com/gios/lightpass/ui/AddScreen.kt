package com.gios.lightpass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(onCamera: () -> Unit, onAlbum: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add pass") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Take a photo", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = onAlbum, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Choose from album", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

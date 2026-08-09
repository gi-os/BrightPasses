package com.gios.lightpass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gios.lightpass.hw.WheelScroll
import com.gios.lightpass.util.PassTimes

/**
 * Pick another pass to fold into this event — the retroactive merge, for tickets added
 * before grouping existed or parsed too differently to auto-match. The whole other group
 * comes along, and this event's kind and movie match win.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(vm: PassViewModel, id: String, onDone: () -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    // Everything except the event being merged into. Archive included on purpose: the
    // duplicates worth cleaning up are usually old.
    val candidates = remember(lists, id) {
        (lists.active + lists.archived).filterNot { g -> g.tickets.any { it.id == id } }
    }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
                title = { Text("Merge which ticket in?") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("CANCEL", color = Color.White,
                        style = MaterialTheme.typography.labelLarge) }
                },
            )
        },
    ) { pad ->
        if (candidates.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().background(Color.Black).padding(24.dp),
                Alignment.Center) {
                Text("Nothing else on the shelf to merge.",
                    style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        } else {
            val listState = rememberLazyListState()
            WheelScroll(listState)
            LazyColumn(state = listState,
                modifier = Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
                items(candidates, key = { it.primary.id }) { group ->
                    val p = group.primary
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { vm.mergeInto(id, p.id); onDone() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(p.movieTitle, style = MaterialTheme.typography.bodyLarge,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            PassTimes.humanDate(p.date), p.time,
                            if (group.count > 1) "${group.count} tickets" else null,
                        ).joinToString(" · ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9A9A9A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = Color(0xFF262626), thickness = 1.dp)
                }
            }
        }
    }
}

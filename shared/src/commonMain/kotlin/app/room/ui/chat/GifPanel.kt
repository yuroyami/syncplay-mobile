package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.klipy.KlipyAPI
import app.klipy.KlipyMedia
import app.klipy.KlipyMediaType
import coil3.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_gif_no_results
import syncplaymobile.shared.generated.resources.room_gif_powered_by
import syncplaymobile.shared.generated.resources.room_gif_tab_gifs
import syncplaymobile.shared.generated.resources.room_gif_tab_stickers

/**
 * GIF/Sticker search panel that overlays the chat message area.
 *
 * Shows a grid of GIF/Sticker results from the Klipy API. The search query
 * is driven by the chat input box text. When a GIF is tapped, its URL is
 * sent as a chat message.
 *
 * @param query The current text in the chat input field used as a search query
 * @param onGifSelected Called when a GIF/sticker is tapped, with the full URL
 * @param modifier Layout modifier
 */
@OptIn(FlowPreview::class)
@Composable
fun GifPanel(
    query: String,
    onGifSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(KlipyMediaType.GIF) }
    val results = remember { mutableStateListOf<KlipyMedia>() }
    var isLoading by remember { mutableStateOf(false) }

    /* Debounced search: reacts to query or tab changes */
    LaunchedEffect(selectedTab) {
        snapshotFlow { query }
            .debounce(400)
            .distinctUntilChanged()
            .collectLatest { q ->
                isLoading = true
                val response = KlipyAPI.search(query = q, type = selectedTab)
                results.clear()
                results.addAll(response)
                isLoading = false
            }
    }

    Column(
        modifier = modifier
            .background(
                color = Color(30, 30, 30, 220),
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        /* Tab Row: GIFs | Stickers */
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedTab == KlipyMediaType.GIF,
                onClick = { selectedTab = KlipyMediaType.GIF },
                label = { Text(stringResource(Res.string.room_gif_tab_gifs), fontSize = 10.sp) }
            )
            FilterChip(
                selected = selectedTab == KlipyMediaType.STICKER,
                onClick = { selectedTab = KlipyMediaType.STICKER },
                label = { Text(stringResource(Res.string.room_gif_tab_stickers), fontSize = 10.sp) }
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.room_gif_powered_by),
                fontSize = 8.sp,
                color = Color.Gray
            )
        }

        /* Results Grid */
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }

                results.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.room_gif_no_results),
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results, key = { it.id }) { media ->
                            AsyncImage(
                                model = media.previewUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onGifSelected(media.fullUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

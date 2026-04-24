package app.room.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.klipy.KlipyMedia
import app.klipy.KlipyMediaType
import app.klipy.KlipyUtils
import app.preferences.Preferences.KLIPY_FAVORITES
import app.preferences.set
import app.preferences.value
import app.uicomponents.AnimatedImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.powered_by_klipy
import syncplaymobile.shared.generated.resources.room_gif_action_favorite
import syncplaymobile.shared.generated.resources.room_gif_action_send
import syncplaymobile.shared.generated.resources.room_gif_action_unfavorite
import syncplaymobile.shared.generated.resources.room_gif_no_results
import syncplaymobile.shared.generated.resources.room_gif_tab_favorites
import syncplaymobile.shared.generated.resources.room_gif_tab_gifs
import syncplaymobile.shared.generated.resources.room_gif_tab_recents
import syncplaymobile.shared.generated.resources.room_gif_tab_stickers
import syncplaymobile.shared.generated.resources.room_gif_tab_trending

/** Which "source" the panel is fetching from. */
private enum class GifSource { SEARCH, TRENDING, RECENTS, FAVORITES }

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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GifPanel(
    query: String,
    onGifSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isHUDVisible: Boolean = true
) {
    var selectedType by remember { mutableStateOf(KlipyMediaType.GIF) }
    var selectedSource by remember { mutableStateOf(GifSource.TRENDING) }
    val results = remember { mutableStateListOf<KlipyMedia>() }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    /* Long-press context menu state */
    var contextMenuMedia by remember { mutableStateOf<KlipyMedia?>(null) }

    /* Favorites stored as JSON-serialized KlipyMedia in a Set<String> */
    var favoriteIds by remember { mutableStateOf(loadFavoriteIds()) }

    LaunchedEffect(query, selectedType, selectedSource) {
        /* Auto-switch to SEARCH when user types */
        if (query.isNotBlank() && selectedSource != GifSource.SEARCH) {
            selectedSource = GifSource.SEARCH
            return@LaunchedEffect
        }

        if (selectedSource == GifSource.FAVORITES) {
            isLoading = true
            results.clear()
            val favorites = loadFavorites().filter { it.type == selectedType }
            results.addAll(favorites)
            hasNextPage = false
            isLoading = false
            return@LaunchedEffect
        }

        /* Helper — fetches a single page for the current source */
        suspend fun fetchPage(page: Int) = when (selectedSource) {
            GifSource.SEARCH -> KlipyUtils.search(query = query, type = selectedType, page = page)
            GifSource.TRENDING -> KlipyUtils.trending(type = selectedType, page = page)
            GifSource.RECENTS -> KlipyUtils.recents(type = selectedType, page = page)
            GifSource.FAVORITES -> error("handled above")
        }

        /* Reset paging state and load first page */
        currentPage = 1
        hasNextPage = false
        isLoadingMore = false

        if (selectedSource == GifSource.SEARCH && query.isNotBlank()) delay(400)

        isLoading = true
        results.clear()
        val first = fetchPage(1)
        results.addAll(first.items)
        hasNextPage = first.hasNext
        isLoading = false

        /* Infinite-scroll: watch for scroll-near-end and load subsequent pages */
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 6 && info.totalItemsCount > 0 && hasNextPage && !isLoadingMore
        }
        .distinctUntilChanged()
        .filter { it }
        .collect {
            isLoadingMore = true
            val nextPage = currentPage + 1
            val response = fetchPage(nextPage)
            val existingIds = results.map { it.id }.toHashSet()
            results.addAll(response.items.filter { it.id !in existingIds })
            currentPage = nextPage
            hasNextPage = response.hasNext
            isLoadingMore = false
        }
    }

    Column(
        modifier = modifier
            .background(
                color = Color(30, 30, 30, 220),
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        /* Row 1: Media type chips (GIFs | Stickers) + Klipy logo */
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedType == KlipyMediaType.GIF,
                onClick = { selectedType = KlipyMediaType.GIF },
                label = { Text(stringResource(Res.string.room_gif_tab_gifs), fontSize = 10.sp) }
            )

            FilterChip(
                selected = selectedType == KlipyMediaType.STICKER,
                onClick = { selectedType = KlipyMediaType.STICKER },
                label = { Text(stringResource(Res.string.room_gif_tab_stickers), fontSize = 10.sp) }
            )

            Spacer(Modifier.weight(1f))

            Image(
                imageVector = vectorResource(Res.drawable.powered_by_klipy),
                contentDescription = "Powered by Klipy",
                modifier = Modifier.height(16.dp).aspectRatio(640 / 107f)
            )
        }

        /* Row 2: Source chips (Trending | Recents | Favorites) */
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedSource == GifSource.TRENDING,
                onClick = { selectedSource = GifSource.TRENDING },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                ),
                label = { Text(stringResource(Res.string.room_gif_tab_trending), fontSize = 10.sp) }
            )

            FilterChip(
                selected = selectedSource == GifSource.RECENTS,
                onClick = { selectedSource = GifSource.RECENTS },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                ),
                label = { Text(stringResource(Res.string.room_gif_tab_recents), fontSize = 10.sp) }
            )

            FilterChip(
                selected = selectedSource == GifSource.FAVORITES,
                onClick = { selectedSource = GifSource.FAVORITES },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiary
                ),
                label = { Text(stringResource(Res.string.room_gif_tab_favorites), fontSize = 10.sp) }
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
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 80.dp),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(results, key = { it.id }) { media ->
                            Box {
                                /* `.alpha()` is applied directly on AnimatedImage's modifier so the
                                 * iOS UIKitView honors it (Compose's parent-Box alpha does not
                                 * cascade into native views — see AnimatedImage.ios.kt). */
                                AnimatedImage(
                                    url = media.previewUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .alpha(if (isHUDVisible) 1f else 0f)
                                        .combinedClickable(
                                            onClick = {
                                                onGifSelected(media.fullUrl)
                                                scope.launch { KlipyUtils.trackShare(media.slug, media.type) }
                                            },
                                            onLongClick = {
                                                contextMenuMedia = media
                                            }
                                        )
                                )

                                /* Long-press context menu */
                                DropdownMenu(
                                    expanded = contextMenuMedia == media,
                                    onDismissRequest = { contextMenuMedia = null },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) },
                                        text = { Text(stringResource(Res.string.room_gif_action_send)) },
                                        onClick = {
                                            contextMenuMedia = null
                                            onGifSelected(media.fullUrl)
                                            scope.launch { KlipyUtils.trackShare(media.slug, media.type) }
                                        }
                                    )
                                    val isFav = media.id in favoriteIds
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                if (isFav) Icons.Filled.HeartBroken else Icons.Filled.Favorite,
                                                null
                                            )
                                        },
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (isFav) Res.string.room_gif_action_unfavorite
                                                    else Res.string.room_gif_action_favorite
                                                )
                                            )
                                        },
                                        onClick = {
                                            contextMenuMedia = null
                                            scope.launch {
                                                if (isFav) {
                                                    removeFavorite(media)
                                                    favoriteIds = loadFavoriteIds()
                                                    if (selectedSource == GifSource.FAVORITES) {
                                                        results.removeAll { it.id == media.id }
                                                    }
                                                } else {
                                                    addFavorite(media)
                                                    favoriteIds = loadFavoriteIds()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadFavorites(): List<KlipyMedia> {
    return KLIPY_FAVORITES.value().mapNotNull { json ->
        try {
            Json.decodeFromString<KlipyMedia>(json)
        } catch (_: Exception) {
            null
        }
    }
}

private fun loadFavoriteIds(): Set<Long> {
    return loadFavorites().map { it.id }.toHashSet()
}

private suspend fun addFavorite(media: KlipyMedia) {
    val current = KLIPY_FAVORITES.value().toMutableSet()
    current.add(Json.encodeToString(media))
    KLIPY_FAVORITES.set(current)
}

private suspend fun removeFavorite(media: KlipyMedia) {
    val current = KLIPY_FAVORITES.value()
    val updated = current.filter { json ->
        try {
            Json.decodeFromString<KlipyMedia>(json).id != media.id
        } catch (_: Exception) {
            true
        }
    }.toSet()
    KLIPY_FAVORITES.set(updated)
}

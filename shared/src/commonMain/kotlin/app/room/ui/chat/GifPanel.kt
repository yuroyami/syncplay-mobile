package app.room.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import app.i18n.strings
import app.uicomponents.controls.Icon
import app.uicomponents.controls.Text
import androidx.compose.runtime.Composable
import app.uicomponents.controls.shimmer
import app.uicomponents.controls.pressFeedback
import app.uicomponents.controls.controlStates
import app.uicomponents.controls.Segmented
import app.uicomponents.controls.Feedback
import app.uicomponents.controls.FontSizeRange
import app.theme.Motion
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import app.klipy.KlipyMedia
import app.klipy.KlipyMediaType
import app.klipy.KlipyUtils
import app.preferences.Preferences.KLIPY_FAVORITES
import app.preferences.set
import app.preferences.value
import app.theme.Radius
import app.theme.Space
import app.theme.Tier
import app.theme.Type
import app.theme.palette
import app.uicomponents.AnimatedImage
import app.uicomponents.controls.ListRow
import app.uicomponents.controls.ProgressBar
import app.uicomponents.controls.RowGap
import app.uicomponents.controls.RowLabel
import app.uicomponents.controls.Rule
import app.uicomponents.controls.SecondaryAction
import app.uicomponents.controls.SendGlyph
import app.uicomponents.frames.Modal
import app.uicomponents.frames.ModalSize
import app.uicomponents.surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.vectorResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.powered_by_klipy
import app.uicomponents.controls.touchTarget

/** Source shown while the composer is empty; typed text becomes a search over the chosen type. */
internal enum class GifSource { TRENDING, RECENTS, FAVORITES }

/**
 * The type switch: a 14 x 30dp track with a knob that sits up for GIFs and down for stickers,
 * the two words beside it. One tap flips it, so it needs no more width than the words.
 */
@Composable
private fun TypeSwitch(gifs: Boolean, modifier: Modifier = Modifier, onChange: (gifs: Boolean) -> Unit) {
    val p = palette
    val source = remember { MutableInteractionSource() }
    // The track grows with the text beside it, or two lines of large type clip against it.
    val trackHeight = 30.dp * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val knob = 10.dp
    val knobY by animateDpAsState(if (gifs) 2.dp else trackHeight - knob - 2.dp, Motion.move(), label = "knob")
    val gifLabel = strings.roomGifTabGifs
    val stickerLabel = strings.roomGifTabStickers

    Row(
        modifier = modifier
            .heightIn(min = Space.rowCompact)
            .clip(Radius.controlShape)
            .clickable(interactionSource = source, indication = null, role = Role.Switch) { Feedback.tick(); onChange(!gifs) }
            .touchTarget()
            .hoverable(source)
            .semantics { contentDescription = if (gifs) gifLabel else stickerLabel }
            .controlStates(source, Radius.controlShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .pressFeedback(source)
            .padding(horizontal = Space.gapTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp, trackHeight).border(Space.hair, p.rule, Radius.tightShape)) {
            Box(Modifier.offset(y = knobY).padding(start = 2.dp).size(knob).background(p.accent, Radius.tightShape))
        }
        RowGap(Space.gapTight)
        Column(Modifier.height(trackHeight), verticalArrangement = Arrangement.SpaceBetween) {
            Text(gifLabel, style = Type.group, color = if (gifs) p.accent else p.inkDim, maxLines = 1, autoSize = FontSizeRange(Type.group.fontSize))
            Text(stickerLabel, style = Type.group, color = if (gifs) p.inkDim else p.accent, maxLines = 1, autoSize = FontSizeRange(Type.group.fontSize))
        }
    }
}

/**
 * Keep all selectors in one row so narrow chat drawers leave more height for the results.
 * The switch takes at most 30 percent of the width; all labels shrink when space is tight.
 */
@Composable
internal fun GifDrawerHeader(
    type: KlipyMediaType,
    onType: (KlipyMediaType) -> Unit,
    source: GifSource,
    onSource: (GifSource) -> Unit,
) {
    val sources = GifSource.entries
    BoxWithConstraints(Modifier.fillMaxWidth().padding(Space.gapTight)) {
        val switchMaxWidth = maxWidth * 0.3f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.gapTight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeSwitch(
                gifs = type == KlipyMediaType.GIF,
                modifier = Modifier.widthIn(max = switchMaxWidth),
            ) { gifs -> onType(if (gifs) KlipyMediaType.GIF else KlipyMediaType.STICKER) }
            Segmented(
                options = listOf(
                    strings.roomGifTabTrending,
                    strings.roomGifTabRecents,
                    strings.roomGifTabFavorites,
                ),
                selected = sources.indexOf(source),
                onSelect = { onSource(sources[it]) },
                modifier = Modifier.weight(1f),
                autoSize = true,
            )
        }
    }
}

/**
 * The GIF drawer: the type switch and the source row, a square tile grid with 4dp gaps, and a
 * failure state that is not an empty one. The composer text is the query, debounced 400 ms on
 * typing only. Selecting sends at once and closes; a long press offers send or favourite.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GifPanel(
    query: String,
    onGifSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    isHUDVisible: Boolean = true,
) {
    val p = palette
    // Read once, outside the grid: a tile with no title still needs a name to be spoken.
    val untitledGif = strings.roomGifUntitled
    var selectedType by remember { mutableStateOf(KlipyMediaType.GIF) }
    var selectedSource by remember { mutableStateOf(GifSource.TRENDING) }
    val results = remember { mutableStateListOf<KlipyMedia>() }
    var isLoading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var longPressed by remember { mutableStateOf<KlipyMedia?>(null) }
    var favoriteIds by remember { mutableStateOf(loadFavoriteIds()) }

    LaunchedEffect(query, selectedType, selectedSource, retry) {
        failed = false
        // Favourites never touch the network: the composer text filters them by slug.
        if (selectedSource == GifSource.FAVORITES) {
            isLoading = true
            results.clear()
            val favorites = loadFavorites().filter { it.type == selectedType }
            results.addAll(if (query.isBlank()) favorites else favorites.filter { it.slug.contains(query, ignoreCase = true) })
            hasNextPage = false
            isLoading = false
            return@LaunchedEffect
        }

        suspend fun fetchPage(page: Int) = if (query.isNotBlank()) {
            KlipyUtils.search(query = query, type = selectedType, page = page)
        } else when (selectedSource) {
            GifSource.TRENDING -> KlipyUtils.trending(type = selectedType, page = page)
            GifSource.RECENTS -> KlipyUtils.recents(type = selectedType, page = page)
            GifSource.FAVORITES -> error("handled above")
        }

        currentPage = 1
        hasNextPage = false
        isLoadingMore = false
        if (query.isNotBlank()) delay(400)

        isLoading = true
        results.clear()
        val first = fetchPage(1)
        results.addAll(first.items)
        hasNextPage = first.hasNext
        failed = first.failed
        isLoading = false

        // Pages de-duplicate by id: Klipy repeats items across page boundaries.
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 6 && info.totalItemsCount > 0 && hasNextPage && !isLoadingMore
        }.distinctUntilChanged().filter { it }.collect {
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

    fun send(media: KlipyMedia) {
        onGifSelected(media.fullUrl)
        // Fire and forget; the share only feeds the recents tab.
        scope.launch { KlipyUtils.trackShare(media.slug, media.type) }
    }

    Column(modifier.surface(Tier.Panel, Radius.panelShape)) {
        GifDrawerHeader(
            type = selectedType,
            onType = { selectedType = it },
            source = selectedSource,
            onSource = { selectedSource = it },
        )
        Rule()

        Box(Modifier.fillMaxSize()) {
            // The attribution stays, small and out of the way, in the grid's bottom end corner.
            Image(
                imageVector = vectorResource(Res.drawable.powered_by_klipy),
                contentDescription = strings.roomGifPoweredBy,
                modifier = Modifier.align(Alignment.BottomEnd).zIndex(1f).padding(Space.gapTight).height(10.dp).aspectRatio(640 / 107f).alpha(0.7f),
            )
            when {
                isLoading -> ProgressBar(null, Modifier.fillMaxWidth().align(Alignment.TopCenter))
                failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(strings.roomGifFailed, style = Type.note, color = p.inkDim)
                    Spacer(Modifier.height(Space.gapTight))
                    SecondaryAction(strings.roomGifRetry, onClick = { retry++ })
                }
                results.isEmpty() -> Text(
                    text = strings.roomGifNoResults,
                    style = Type.note,
                    color = p.inkDim,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(CHAT_MEDIA_COLUMNS),
                    contentPadding = PaddingValues(CHAT_MEDIA_GAP),
                    horizontalArrangement = Arrangement.spacedBy(CHAT_MEDIA_GAP),
                    verticalArrangement = Arrangement.spacedBy(CHAT_MEDIA_GAP),
                ) {
                    items(results, key = { it.id }) { media ->
                        /* Fixed width and height on the tile: an empty UIImageView reports zero
                         * size and Compose never re-measures UIKit interop after the image loads.
                         * Alpha is a parameter for the same interop reason and follows the HUD
                         * only: at alpha 0 Android composes no image at all, so gating it on
                         * "loaded" meant the load never started and the shimmer never left. The
                         * shimmer sits under the image until the image reports itself loaded. */
                        var loaded by remember(media.id) { mutableStateOf(false) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(Radius.tightShape)
                                /* The name goes on the tile, not on the image inside it: on iOS
                                 * the image is a native view the Compose accessibility bridge
                                 * cannot reach, so the whole grid read as unlabelled buttons. */
                                .semantics(mergeDescendants = true) {
                                    contentDescription = media.title.ifBlank { untitledGif }
                                    role = Role.Button
                                }
                                .combinedClickable(onClick = { send(media) }, onLongClick = { longPressed = media }),
                        ) {
                            if (!loaded && isHUDVisible) Box(Modifier.matchParentSize().shimmer())
                            AnimatedImage(
                                url = media.previewUrl,
                                contentDescription = media.title.ifBlank { null },
                                contentScale = ContentScale.Crop,
                                alpha = if (isHUDVisible) 1f else 0f,
                                onLoaded = { loaded = true },
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                    if (isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ProgressBar(null, Modifier.fillMaxWidth().padding(Space.gapTight))
                        }
                    }
                }
            }
        }
    }

    val target = longPressed
    Modal(open = target != null, onDismiss = { longPressed = null }, size = ModalSize.Ask, inset = false) {
        if (target != null) {
            val isFav = target.id in favoriteIds
            ListRow(onClick = { longPressed = null; send(target) }) {
                Icon(SendGlyph, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(strings.roomGifActionSend)
            }
            ListRow(onClick = {
                longPressed = null
                scope.launch {
                    if (isFav) {
                        removeFavorite(target)
                        if (selectedSource == GifSource.FAVORITES) results.removeAll { it.id == target.id }
                    } else {
                        addFavorite(target)
                    }
                    favoriteIds = loadFavoriteIds()
                }
            }) {
                Icon(if (isFav) Icons.Filled.HeartBroken else Icons.Filled.Favorite, contentDescription = null, tint = p.inkDim, modifier = Modifier.size(Space.glyph))
                RowGap()
                RowLabel(if (isFav) strings.roomGifActionUnfavorite else strings.roomGifActionFavorite)
            }
        }
    }
}

private fun loadFavorites(): List<KlipyMedia> = KLIPY_FAVORITES.value().mapNotNull { json ->
    runCatching { Json.decodeFromString<KlipyMedia>(json) }.getOrNull()
}

private fun loadFavoriteIds(): Set<Long> = loadFavorites().map { it.id }.toHashSet()

private suspend fun addFavorite(media: KlipyMedia) {
    KLIPY_FAVORITES.set(KLIPY_FAVORITES.value() + Json.encodeToString(media))
}

private suspend fun removeFavorite(media: KlipyMedia) {
    val updated = KLIPY_FAVORITES.value().filter { json ->
        runCatching { Json.decodeFromString<KlipyMedia>(json).id != media.id }.getOrDefault(true)
    }.toSet()
    KLIPY_FAVORITES.set(updated)
}

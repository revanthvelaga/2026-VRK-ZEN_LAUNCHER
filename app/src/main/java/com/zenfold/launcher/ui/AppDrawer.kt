package com.zenfold.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val railLetters = ('A'..'Z').toList() + '#'

private fun sectionOf(app: AppEntry): Char {
    val first = app.label.trim().firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first else '#'
}

/** One alphabet section of the drawer: its letter, apps, and where its header sits in the grid. */
private data class Section(val letter: Char, val apps: List<AppEntry>, val headerIndex: Int)

// Drawn in the same window as the home screen (not a Material bottom sheet, which
// lives in its own popup layer) so Haze can blur the wallpaper behind it.
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    suggested: List<AppEntry>,
    style: CustomStyle,
    hazeState: HazeState,
    badged: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    appActions: (AppEntry) -> List<MenuAction>,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Keyed by grid item key, not package: an app can be in both Suggested and A–Z.
    var menuFor by remember { mutableStateOf<String?>(null) }

    @Composable
    fun DrawerApp(key: String, app: AppEntry) {
        Box(contentAlignment = Alignment.TopCenter) {
            AppIcon(
                app,
                style = style,
                badged = app.packageName in badged,
                onLongClick = { menuFor = key },
                onClick = { onLaunch(app) }
            )
            AppActionsMenu(
                app,
                expanded = menuFor == key,
                onDismiss = { menuFor = null },
                actions = if (menuFor == key) appActions(app) else emptyList()
            )
        }
    }

    val searching = query.isNotBlank()
    val results = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val showSuggested = !searching && suggested.isNotEmpty()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // A–Z sections (with '#' for digits/symbols/other scripts last). Grid item indices
    // are counted here so the rail can jump straight to a section header.
    val sections = remember(apps, showSuggested, suggested.size) {
        var index = if (showSuggested) suggested.size + 2 else 0
        apps.groupBy(::sectionOf)
            .toSortedMap(compareBy<Char> { if (it == '#') Char.MAX_VALUE else it })
            .map { (letter, sectionApps) ->
                Section(letter, sectionApps, index).also { index += 1 + sectionApps.size }
            }
    }
    val sectionByLetter = remember(sections) { sections.associateBy { it.letter } }
    val currentLetter by remember(sections) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            sections.lastOrNull { it.headerIndex <= first }?.letter ?: sections.firstOrNull()?.letter
        }
    }

    val currentDismiss by rememberUpdatedState(onDismiss)
    val closeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val pullDownToClose = remember(closeThreshold) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    pulled += available.y
                    if (pulled > closeThreshold) {
                        pulled = 0f
                        currentDismiss()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(focusSearch) {
        if (focusSearch) focusRequester.requestFocus()
    }

    Box(
        modifier
            .fillMaxSize()
            .hazeChild(state = hazeState, shape = RectangleShape)
            .background(style.background.copy(alpha = 0.55f))
            // Swallow taps on empty space so they don't fall through to the home screen.
            .pointerInput(Unit) { detectTapGestures { } }
            .nestedScroll(pullDownToClose)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(style.onBackground.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                )
            }

            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                style = style,
                focusRequester = focusRequester,
                onGo = { results.firstOrNull()?.let(onLaunch) }
            )

            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f)) {
                val showRail = !searching && sections.size > 1
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(end = if (showRail) 24.dp else 0.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (searching) {
                        if (results.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                Text("No apps match \"$query\"", fontSize = 14.sp, color = style.onSurfaceVariant)
                            }
                        }
                        items(results, key = { "r-" + it.packageName }) { app ->
                            DrawerApp("r-" + app.packageName, app)
                        }
                    } else {
                        if (showSuggested) {
                            item(key = "h-suggested", span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel("Suggested", style)
                            }
                            items(suggested, key = { "s-" + it.packageName }) { app ->
                                DrawerApp("s-" + app.packageName, app)
                            }
                            item(key = "h-all", span = { GridItemSpan(maxLineSpan) }) {
                                SectionLabel("All apps", style)
                            }
                        }
                        sections.forEach { section ->
                            item(key = "l-${section.letter}", span = { GridItemSpan(maxLineSpan) }) {
                                LetterHeader(section.letter, style)
                            }
                            items(section.apps, key = { "a-" + it.packageName }) { app ->
                                DrawerApp("a-" + app.packageName, app)
                            }
                        }
                    }
                }

                if (showRail) {
                    AlphabetRail(
                        available = sectionByLetter.keys,
                        current = currentLetter,
                        style = style,
                        onPick = { letter ->
                            // Letters with no apps jump to the next section that has some.
                            val target = sectionByLetter[letter]
                                ?: sections.firstOrNull { railLetters.indexOf(it.letter) > railLetters.indexOf(letter) }
                                ?: sections.last()
                            scope.launch { gridState.scrollToItem(target.headerIndex) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(0.92f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    style: CustomStyle,
    focusRequester: FocusRequester,
    onGo: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .glassTint(RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = style.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search apps", fontSize = 15.sp, color = style.onBackground.copy(alpha = 0.6f))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = style.onBackground),
                cursorBrush = SolidColor(style.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, style: CustomStyle) {
    Text(
        text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = style.onBackground.copy(alpha = 0.45f),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun LetterHeader(letter: Char, style: CustomStyle) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            letter.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = style.accent
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(style.onBackground.copy(alpha = 0.1f))
        )
    }
}

// Full A–Z (+ #) rail. Tap or drag to jump; the letter under your finger pops up in a
// glass bubble, and the section currently on screen is highlighted — like One UI.
@Composable
private fun AlphabetRail(
    available: Set<Char>,
    current: Char?,
    style: CustomStyle,
    onPick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPick by rememberUpdatedState(onPick)
    val haptics = LocalHapticFeedback.current
    var active by remember { mutableStateOf<Char?>(null) }
    var activeY by remember { mutableFloatStateOf(0f) }
    val bubbleSize = 56.dp
    val bubbleGap = 12.dp

    Box(modifier.width(20.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    fun pickAt(y: Float) {
                        val i = (y / size.height * railLetters.size).toInt().coerceIn(0, railLetters.lastIndex)
                        val letter = railLetters[i]
                        activeY = y.coerceIn(0f, size.height.toFloat())
                        if (letter != active) {
                            active = letter
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentPick(letter)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pickAt(down.position.y)
                        down.consume()
                        drag(down.id) { change ->
                            pickAt(change.position.y)
                            change.consume()
                        }
                        active = null
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            railLetters.forEach { letter ->
                val isCurrent = letter == (active ?: current)
                Text(
                    letter.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isCurrent -> style.accent
                        letter in available -> style.onBackground.copy(alpha = 0.6f)
                        else -> style.onBackground.copy(alpha = 0.2f)
                    }
                )
            }
        }

        active?.let { letter ->
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = -(bubbleSize + bubbleGap).roundToPx(),
                            y = (activeY - bubbleSize.toPx() / 2).roundToInt()
                        )
                    }
                    // requiredSize: the rail is only 20dp wide, so a plain size() would squash it.
                    .requiredSize(bubbleSize)
                    .glassTint(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(letter.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = style.onBackground)
            }
        }
    }
}

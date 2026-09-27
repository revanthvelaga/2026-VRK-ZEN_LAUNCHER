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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.AppEntry
import com.zenfold.launcher.style.CustomStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

// Drawn in the same window as the home screen (not a Material bottom sheet, which
// lives in its own popup layer) so Haze can blur the wallpaper behind it.
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    suggested: List<AppEntry>,
    style: CustomStyle,
    hazeState: HazeState,
    query: String,
    onQueryChange: (String) -> Unit,
    focusSearch: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val showSuggested = query.isBlank() && suggested.isNotEmpty()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Index of each letter's first app, offset past the "Suggested" section when shown.
    val headerCount = if (showSuggested) suggested.size + 2 else 0
    val letterIndex = remember(filtered) {
        buildMap<Char, Int> {
            filtered.forEachIndexed { index, app ->
                val first = app.label.firstOrNull()?.uppercaseChar()
                val key = if (first != null && first.isLetter()) first else '#'
                if (key !in this) put(key, index)
            }
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
                        keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(onLaunch) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(Modifier.weight(1f)) {
                val showRail = query.isBlank() && letterIndex.size > 1
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(end = if (showRail) 22.dp else 0.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (showSuggested) {
                        item(key = "h-suggested", span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel("Suggested", style)
                        }
                        items(suggested, key = { "s-" + it.packageName }) { app ->
                            AppIcon(app, style = style, onClick = { onLaunch(app) })
                        }
                        item(key = "h-all", span = { GridItemSpan(maxLineSpan) }) {
                            SectionLabel("All apps", style)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text("No apps match \"$query\"", fontSize = 14.sp, color = style.onSurfaceVariant)
                        }
                    }
                    items(filtered, key = { "a-" + it.packageName }) { app ->
                        AppIcon(app, style = style, onClick = { onLaunch(app) })
                    }
                }

                if (showRail) {
                    AlphabetRail(
                        letters = letterIndex.keys.toList(),
                        style = style,
                        onPick = { letter ->
                            val index = letterIndex[letter] ?: return@AlphabetRail
                            scope.launch { gridState.scrollToItem(headerCount + index) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
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

// Tap or drag along the rail to jump to a letter, like One UI / OxygenOS drawers.
@Composable
private fun AlphabetRail(
    letters: List<Char>,
    style: CustomStyle,
    onPick: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentPick by rememberUpdatedState(onPick)
    Column(
        modifier
            .width(20.dp)
            .fillMaxHeight(0.85f)
            .pointerInput(letters) {
                fun pickAt(y: Float) {
                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    currentPick(letters[i])
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pickAt(down.position.y)
                    down.consume()
                    drag(down.id) { change ->
                        pickAt(change.position.y)
                        change.consume()
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach {
            Text(
                it.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = style.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

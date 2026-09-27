package com.zenfold.launcher.home

import com.zenfold.launcher.GridPos

/** Something occupying one cell of the home grid: a single app, or a folder of apps. */
sealed interface HomeItem {
    val pos: GridPos
    val packages: List<String>
}

data class HomeApp(override val pos: GridPos, val packageName: String) : HomeItem {
    override val packages: List<String> get() = listOf(packageName)
}

data class HomeFolder(override val pos: GridPos, val name: String, override val packages: List<String>) : HomeItem

/** Cells per home page. User-adjustable in Settings. */
data class GridSpec(val columns: Int, val rows: Int)

val DEFAULT_GRID = GridSpec(columns = 4, rows = 4)

const val DEFAULT_FOLDER_NAME = "Folder"

/**
 * The home screen as an explicit, user-owned list spread over as many pages as it needs —
 * nothing appears on it unless the user (or the one-time seed) put it there, and it's
 * never "full": a new app goes to the first free cell, on a new page if necessary. Every
 * edit is a pure function returning a new list, so the caller just persists the result.
 */
object HomeLayout {

    // Control characters as separators: never typable, so folder names need no escaping.
    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"
    private const val LIST = "\u001D"

    fun encode(items: List<HomeItem>): String = items.joinToString(RECORD) { item ->
        val pos = item.pos
        when (item) {
            is HomeApp -> listOf("AP", pos.page, pos.row, pos.col, item.packageName)
            is HomeFolder -> listOf("FP", pos.page, pos.row, pos.col, cleanName(item.name), item.packages.joinToString(LIST))
        }.joinToString(FIELD)
    }

    fun decode(raw: String): List<HomeItem> {
        if (raw.isEmpty()) return emptyList()
        val taken = mutableSetOf<GridPos>()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            // "A"/"F" records predate pages (all on page 0); "AP"/"FP" carry a page number.
            val paged = parts[0].endsWith("P")
            val offset = if (paged) 1 else 0
            val page = if (paged) (parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null) else 0
            val row = parts.getOrNull(1 + offset)?.toIntOrNull() ?: return@mapNotNull null
            val col = parts.getOrNull(2 + offset)?.toIntOrNull() ?: return@mapNotNull null
            val pos = GridPos(row, col, page)
            val item = when (parts[0].removeSuffix("P")) {
                "A" -> parts.getOrNull(3 + offset)?.let { HomeApp(pos, it) }
                "F" -> {
                    val name = parts.getOrNull(3 + offset) ?: return@mapNotNull null
                    val packages = parts.getOrNull(4 + offset)?.split(LIST)?.filter { it.isNotEmpty() }.orEmpty()
                    HomeFolder(pos, name, packages).takeIf { packages.isNotEmpty() }
                }
                else -> null
            }
            item?.takeIf { page >= 0 && row >= 0 && col >= 0 && taken.add(pos) }
        }
    }

    /**
     * The first-run layout: apps with a position from the old single-page layout keep it,
     * the rest fill the remaining cells in order.
     */
    fun seed(packages: List<String>, savedPositions: Map<String, GridPos>, spec: GridSpec): List<HomeItem> {
        var items = packages.mapNotNull { pkg ->
            savedPositions[pkg]?.takeIf { it.row < spec.rows && it.col < spec.columns }?.let { HomeApp(it, pkg) }
        }.distinctBy { it.pos }
        packages.filterNot { contains(items, it) }.forEach { pkg -> items = items + HomeApp(firstFreeCell(items, spec), pkg) }
        return items
    }

    /** Drops apps that aren't installed; a folder left with one app becomes that app. */
    fun prune(items: List<HomeItem>, installed: Set<String>): List<HomeItem> = items.mapNotNull { item ->
        val live = item.packages.filter { it in installed }
        when {
            live.isEmpty() -> null
            live.size == item.packages.size -> item
            live.size == 1 -> HomeApp(item.pos, live.single())
            else -> (item as HomeFolder).copy(packages = live)
        }
    }

    /**
     * Fits a stored layout to the current grid size: anything outside it (after the grid
     * shrank) moves to the first free cell, and pages left empty close up.
     */
    fun normalize(items: List<HomeItem>, spec: GridSpec): List<HomeItem> {
        val (fits, overflow) = items.partition { it.pos.row < spec.rows && it.pos.col < spec.columns }
        var placed = fits
        overflow.forEach { item -> placed = placed + item.movedTo(firstFreeCell(placed, spec)) }
        val pageIndex = placed.map { it.pos.page }.distinct().sorted().withIndex().associate { (i, page) -> page to i }
        return placed.map { it.movedTo(it.pos.copy(page = pageIndex.getValue(it.pos.page))) }
    }

    fun pageCount(items: List<HomeItem>): Int = (items.maxOfOrNull { it.pos.page } ?: 0) + 1

    fun at(items: List<HomeItem>, pos: GridPos): HomeItem? = items.firstOrNull { it.pos == pos }

    fun contains(items: List<HomeItem>, packageName: String): Boolean = items.any { packageName in it.packages }

    /** First free cell scanning from page 0 — past the last page if every page is full. */
    fun firstFreeCell(items: List<HomeItem>, spec: GridSpec): GridPos {
        var page = 0
        while (true) {
            freeCellOnPage(items, spec, page)?.let { return it }
            page++
        }
    }

    private fun freeCellOnPage(items: List<HomeItem>, spec: GridSpec, page: Int): GridPos? {
        val used = items.mapTo(HashSet()) { it.pos }
        return (0 until spec.rows * spec.columns)
            .map { GridPos(it / spec.columns, it % spec.columns, page) }
            .firstOrNull { it !in used }
    }

    /**
     * Drag-and-drop: onto an empty cell moves; an app onto an app makes a folder; an app onto
     * a folder joins it; a folder dropped on anything occupied swaps places (no nesting).
     */
    fun drop(items: List<HomeItem>, from: GridPos, to: GridPos, folderName: (List<String>) -> String): List<HomeItem> {
        if (from == to) return items
        val source = at(items, from) ?: return items
        val target = at(items, to)
        val rest = items.filterNot { it === source || it === target }
        return when {
            target == null -> rest + source.movedTo(to)
            source is HomeApp && target is HomeApp -> {
                val packages = listOf(target.packageName, source.packageName)
                rest + HomeFolder(to, folderName(packages), packages)
            }
            source is HomeApp && target is HomeFolder -> rest + target.copy(packages = target.packages + source.packageName)
            else -> rest + source.movedTo(to) + target.movedTo(from)
        }
    }

    /** Moves an item to the first free cell of [page] (a new page if it's past the last). Null if that page is full. */
    fun moveToPage(items: List<HomeItem>, from: GridPos, page: Int, spec: GridSpec): List<HomeItem>? {
        val item = at(items, from) ?: return items
        val rest = items.filterNot { it === item }
        val cell = freeCellOnPage(rest, spec, page) ?: return null
        return rest + item.movedTo(cell)
    }

    /** An app already on Home (even inside a folder) stays where it is. */
    fun addApp(items: List<HomeItem>, packageName: String, spec: GridSpec): List<HomeItem> =
        if (contains(items, packageName)) items else items + HomeApp(firstFreeCell(items, spec), packageName)

    fun removeApp(items: List<HomeItem>, packageName: String): List<HomeItem> = items.mapNotNull { item ->
        when {
            packageName !in item.packages -> item
            item is HomeFolder -> item.without(packageName)
            else -> null
        }
    }

    fun removeAt(items: List<HomeItem>, pos: GridPos): List<HomeItem> = items.filterNot { it.pos == pos }

    fun removeAll(items: List<HomeItem>, positions: Set<GridPos>): List<HomeItem> = items.filterNot { it.pos in positions }

    /**
     * Merges the selected apps and folders into one folder, in the first selected cell
     * (reading order). Folders' apps are merged in; fewer than two apps changes nothing.
     */
    fun groupIntoFolder(items: List<HomeItem>, positions: Set<GridPos>, folderName: (List<String>) -> String): List<HomeItem> {
        val chosen = items.filter { it.pos in positions }
            .sortedWith(compareBy<HomeItem>({ it.pos.page }, { it.pos.row }, { it.pos.col }))
        val packages = chosen.flatMap { it.packages }.distinct()
        if (packages.size < 2) return items
        val existingName = chosen.firstNotNullOfOrNull { (it as? HomeFolder)?.name }
        return items.filterNot { it.pos in positions } +
            HomeFolder(chosen.first().pos, existingName ?: folderName(packages), packages)
    }

    fun renameFolder(items: List<HomeItem>, pos: GridPos, name: String): List<HomeItem> = items.map { item ->
        if (item is HomeFolder && item.pos == pos) item.copy(name = cleanName(name)) else item
    }

    /** Takes an app out of a folder, onto the folder's own page if there's room. */
    fun moveOutOfFolder(items: List<HomeItem>, folderPos: GridPos, packageName: String, spec: GridSpec): List<HomeItem> {
        val folder = at(items, folderPos) as? HomeFolder ?: return items
        if (packageName !in folder.packages) return items
        val without = items.mapNotNull { if (it === folder) folder.without(packageName) else it }
        val cell = freeCellOnPage(without, spec, folderPos.page) ?: firstFreeCell(without, spec)
        return without + HomeApp(cell, packageName)
    }

    private fun HomeFolder.without(packageName: String): HomeItem? {
        val left = packages - packageName
        return when (left.size) {
            0 -> null
            1 -> HomeApp(pos, left.single())
            else -> copy(packages = left)
        }
    }

    private fun HomeItem.movedTo(pos: GridPos): HomeItem = when (this) {
        is HomeApp -> copy(pos = pos)
        is HomeFolder -> copy(pos = pos)
    }

    private fun cleanName(name: String): String =
        name.filterNot { it.isISOControl() }.trim().ifEmpty { DEFAULT_FOLDER_NAME }
}

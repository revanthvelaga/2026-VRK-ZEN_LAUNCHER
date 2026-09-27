package com.zenfold.launcher.home

import com.zenfold.launcher.GRID_COLUMNS
import com.zenfold.launcher.GRID_ROWS
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

const val DEFAULT_FOLDER_NAME = "Folder"

/**
 * The home grid as an explicit, user-owned list — nothing appears on it unless the user
 * (or the one-time seed) put it there. Every edit is a pure function returning a new list,
 * so the caller just persists whatever comes back.
 */
object HomeLayout {

    // Control characters as separators: never typable, so folder names need no escaping.
    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"
    private const val LIST = "\u001D"

    fun encode(items: List<HomeItem>): String = items.joinToString(RECORD) { item ->
        when (item) {
            is HomeApp -> listOf("A", item.pos.row, item.pos.col, item.packageName)
            is HomeFolder -> listOf("F", item.pos.row, item.pos.col, cleanName(item.name), item.packages.joinToString(LIST))
        }.joinToString(FIELD)
    }

    fun decode(raw: String): List<HomeItem> {
        if (raw.isEmpty()) return emptyList()
        val taken = mutableSetOf<GridPos>()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            val row = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val col = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            val pos = GridPos(row, col)
            val item = when {
                parts[0] == "A" && parts.size == 4 -> HomeApp(pos, parts[3])
                parts[0] == "F" && parts.size == 5 ->
                    HomeFolder(pos, parts[3], parts[4].split(LIST).filter { it.isNotEmpty() })
                        .takeIf { it.packages.isNotEmpty() }
                else -> null
            }
            item?.takeIf { pos.inGrid() && taken.add(pos) }
        }
    }

    /**
     * The first-run grid: apps with a position from the old per-app layout keep it, the rest
     * fill the remaining cells in order.
     */
    fun seed(packages: List<String>, savedPositions: Map<String, GridPos>): List<HomeItem> {
        val placed = mutableMapOf<GridPos, String>()
        packages.forEach { pkg ->
            val pos = savedPositions[pkg]
            if (pos != null && pos.inGrid() && pos !in placed) placed[pos] = pkg
        }
        packages.filterNot { it in placed.values }.forEach { pkg ->
            val free = allCells().firstOrNull { it !in placed } ?: return@forEach
            placed[free] = pkg
        }
        return placed.map { (pos, pkg) -> HomeApp(pos, pkg) }
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

    fun at(items: List<HomeItem>, pos: GridPos): HomeItem? = items.firstOrNull { it.pos == pos }

    fun contains(items: List<HomeItem>, packageName: String): Boolean = items.any { packageName in it.packages }

    fun firstFreeCell(items: List<HomeItem>): GridPos? {
        val used = items.mapTo(HashSet()) { it.pos }
        return allCells().firstOrNull { it !in used }
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

    /** Null when there's no free cell. An app already on Home (even inside a folder) stays put. */
    fun addApp(items: List<HomeItem>, packageName: String): List<HomeItem>? {
        if (contains(items, packageName)) return items
        val cell = firstFreeCell(items) ?: return null
        return items + HomeApp(cell, packageName)
    }

    fun removeApp(items: List<HomeItem>, packageName: String): List<HomeItem> = items.mapNotNull { item ->
        when {
            packageName !in item.packages -> item
            item is HomeFolder -> item.without(packageName)
            else -> null
        }
    }

    fun removeAt(items: List<HomeItem>, pos: GridPos): List<HomeItem> = items.filterNot { it.pos == pos }

    fun renameFolder(items: List<HomeItem>, pos: GridPos, name: String): List<HomeItem> = items.map { item ->
        if (item is HomeFolder && item.pos == pos) item.copy(name = cleanName(name)) else item
    }

    /** Takes an app out of a folder into the first free cell. Null when the grid is full. */
    fun moveOutOfFolder(items: List<HomeItem>, folderPos: GridPos, packageName: String): List<HomeItem>? {
        val folder = at(items, folderPos) as? HomeFolder ?: return items
        if (packageName !in folder.packages) return items
        val without = items.mapNotNull { if (it === folder) folder.without(packageName) else it }
        val cell = firstFreeCell(without) ?: return null
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

    private fun GridPos.inGrid(): Boolean = row in 0 until GRID_ROWS && col in 0 until GRID_COLUMNS

    private fun allCells(): List<GridPos> =
        (0 until GRID_ROWS * GRID_COLUMNS).map { GridPos(it / GRID_COLUMNS, it % GRID_COLUMNS) }
}

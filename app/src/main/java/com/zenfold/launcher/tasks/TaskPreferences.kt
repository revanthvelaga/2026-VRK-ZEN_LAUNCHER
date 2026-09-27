package com.zenfold.launcher.tasks

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.util.UUID

data class TaskItem(val id: String, val text: String, val done: Boolean)

private val Context.taskPrefs by preferencesDataStore(name = "tasks_prefs")
private val TASKS_KEY = stringPreferencesKey("tasks")

// Control characters as separators (never typable, so task text never needs escaping) —
// simpler and safer than comma-joining free text, without pulling in a JSON dependency.
private const val FIELD_SEP = "\u001F"
private const val RECORD_SEP = "\u001E"

private fun encodeTasks(tasks: List<TaskItem>): String =
    tasks.joinToString(RECORD_SEP) { "${it.id}$FIELD_SEP${it.done}$FIELD_SEP${it.text}" }

private fun decodeTasks(raw: String?): List<TaskItem> {
    if (raw.isNullOrEmpty()) return emptyList()
    return raw.split(RECORD_SEP).mapNotNull { record ->
        val parts = record.split(FIELD_SEP, limit = 3)
        if (parts.size == 3) TaskItem(id = parts[0], done = parts[1].toBoolean(), text = parts[2]) else null
    }
}

class TaskPreferences(private val context: Context) {

    val tasks = context.taskPrefs.data.map { decodeTasks(it[TASKS_KEY]) }

    suspend fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        context.taskPrefs.edit { prefs ->
            val next = decodeTasks(prefs[TASKS_KEY]) + TaskItem(id = UUID.randomUUID().toString(), text = trimmed, done = false)
            prefs[TASKS_KEY] = encodeTasks(next)
        }
    }

    suspend fun setDone(id: String, done: Boolean) {
        context.taskPrefs.edit { prefs ->
            val next = decodeTasks(prefs[TASKS_KEY]).map { if (it.id == id) it.copy(done = done) else it }
            prefs[TASKS_KEY] = encodeTasks(next)
        }
    }

    suspend fun remove(id: String) {
        context.taskPrefs.edit { prefs ->
            val next = decodeTasks(prefs[TASKS_KEY]).filterNot { it.id == id }
            prefs[TASKS_KEY] = encodeTasks(next)
        }
    }
}

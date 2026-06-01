package com.example.myapplication.data.storage

import android.content.Context
import com.example.myapplication.data.model.TravelEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EntryStorage(context: Context) {

    private val file = File(context.filesDir, "entries.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<TravelEntry>>(emptyList())
    val entries: StateFlow<List<TravelEntry>> = _entries.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!file.exists()) return
        runCatching {
            _entries.value = json.decodeFromString(file.readText())
        }
    }

    suspend fun save(entry: TravelEntry) = withContext(Dispatchers.IO) {
        val list = _entries.value.toMutableList()
        val idx = list.indexOfFirst { it.id == entry.id }
        if (idx >= 0) list[idx] = entry else list.add(0, entry)
        _entries.value = list
        file.writeText(json.encodeToString(list))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val list = _entries.value.filter { it.id != id }
        _entries.value = list
        file.writeText(json.encodeToString(list))
    }

    fun getById(id: String): TravelEntry? = _entries.value.find { it.id == id }
}

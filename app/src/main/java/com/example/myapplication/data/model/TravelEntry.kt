package com.example.myapplication.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TravelEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val location: String,
    val dateMillis: Long,
    val description: String,
    val mood: String,
    val photoUris: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/** Distinct tags across the given entries, most-used first then alphabetical. */
fun List<TravelEntry>.tagsByFrequency(): List<String> =
    flatMap { it.tags }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

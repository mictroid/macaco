package com.example.myapplication.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [tagsByFrequency] — most-used-first then alphabetical tag ranking. */
class TagsByFrequencyTest {

    private fun entry(vararg tags: String) = TravelEntry(
        title = "t",
        location = "",
        dateMillis = 0L,
        description = "",
        mood = "",
        tags = tags.toList()
    )

    @Test
    fun emptyListYieldsNoTags() {
        assertEquals(emptyList<String>(), emptyList<TravelEntry>().tagsByFrequency())
    }

    @Test
    fun entriesWithoutTagsYieldNoTags() {
        assertEquals(emptyList<String>(), listOf(entry(), entry()).tagsByFrequency())
    }

    @Test
    fun deduplicatesTagsAcrossEntries() {
        val result = listOf(entry("beach"), entry("beach")).tagsByFrequency()
        assertEquals(listOf("beach"), result)
    }

    @Test
    fun ordersByDescendingFrequency() {
        val entries = listOf(
            entry("museum", "beach"),
            entry("museum", "art"),
            entry("museum")
        )
        // museum=3, then art=1 and beach=1 broken alphabetically.
        assertEquals(listOf("museum", "art", "beach"), entries.tagsByFrequency())
    }

    @Test
    fun breaksFrequencyTiesAlphabetically() {
        val entries = listOf(entry("zebra", "apple", "mango"))
        assertEquals(listOf("apple", "mango", "zebra"), entries.tagsByFrequency())
    }
}

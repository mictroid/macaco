package com.example.myapplication.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [normalizeTag] — the Instagram-style hashtag normalizer used by the tag field. */
class NormalizeTagTest {

    @Test
    fun stripsLeadingHashAndLowercases() {
        assertEquals("museum", normalizeTag("#Museum"))
        assertEquals("architecture", normalizeTag("Architecture"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("vacation", normalizeTag("   vacation   "))
    }

    @Test
    fun removesSpacesAndPunctuationWithin() {
        assertEquals("oldtown", normalizeTag("Old Town"))
        assertEquals("vacation", normalizeTag("#vacation!"))
        assertEquals("foo_bar2", normalizeTag("#Foo_Bar 2"))
    }

    @Test
    fun keepsDigitsAndUnderscores() {
        assertEquals("snake_case", normalizeTag("snake_case"))
        assertEquals("123", normalizeTag("123"))
    }

    @Test
    fun onlyStripsASingleLeadingHash() {
        // removePrefix drops one '#'; the rest are non-alphanumeric and get filtered out.
        assertEquals("", normalizeTag("###"))
        assertEquals("tag", normalizeTag("##tag"))
    }

    @Test
    fun returnsEmptyWhenNothingUsableRemains() {
        assertEquals("", normalizeTag(""))
        assertEquals("", normalizeTag("#"))
        assertEquals("", normalizeTag("   "))
        assertEquals("", normalizeTag("!!!"))
    }
}

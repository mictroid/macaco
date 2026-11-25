package com.houseofmmminq.macaco.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

object DictationText {
    private val SENTENCE_END = charArrayOf('.', '!', '?', '…', '。', '！', '？')

    /** Han / Hiragana / Katakana and CJK punctuation: these scripts use no inter-word spaces
     *  and close sentences with '。' rather than '.'. */
    private fun Char.isCjk(): Boolean {
        if (this in '　'..'〿' || this in '＀'..'￯') return true
        return when (Character.UnicodeScript.of(code)) {
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA -> true
            else -> false
        }
    }

    /** Inserts dictated [raw] at the cursor of [current], capitalizing / punctuating so
     *  consecutive dictations read as sentences. Never alters the user's existing text. */
    fun insert(current: TextFieldValue, raw: String): TextFieldValue {
        val spoken = raw.trim()
        if (spoken.isEmpty()) return current
        val cursor = current.selection.end.coerceIn(0, current.text.length)
        val before = current.text.substring(0, cursor)
        val after = current.text.substring(cursor)

        val prev = before.trimEnd()
        val startsSentence = prev.isEmpty() || prev.last() in SENTENCE_END || before.endsWith("\n")
        var chunk = if (startsSentence) spoken.replaceFirstChar { it.titlecase() } else spoken
        // Each dictation is usually one thought: close it with a full stop if the recognizer
        // didn't, but only when it lands at the end of the text (don't punctuate mid-sentence).
        if (after.isBlank() && chunk.last() !in SENTENCE_END && chunk.last().isLetterOrDigit()) {
            chunk += if (chunk.last().isCjk()) "。" else "."
        }
        // No joining space between CJK text, or after existing whitespace.
        val sep = when {
            before.isEmpty() || before.last().isWhitespace() -> ""
            before.last().isCjk() && chunk.first().isCjk() -> ""
            else -> " "
        }
        val trailing = when {
            after.isEmpty() || after.first().isWhitespace() -> ""
            chunk.last().isCjk() && after.first().isCjk() -> ""
            else -> " "
        }
        val newText = before + sep + chunk + trailing + after
        val newCursor = (before + sep + chunk).length
        return TextFieldValue(newText, TextRange(newCursor))
    }
}

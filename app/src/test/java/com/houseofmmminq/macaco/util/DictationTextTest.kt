package com.houseofmmminq.macaco.util

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class DictationTextTest {

    /** Builds a value from text with a `|` cursor marker. */
    private fun tfv(marked: String): TextFieldValue {
        val i = marked.indexOf('|')
        return TextFieldValue(marked.removeRange(i, i + 1), TextRange(i))
    }

    private fun render(v: TextFieldValue): String =
        v.text.substring(0, v.selection.end) + "|" + v.text.substring(v.selection.end)

    private fun check(before: String, spoken: String, expected: String) =
        assertEquals(expected, render(DictationText.insert(tfv(before), spoken)))

    @Test fun emptyField_capitalizesAndCloses() =
        check("|", "we watched the sunset", "We watched the sunset.|")

    @Test fun secondDictation_startsNewSentence() =
        check("We watched the sunset.|", "then we ate pasteis de nata",
            "We watched the sunset. Then we ate pasteis de nata.|")

    @Test fun continuesUnfinishedSentence() =
        check("We watched the sunset|", "from the miradouro",
            "We watched the sunset from the miradouro.|")

    @Test fun midTextInsert_noPeriod() =
        check("Great day. | It rained later.", "the food was amazing",
            "Great day. The food was amazing| It rained later.")

    @Test fun alreadyFormatted_untouched() =
        check("Hello!|", "What a view?", "Hello! What a view?|")

    @Test fun japanese_noSpaceAndCjkFullStop() =
        check("今日は晴れ。|", "海を見た", "今日は晴れ。海を見た。|")

    @Test fun newline_startsSentence() =
        check("Day one.\n|", "we landed", "Day one.\nWe landed.|")

    @Test fun blankDictation_isNoOp() =
        check("Hi.|", "   ", "Hi.|")
}

package com.christianh.daily

import com.christianh.daily.domain.Inline
import com.christianh.daily.domain.Inline.Emphasis
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The markdown subset the corpus is authored in.
 *
 * Worth testing off-device for the same reason [com.christianh.daily.domain.FlickDetector]
 * is: the only hard part is a string problem, and checking it by installing the APK
 * and squinting at a paragraph is how the literal asterisks survived in the first
 * place. The failure that matters is not a missed emphasis — it is a stray asterisk
 * in ordinary prose swallowing half a paragraph into italics.
 */
class InlineTest {

    private fun text(s: String) = Inline.parse(s).text
    private fun spans(s: String) = Inline.parse(s).spans.map {
        Triple(it.emphasis, it.start, it.end)
    }

    // --- the two the corpus actually uses ----------------------------------------

    @Test fun `bold loses its markers and keeps its range`() {
        assertEquals("bold", text("**bold**"))
        assertEquals(listOf(Triple(Emphasis.BOLD, 0, 4)), spans("**bold**"))
    }

    @Test fun `italic loses its markers and keeps its range`() {
        assertEquals("italic", text("*italic*"))
        assertEquals(listOf(Triple(Emphasis.ITALIC, 0, 6)), spans("*italic*"))
    }

    @Test fun `a book title mid-sentence is italicised and nothing else moves`() {
        val source = "the *Nicomachean Ethics* is where"
        assertEquals("the Nicomachean Ethics is where", text(source))
        assertEquals(listOf(Triple(Emphasis.ITALIC, 4, 22)), spans(source))
    }

    @Test fun `a bolded lead runs to its full stop and stops there`() {
        val source = "**A caution about the line this attracts.** It is Horace."
        assertEquals("A caution about the line this attracts. It is Horace.", text(source))
        assertEquals(listOf(Triple(Emphasis.BOLD, 0, 39)), spans(source))
    }

    @Test fun `several runs in one paragraph`() {
        assertEquals("a b c d e", text("a **b** c *d* e"))
        assertEquals(
            listOf(Triple(Emphasis.BOLD, 2, 3), Triple(Emphasis.ITALIC, 6, 7)),
            spans("a **b** c *d* e")
        )
    }

    // --- nesting -----------------------------------------------------------------

    @Test fun `italic inside bold gets both, over the right ranges`() {
        val source = "**bold with *it* inside**"
        assertEquals("bold with it inside", text(source))
        assertEquals(
            listOf(Triple(Emphasis.ITALIC, 10, 12), Triple(Emphasis.BOLD, 0, 19)),
            spans(source)
        )
    }

    @Test fun `three asterisks are one run, not a bold next to an italic`() {
        assertEquals("both", text("***both***"))
        assertEquals(listOf(Triple(Emphasis.BOLD_ITALIC, 0, 4)), spans("***both***"))
    }

    @Test fun `adjacent runs do not bleed into each other`() {
        assertEquals("ab", text("**a****b**"))
        assertEquals(
            listOf(Triple(Emphasis.BOLD, 0, 1), Triple(Emphasis.BOLD, 1, 2)),
            spans("**a****b**")
        )
    }

    // --- the failures that would ruin a paragraph --------------------------------

    /**
     * The space rule. Without it the two loose asterisks pair up and everything
     * between them turns italic — a whole clause lost to arithmetic.
     */
    @Test fun `loose asterisks in prose are left exactly as they are`() {
        for (source in listOf("2 * 3 * 4", "a * b * c", "* leading", "trailing *")) {
            assertEquals(source, text(source))
            assertEquals(emptyList<Triple<Emphasis, Int, Int>>(), spans(source))
        }
    }

    @Test fun `an unclosed marker is literal, not an emphasis to the end of the text`() {
        assertEquals("unclosed *text here", text("unclosed *text here"))
        assertEquals(emptyList<Triple<Emphasis, Int, Int>>(), spans("unclosed *text here"))
    }

    @Test fun `bare markers survive`() {
        assertEquals("*", text("*"))
        assertEquals("**", text("**"))
        assertEquals("***", text("***"))
    }

    @Test fun `empty emphasis is not an emphasis`() {
        assertEquals("****", text("****"))
        assertEquals(emptyList<Triple<Emphasis, Int, Int>>(), spans("****"))
    }

    // --- the ordinary case, which is most of the corpus --------------------------

    @Test fun `prose with no markers is returned untouched`() {
        val source = "Almost every piece of advice you will hear about anger is about managing it."
        assertEquals(source, text(source))
        assertEquals(emptyList<Triple<Emphasis, Int, Int>>(), spans(source))
    }

    @Test fun `an apostrophe is not a marker`() {
        val source = "Kierkegaard's *Angest* is a translator's compromise"
        assertEquals("Kierkegaard's Angest is a translator's compromise", text(source))
        assertEquals(listOf(Triple(Emphasis.ITALIC, 14, 20)), spans(source))
    }

    /** Every span has to be addressable in the string it is reported against. */
    @Test fun `spans always fall inside the output`() {
        val sources = listOf(
            "**bold**", "*it*", "***both***", "a **b** c *d* e",
            "**bold with *it* inside**", "unclosed *text", "2 * 3", "plain"
        )
        for (source in sources) {
            val marked = Inline.parse(source)
            for (span in marked.spans) {
                assert(span.start in 0..marked.text.length) { "$source: start out of range" }
                assert(span.end in span.start..marked.text.length) { "$source: end out of range" }
            }
        }
    }
}

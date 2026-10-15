package com.christianh.daily

import com.christianh.daily.domain.NoteMerge
import com.christianh.daily.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The merge behind restore and behind the backup's first-write guard (D-028).
 * Losing a note here is the failure the whole feature exists to prevent.
 */
class NoteMergeTest {

    private val d1 = LocalDate.of(2026, 10, 1)
    private val d2 = LocalDate.of(2026, 10, 2)
    private val d3 = LocalDate.of(2026, 10, 3)

    private fun note(on: LocalDate, text: String, updatedAt: Long) =
        Note(date = on, entryId = "e-$on", text = text, createdAt = 1L, updatedAt = updatedAt)

    @Test fun `adds days the phone does not have`() {
        val local = mapOf(d1 to note(d1, "mine", 10))
        val incoming = mapOf(d2 to note(d2, "theirs", 10))

        val r = NoteMerge.merge(local, incoming)

        assertEquals(setOf(d1, d2), r.notes.keys)
        assertEquals(1, r.added)
        assertEquals(0, r.updated)
    }

    @Test fun `the later edit wins a day both sides have`() {
        val local = mapOf(d1 to note(d1, "old", 10), d2 to note(d2, "newer here", 30))
        val incoming = mapOf(d1 to note(d1, "newer there", 20), d2 to note(d2, "old there", 20))

        val r = NoteMerge.merge(local, incoming)

        assertEquals("newer there", r.notes[d1]?.text)
        assertEquals("newer here", r.notes[d2]?.text)
        assertEquals(0, r.added)
        assertEquals(1, r.updated)
    }

    @Test fun `a tie keeps the phone's note`() {
        val r = NoteMerge.merge(
            mapOf(d1 to note(d1, "mine", 10)),
            mapOf(d1 to note(d1, "theirs", 10))
        )
        assertEquals("mine", r.notes[d1]?.text)
        assertEquals(0, r.updated)
    }

    @Test fun `never deletes a note the copy lacks`() {
        val local = mapOf(d1 to note(d1, "a", 10), d2 to note(d2, "b", 10))

        val r = NoteMerge.merge(local, emptyMap())

        assertEquals(local, r.notes)
    }

    @Test fun `a blank incoming note is no note`() {
        val r = NoteMerge.merge(
            mapOf(d1 to note(d1, "keep me", 10)),
            mapOf(d1 to note(d1, "   ", 99), d3 to note(d3, "", 99))
        )
        assertEquals("keep me", r.notes[d1]?.text)
        assertEquals(setOf(d1), r.notes.keys)
        assertEquals(0, r.added + r.updated)
    }

    @Test fun `wouldLose counts exactly what a merge would bring in`() {
        val local = mapOf(d1 to note(d1, "x", 10))
        val incoming = mapOf(
            d1 to note(d1, "edited later", 20),   // would be lost: newer
            d2 to note(d2, "only there", 5),      // would be lost: absent here
            d3 to note(d3, "", 5)                 // blank: nothing to lose
        )
        assertEquals(2, NoteMerge.wouldLose(local, incoming))
    }

    @Test fun `overwriting is safe once the phone has everything`() {
        val local = mapOf(d1 to note(d1, "x", 20), d2 to note(d2, "y", 20))
        val incoming = mapOf(d1 to note(d1, "x", 20), d2 to note(d2, "older y", 10))
        assertEquals(0, NoteMerge.wouldLose(local, incoming))
    }

    @Test fun `an empty phone would lose the whole copy`() {
        // The reinstall case the first-write guard exists for.
        val incoming = mapOf(d1 to note(d1, "a", 1), d2 to note(d2, "b", 1))
        assertEquals(2, NoteMerge.wouldLose(emptyMap(), incoming))
    }
}

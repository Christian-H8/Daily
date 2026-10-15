package com.christianh.daily

import com.christianh.daily.domain.model.Note
import com.christianh.daily.data.NoteStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class NoteStoreTest {

    private fun note(d: String, text: String) = Note(
        date = LocalDate.parse(d),
        entryId = "some-entry",
        text = text,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    @Test fun `round trips`() {
        val notes = mapOf(
            LocalDate.parse("2026-09-12") to note("2026-09-12", "first"),
            LocalDate.parse("2026-09-14") to note("2026-09-14", "second\nwith a newline")
        )
        val back = NoteStore.decode(NoteStore.encode(notes))
        assertEquals(notes, back)
    }

    @Test fun `encodes the version, so a future migration has something to read`() {
        val json = NoteStore.encode(emptyMap())
        assertTrue(json.contains("\"version\""))
    }

    @Test fun `a malformed date is skipped rather than losing the whole file`() {
        val json = """
            {"version":1,"notes":[
              {"date":"not-a-date","entryId":"x","text":"lost"},
              {"date":"2026-09-12","entryId":"y","text":"kept"}
            ]}
        """.trimIndent()
        val back = NoteStore.decode(json)
        assertEquals(1, back.size)
        assertEquals("kept", back[LocalDate.parse("2026-09-12")]?.text)
    }

    @Test fun `missing optional fields do not throw`() {
        val json = """{"version":1,"notes":[{"date":"2026-09-12"}]}"""
        val n = NoteStore.decode(json)[LocalDate.parse("2026-09-12")]!!
        assertEquals("", n.text)
        assertEquals("", n.entryId)
    }

    @Test fun `an empty document decodes to nothing`() {
        assertTrue(NoteStore.decode("""{"version":1}""").isEmpty())
    }

    @Test fun `unicode and quotes survive the round trip`() {
        val text = "“hevel” — vapour, not \"meaningless\"\n\ttabbed"
        val notes = mapOf(LocalDate.parse("2026-09-12") to note("2026-09-12", text))
        assertEquals(text, NoteStore.decode(NoteStore.encode(notes)).values.first().text)
    }
}

package com.christianh.daily

import com.christianh.daily.domain.model.Block
import com.christianh.daily.data.CorpusLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorpusLoaderTest {

    private val json = """
        {
          "corpusVersion": "c2a98e94a53c",
          "entries": [
            {
              "id": "where-the-state-ceaseth",
              "type": "expound",
              "title": "Where The State Ceaseth",
              "tagline": "A line about what is left",
              "tags": ["politics", "freedom"],
              "related": ["a-vault-only-note"],
              "words": 1089,
              "minutes": 5,
              "sections": [
                {
                  "heading": "Quote",
                  "blocks": [
                    { "kind": "quote", "text": "Where the state ceaseth.",
                      "attribution": "Nietzsche, Thus Spoke Zarathustra" }
                  ]
                },
                {
                  "heading": "Idea One",
                  "meta": { "thinkers": ["Nietzsche", "Weber"] },
                  "blocks": [
                    { "kind": "p", "text": "A paragraph." },
                    { "kind": "sparkline", "text": "from a future schema" },
                    { "kind": "p", "text": "Another paragraph." }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test fun `parses an entry with its sections and blocks`() {
        val e = CorpusLoader.parse(json).single()
        assertEquals("where-the-state-ceaseth", e.id)
        assertEquals("expound", e.type)
        assertEquals(listOf("politics", "freedom"), e.tags)
        assertEquals(1089, e.words)
        assertEquals(2, e.sections.size)
    }

    @Test fun `a quote keeps its attribution`() {
        val b = CorpusLoader.parse(json).single().sections[0].blocks.single()
        assertTrue(b is Block.Quote)
        assertEquals("Nietzsche, Thus Spoke Zarathustra", (b as Block.Quote).attribution)
    }

    @Test fun `thinkers come off section meta`() {
        val s = CorpusLoader.parse(json).single().sections[1]
        assertEquals(listOf("Nietzsche", "Weber"), s.thinkers)
    }

    /**
     * The corpus ships ahead of the client by design, so a block kind this build has
     * never heard of must degrade to "missing" rather than to a crash on the commute.
     */
    @Test fun `an unknown block kind is skipped, not fatal`() {
        val blocks = CorpusLoader.parse(json).single().sections[1].blocks
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is Block.Paragraph })
    }

    @Test fun `reads the corpus version`() {
        assertEquals("c2a98e94a53c", CorpusLoader.versionOf(json))
    }

    @Test fun `a quote with no attribution gives null rather than an empty string`() {
        val bare = """
            {"entries":[{"id":"x","type":"passage","title":"X","sections":[
              {"heading":"Passage","blocks":[{"kind":"quote","text":"t"}]}
            ]}]}
        """.trimIndent()
        val b = CorpusLoader.parse(bare).single().sections[0].blocks.single() as Block.Quote
        assertNull(b.attribution)
    }

    @Test fun `absent optional fields fall back rather than throwing`() {
        val bare = """{"entries":[{"id":"x","type":"passage","title":"X","sections":[]}]}"""
        val e = CorpusLoader.parse(bare).single()
        assertEquals("", e.tagline)
        assertTrue(e.tags.isEmpty())
        assertEquals(1, e.minutes)
    }
}

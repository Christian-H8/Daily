package com.christianh.daily.domain.model

/**
 * SCHEMA.md v2.
 *
 * An entry is a list of sections, not a set of named fields. The renderer walks
 * them in order and styles by heading and block kind, so adding a section — or a
 * fifth entry type — needs no client change. That flexibility is deliberate:
 * see SCHEMA §3 and DECISIONS D-015.
 */
data class Entry(
    val id: String,
    val type: String,          // collision | expound | principle | passage
    val title: String,
    val tagline: String,
    val tags: List<String>,
    val related: List<String>, // vault-only links; NOT rendered as navigation (D-016)
    val words: Int,            // computed by publish.py, never authored
    val minutes: Int,
    val sections: List<Section>
)

data class Section(
    val heading: String,
    val thinkers: List<String>, // from "**Thinkers:**"; empty for most sections
    val blocks: List<Block>
)

sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Quote(val text: String, val attribution: String?) : Block
}

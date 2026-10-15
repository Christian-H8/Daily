package com.christianh.daily.domain

/**
 * FNV-1a, 64-bit. Specified rather than borrowed, because publish.py must
 * produce byte-identical results — see SCHEMA.md and DECISIONS.md D-005.
 *
 * Deliberately NOT String.hashCode() (clusters on short similar strings) and
 * NOT Random(seed).shuffled() (Kotlin's PRNG is not contractually frozen, so a
 * stdlib update could silently reorder every future day).
 */
object StableHash {
    fun of(id: String): Long {
        var h = -0x340d631b7bdddcdbL          // 0xcbf29ce484222325
        for (b in id.encodeToByteArray()) {
            h = h xor (b.toInt() and 0xFF).toLong()
            h *= 0x100000001b3L
        }
        return h
    }
}
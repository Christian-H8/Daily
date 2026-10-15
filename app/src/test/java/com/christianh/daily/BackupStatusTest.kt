package com.christianh.daily

import com.christianh.daily.data.BackupStatus
import com.christianh.daily.data.RestoreResult
import com.christianh.daily.data.line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The Settings wording for the notes backup (D-028). Each failure mode needs a line
 * that names it, or "no backup" becomes several problems wearing one face.
 */
class BackupStatusTest {

    private val zone = ZoneId.of("America/New_York")

    @Test fun `a saved copy says when and how many`() {
        val at = ZonedDateTime.of(2026, 10, 10, 18, 42, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(
            "Backed up Oct 10, 18:42 · 41 notes",
            BackupStatus.Saved(at, 41).line(zone, Locale.US)
        )
    }

    @Test fun `one note is singular`() {
        val at = ZonedDateTime.of(2026, 1, 2, 7, 5, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("Backed up Jan 2, 07:05 · 1 note", BackupStatus.Saved(at, 1).line(zone, Locale.US))
    }

    @Test fun `a refused first write says why and what to do`() {
        val line = BackupStatus.Blocked(3).line(zone, Locale.US)
        assertTrue(line, line.contains("3 notes"))
        assertTrue(line, line.endsWith("Restore first."))
    }

    @Test fun `read-only and no-folder are told apart`() {
        val ro = BackupStatus.ReadOnly.line(zone, Locale.US)
        val none = BackupStatus.NoFolder.line(zone, Locale.US)
        assertTrue(ro, ro.contains("Choose the folder again"))
        assertTrue(none, none.contains("only on this phone"))
    }

    @Test fun `restore lines distinguish nothing-new from an empty copy`() {
        assertEquals("Restored from the folder: 2 added, 1 updated.", RestoreResult.Merged(2, 1, 9).line())
        assertEquals("Already up to date with the folder's copy (9 notes).", RestoreResult.Merged(0, 0, 9).line())
        assertEquals("The folder's copy is empty. Nothing was changed.", RestoreResult.Merged(0, 0, 0).line())
        assertEquals("There is no daily-notes.json in the folder yet.", RestoreResult.NoCopy.line())
    }
}

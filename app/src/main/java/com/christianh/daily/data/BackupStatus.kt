package com.christianh.daily.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Where the off-device copy of the notes stands (D-028), as Settings reports it.
 *
 * Kept free of Android types so the wording is tested off-device. Like the forecast
 * diagnostic, its job is to stop several different problems from wearing one face:
 * "no backup" can mean no folder, a folder the app may only read, a write that
 * failed, or a write that was refused on purpose.
 */
sealed interface BackupStatus {
    /** No folder chosen: the notes exist only in app storage. */
    data object NoFolder : BackupStatus

    /**
     * A folder is chosen but the app holds only read permission on it. Every install
     * that chose its folder before D-028 is in this state until the folder is chosen
     * again, because only read was ever persisted.
     */
    data object ReadOnly : BackupStatus

    /** Writable, but nothing has been written to this folder yet. */
    data object Pending : BackupStatus

    data class Saved(val atMillis: Long, val count: Int) : BackupStatus

    /** The first write to this folder was refused: its copy has notes this phone lacks. */
    data class Blocked(val wouldLose: Int) : BackupStatus

    data class Failed(val reason: String) : BackupStatus
}

/** What a restore did. */
sealed interface RestoreResult {
    data object NoFolder : RestoreResult
    data object NoCopy : RestoreResult
    data object Unreadable : RestoreResult
    data class Merged(val added: Int, val updated: Int, val inCopy: Int) : RestoreResult
}

private fun notes(n: Int) = if (n == 1) "1 note" else "$n notes"

fun BackupStatus.line(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): String = when (this) {
    BackupStatus.NoFolder ->
        "No folder chosen. Your notes exist only on this phone."
    BackupStatus.ReadOnly ->
        "Daily can read this folder but not write to it. Choose the folder again to turn the backup on."
    BackupStatus.Pending ->
        "No copy yet. One is written the next time you write a note."
    is BackupStatus.Saved -> {
        val at = DateTimeFormatter.ofPattern("MMM d, HH:mm", locale)
            .format(Instant.ofEpochMilli(atMillis).atZone(zone))
        "Backed up $at · ${notes(count)}"
    }
    is BackupStatus.Blocked ->
        "Not backed up: the folder's copy has ${notes(wouldLose)} this phone doesn't. Restore first."
    is BackupStatus.Failed ->
        "Backup failed: $reason"
}

fun RestoreResult.line(): String = when (this) {
    RestoreResult.NoFolder -> "Choose a folder first."
    RestoreResult.NoCopy -> "There is no ${NoteBackupFile.NAME} in the folder yet."
    RestoreResult.Unreadable -> "${NoteBackupFile.NAME} could not be read. Nothing was changed."
    is RestoreResult.Merged -> when {
        added + updated > 0 ->
            "Restored from the folder: $added added, $updated updated."
        inCopy == 0 ->
            "The folder's copy is empty. Nothing was changed."
        else ->
            "Already up to date with the folder's copy (${notes(inCopy)})."
    }
}

/** The file name, here rather than on [NoteBackup] so this file stays Android-free. */
object NoteBackupFile {
    const val NAME = "daily-notes.json"
}

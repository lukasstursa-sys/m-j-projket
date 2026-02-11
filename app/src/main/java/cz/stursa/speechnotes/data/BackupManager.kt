package cz.stursa.speechnotes.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(private val context: Context) {

    data class BackupResult(
        val success: Boolean,
        val message: String,
        val filePath: String = ""
    )

    fun exportDatabase(): BackupResult {
        return try {
            val dbPath = AppDatabase.getDatabasePath(context)
            val dbFile = File(dbPath)
            if (!dbFile.exists()) {
                return BackupResult(false, "Databáze neexistuje")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                .format(Date())
            val backupDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SpeechNotes_Backup"
            )
            backupDir.mkdirs()

            val backupFile = File(backupDir, "speech_notes_$timestamp.db")

            // Also copy WAL and SHM files for consistency
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            val walFile = File("$dbPath-wal")
            if (walFile.exists()) {
                FileInputStream(walFile).use { input ->
                    FileOutputStream(File(backupDir, "speech_notes_$timestamp.db-wal")).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            BackupResult(true, "Záloha uložena", backupFile.absolutePath)
        } catch (e: Exception) {
            BackupResult(false, "Chyba zálohy: ${e.message}")
        }
    }

    fun importDatabase(backupFilePath: String): BackupResult {
        return try {
            val backupFile = File(backupFilePath)
            if (!backupFile.exists()) {
                return BackupResult(false, "Záložní soubor neexistuje")
            }

            val dbPath = AppDatabase.getDatabasePath(context)
            val dbFile = File(dbPath)

            // Close database before copying
            AppDatabase.getInstance(context).close()

            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Also copy WAL if present
            val walBackup = File("${backupFilePath}-wal")
            if (walBackup.exists()) {
                FileInputStream(walBackup).use { input ->
                    FileOutputStream(File("$dbPath-wal")).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            BackupResult(true, "Databáze obnovena. Restartujte aplikaci.")
        } catch (e: Exception) {
            BackupResult(false, "Chyba obnovy: ${e.message}")
        }
    }

    fun listBackups(): List<File> {
        val backupDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SpeechNotes_Backup"
        )
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { file ->
            file.name.endsWith(".db") && file.name.startsWith("speech_notes_")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

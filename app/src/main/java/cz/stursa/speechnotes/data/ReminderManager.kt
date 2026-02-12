package cz.stursa.speechnotes.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cz.stursa.speechnotes.NoteDetailActivity
import cz.stursa.speechnotes.R

class ReminderManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "note_reminders"
        const val EXTRA_NOTE_ID = "reminder_note_id"
        const val EXTRA_NOTE_TITLE = "reminder_note_title"
        const val EXTRA_NOTE_CONTENT = "reminder_note_content"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pripominky poznamek",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Upozorneni na pripominky poznamek"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun scheduleReminder(noteId: Long, noteTitle: String, noteContent: String, triggerTime: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_NOTE_TITLE, noteTitle)
            putExtra(EXTRA_NOTE_CONTENT, noteContent.take(100))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    fun cancelReminder(noteId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderManager.EXTRA_NOTE_ID, -1)
        val title = intent.getStringExtra(ReminderManager.EXTRA_NOTE_TITLE) ?: "Pripominka"
        val content = intent.getStringExtra(ReminderManager.EXTRA_NOTE_CONTENT) ?: ""

        val openIntent = Intent(context, NoteDetailActivity::class.java).apply {
            putExtra(NoteDetailActivity.EXTRA_NOTE_ID, noteId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(noteId.toInt(), notification)
    }
}

package br.com.willendary.designacoesjw.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.willendary.designacoesjw.MainActivity
import br.com.willendary.designacoesjw.R
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege

object MeetingReminderHelper {

    private const val CHANNEL_ID = "meeting_reminders"
    private const val CHANNEL_NAME = "Lembretes de Reuniões"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificações e lembretes com as designações da reunião"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun notifyMeeting(
        context: Context,
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): Boolean {
        createChannel(context)

        val managerCompat = NotificationManagerCompat.from(context)
        if (!managerCompat.areNotificationsEnabled()) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            meeting.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val assignmentsLines = if (meeting.assignments.isEmpty()) {
            "Sem designações cadastradas."
        } else {
            meeting.assignments.joinToString("\n") { a ->
                val p = privileges.find { it.id == a.privilegeId }?.name ?: "Privilégio"
                val b = brothers.find { it.id == a.brotherId }?.name ?: "Irmão"
                "• $p: $b"
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Lembrete: Reunião ${meeting.date} (${meeting.type})")
            .setContentText("${meeting.assignments.size} designações programadas.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Designações para ${meeting.date}:\n\n$assignmentsLines")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        return try {
            managerCompat.notify(meeting.id.toInt(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

package org.archuser.fuelmath

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object MaintenanceReminderScheduler {
    private const val CHANNEL_ID = "maintenance_reminders"
    private const val CHANNEL_NAME = "Maintenance reminders"
    private const val NOTIFICATION_ID = 4201
    private const val REMINDER_REQUEST_CODE = 4202
    private const val OPEN_APP_REQUEST_CODE = 4203

    fun sync(context: Context, data: FuelMathData) {
        if (data.settings.maintenanceRemindersEnabled) {
            scheduleDaily(context)
        } else {
            cancel(context)
        }
    }

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextNineAmMillis(),
            AlarmManager.INTERVAL_DAY,
            reminderPendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(reminderPendingIntent(context))
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showDueNotification(context: Context, snapshot: MaintenanceReminderSnapshot) {
        if (!notificationsAllowed(context)) return
        ensureNotificationChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val openIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.message)
            .setStyle(Notification.BigTextStyle().bigText(snapshot.message))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily reminders for due and overdue maintenance."
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun reminderPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            Intent(context, MaintenanceReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextNineAmMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(9, 0))
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.atZone(zone).toInstant().toEpochMilli()
    }
}

class MaintenanceReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val data = try {
            FuelRepository(context).loadData()
        } catch (_: RuntimeException) {
            return
        }
        if (!data.settings.maintenanceRemindersEnabled) return
        val snapshot = FuelCalculator.buildReminderSnapshot(data) ?: return
        MaintenanceReminderScheduler.showDueNotification(context, snapshot)
    }
}

class MaintenanceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val data = try {
            FuelRepository(context).loadData()
        } catch (_: RuntimeException) {
            return
        }
        MaintenanceReminderScheduler.sync(context, data)
    }
}

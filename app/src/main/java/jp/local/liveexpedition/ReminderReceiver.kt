package jp.local.liveexpedition

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    private fun pending(context: Context, id: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java)
                .setAction("jp.local.liveexpedition.REMIND.$id"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id))
    }

    fun set(context: Context, id: String, at: LocalDateTime) {
        val time = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        require(time > System.currentTimeMillis()) { "通知日時は未来を指定してください" }
        require(
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        ) {
            "通知権限を許可してください"
        }
        // Inexact alarm: no exact-alarm permission and no persistent service.
        context
            .getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending(context, id))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.startsWith("jp.local.liveexpedition.REMIND.") != true) return
        if (
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("private", "予定のお知らせ", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        manager.notify(
            intent.action.hashCode(),
            Notification.Builder(context, "private")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("予定のお知らせ")
                .setContentText("アプリを開いて確認してください")
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

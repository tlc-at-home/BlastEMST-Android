package com.example.blastemst

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "blast_emst_reminder_channel"
        const val NOTIFICATION_ID = 101
    }

    override suspend fun doWork(): Result {
        Log.d("ReminderWorker", "Worker triggered, checking conditions...")

        try {
            // Get the user's goal from settings
            val weeklyGoalStr = RustBridge.getSetting("goal_sessions_per_week", "5")
            val weeklyGoal = weeklyGoalStr.toIntOrNull() ?: 5

            // Get the number of sessions completed this week
            val sessionsThisWeek = RustBridge.getSessionCountForWeek()

            Log.d("ReminderWorker", "Sessions this week: $sessionsThisWeek, Goal: $weeklyGoal")

            // Only show the notification if the goal has not been met
            if (sessionsThisWeek < weeklyGoal) {
                Log.d("ReminderWorker", "Goal not met. Showing notification.")
                showNotification()
            } else {
                Log.d("ReminderWorker", "Goal has been met. No notification will be shown.")
            }
        } catch (e: Exception) {
            Log.e("ReminderWorker", "Error executing work: ${e.message}")
            return Result.failure()
        }

        return Result.success()
    }

    private fun showNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Time for your session!")
            .setContentText("Don't forget to complete your EMST exercise.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(applicationContext)) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, notification)
                Log.d("ReminderWorker", "Notification posted successfully.")
            } else {
                Log.w("ReminderWorker", "POST_NOTIFICATIONS permission not granted. Cannot show reminder.")
            }
        }
    }
}
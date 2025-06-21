package com.example.blastemst

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ReminderManager(private val context: Context) {

    companion object {
        private const val REMINDER_WORK_TAG = "blast_emst_inactivity_reminder"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Schedules a single reminder check to occur 24 hours from now.
     * Any previously scheduled check with the same unique tag will be replaced.
     */
    fun scheduleInactivityCheck(delayInMinutes: Long) {
        // This function now takes a specific delay in minutes
        if (delayInMinutes <= 0) {
            Log.w("ReminderManager", "Attempted to schedule a check with zero or negative delay. Aborting.")
            return
        }

        val workManager = WorkManager.getInstance(context)

        val reminderRequest =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delayInMinutes, TimeUnit.MINUTES)
                .addTag(REMINDER_WORK_TAG)
                .build()

        workManager.enqueueUniqueWork(
            REMINDER_WORK_TAG,
            ExistingWorkPolicy.REPLACE,
            reminderRequest
        )
        Log.d("ReminderManager", "Inactivity check scheduled for $delayInMinutes minutes from now.")
    }

    /**
     * Cancels any pending inactivity check.
     */
    fun cancelInactiveCheck() {
        WorkManager.getInstance(context).cancelUniqueWork(REMINDER_WORK_TAG)
        Log.d("ReminderManager", "Pending inactivity check has been cancelled.")
    }

    // The createNotificationChannel function remains the same as before
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "EMST Reminders"
            val descriptionText = "Notifications to remind you to do your EMST sessions"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(ReminderWorker.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
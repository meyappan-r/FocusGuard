package com.focusguard.core.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusguard.R
import com.focusguard.core.scheduling.SessionScheduler
import com.focusguard.core.services.BlockerAccessibilityService
import com.focusguard.data.local.SessionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // Read the DataStore off the main thread; goAsync keeps the receiver alive until we finish.
            val pending = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sessions = SessionDataStore(appContext).sessionsFlow.firstOrNull() ?: emptyList()

                    // 1. Reschedule all saved sessions entirely statelessly
                    val scheduler = SessionScheduler(appContext)
                    sessions.forEach { session -> scheduler.scheduleSession(session) }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to restore session alarms", e)
                }

                try {
                    // 2. Accessibility Service verification
                    if (!isAccessibilityServiceEnabled(appContext)) {
                        sendAccessibilityNotification(appContext)
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to check accessibility service", e)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceClass = BlockerAccessibilityService::class.java
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val componentName = android.content.ComponentName.unflattenFromString(componentNameString)
            if (componentName != null && componentName.className == serviceClass.name) {
                return true
            }
        }
        return false
    }

    private fun sendAccessibilityNotification(context: Context) {
        val channelId = "focusguard_system_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FocusGuard System Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("FocusGuard Disabled")
            .setContentText("Accessibility service is disabled. Tap to enable and resume app blocking.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        manager.notify(1001, builder.build())
    }
}

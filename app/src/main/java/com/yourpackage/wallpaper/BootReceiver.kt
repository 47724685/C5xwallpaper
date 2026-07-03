package com.yourpackage.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：系统启动后自动启动悬浮球 Service
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startService(Intent(context, FloatBallService::class.java))
        }
    }
}

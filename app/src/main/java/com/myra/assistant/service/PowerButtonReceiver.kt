package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerButtonReceiver : BroadcastReceiver() {
    private var lastScreenOffTime: Long = 0

    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        if (intent.action == Intent.ACTION_SCREEN_OFF) {
            lastScreenOffTime = now
        } else if (intent.action == Intent.ACTION_SCREEN_ON) {
            if (now - lastScreenOffTime < 600) {
                context.startService(Intent(context, MyraOverlayService::class.java))
            }
        }
    }
}
package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.myra.assistant.ui.main.MainActivity

class CallMonitorService : Service() {

    private lateinit var telephonyManager: TelephonyManager

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    val caller = resolveContactName(phoneNumber) ?: "Unknown"
                    val intent = Intent(this@CallMonitorService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("INCOMING_CALL", true)
                        putExtra("CALLER_NAME", caller)
                    }
                    startActivity(intent)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    sendBroadcast(Intent("com.myra.CALL_ENDED"))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("call_monitor", "Call Monitor", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        startForeground(101, Notification.Builder(this, "call_monitor").setContentTitle("Call Monitor Running").build())

        telephonyManager = getSystemService(TelephonyManager::class.java)
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveContactName(number: String?): String? {
        if (number.isNullOrEmpty()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }
}
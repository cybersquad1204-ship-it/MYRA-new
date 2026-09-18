package com.myra.assistant

import android.app.Application
import android.content.Intent
import android.os.Process
import kotlin.system.exitProcess

class MyraApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                throwable.printStackTrace(pw)
                val trace = sw.toString()

                val intent = Intent(applicationContext, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash_trace", trace)
                }
                startActivity(intent)

                // Give the new activity time to actually launch before killing this process
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Process.killProcess(Process.myPid())
                    exitProcess(1)
                }, 1000)
            } catch (e: Exception) {
                defaultHandler?.uncaughtException(thread, throwable)
                Process.killProcess(Process.myPid())
                exitProcess(1)
            }
        }
    }
}

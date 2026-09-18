package com.myra.assistant

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra("crash_trace") ?: "Unknown crash (no stack trace captured)"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        val title = TextView(this).apply {
            text = "MYRA crashed — copy this and send it"
            textSize = 18f
            setPadding(0, 0, 0, 24)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val traceView = TextView(this).apply {
            text = trace
            textSize = 12f
            setTextIsSelectable(true)
        }
        scrollView.addView(traceView)

        val copyButton = Button(this).apply {
            text = "Copy crash log"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("MYRA crash log", trace)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        val closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { finishAffinity() }
        }

        root.addView(title)
        root.addView(scrollView)
        root.addView(copyButton)
        root.addView(closeButton)

        setContentView(root)
    }
}

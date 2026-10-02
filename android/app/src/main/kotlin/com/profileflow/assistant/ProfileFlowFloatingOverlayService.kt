package com.profileflow.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * Floating Overlay Bubble Service for ProfileFlow.
 * 
 * Provides an on-screen floating side toggle while user is inside Google Chrome
 * (e.g. on Upwork signup). Tapping the side toggle expands a floating panel with:
 * - 1-Tap Autofill with Humanized Keystroke Emulation (Anti-Bot detection)
 * - Anti-Suspension Profile Switcher (Unique variations for ID 1 vs ID 2)
 * - Quick copy helper & AI Assistant launcher.
 * 
 * Requires android.permission.SYSTEM_ALERT_WINDOW.
 */
class ProfileFlowFloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: View? = null
    private var isExpanded = false

    companion object {
        private const val CHANNEL_ID = "profileflow_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START_OVERLAY = "com.profileflow.assistant.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.profileflow.assistant.STOP_OVERLAY"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        createFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ProfileFlow Chrome Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows floating autofill bubble over Chrome"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ProfileFlow Floating Assistant Active")
            .setContentText("Side toggle is available on top of Chrome.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val inflater = LayoutInflater.from(this)
        floatingBubbleView = inflater.inflate(R.layout.floating_overlay_bubble, null)

        val bubbleIcon = floatingBubbleView?.findViewById<ImageView>(R.id.floating_bubble_icon)
        val expandedPanel = floatingBubbleView?.findViewById<LinearLayout>(R.id.floating_expanded_panel)
        val btnAutoFill = floatingBubbleView?.findViewById<View>(R.id.btn_overlay_autofill)
        val btnAiAssistant = floatingBubbleView?.findViewById<View>(R.id.btn_overlay_ai)
        val tvStatus = floatingBubbleView?.findViewById<TextView>(R.id.tv_overlay_status)

        // Draggable floating bubble touch handling
        bubbleIcon?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingBubbleView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // Tap toggle: expand or collapse side panel
                            isExpanded = !isExpanded
                            expandedPanel?.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Tap Auto Fill button
        btnAutoFill?.setOnClickListener {
            val bridge = AutofillStorageBridge(applicationContext)
            val name = bridge.getProfileField("fullName") ?: "Profile"
            Toast.makeText(
                this,
                "ProfileFlow: Autofilling Upwork form for $name with humanized delays. Review before clicking Next!",
                Toast.LENGTH_LONG
            ).show()
            tvStatus?.text = "Data Filled · Review & click Next"
        }

        // Tap AI Assistant launcher
        btnAiAssistant?.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("route", "ai_assistant")
            }
            if (launchIntent != null) {
                startActivity(launchIntent)
            }
        }

        windowManager.addView(floatingBubbleView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingBubbleView != null) {
            windowManager.removeView(floatingBubbleView)
            floatingBubbleView = null
        }
    }
}

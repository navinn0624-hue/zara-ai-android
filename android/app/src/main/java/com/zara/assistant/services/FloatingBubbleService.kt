package com.zara.assistant.services

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
import com.zara.assistant.R
import com.zara.assistant.overlay.FloatingOverlayManager

class FloatingBubbleService : Service() {

    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingBubble()
    }

    private fun createFloatingBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.layout_floating_bubble, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = 400
        }

        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(bubbleView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            // Tap on floating bubble: Launch Siri assistant immediately!
                            FloatingOverlayManager.show(applicationContext)
                        } else {
                            // Snap bubble to nearest screen edge (left or right)
                            val displayMetrics = resources.displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            layoutParams.x = if (layoutParams.x > screenWidth / 2) screenWidth - 140 else 10
                            windowManager?.updateViewLayout(bubbleView, layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(bubbleView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager?.removeView(bubbleView)
                bubbleView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

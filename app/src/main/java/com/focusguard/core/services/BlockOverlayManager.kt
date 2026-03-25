package com.focusguard.core.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.focusguard.R
import com.focusguard.data.local.SessionStorage

@SuppressLint("StaticFieldLeak")
object BlockOverlayManager {

    private var currentOverlayView: View? = null
    private var isShowing = false

    fun showOverlay(
        service: AccessibilityService,
        packageName: String,
        source: String
    ) {
        if (isShowing) return

        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(service)
        
        val overlayView = inflater.inflate(R.layout.block_overlay, null)
        
        // Setup UI
        val tvTitle = overlayView.findViewById<TextView>(R.id.tvBlockedTitle)
        val tvSub = overlayView.findViewById<TextView>(R.id.tvBlockedSubtitle)
        val ivIcon = overlayView.findViewById<ImageView>(R.id.ivBlockedIcon)
        val btnUnblock = overlayView.findViewById<Button>(R.id.btnUnblock)
        val btnExit = overlayView.findViewById<Button>(R.id.btnExit)

        val pm = service.packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }

        try {
            val icon = pm.getApplicationIcon(packageName)
            ivIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            // Ignore if icon not found
        }

        tvTitle.text = "$appName is blocked"
        
        if (source == "session") {
            tvSub.text = "This app is blocked during your active focus session."
        } else {
            tvSub.text = "You've reached your daily usage limit for $appName."
        }

        // Setup Buttons
        btnUnblock.setOnClickListener {
            removeOverlay(windowManager)
            if (source == "session") {
                SessionStorage(service).clearActiveSession()
            }
            val mainIntent = Intent(service, com.focusguard.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "blocks")
            }
            service.startActivity(mainIntent)
        }

        btnExit.setOnClickListener {
            removeOverlay(windowManager)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            service.startActivity(homeIntent)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.dimAmount = 0.6f

        // Must run on main thread
        Handler(Looper.getMainLooper()).post {
            try {
                windowManager.addView(overlayView, params)
                currentOverlayView = overlayView
                isShowing = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeOverlay(windowManager: WindowManager) {
        if (!isShowing) return
        Handler(Looper.getMainLooper()).post {
            try {
                currentOverlayView?.let { windowManager.removeView(it) }
                currentOverlayView = null
                isShowing = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

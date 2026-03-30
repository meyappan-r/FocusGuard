package com.focusguard.core.services

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.focusguard.R
import com.focusguard.data.local.SessionDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BlockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.block_overlay)

        val packageName = intent.getStringExtra("blocked_package") ?: return
        val source = intent.getStringExtra("block_source") ?: return
        val sessionName = intent.getStringExtra("session_name")

        val tvTitle = findViewById<TextView>(R.id.tvBlockedTitle)
        val tvSub = findViewById<TextView>(R.id.tvBlockedSubtitle)
        val ivIcon = findViewById<ImageView>(R.id.ivBlockedIcon)
        val btnUnblock = findViewById<Button>(R.id.btnUnblock)
        val btnExit = findViewById<Button>(R.id.btnExit)

        val pm = packageManager
        val appName = try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
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
            tvSub.text = if (sessionName != null) {
                "Blocked by: $sessionName"
            } else {
                "This app is blocked during your active focus session."
            }
        } else {
            tvSub.text = "You've reached your daily usage limit for $appName."
        }

        btnUnblock.setOnClickListener {
            val options = arrayOf("2 minutes", "5 minutes", "10 minutes")
            val values = intArrayOf(2, 5, 10)
            
            AlertDialog.Builder(this)
                .setTitle("Select Break Duration")
                .setItems(options) { dialog, which ->
                    val breakMillis = values[which] * 60 * 1000L
                    val expiry = System.currentTimeMillis() + breakMillis
                    CoroutineScope(Dispatchers.IO).launch {
                        SessionDataStore(this@BlockActivity).setBreakExpiry(expiry)
                    }
                    finish()
                }
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        btnExit.setOnClickListener {
            goHomeAndFinish()
        }
    }

    private fun goHomeAndFinish() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onBackPressed() {
        goHomeAndFinish()
    }
}

package com.focusguard.ui.blocks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusguard.data.local.SessionStorage

class BlockActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName   = intent.getStringExtra("BLOCKED_APP_NAME") ?: "This App"
        val pkgName   = intent.getStringExtra("BLOCKED_PACKAGE") ?: ""
        val source    = intent.getStringExtra("BLOCK_SOURCE") ?: "session"
        val storage   = SessionStorage(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BlockBottomSheet(
                    appName = appName,
                    source  = source,
                    onStopSession = {
                        storage.clearActiveSession()
                        finish()
                    },
                    onOpenFocusGuard = {
                        val main = Intent(this, com.focusguard.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("navigate_to", "blocks")
                        }
                        startActivity(main)
                        finish()
                    },
                    onGoHome = {
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(home)
                        finish()
                    }
                )
            }
        }
    }

    // Prevent back button from returning to the blocked app — send home instead
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }
}

@Composable
fun BlockBottomSheet(
    appName: String,
    source: String,
    onStopSession: () -> Unit,
    onOpenFocusGuard: () -> Unit,
    onGoHome: () -> Unit
) {
    // Full screen transparent scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        // The card that slides up from the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Lock icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Blocked",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "$appName is blocked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            val reason = if (source == "session")
                "This app is blocked during your active focus session."
            else
                "You've reached your daily usage limit for $appName."

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (source == "session") {
                Button(
                    onClick = onStopSession,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Stop Session & Unblock", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onOpenFocusGuard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Unblock manually")
                }

                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Button(
                    onClick = onOpenFocusGuard,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Unblock manually", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            TextButton(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Go Home", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

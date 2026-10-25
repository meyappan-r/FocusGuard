package com.focusguard.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Prominent disclosure shown before the user is sent to Android's Accessibility settings
 * (required by Google Play for apps that are not accessibility tools). It must say what is
 * accessed, why, and how data is used, and ask for explicit consent.
 */
@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Accessibility access", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "FocusGuard uses Android's Accessibility Service for one purpose: to notice which app " +
                        "has just opened, so it can show its block screen when that app is blocked by one of " +
                        "your focus sessions or daily limits.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Section("What it reads")
                Text(
                    "Only the name of the app that comes to the foreground. It does not read what is on your " +
                        "screen, what you type, passwords, messages or notifications.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Section("What it does")
                Text(
                    "When a blocked app opens, FocusGuard shows its block screen over it. It never taps, types " +
                        "or changes settings for you, and it never stops you from disabling or uninstalling FocusGuard.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Section("Your data")
                Text(
                    "Nothing is collected, sent or shared. Everything stays on this device, and FocusGuard has " +
                        "no internet access.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "You can turn this off at any time in Android Settings > Accessibility. Without it, " +
                        "blocking does not work.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) { Text("Agree and continue", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("Not now") }
        }
    )
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(2.dp))
}

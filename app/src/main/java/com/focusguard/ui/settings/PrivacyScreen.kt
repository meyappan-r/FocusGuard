package com.focusguard.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.ui.theme.FGColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, isDarkMode: Boolean = false) {
    val bg = MaterialTheme.colorScheme.background
    val hdrBg = MaterialTheme.colorScheme.surface
    val txtPri = MaterialTheme.colorScheme.onSurface
    val txtSec = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold, color = txtPri) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = txtPri)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = hdrBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("FocusGuard Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = txtPri)
            Spacer(modifier = Modifier.height(24.dp))

            PrivacySection("1. Introduction", 
                "FocusGuard is a digital wellbeing application designed to help users reduce distractions and improve focus by monitoring app usage and enabling scheduled blocking sessions.\n\nYour privacy is important. This application is designed with a privacy-first approach, ensuring that personal data remains under your control.", txtPri, txtSec)

            PrivacySection("2. Data Collection", 
                "FocusGuard does NOT collect, store, or transmit personal data to any external servers.\n\nAll app usage statistics, session schedules, and user preferences are stored locally on your device only.\n\nWe do not collect:\n• Personal identity information\n• Contacts\n• Messages\n• Media files\n• Location data\n• Account information", txtPri, txtSec)

            PrivacySection("3. Usage Data Access", 
                "FocusGuard requires Usage Access permission to:\n• Measure screen-on-time for apps\n• Identify distracting applications\n• Provide usage insights\n• Enable scheduled focus sessions\n\nThis data is processed entirely on-device and is never shared externally.", txtPri, txtSec)

            PrivacySection("4. Accessibility Permission", 
                "FocusGuard uses Accessibility Service strictly for:\n• Detecting when blocked apps are opened\n• Displaying blocking overlay screens\n• Enforcing focus sessions\n\nAccessibility data is:\n• Not recorded\n• Not transmitted\n• Not stored outside device\n• Not used for analytics\n\nFocusGuard does not read typed content, passwords, or personal messages.", txtPri, txtSec)

            PrivacySection("5. Overlay Permission", 
                "Overlay permission is required to:\n• Display blocking interface when a restricted app is launched\n• Show focus session reminders\n\nThis overlay is used only for focus enforcement features.", txtPri, txtSec)

            PrivacySection("6. Background Execution", 
                "FocusGuard may run background services to:\n• Maintain active focus sessions\n• Ensure reliable scheduling\n• Prevent session bypass\n\nThese services do not collect or transmit personal data.", txtPri, txtSec)

            PrivacySection("7. Data Security", 
                "Since all data remains on-device:\n• No cloud storage is used\n• No third-party analytics SDKs are integrated\n• No advertising trackers are present\n\nUser data remains fully under user control.", txtPri, txtSec)

            PrivacySection("8. User Control", 
                "Users can:\n• Revoke permissions anytime via system settings\n• Clear all stored data by uninstalling the app\n• Disable focus sessions at will", txtPri, txtSec)

            PrivacySection("9. Children’s Privacy", 
                "FocusGuard does not knowingly collect personal data from children.\nAll functionality operates locally on the device.", txtPri, txtSec)

            PrivacySection("10. Changes to Privacy Policy", 
                "Future updates may modify this policy to reflect:\n• Feature changes\n• Regulatory requirements\n• Platform policy updates\n\nUsers are encouraged to review this policy periodically.", txtPri, txtSec)

            PrivacySection("11. Contact", 
                "For privacy-related concerns or inquiries:\n• Developer: Mey\n• App: FocusGuard\n• Contact Email: meyappanrmn53953@gmail.com", txtPri, txtSec)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PrivacySection(title: String, body: String, txtPri: androidx.compose.ui.graphics.Color, txtSec: androidx.compose.ui.graphics.Color) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = txtPri)
    Spacer(modifier = Modifier.height(8.dp))
    Text(body, fontSize = 15.sp, color = txtSec, lineHeight = 22.sp)
    Spacer(modifier = Modifier.height(24.dp))
}

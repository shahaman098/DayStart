package com.loudwake.app.billing

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext

@Composable
fun PaywallDialog(
    onDismiss: () -> Unit,
    onPurchased: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DayStart Premium") },
        text = {
            Text(
                "Free tier: one alarm with Math Wake Gate.\n\n" +
                    "Premium unlocks unlimited alarms and every Wake Gate mode " +
                    "(shake, steps, phrase, memory, gauntlet, strict). " +
                    "Purchases run through RevenueCat.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (activity == null) {
                        onDismiss()
                        return@TextButton
                    }
                    DayStartBilling.purchaseDefaultPackage(activity) { ok ->
                        if (ok) onPurchased()
                    }
                },
                enabled = DayStartBilling.billingReady()
            ) {
                Text(if (DayStartBilling.billingReady()) "Subscribe" else "Billing not configured")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

package com.wayfare.ride.ui.payments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.theme.LocalWayfareColors

@Composable
fun PaymentReturnScreen(
    result: PaymentVerificationResult,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    BackHandler { onDone() }

    val title = when (result.state) {
        PaymentVerificationState.CHECKING -> "Verifying Paystack payment"
        PaymentVerificationState.AUTHORIZED -> "Fare reserved"
        PaymentVerificationState.PAID -> "Payment confirmed"
        PaymentVerificationState.PENDING -> "Confirmation pending"
        PaymentVerificationState.FAILED -> "Authorization not confirmed"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background.copy(alpha = 0.96f))
            .padding(20.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            border = BorderStroke(1.dp, colorScheme.outline),
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(22.dp)
            ) {
                when (result.state) {
                    PaymentVerificationState.CHECKING -> {
                        CircularProgressIndicator(modifier = Modifier.size(38.dp), color = colorScheme.primary)
                    }
                    PaymentVerificationState.AUTHORIZED, PaymentVerificationState.PAID -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = wayfareColors.positive, modifier = Modifier.size(42.dp))
                    }
                    PaymentVerificationState.PENDING -> {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = wayfareColors.warning, modifier = Modifier.size(42.dp))
                    }
                    PaymentVerificationState.FAILED -> {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = colorScheme.error, modifier = Modifier.size(42.dp))
                    }
                }

                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(result.message, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)

                MonoBadge(text = "${result.reference} · ${LaunchPolicy.formatZarMinor(result.amountMinor)}")

                Button(
                    onClick = onDone,
                    enabled = result.state != PaymentVerificationState.CHECKING,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("payment_return_done_btn")
                ) {
                    Text("Return to Vhaya", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

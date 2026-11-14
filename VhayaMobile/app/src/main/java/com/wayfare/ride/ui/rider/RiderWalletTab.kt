package com.wayfare.ride.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private enum class WalletActivityFilter(val label: String) {
    ALL("All"),
    CREDITS("Top-ups"),
    DEBITS("Rides")
}

@Composable
fun RiderWalletTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var hideBalance by rememberSaveable { mutableStateOf(false) }
    var showCustomTopUp by rememberSaveable { mutableStateOf(false) }
    var customAmountText by rememberSaveable { mutableStateOf("150") }
    var activityFilter by rememberSaveable { mutableStateOf(WalletActivityFilter.ALL) }

    val totalInMinor = remember(uiState.walletTransactions) {
        uiState.walletTransactions.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    }
    val totalOutMinor = remember(uiState.walletTransactions) {
        uiState.walletTransactions.filter { it.amountMinor < 0 }.sumOf { abs(it.amountMinor) }
    }

    val filteredTransactions = remember(uiState.walletTransactions, activityFilter) {
        when (activityFilter) {
            WalletActivityFilter.ALL -> uiState.walletTransactions
            WalletActivityFilter.CREDITS -> uiState.walletTransactions.filter { it.amountMinor >= 0 }
            WalletActivityFilter.DEBITS -> uiState.walletTransactions.filter { it.amountMinor < 0 }
        }
    }

    val cardHolderName = remember(uiState.currentSession?.displayName) {
        uiState.currentSession?.displayName?.trim()?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            ?: "VHAYA RIDER"
    }
    val maskedSuffix = remember(uiState.activeUserId) {
        uiState.activeUserId.filter { it.isLetterOrDigit() }.takeLast(4).uppercase(Locale.ROOT).ifBlank { "8420" }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Wallet",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ZAR Instant Ride Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = wayfareColors.brandHighlight,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Secured by Paystack",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Paystack",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Virtual Bank Card Hero
        item {
            BankWalletCard(
                balanceMinor = uiState.walletBalanceMinor,
                hideBalance = hideBalance,
                cardHolderName = cardHolderName,
                maskedSuffix = maskedSuffix,
                onToggleVisibility = { hideBalance = !hideBalance }
            )
        }

        // Quick Top-Up Strip + Custom Amount Drawer
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Top up funds",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Instant ZAR deposit",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(50.0, 100.0, 250.0).forEach { amount ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = wayfareColors.brandHighlight.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.22f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .tactilePressScale()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.initializeWalletTopUp(amount) }
                                    .testTag("wallet_topup_${amount.toInt()}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add R${amount.toInt()}",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "R${amount.toInt()}",
                                        fontFamily = WayfareMonoFamily,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (showCustomTopUp) colorScheme.primary else colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                1.dp,
                                if (showCustomTopUp) colorScheme.primary else colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .tactilePressScale()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showCustomTopUp = !showCustomTopUp }
                                .testTag("wallet_topup_custom")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (showCustomTopUp) colorScheme.onPrimary else colorScheme.onSurface
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = showCustomTopUp) {
                        val parsedCustom = customAmountText.toDoubleOrNull()
                        val validCustom = parsedCustom != null && parsedCustom >= 20.0
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = customAmountText,
                                    onValueChange = { input ->
                                        customAmountText = input.filter { it.isDigit() }.take(5)
                                    },
                                    label = { Text("Amount (min R 20)") },
                                    prefix = { Text("R ", fontFamily = WayfareMonoFamily, fontWeight = FontWeight.Bold) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f).testTag("wallet_custom_input")
                                )
                                Button(
                                    onClick = {
                                        parsedCustom?.takeIf { it >= 20.0 }?.let {
                                            viewModel.initializeWalletTopUp(it)
                                            showCustomTopUp = false
                                        }
                                    },
                                    enabled = validCustom,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.height(54.dp).testTag("wallet_custom_submit")
                                ) {
                                    Text("Deposit", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Money In / Money Out Banking Summary Tiles
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CashflowStatTile(
                    title = "Money in",
                    amountText = if (hideBalance) "R ••••" else LaunchPolicy.formatZarMinor(totalInMinor),
                    icon = Icons.AutoMirrored.Filled.CallReceived,
                    accentColor = wayfareColors.positive,
                    modifier = Modifier.weight(1f)
                )
                CashflowStatTile(
                    title = "Money out",
                    amountText = if (hideBalance) "R ••••" else LaunchPolicy.formatZarMinor(totalOutMinor),
                    icon = Icons.AutoMirrored.Filled.CallMade,
                    accentColor = wayfareColors.crimsonAccent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Statement Header & Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Statement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredTransactions.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalletActivityFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activityFilter == filter,
                            onClick = { activityFilter = filter },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.brandHighlight,
                                selectedLabelColor = colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("wallet_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredTransactions.isEmpty()) {
            item {
                WayfareEmptyState(
                    icon = Icons.Filled.AccountBalance,
                    title = if (uiState.walletTransactions.isEmpty()) "No wallet activity yet" else "No matching entries",
                    message = if (uiState.walletTransactions.isEmpty()) {
                        "Top up your wallet or complete a ride to see transactions here."
                    } else {
                        "Choose another filter to see your wallet activity."
                    },
                    modifier = Modifier.testTag("wallet_empty_state")
                )
            }
        }

        items(filteredTransactions, key = { it.id }) { tx ->
            BankStatementRow(tx = tx, hideBalance = hideBalance)
        }
    }
}

@Composable
private fun BankWalletCard(
    balanceMinor: Long,
    hideBalance: Boolean,
    cardHolderName: String,
    maskedSuffix: String,
    onToggleVisibility: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val cardStart = wayfareColors.walletCardStart
    val cardEnd = wayfareColors.walletCardEnd
    val onCardText = wayfareColors.walletOnCard
    val chipGold = wayfareColors.warning
    val shimmerHighlight = wayfareColors.walletAccent

    val formattedFull = remember(balanceMinor) { LaunchPolicy.formatZarMinor(balanceMinor) }
    val (wholePart, centsPart) = remember(balanceMinor) {
        val absMinor = abs(balanceMinor)
        val whole = absMinor / 100L
        val cents = absMinor % 100L
        val grouped = NumberFormat.getIntegerInstance(Locale("en", "ZA")).format(whole)
        val sign = if (balanceMinor < 0L) "-" else ""
        "${sign}$grouped" to ".%02d".format(Locale.US, cents)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, wayfareColors.walletCardBorder.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(cardStart, cardEnd)
                    )
                )
        ) {
            // Subtle banking card geometric arcs + metallic EMV chip + contactless waves
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = shimmerHighlight.copy(alpha = 0.12f),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * 0.92f, size.height * 0.14f)
                )
                drawCircle(
                    color = onCardText.copy(alpha = 0.06f),
                    radius = size.width * 0.34f,
                    center = Offset(size.width * 0.12f, size.height * 0.98f)
                )
                drawCircle(
                    color = shimmerHighlight.copy(alpha = 0.16f),
                    radius = size.width * 0.56f,
                    center = Offset(size.width * 0.92f, size.height * 0.14f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                // Card Top Header: EMV Chip + Contactless + Brand + Eye Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Metallic EMV Chip
                        Canvas(modifier = Modifier.size(width = 38.dp, height = 28.dp)) {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        chipGold.copy(alpha = 0.95f),
                                        onCardText.copy(alpha = 0.85f),
                                        chipGold.copy(alpha = 0.85f)
                                    )
                                ),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            val lineColor = cardEnd.copy(alpha = 0.45f)
                            drawLine(
                                color = lineColor,
                                start = Offset(0f, size.height * 0.5f),
                                end = Offset(size.width, size.height * 0.5f),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(size.width * 0.35f, 0f),
                                end = Offset(size.width * 0.35f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = lineColor,
                                start = Offset(size.width * 0.65f, 0f),
                                end = Offset(size.width * 0.65f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        // Contactless NFC arcs
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val center = Offset(2.dp.toPx(), size.height / 2f)
                            listOf(5.dp, 9.dp, 13.dp).forEach { r ->
                                val rad = r.toPx()
                                drawArc(
                                    color = onCardText.copy(alpha = 0.72f),
                                    startAngle = -42f,
                                    sweepAngle = 84f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - rad, center.y - rad),
                                    size = Size(rad * 2f, rad * 2f),
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "VHAYA PAY",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp,
                                color = onCardText
                            )
                            Text(
                                text = "ZAR DEBIT WALLET",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.8.sp,
                                color = onCardText.copy(alpha = 0.68f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleVisibility,
                        modifier = Modifier.size(48.dp).testTag("wallet_toggle_visibility")
                    ) {
                        Icon(
                            imageVector = if (hideBalance) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (hideBalance) "Show balance" else "Hide balance",
                            tint = onCardText.copy(alpha = 0.9f)
                        )
                    }
                }

                // Hero Currency Balance with Split Rand & Cents
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Available balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = onCardText.copy(alpha = 0.76f)
                    )
                    if (hideBalance) {
                        Text(
                            text = "R ••••••",
                            fontFamily = WayfareMonoFamily,
                            fontSize = 36.sp,
                            lineHeight = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = onCardText,
                            modifier = Modifier
                                .semantics { contentDescription = "Balance hidden" }
                                .testTag("wallet_balance_text")
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Available balance $formattedFull"
                                }
                                .testTag("wallet_balance_text")
                        ) {
                            Text(
                                text = "R",
                                fontFamily = WayfareMonoFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = shimmerHighlight,
                                modifier = Modifier.padding(top = 6.dp, end = 6.dp)
                            )
                            Text(
                                text = wholePart,
                                fontFamily = WayfareMonoFamily,
                                fontSize = 38.sp,
                                lineHeight = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                                color = onCardText
                            )
                            Text(
                                text = centsPart,
                                fontFamily = WayfareMonoFamily,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = onCardText.copy(alpha = 0.82f),
                                modifier = Modifier.padding(top = 5.dp, start = 2.dp)
                            )
                        }
                    }
                }

                // Cardholder & Masked Virtual Number Footer
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CARDHOLDER",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.9.sp,
                            color = onCardText.copy(alpha = 0.62f)
                        )
                        Text(
                            text = cardHolderName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = onCardText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "VIRTUAL ACCOUNT",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 0.9.sp,
                            color = onCardText.copy(alpha = 0.62f)
                        )
                        Text(
                            text = "•••• •••• $maskedSuffix",
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = onCardText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CashflowStatTile(
    title: String,
    amountText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        tonalElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = amountText,
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BankStatementRow(
    tx: WalletTransaction,
    hideBalance: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val isCredit = tx.amountMinor >= 0L
    val iconColor = if (isCredit) wayfareColors.positive else colorScheme.primary
    val txIcon = if (isCredit) Icons.AutoMirrored.Filled.CallReceived else Icons.Filled.DirectionsCar
    val signedAmount = (if (isCredit) "+" else "") + LaunchPolicy.formatZarMinor(tx.amountMinor)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = txIcon,
                        contentDescription = if (isCredit) "Wallet credit" else "Ride debit",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tx.reference} · ${formatShortTime(tx.createdAt)}",
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (hideBalance) "R •••" else signedAmount,
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCredit) wayfareColors.positive else colorScheme.onSurface
                )
                Text(
                    text = if (isCredit) "Credit" else "Settled",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

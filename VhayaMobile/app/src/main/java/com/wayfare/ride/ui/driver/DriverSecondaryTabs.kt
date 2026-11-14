package com.wayfare.ride.ui.driver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
fun DriverProfileTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier,
    topContentPaddingDp: Dp = 78.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val driverApp = uiState.currentDriverApp ?: DriverApplication(
        userId = uiState.activeUserId,
        displayName = uiState.currentSession?.displayName.orEmpty().ifBlank { "Vhaya Driver Applicant" },
        email = uiState.currentSession?.email
    )

    var step by rememberSaveable { mutableIntStateOf(0) }
    val currentStep = step.coerceIn(0, 2)
    var phone by remember(driverApp.userId, driverApp.contactPhone) { mutableStateOf(driverApp.contactPhone) }
    var city by remember(driverApp.userId, driverApp.city) { mutableStateOf(driverApp.city) }
    var vehicle by remember(driverApp.userId, driverApp.vehicle) { mutableStateOf(driverApp.vehicle) }
    var vehicleColor by remember(driverApp.userId, driverApp.vehicleColor) { mutableStateOf(driverApp.vehicleColor) }
    var registration by remember(driverApp.userId, driverApp.registration) { mutableStateOf(driverApp.registration) }
    var licenceExpiry by remember(driverApp.userId, driverApp.licenceExpiry) { mutableStateOf(driverApp.licenceExpiry) }
    var operatingRef by remember(driverApp.userId, driverApp.operatingLicenceReference) { mutableStateOf(driverApp.operatingLicenceReference) }
    var consent by remember(driverApp.userId, driverApp.documentsConsent) { mutableStateOf(driverApp.documentsConsent) }
    var copiedEmail by remember { mutableStateOf(false) }

    val draftSnapshot = driverApp.copy(
        contactPhone = phone,
        city = city,
        vehicle = vehicle,
        vehicleColor = vehicleColor,
        registration = registration,
        licenceExpiry = licenceExpiry,
        operatingLicenceReference = operatingRef,
        documentsConsent = consent
    )

    val steps = listOf("1. Contact", "2. Vehicle", "3. Review")
    val vehicleSuggestions = remember(vehicle) { LaunchPolicy.searchVehicles(vehicle, limit = 6) }

    LazyColumn(
        contentPadding = PaddingValues(top = topContentPaddingDp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = driverApp.displayName.ifBlank { "Driver Onboarding" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(driverApp.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
                StatusChip(
                    label = driverApp.verificationStatus.replaceFirstChar { it.uppercase() },
                    containerColor = if (driverApp.verificationStatus == "approved") wayfareColors.brandHighlight else colorScheme.secondaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                )
            }
        }

        driverApp.staffNote?.takeIf { it.isNotBlank() }?.let { note ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Review note: $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Streamlined 3-step Interest & Review Stepper
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                steps.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = currentStep == idx,
                        onClick = {
                            viewModel.clearDriverOnboardingMessages()
                            step = idx
                        },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f).testTag("driver_step_$idx")
                    )
                }
            }
        }

        uiState.driverOnboardingError?.let { errorMsg ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().testTag("driver_onboarding_error")
                ) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        uiState.driverOnboardingNotice?.let { noticeMsg ->
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = wayfareColors.brandHighlight,
                    modifier = Modifier.fillMaxWidth().testTag("driver_onboarding_notice")
                ) {
                    Text(
                        text = noticeMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
                    if (uiState.driverOnboardingBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    when (currentStep) {
                        0 -> {
                            Text("Contact & operating city", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Phone number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("driver_phone_input")
                            )
                            OutlinedTextField(
                                value = city,
                                onValueChange = { city = it },
                                label = { Text("City") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("driver_city_input")
                            )
                        }
                        1 -> {
                            Text("Vehicle & licence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = vehicle,
                                onValueChange = { vehicle = it },
                                label = { Text("Make & model") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("driver_vehicle_input")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                vehicleSuggestions.take(3).forEach { suggestion ->
                                    FilterChip(
                                        selected = vehicle == suggestion,
                                        onClick = { vehicle = suggestion },
                                        label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = vehicleColor,
                                    onValueChange = { vehicleColor = it },
                                    label = { Text("Colour") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = registration,
                                    onValueChange = { registration = LaunchPolicy.formatSouthAfricanRegistration(it) },
                                    label = { Text("Plate") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("driver_plate_input")
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = licenceExpiry,
                                    onValueChange = { licenceExpiry = it },
                                    label = { Text("Licence expiry") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = operatingRef,
                                    onValueChange = { operatingRef = it },
                                    label = { Text("Operating ref") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        else -> {
                            Text("Review & email documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = listOf(vehicleColor, vehicle).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Vehicle not set" } +
                                    if (registration.isNotBlank()) " · ${LaunchPolicy.formatSouthAfricanRegistration(registration)}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "City: ${city.ifBlank { "Not set" }} · Phone: ${phone.ifBlank { "Not set" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = wayfareColors.brandHighlight.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth().testTag("driver_email_docs_card")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Email,
                                            contentDescription = "Email documents",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Send documents by email",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "Submit your interest below, then email clear photos or PDFs of your ID/passport, driver's licence, vehicle registration, and operating permit (if applicable) for manual review to:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = LaunchPolicy.DRIVER_DOCS_EMAIL,
                                        fontFamily = WayfareMonoFamily,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = {
                                                val subject = Uri.encode("Vhaya Driver Application - ${driverApp.displayName}")
                                                val body = Uri.encode(
                                                    "Name: ${driverApp.displayName}\n" +
                                                        "Phone: $phone\n" +
                                                        "City: $city\n" +
                                                        "Vehicle: $vehicleColor $vehicle ($registration)\n\n" +
                                                        "Attached documents: ID/Passport, Driver Licence, Vehicle Registration, Operating Permit."
                                                )
                                                val intent = Intent(
                                                    Intent.ACTION_SENDTO,
                                                    Uri.parse("mailto:${LaunchPolicy.DRIVER_DOCS_EMAIL}?subject=$subject&body=$body")
                                                )
                                                runCatching { context.startActivity(intent) }
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp).testTag("driver_open_email_btn")
                                        ) {
                                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Email docs", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(LaunchPolicy.DRIVER_DOCS_EMAIL))
                                                copiedEmail = true
                                            },
                                            modifier = Modifier.height(48.dp).testTag("driver_copy_email_btn")
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy email address", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (copiedEmail) "Copied" else "Copy")
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { consent = !consent }
                                    .testTag("driver_consent_row")
                            ) {
                                Checkbox(checked = consent, onCheckedChange = { consent = it })
                                Text(
                                    text = "I confirm my details are accurate and I will email my documents to ${LaunchPolicy.DRIVER_DOCS_EMAIL}.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { step = currentStep - 1 },
                                enabled = !uiState.driverOnboardingBusy,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Back")
                            }
                        }
                        if (currentStep < 2) {
                            Button(
                                onClick = {
                                    viewModel.saveDriverOnboarding(draftSnapshot, submit = false)
                                    step = currentStep + 1
                                },
                                enabled = !uiState.driverOnboardingBusy,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("Save & next")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.saveDriverOnboarding(draftSnapshot, submit = true) },
                                enabled = consent && !uiState.driverOnboardingBusy,
                                modifier = Modifier.weight(1f).height(48.dp).testTag("driver_submit_application")
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Submit application")
                            }
                        }
                    }
                }
            }
        }
    }
}

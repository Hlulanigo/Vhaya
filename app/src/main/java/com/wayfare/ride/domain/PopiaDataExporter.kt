package com.wayfare.ride.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PopiaRetentionItem(
    val category: String,
    val retentionPeriod: String,
    val legalBasis: String
)

@Serializable
data class PopiaExportBundle(
    val schemaVersion: String = "vhaya-popia-export-v1",
    val exportedAtIso: String,
    val jurisdiction: String = "South Africa (POPIA Act 4 of 2013)",
    val account: PopiaAccountSummary?,
    val privacyPreferences: PrivacyPreferences,
    val trips: List<RideTrip>,
    val savedDestinations: List<SavedDestination>,
    val trustedContacts: List<TrustedContact>,
    val walletTransactions: List<WalletTransaction>,
    val supportCases: List<SupportCase>
)

@Serializable
data class PopiaAccountSummary(
    val userId: String,
    val displayName: String,
    val email: String,
    val role: RideRole
)

object PopiaDataExporter {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val RETENTION_SCHEDULE: List<PopiaRetentionItem> = listOf(
        PopiaRetentionItem(
            category = "Trip receipts & fare records",
            retentionPeriod = "5 years",
            legalBasis = "SARS financial record-keeping & NLTA transport compliance"
        ),
        PopiaRetentionItem(
            category = "Live GPS coordinates & route telemetry",
            retentionPeriod = "30 days (anonymized thereafter)",
            legalBasis = "Trip dispatch, arrival verification & rider safety"
        ),
        PopiaRetentionItem(
            category = "Support tickets & safety incidents",
            retentionPeriod = "24 months after resolution",
            legalBasis = "Dispute resolution & safety auditing"
        ),
        PopiaRetentionItem(
            category = "Saved places & trusted contacts",
            retentionPeriod = "Until deleted or account erasure",
            legalBasis = "Explicit user consent (on-device encrypted storage)"
        )
    )

    fun buildExportJson(
        session: UserSession?,
        privacyPreferences: PrivacyPreferences,
        trips: List<RideTrip>,
        savedDestinations: List<SavedDestination>,
        trustedContacts: List<TrustedContact>,
        walletTransactions: List<WalletTransaction>,
        supportCases: List<SupportCase>,
        nowEpochMs: Long = System.currentTimeMillis()
    ): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date(nowEpochMs))
        val bundle = PopiaExportBundle(
            exportedAtIso = iso,
            account = session?.let {
                PopiaAccountSummary(
                    userId = it.userId,
                    displayName = it.displayName,
                    email = it.email,
                    role = it.role
                )
            },
            privacyPreferences = privacyPreferences,
            trips = trips,
            savedDestinations = savedDestinations,
            trustedContacts = trustedContacts,
            walletTransactions = walletTransactions,
            supportCases = supportCases
        )
        return prettyJson.encodeToString(bundle)
    }

    fun suggestedFileName(nowEpochMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(nowEpochMs))
        return "vhaya_popia_data_$stamp.json"
    }

    fun shareExportFile(context: Context, jsonText: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, suggestedFileName())
            file.writeText(jsonText, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Vhaya Personal Data Export (POPIA)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share POPIA data export").apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }
}

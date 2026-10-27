@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import com.paisafloww.app.ui.AmountField
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.ChipChoice
import com.paisafloww.app.ui.ConfirmDialog
import com.paisafloww.app.ui.MainViewModel
import com.paisafloww.app.ui.MessageDialog
import com.paisafloww.app.ui.PrimaryButton
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SecondaryButton
import com.paisafloww.app.ui.SectionTitle
import com.paisafloww.app.ui.UiState
import java.time.LocalDate

@Composable
fun SettingsScreen(ui: UiState, vm: MainViewModel) {
    var startText by rememberSaveable { mutableStateOf(Money.toInput(ui.settings.startingBalancePaise)) }
    var startError by remember { mutableStateOf<String?>(null) }
    var startOk by remember { mutableStateOf(false) }

    val stamp = LocalDate.ofEpochDay(Dates.today()).toString()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Settings")

        SectionTitle("Theme")
        ChipChoice(
            listOf("System", "Light", "Dark"),
            when (ui.settings.theme) { "light" -> "Light"; "dark" -> "Dark"; else -> "System" },
            { choice -> vm.run({ }) { vm.repo.setTheme(choice.lowercase()) } }
        )

        SectionTitle("Starting balance")
        Text(
            "The money you already had before you began recording in this app. It is the base of your available balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountField(startText, { startText = it; startError = null; startOk = false }, label = "Starting balance", error = startError)
        SecondaryButton("Save starting balance", {
            val parsed = Money.parse(startText, allowZero = true)
            if (parsed.paise == null) {
                startError = parsed.error
            } else {
                vm.run({ r -> if (r == null) { startOk = true } else { startError = r } }) {
                    vm.repo.setStartingBalance(parsed.paise)
                }
            }
        }, Modifier.fillMaxWidth())
        if (startOk) Text("Saved.", color = MaterialTheme.colorScheme.secondary)

        SectionTitle("Backup and restore")
        AppCard {
            Text(
                "Your records live only on this phone. Uninstalling the app, clearing its storage or resetting the phone erases them. " +
                    "Automatic Android cloud backup is turned OFF for this app (for privacy), so please make your own backups regularly " +
                    "and copy the file somewhere safe, such as your computer.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Backup files are plain, readable JSON. They are NOT encrypted - keep them private.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        PrimaryButton("Export backup (JSON)", { backupLauncher.launch("paisafloww-backup-$stamp.json") })
        SecondaryButton("Restore from backup", { restoreLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
        SecondaryButton("Export transactions (CSV, view only)", { csvLauncher.launch("paisafloww-transactions-$stamp.csv") }, Modifier.fillMaxWidth())

        SectionTitle("Privacy")
        AppCard {
            Text(
                "Paisa Floww works fully offline. It has no login, no internet permission, no analytics, no ads and no cloud sync. " +
                    "Nothing you enter ever leaves this phone unless you export a file yourself.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text("Paisa Floww 1.0.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }

    vm.pendingRestore?.let { data ->
        ConfirmDialog(
            title = "Replace all current data?",
            text = "This backup contains:\n" + vm.describeBackup(data) + ".\n\n" +
                "Restoring will REPLACE everything currently in the app (" + ui.snap.transactions.size + " transactions, " +
                ui.snap.loans.size + " loans). This cannot be undone. Export a backup of your current data first if unsure.",
            confirmLabel = "Replace",
            danger = true,
            onDismiss = { vm.pendingRestore = null },
            onConfirm = { vm.confirmRestore() }
        )
    }
    vm.message?.let { text -> MessageDialog(text) { vm.message = null } }
}

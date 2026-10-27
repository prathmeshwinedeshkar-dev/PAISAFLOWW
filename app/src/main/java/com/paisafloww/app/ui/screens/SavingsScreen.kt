@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paisafloww.app.data.SavingsEntryEntity
import com.paisafloww.app.data.SavingsKind
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import com.paisafloww.app.ui.AmountField
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.AppTextField
import com.paisafloww.app.ui.ChipChoice
import com.paisafloww.app.ui.ConfirmDialog
import com.paisafloww.app.ui.DateField
import com.paisafloww.app.ui.MainViewModel
import com.paisafloww.app.ui.PrimaryButton
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SecondaryButton
import com.paisafloww.app.ui.SectionTitle
import com.paisafloww.app.ui.UiState
import com.paisafloww.app.ui.theme.expense
import com.paisafloww.app.ui.theme.income

@Composable
fun SavingsScreen(ui: UiState, vm: MainViewModel, onBack: () -> Unit) {
    val goal = ui.settings.savingsGoalPaise
    val saved = ui.saved
    val fraction = if (goal <= 0L) 0f else (saved.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val remaining = (goal - saved).coerceAtLeast(0L)
    val reached = saved >= goal

    var goalText by rememberSaveable { mutableStateOf(Money.toInput(goal)) }
    var goalError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var entryDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsEntryEntity?>(null) }
    var deleting by remember { mutableStateOf<SavingsEntryEntity?>(null) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Savings goal", onBack)

        AppCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.size(150.dp),
                    strokeWidth = 12.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (reached) {
                Text("Goal reached! \uD83C\uDF89", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            DetailLine("Target", Money.format(goal))
            DetailLine("Saved", Money.format(saved))
            DetailLine("Remaining", Money.format(remaining))
            DetailLine("Available to spend", Money.format(ui.available))
        }

        Text(
            "Savings contributions move money from your available balance into a reserved pot. " +
                "They are not income or expenses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        PrimaryButton("Add or withdraw savings", { editing = null; entryDialog = true })

        SectionTitle("Change target")
        AmountField(goalText, { goalText = it; goalError = null }, label = "Savings target", error = goalError)
        SecondaryButton("Update target", {
            val parsed = Money.parse(goalText)
            if (parsed.paise == null) {
                goalError = parsed.error
            } else {
                vm.run({ r -> if (r == null) { message = "Target updated." } else { goalError = r } }) {
                    vm.repo.setSavingsGoal(parsed.paise)
                }
            }
        }, Modifier.fillMaxWidth())
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

        SectionTitle("Savings history")
        if (ui.snap.savings.isEmpty()) {
            Text("No savings recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AppCard {
                ui.snap.savings.forEachIndexed { index, s ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    val deposit = s.kind == SavingsKind.DEPOSIT
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = s; entryDialog = true }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (deposit) "Saved " else "Withdrew ") + Money.format(s.amountPaise),
                                fontWeight = FontWeight.Bold,
                                color = if (deposit) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
                            )
                            Text(
                                Dates.format(s.dateEpochDay) + if (s.note.isNotBlank()) "  \u00B7  " + s.note else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { deleting = s }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Text("Tap an entry to edit it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (entryDialog) {
        SavingsEntryDialog(vm, editing) { entryDialog = false; editing = null }
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "Delete savings entry?",
            text = if (s.kind == SavingsKind.DEPOSIT) "The " + Money.format(s.amountPaise) + " returns to your available balance."
            else "The " + Money.format(s.amountPaise) + " goes back into your savings.",
            confirmLabel = "Delete",
            danger = true,
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                vm.run({ r -> message = r }) { vm.repo.deleteSaving(s.id) }
            }
        )
    }
}

@Composable
private fun SavingsEntryDialog(vm: MainViewModel, existing: SavingsEntryEntity?, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: SavingsKind.DEPOSIT) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.amountPaise) } ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: Dates.today()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Savings" else "Edit savings entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipChoice(
                    listOf("Add to savings", "Withdraw"),
                    if (kind == SavingsKind.DEPOSIT) "Add to savings" else "Withdraw",
                    { kind = if (it == "Withdraw") SavingsKind.WITHDRAW else SavingsKind.DEPOSIT }
                )
                AmountField(amountText, { amountText = it; error = null })
                DateField("Date", date, { if (it != null) date = it })
                AppTextField(note, { note = it }, "Note (optional)", maxLength = 500)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val parsed = Money.parse(amountText)
                    if (parsed.paise == null) {
                        error = parsed.error
                    } else {
                        submitting = true
                        vm.run({ r ->
                            if (r == null) { onDismiss() } else { error = r; submitting = false }
                        }) { vm.repo.saveSaving(existing?.id, kind, parsed.paise, date, note) }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

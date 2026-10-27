@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.paisafloww.app.data.Direction
import com.paisafloww.app.data.RepaymentEntity
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import com.paisafloww.app.ui.AmountField
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.AppTextField
import com.paisafloww.app.ui.ChipChoice
import com.paisafloww.app.ui.ConfirmDialog
import com.paisafloww.app.ui.DateField
import com.paisafloww.app.ui.EmptyState
import com.paisafloww.app.ui.LoanRow
import com.paisafloww.app.ui.MainViewModel
import com.paisafloww.app.ui.PrimaryButton
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SecondaryButton
import com.paisafloww.app.ui.SectionTitle
import com.paisafloww.app.ui.StatTile
import com.paisafloww.app.ui.UiState
import com.paisafloww.app.ui.theme.expense
import com.paisafloww.app.ui.theme.income

private val LOAN_FILTERS = listOf("All", "Borrowed", "Lent", "Active", "Paid", "Overdue", "Archived")

@Composable
fun LoansScreen(ui: UiState, onOpen: (Long) -> Unit) {
    val today = Dates.today()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    val totals = Accounting.loanTotals(ui.loanViews, today)

    val list = remember(ui.loanViews, query, filter) {
        val q = query.trim().lowercase()
        ui.loanViews.filter { v ->
            val base = if (filter == "Archived") v.loan.archived else !v.loan.archived
            base && when (filter) {
                "Borrowed" -> v.isBorrowed
                "Lent" -> !v.isBorrowed
                "Active" -> !v.isPaid
                "Paid" -> v.isPaid
                "Overdue" -> v.isOverdue(today)
                else -> true
            } && (q.isEmpty() || v.loan.friendName.lowercase().contains(q))
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Friends' loans") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("You owe", Money.format(totals.borrowedOwedPaise), MaterialTheme.colorScheme.expense, Modifier.weight(1f))
                StatTile("You are owed", Money.format(totals.lentOwedPaise), MaterialTheme.colorScheme.income, Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Active: ${totals.activeBorrowed} borrowed, ${totals.activeLent} lent   \u00B7   Overdue: ${totals.overdue}   \u00B7   Due soon: ${totals.dueSoon}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { AppTextField(query, { query = it }, "Search by friend name") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LOAN_FILTERS.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
            }
        }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    title = if (ui.snap.loans.isEmpty()) "No loans yet" else "Nothing matches",
                    subtitle = if (ui.snap.loans.isEmpty()) "Tap Add, then choose \"Money I borrowed\" or \"Money I lent\"." else "Try another filter or search."
                )
            }
        } else {
            items(list, key = { it.loan.id }) { v -> LoanRow(v, today) { onOpen(v.loan.id) } }
        }
    }
}

@Composable
fun LoanFormScreen(ui: UiState, vm: MainViewModel, id: Long, defaultDirection: String, onBack: () -> Unit) {
    val existing = if (id == 0L) null else ui.loan(id)?.loan
    if (id != 0L && existing == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Edit loan", onBack)
            EmptyState("Loan not found", "It may have been deleted.")
        }
        return
    }

    var direction by rememberSaveable { mutableStateOf(existing?.direction ?: defaultDirection) }
    var name by rememberSaveable { mutableStateOf(existing?.friendName ?: "") }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.principalPaise) } ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: Dates.today()) }
    var due by rememberSaveable { mutableStateOf<Long?>(existing?.dueEpochDay) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }
    var reason by rememberSaveable { mutableStateOf(existing?.reason ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf(false) }

    val borrowed = direction == Direction.BORROWED

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(if (existing == null) "New loan" else "Edit loan", onBack)

        if (existing == null) {
            ChipChoice(
                listOf("I borrowed", "I lent"),
                if (borrowed) "I borrowed" else "I lent",
                { direction = if (it == "I borrowed") Direction.BORROWED else Direction.LENT }
            )
        } else {
            Text(
                if (borrowed) "You borrowed this money (direction can't be changed)" else "You lent this money (direction can't be changed)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        AppTextField(name, { name = it; nameError = null }, "Friend's name", maxLength = 60, error = nameError)
        AmountField(amountText, { amountText = it; amountError = null }, error = amountError)
        DateField("Loan date", date, { if (it != null) date = it })
        DateField("Due date (optional)", due, { due = it }, allowClear = true)
        AppTextField(phone, { phone = it }, "Phone (optional)", maxLength = 20, keyboardType = KeyboardType.Phone)
        AppTextField(reason, { reason = it }, "Reason (optional)", maxLength = 200)
        AppTextField(notes, { notes = it }, "Notes (optional)", singleLine = false, maxLength = 1000)
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        PrimaryButton(
            if (existing == null) "Save loan" else "Save changes",
            enabled = !submitting,
            onClick = {
                val parsed = Money.parse(amountText)
                if (name.isBlank()) {
                    nameError = "Enter your friend's name"
                } else if (parsed.paise == null) {
                    amountError = parsed.error
                } else {
                    submitting = true
                    formError = null
                    vm.run({ r ->
                        if (r == null) { savedMessage = true } else { formError = r; submitting = false }
                    }) { vm.repo.saveLoan(existing?.id, name, direction, parsed.paise, date, due, phone, reason, notes) }
                }
            }
        )
        Spacer(Modifier.height(24.dp))
    }

    if (savedMessage) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("Saved") },
            text = { Text(if (existing == null) "The loan was added to your list." else "The loan was updated.") },
            confirmButton = { TextButton(onClick = { onBack() }) { Text("OK") } }
        )
    }
}

@Composable
fun LoanDetailScreen(
    ui: UiState,
    vm: MainViewModel,
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long, String) -> Unit
) {
    val view = ui.loan(id)
    val today = Dates.today()
    var repaymentDialog by remember { mutableStateOf<RepaymentEntity?>(null) }
    var showNewRepayment by remember { mutableStateOf(false) }
    var showSettle by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteRepayment by remember { mutableStateOf<RepaymentEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Loan details", onBack)
        if (view == null) {
            EmptyState("Loan not found", "It may have been deleted.")
            return@Column
        }
        val loan = view.loan
        val repayments = ui.snap.repayments.filter { it.loanId == loan.id }
        val borrowed = view.isBorrowed
        val settled = view.repaidPaise + loan.writtenOffPaise

        AppCard {
            Text(loan.friendName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                if (borrowed) "You borrowed this money" else "You lent this money",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("Outstanding", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.format(view.outstandingPaise),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (view.isPaid) MaterialTheme.colorScheme.income else if (borrowed) MaterialTheme.colorScheme.expense else MaterialTheme.colorScheme.income
            )
            Text(
                if (loan.archived) "ARCHIVED" else if (view.isPaid) "PAID" else if (view.isOverdue(today)) "OVERDUE" else "ACTIVE",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = if (view.isOverdue(today)) MaterialTheme.colorScheme.expense else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { (settled.toFloat() / loan.principalPaise.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            DetailLine("Original amount", Money.format(loan.principalPaise))
            DetailLine(if (borrowed) "Total repaid by you" else "Total received", Money.format(view.repaidPaise))
            if (loan.writtenOffPaise > 0L) DetailLine("Manually settled", Money.format(loan.writtenOffPaise))
            DetailLine("Loan date", Dates.format(loan.dateEpochDay))
            DetailLine("Due date", loan.dueEpochDay?.let { Dates.format(it) } ?: "\u2014")
            if (loan.phone.isNotBlank()) DetailLine("Phone", loan.phone)
            if (loan.reason.isNotBlank()) DetailLine("Reason", loan.reason)
            if (loan.notes.isNotBlank()) DetailLine("Notes", loan.notes)
            if (loan.writtenOffPaise > 0L) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Settled without full recorded repayment. Reason: " + loan.settlementReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!view.isPaid) {
            PrimaryButton(if (borrowed) "Record repayment (I paid back)" else "Record repayment (friend paid me)", { showNewRepayment = true })
            SecondaryButton("Mark as settled", { showSettle = true }, Modifier.fillMaxWidth())
        }
        SecondaryButton("Edit loan details", { onEdit(loan.id, loan.direction) }, Modifier.fillMaxWidth())
        if (loan.writtenOffPaise > 0L) {
            SecondaryButton("Reopen (undo manual settlement)", {
                vm.run({ r -> error = r }) { vm.repo.reopenLoan(loan.id) }
            }, Modifier.fillMaxWidth())
        }
        if (view.isPaid) {
            SecondaryButton(if (loan.archived) "Unarchive" else "Archive (keeps history)", {
                vm.run({ r -> error = r }) { vm.repo.setArchived(loan.id, !loan.archived) }
            }, Modifier.fillMaxWidth())
        }
        SecondaryButton("Delete loan and its history", { confirmDelete = true }, Modifier.fillMaxWidth())

        SectionTitle("Repayment history")
        if (repayments.isEmpty()) {
            Text("No repayments recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AppCard {
                repayments.forEachIndexed { index, r ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { repaymentDialog = r }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Money.format(r.amountPaise), fontWeight = FontWeight.Bold)
                            Text(
                                Dates.format(r.dateEpochDay) + if (r.note.isNotBlank()) "  \u00B7  " + r.note else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { deleteRepayment = r }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Text("Tap a repayment to edit it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (view != null) {
        if (showNewRepayment || repaymentDialog != null) {
            RepaymentDialog(
                vm = vm,
                loanId = view.loan.id,
                existing = repaymentDialog,
                onDismiss = { showNewRepayment = false; repaymentDialog = null }
            )
        }
        if (showSettle) {
            SettleDialog(vm, view.loan.id, view.outstandingPaise) { showSettle = false }
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = "Delete this loan?",
                text = "This permanently deletes the loan AND all of its repayment history, and your balance will change accordingly. " +
                    "If the loan is paid, archiving keeps the history instead.",
                confirmLabel = "Delete",
                danger = true,
                onDismiss = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    vm.run({ r -> if (r == null) { onBack() } else { error = r } }) { vm.repo.deleteLoan(view.loan.id) }
                }
            )
        }
        deleteRepayment?.let { r ->
            ConfirmDialog(
                title = "Delete repayment?",
                text = "The outstanding balance will increase by " + Money.format(r.amountPaise) + ".",
                confirmLabel = "Delete",
                danger = true,
                onDismiss = { deleteRepayment = null },
                onConfirm = {
                    deleteRepayment = null
                    vm.run({ res -> error = res }) { vm.repo.deleteRepayment(r.id) }
                }
            )
        }
    }
}

@Composable
private fun RepaymentDialog(vm: MainViewModel, loanId: Long, existing: RepaymentEntity?, onDismiss: () -> Unit) {
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.amountPaise) } ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: Dates.today()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Record repayment" else "Edit repayment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        }) { vm.repo.saveRepayment(existing?.id, loanId, parsed.paise, date, note) }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettleDialog(vm: MainViewModel, loanId: Long, outstanding: Long, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as settled?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This closes the remaining " + Money.format(outstanding) +
                        " WITHOUT recording a repayment. Your balance will not change. A reason is required."
                )
                AppTextField(reason, { reason = it; error = null }, "Reason (required)", maxLength = 200)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    submitting = true
                    vm.run({ r ->
                        if (r == null) { onDismiss() } else { error = r; submitting = false }
                    }) { vm.repo.settleLoan(loanId, reason) }
                }
            ) { Text("Settle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

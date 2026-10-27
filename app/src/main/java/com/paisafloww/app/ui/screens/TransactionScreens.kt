@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.paisafloww.app.data.TransactionEntity
import com.paisafloww.app.data.TxType
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.DefaultCategories
import com.paisafloww.app.domain.Money
import com.paisafloww.app.ui.AmountField
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.AppTextField
import com.paisafloww.app.ui.ChipChoice
import com.paisafloww.app.ui.ConfirmDialog
import com.paisafloww.app.ui.DateField
import com.paisafloww.app.ui.EmptyState
import com.paisafloww.app.ui.MainViewModel
import com.paisafloww.app.ui.PrimaryButton
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SecondaryButton
import com.paisafloww.app.ui.TransactionRow
import com.paisafloww.app.ui.UiState
import com.paisafloww.app.ui.theme.expense
import com.paisafloww.app.ui.theme.income
import java.time.YearMonth

private sealed interface TxRow {
    data class Header(val label: String, val income: Long, val expense: Long) : TxRow
    data class Item(val tx: TransactionEntity) : TxRow
}

private fun buildRows(list: List<TransactionEntity>): List<TxRow> {
    val rows = mutableListOf<TxRow>()
    var current: YearMonth? = null
    for (t in list) {
        val ym = Dates.month(t.dateEpochDay)
        if (ym != current) {
            current = ym
            val inMonth = list.filter { Dates.month(it.dateEpochDay) == ym }
            val inc = inMonth.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
            val exp = inMonth.filter { it.type == TxType.EXPENSE }.sumOf { it.amountPaise }
            rows.add(TxRow.Header(Dates.monthLabel(ym), inc, exp))
        }
        rows.add(TxRow.Item(t))
    }
    return rows
}

@Composable
fun TransactionsScreen(ui: UiState, onOpen: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("All") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }

    val all = ui.snap.transactions
    val categories = remember(all) { all.map { it.category }.distinct().sorted() }

    val filtered = remember(all, query, type, category, from, to, oldestFirst) {
        val q = query.trim().lowercase()
        val result = all.filter { t ->
            (type == "All" || (type == "Income" && t.type == TxType.INCOME) || (type == "Expense" && t.type == TxType.EXPENSE)) &&
                (category == null || t.category == category) &&
                (from == null || t.dateEpochDay >= from!!) &&
                (to == null || t.dateEpochDay <= to!!) &&
                (q.isEmpty() || t.note.lowercase().contains(q) || t.category.lowercase().contains(q) ||
                    Money.format(t.amountPaise).contains(q) || Money.toInput(t.amountPaise).contains(q))
        }
        if (oldestFirst) result.reversed() else result
    }
    val rows = remember(filtered) { buildRows(filtered) }
    val totalIn = filtered.filter { it.type == TxType.INCOME }.sumOf { it.amountPaise }
    val totalOut = filtered.filter { it.type == TxType.EXPENSE }.sumOf { it.amountPaise }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Transactions") }
        item { AppTextField(query, { query = it }, "Search notes, category or amount") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Income", "Expense").forEach { opt ->
                    FilterChip(selected = type == opt, onClick = { type = opt }, label = { Text(opt) })
                }
                FilterChip(
                    selected = oldestFirst,
                    onClick = { oldestFirst = !oldestFirst },
                    label = { Text(if (oldestFirst) "Oldest first" else "Newest first") }
                )
            }
        }
        if (categories.isNotEmpty()) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All categories") })
                    categories.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c) })
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { DateField("From", from, { from = it }, allowClear = true) }
                Column(Modifier.weight(1f)) { DateField("To", to, { to = it }, allowClear = true) }
            }
        }
        item {
            Text(
                "Shown: ${filtered.size}   In " + Money.format(totalIn) + "   Out " + Money.format(totalOut),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (rows.isEmpty()) {
            item {
                EmptyState(
                    title = if (all.isEmpty()) "No transactions yet" else "Nothing matches your filters",
                    subtitle = if (all.isEmpty()) "Tap Add to record your first expense or income." else "Try clearing the search or filters."
                )
            }
        } else {
            items(rows, key = { r ->
                when (r) {
                    is TxRow.Header -> "h" + r.label
                    is TxRow.Item -> "t" + r.tx.id
                }
            }) { r ->
                when (r) {
                    is TxRow.Header -> Column(Modifier.padding(top = 8.dp)) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "In " + Money.format(r.income) + "  \u00B7  Out " + Money.format(r.expense),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                    is TxRow.Item -> TransactionRow(r.tx) { onOpen(r.tx.id) }
                }
            }
        }
    }
}

@Composable
fun TransactionDetailScreen(
    ui: UiState,
    vm: MainViewModel,
    id: Long,
    onBack: () -> Unit,
    onEdit: (Long, String) -> Unit
) {
    val t = ui.snap.transactions.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Transaction", onBack)
        if (t == null) {
            EmptyState("Transaction not found", "It may have been deleted.")
            return@Column
        }
        val isIncome = t.type == TxType.INCOME
        AppCard {
            Text(if (isIncome) "Income" else "Expense", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                (if (isIncome) "+" else "-") + Money.format(t.amountPaise),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (isIncome) MaterialTheme.colorScheme.income else MaterialTheme.colorScheme.expense
            )
            Spacer(Modifier.height(12.dp))
            DetailLine("Category", t.category)
            DetailLine("Date", Dates.format(t.dateEpochDay))
            DetailLine("Note", if (t.note.isBlank()) "\u2014" else t.note)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryButton("Edit", { onEdit(t.id, t.type) })
        SecondaryButton("Delete", { confirmDelete = true }, Modifier.fillMaxWidth())
    }

    if (confirmDelete && t != null) {
        ConfirmDialog(
            title = "Delete transaction?",
            text = "This removes it from your history, balance and reports. This cannot be undone.",
            confirmLabel = "Delete",
            danger = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                vm.run({ r -> if (r == null) { onBack() } else { error = r } }) { vm.repo.deleteTransaction(t.id) }
            }
        )
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
fun TransactionFormScreen(ui: UiState, vm: MainViewModel, id: Long, defaultType: String, onBack: () -> Unit) {
    val existing = if (id == 0L) null else ui.snap.transactions.firstOrNull { it.id == id }
    if (id != 0L && existing == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Edit transaction", onBack)
            EmptyState("Transaction not found", "It may have been deleted.")
        }
        return
    }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: defaultType) }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.amountPaise) } ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.dateEpochDay ?: Dates.today()) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var amountError by rememberSaveable { mutableStateOf<String?>(null) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var newCategory by rememberSaveable { mutableStateOf("") }

    val isIncome = type == TxType.INCOME
    val options = remember(type, ui.snap.categories) {
        val defaults = if (type == TxType.INCOME) DefaultCategories.income else DefaultCategories.expense
        (defaults + ui.snap.categories.filter { it.type == type }.map { it.name }).distinct()
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(if (existing == null) "Add " + (if (isIncome) "income" else "expense") else "Edit transaction", onBack)

        if (existing == null) {
            ChipChoice(
                listOf("Expense", "Income"),
                if (isIncome) "Income" else "Expense",
                { type = if (it == "Income") TxType.INCOME else TxType.EXPENSE; category = "" }
            )
        }
        AmountField(amountText, { amountText = it; amountError = null }, error = amountError)
        DateField("Date", date, { if (it != null) date = it })

        Text("Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        ChipChoice(options, category, { category = it; newCategory = "" })
        AppTextField(newCategory, { newCategory = it; if (it.isNotBlank()) category = "" }, "Or type a new category", maxLength = 40)

        AppTextField(note, { note = it }, "Note (optional)", singleLine = false, maxLength = 500)
        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        PrimaryButton(
            if (existing == null) "Save" else "Save changes",
            enabled = !submitting,
            onClick = {
                val parsed = Money.parse(amountText)
                val chosen = if (newCategory.isNotBlank()) newCategory.trim() else category
                if (parsed.paise == null) {
                    amountError = parsed.error
                } else if (chosen.isBlank()) {
                    formError = "Choose or type a category"
                } else {
                    submitting = true
                    formError = null
                    vm.run({ r ->
                        if (r == null) onBack() else { formError = r; submitting = false }
                    }) { vm.repo.saveTransaction(existing?.id, type, parsed.paise, chosen, note, date) }
                }
            }
        )
        Spacer(Modifier.height(24.dp))
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import com.paisafloww.app.domain.Periods
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.EmptyState
import com.paisafloww.app.ui.LoanRow
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SecondaryButton
import com.paisafloww.app.ui.SectionTitle
import com.paisafloww.app.ui.StatTile
import com.paisafloww.app.ui.TransactionRow
import com.paisafloww.app.ui.UiState
import com.paisafloww.app.ui.theme.expense
import com.paisafloww.app.ui.theme.income

@Composable
fun HomeScreen(
    ui: UiState,
    onTx: (Long) -> Unit,
    onLoan: (Long) -> Unit,
    onSavings: () -> Unit,
    onSeeTransactions: () -> Unit,
    onSeeLoans: () -> Unit,
    onAdd: (String) -> Unit
) {
    val today = Dates.today()
    var period by rememberSaveable { mutableIntStateOf(0) }
    val range = Periods.range(period, today)
    val sum = Accounting.periodSummary(ui.snap.transactions, range.first, range.second)
    val totals = Accounting.loanTotals(ui.loanViews, today)
    val goal = ui.settings.savingsGoalPaise
    val savedFraction = if (goal <= 0L) 0f else (ui.saved.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val upcoming = ui.loanViews
        .filter { !it.isPaid && !it.loan.archived && it.loan.dueEpochDay != null }
        .sortedBy { it.loan.dueEpochDay }
        .take(3)
    val isEmpty = ui.snap.transactions.isEmpty() && ui.snap.loans.isEmpty() &&
        ui.snap.savings.isEmpty() && ui.settings.startingBalancePaise == 0L

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ScreenHeader("Paisa Floww") }

        item {
            AppCard {
                Text("Available balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    Money.format(ui.available),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (ui.available < 0) MaterialTheme.colorScheme.expense else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Plus " + Money.format(ui.saved) + " set aside for savings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isEmpty) {
            item {
                AppCard {
                    EmptyState(
                        title = "Welcome to Paisa Floww",
                        subtitle = "Everything stays on this phone. Start by adding your first expense or income, or set your starting balance in Settings."
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Add expense", { onAdd("EXPENSE") })
                            SecondaryButton("Add income", { onAdd("INCOME") })
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Periods.labels.forEachIndexed { i, label ->
                    FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Income", Money.format(sum.income), MaterialTheme.colorScheme.income, Modifier.weight(1f))
                StatTile("Expenses", Money.format(sum.expense), MaterialTheme.colorScheme.expense, Modifier.weight(1f))
            }
        }
        item {
            StatTile(
                "Net income (income minus expenses)",
                Money.format(sum.net),
                if (sum.net < 0) MaterialTheme.colorScheme.expense else MaterialTheme.colorScheme.income
            )
        }

        item { SectionTitle("Friends & loans") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("You owe friends", Money.format(totals.borrowedOwedPaise), MaterialTheme.colorScheme.expense, Modifier.weight(1f))
                StatTile("Friends owe you", Money.format(totals.lentOwedPaise), MaterialTheme.colorScheme.income, Modifier.weight(1f))
            }
        }
        if (totals.overdue > 0 || totals.dueSoon > 0) {
            item {
                Text(
                    buildString {
                        if (totals.overdue > 0) append("${totals.overdue} overdue. ")
                        if (totals.dueSoon > 0) append("${totals.dueSoon} due within 7 days.")
                    },
                    color = MaterialTheme.colorScheme.expense,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        item {
            AppCard(onClick = onSavings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("Savings goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            Money.format(ui.saved) + " of " + Money.format(goal),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (ui.saved >= goal) "Goal reached!" else "${(savedFraction * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { savedFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        if (upcoming.isNotEmpty()) {
            item { SectionTitle("Upcoming loan due dates") }
            items(upcoming, key = { "due" + it.loan.id }) { v -> LoanRow(v, today) { onLoan(v.loan.id) } }
            item { TextButton(onClick = onSeeLoans) { Text("See all loans") } }
        }

        item { SectionTitle("Recent transactions") }
        if (ui.snap.transactions.isEmpty()) {
            item { Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {
                AppCard {
                    ui.snap.transactions.take(5).forEach { TransactionRow(it) { onTx(it.id) } }
                }
            }
            item { TextButton(onClick = onSeeTransactions) { Text("See all transactions") } }
        }

        item { SectionTitle("Quick actions") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Expense", { onAdd("EXPENSE") }, Modifier.weight(1f))
                SecondaryButton("Income", { onAdd("INCOME") }, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("I borrowed", { onAdd("BORROWED") }, Modifier.weight(1f))
                SecondaryButton("I lent", { onAdd("LENT") }, Modifier.weight(1f))
            }
        }
    }
}

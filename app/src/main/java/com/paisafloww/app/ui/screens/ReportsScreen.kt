@file:OptIn(ExperimentalMaterial3Api::class)

package com.paisafloww.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paisafloww.app.data.TxType
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import com.paisafloww.app.domain.Periods
import com.paisafloww.app.ui.AppCard
import com.paisafloww.app.ui.BarChart
import com.paisafloww.app.ui.ScreenHeader
import com.paisafloww.app.ui.SectionTitle
import com.paisafloww.app.ui.UiState
import com.paisafloww.app.ui.theme.expense
import com.paisafloww.app.ui.theme.income

@Composable
fun ReportsScreen(ui: UiState) {
    val today = Dates.today()
    var period by rememberSaveable { mutableIntStateOf(0) }
    val range = Periods.range(period, today)
    val snap = ui.snap

    val summary = Accounting.periodSummary(snap.transactions, range.first, range.second)
    val byCategory = Accounting.categoryBreakdown(snap.transactions, TxType.EXPENSE, range.first, range.second)
    val move = Accounting.movement(snap.transactions, snap.loans, snap.repayments, snap.savings, range.first, range.second)
    val trend = Accounting.monthlySeries(snap.transactions, today, 6)
    val totals = Accounting.loanTotals(ui.loanViews, today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("Reports")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Periods.labels.forEachIndexed { i, label ->
                FilterChip(selected = period == i, onClick = { period = i }, label = { Text(label) })
            }
        }

        SectionTitle("Income and spending")
        AppCard {
            ReportLine("Income", Money.format(summary.income), MaterialTheme.colorScheme.income)
            ReportLine("Spending (expenses)", Money.format(summary.expense), MaterialTheme.colorScheme.expense)
            ReportLine(
                "Net income", Money.format(summary.net),
                if (summary.net < 0) MaterialTheme.colorScheme.expense else MaterialTheme.colorScheme.income,
                bold = true
            )
        }

        SectionTitle("Spending by category")
        if (byCategory.isEmpty()) {
            Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AppCard {
                val maxValue = byCategory.first().second.coerceAtLeast(1L)
                byCategory.forEach { (cat, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat, style = MaterialTheme.typography.bodyMedium)
                        Text(Money.format(value), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { value.toFloat() / maxValue.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        SectionTitle("Expenses, last 6 months")
        AppCard {
            BarChart(
                values = trend.map { it.expense },
                labels = trend.map { Dates.shortMonth(it.month) },
                barColor = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            trend.forEach {
                ReportLine(Dates.monthLabel(it.month), "In " + Money.format(it.income) + "  Out " + Money.format(it.expense), MaterialTheme.colorScheme.onSurface)
            }
        }

        SectionTitle("Loans (not counted as income or spending)")
        AppCard {
            ReportLine("You still owe friends", Money.format(totals.borrowedOwedPaise), MaterialTheme.colorScheme.expense, bold = true)
            ReportLine("Friends still owe you", Money.format(totals.lentOwedPaise), MaterialTheme.colorScheme.income, bold = true)
            Spacer(Modifier.height(6.dp))
            Text("In this period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ReportLine("Money borrowed", Money.format(move.borrowed), MaterialTheme.colorScheme.onSurface)
            ReportLine("Repaid by you", Money.format(move.repaidByYou), MaterialTheme.colorScheme.onSurface)
            ReportLine("Money lent", Money.format(move.lent), MaterialTheme.colorScheme.onSurface)
            ReportLine("Repayments received", Money.format(move.repaymentsReceived), MaterialTheme.colorScheme.onSurface)
        }

        SectionTitle("Savings transfers")
        AppCard {
            ReportLine("Moved into savings", Money.format(move.savedIn), MaterialTheme.colorScheme.onSurface)
            ReportLine("Withdrawn from savings", Money.format(move.savedOut), MaterialTheme.colorScheme.onSurface)
            ReportLine("Total saved now", Money.format(ui.saved), MaterialTheme.colorScheme.secondary, bold = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReportLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

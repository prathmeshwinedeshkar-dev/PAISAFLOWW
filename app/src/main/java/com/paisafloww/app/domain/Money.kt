package com.paisafloww.app.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Money {
    /** Rs 99,99,99,999.99 - large enough for personal use, small enough to never overflow a Long. */
    const val MAX_PAISE: Long = 99_999_999_999L

    private val AMOUNT_REGEX = Regex("^\\d{1,12}(\\.\\d{1,2})?$")

    data class ParseResult(val paise: Long?, val error: String?)

    /** Parses text typed by the user into exact integer paise. Never uses Float/Double. */
    fun parse(input: String, allowZero: Boolean = false): ParseResult {
        val cleaned = input.trim().replace(",", "").replace("\u20B9", "").replace(" ", "")
        if (cleaned.isEmpty()) return ParseResult(null, "Enter an amount")
        if (!AMOUNT_REGEX.matches(cleaned)) {
            return ParseResult(null, "Enter a valid amount, e.g. 250 or 99.50")
        }
        val paise = BigDecimal(cleaned).movePointRight(2).toLong()
        if (paise == 0L && !allowZero) return ParseResult(null, "Amount must be greater than zero")
        if (paise > MAX_PAISE) return ParseResult(null, "Amount is too large")
        return ParseResult(paise, null)
    }

    /** Validation for an amount that is already a number (repository / domain layer). */
    fun checkStored(paise: Long): String? = when {
        paise <= 0L -> "Amount must be greater than zero"
        paise > MAX_PAISE -> "Amount is too large"
        else -> null
    }

    /** Indian digit grouping, e.g. the rupee sign followed by 1,23,456.50 */
    fun format(paise: Long): String {
        val negative = paise < 0
        val abs = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = (abs / 100).toString()
        val p = (abs % 100).toString().padStart(2, '0')
        val grouped = if (rupees.length <= 3) {
            rupees
        } else {
            val groups = mutableListOf(rupees.takeLast(3))
            var rest = rupees.dropLast(3)
            while (rest.length > 2) {
                groups.add(0, rest.takeLast(2))
                rest = rest.dropLast(2)
            }
            if (rest.isNotEmpty()) groups.add(0, rest)
            groups.joinToString(",")
        }
        return (if (negative) "-" else "") + "\u20B9" + grouped + "." + p
    }

    /** Plain text for editing in a field, e.g. 10050 -> "100.50" */
    fun toInput(paise: Long): String =
        "${paise / 100}." + (paise % 100).toString().padStart(2, '0')

    /** Short label for charts: 12.5k, 3.2L, 1.1Cr */
    fun compact(paise: Long): String {
        val rupees = kotlin.math.abs(paise) / 100
        fun one(v: Long, unit: Long): String {
            val whole = v / unit
            val tenth = (v % unit) * 10 / unit
            return if (tenth == 0L) "$whole" else "$whole.$tenth"
        }
        return when {
            rupees >= 10_000_000L -> one(rupees, 10_000_000L) + "Cr"
            rupees >= 100_000L -> one(rupees, 100_000L) + "L"
            rupees >= 1_000L -> one(rupees, 1_000L) + "k"
            else -> rupees.toString()
        }
    }
}

object Dates {
    private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    private val MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val SHORT_MONTH_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
    val MAX_DAY: Long = LocalDate.of(2200, 1, 1).toEpochDay()

    fun today(): Long = LocalDate.now().toEpochDay()
    fun valid(epochDay: Long): Boolean = epochDay in 0..MAX_DAY
    fun format(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(FMT)
    fun monthLabel(ym: YearMonth): String = ym.format(MONTH_FMT)
    fun shortMonth(ym: YearMonth): String = ym.format(SHORT_MONTH_FMT)
    fun month(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))
}

object DefaultCategories {
    val expense = listOf(
        "Food", "Rent / PG", "Groceries", "Travel", "Mobile / Internet", "Education",
        "Shopping", "Entertainment", "Health", "Personal care", "Bills", "Other"
    )
    val income = listOf("Pocket money", "Salary", "Freelance", "Refund", "Gift", "Other")
}

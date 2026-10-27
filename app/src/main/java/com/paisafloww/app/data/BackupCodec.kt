package com.paisafloww.app.data

import com.paisafloww.app.domain.Dates
import com.paisafloww.app.domain.Money
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class BackupException(message: String) : Exception(message)

data class BackupData(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val transactions: List<TransactionEntity>,
    val loans: List<LoanEntity>,
    val repayments: List<RepaymentEntity>,
    val savings: List<SavingsEntryEntity>,
    val categories: List<CategoryEntity>,
    val settings: List<SettingEntity>
)

/**
 * Backup file format (JSON, UTF-8, NOT encrypted):
 * {
 *   "format": "paisafloww-backup", "schemaVersion": 1, "exportedAt": <epoch millis>, "appVersion": "1.0.0",
 *   "currency": "INR", "amountUnit": "paise",
 *   "transactions": [ {id,type,amountPaise,category,note,dateEpochDay,createdAt} ],
 *   "loans":        [ {id,friendName,direction,principalPaise,dateEpochDay,dueEpochDay|null,phone,reason,notes,
 *                      archived,writtenOffPaise,settlementReason,createdAt,updatedAt} ],
 *   "repayments":   [ {id,loanId,amountPaise,dateEpochDay,note,createdAt} ],
 *   "savings":      [ {id,kind,amountPaise,dateEpochDay,note,createdAt} ],
 *   "categories":   [ {type,name} ],
 *   "settings":     [ {key,value} ]
 * }
 */
object BackupCodec {
    const val FORMAT = "paisafloww-backup"
    const val SCHEMA_VERSION = 1

    fun encode(d: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("schemaVersion", d.schemaVersion)
        root.put("exportedAt", d.exportedAt)
        root.put("appVersion", d.appVersion)
        root.put("currency", "INR")
        root.put("amountUnit", "paise")

        val tx = JSONArray()
        d.transactions.forEach {
            tx.put(
                JSONObject().put("id", it.id).put("type", it.type).put("amountPaise", it.amountPaise)
                    .put("category", it.category).put("note", it.note)
                    .put("dateEpochDay", it.dateEpochDay).put("createdAt", it.createdAt)
            )
        }
        root.put("transactions", tx)

        val loans = JSONArray()
        d.loans.forEach {
            loans.put(
                JSONObject().put("id", it.id).put("friendName", it.friendName).put("direction", it.direction)
                    .put("principalPaise", it.principalPaise).put("dateEpochDay", it.dateEpochDay)
                    .put("dueEpochDay", it.dueEpochDay ?: JSONObject.NULL)
                    .put("phone", it.phone).put("reason", it.reason).put("notes", it.notes)
                    .put("archived", it.archived).put("writtenOffPaise", it.writtenOffPaise)
                    .put("settlementReason", it.settlementReason)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
            )
        }
        root.put("loans", loans)

        val reps = JSONArray()
        d.repayments.forEach {
            reps.put(
                JSONObject().put("id", it.id).put("loanId", it.loanId).put("amountPaise", it.amountPaise)
                    .put("dateEpochDay", it.dateEpochDay).put("note", it.note).put("createdAt", it.createdAt)
            )
        }
        root.put("repayments", reps)

        val sav = JSONArray()
        d.savings.forEach {
            sav.put(
                JSONObject().put("id", it.id).put("kind", it.kind).put("amountPaise", it.amountPaise)
                    .put("dateEpochDay", it.dateEpochDay).put("note", it.note).put("createdAt", it.createdAt)
            )
        }
        root.put("savings", sav)

        val cats = JSONArray()
        d.categories.forEach { cats.put(JSONObject().put("type", it.type).put("name", it.name)) }
        root.put("categories", cats)

        val settings = JSONArray()
        d.settings.forEach { settings.put(JSONObject().put("key", it.key).put("value", it.value)) }
        root.put("settings", settings)

        return root.toString(2)
    }

    /** Parses AND fully validates. Throws BackupException; never returns partially valid data. */
    fun decode(text: String): BackupData {
        try {
            return parse(text)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("This file is not a valid Paisa Floww backup.")
        }
    }

    // ---------------------------------------------------------------- parsing helpers

    private fun JSONObject.reqLong(key: String): Long {
        val v = opt(key)
        return when (v) {
            is Int -> v.toLong()
            is Long -> v
            else -> throw BackupException("Field '$key' must be a whole number")
        }
    }

    private fun JSONObject.reqStr(key: String): String {
        val v = opt(key)
        if (v is String) return v
        throw BackupException("Field '$key' must be text")
    }

    private fun JSONObject.reqBool(key: String): Boolean {
        val v = opt(key)
        if (v is Boolean) return v
        throw BackupException("Field '$key' must be true or false")
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return reqLong(key)
    }

    private fun JSONObject.reqArray(key: String): JSONArray =
        optJSONArray(key) ?: throw BackupException("Missing list '$key'")

    private fun JSONArray.objects(name: String): List<JSONObject> =
        (0 until length()).map { i ->
            optJSONObject(i) ?: throw BackupException("Entry ${i + 1} in '$name' is not valid")
        }

    private fun checkAmount(v: Long, what: String): Long {
        if (v < 1L || v > Money.MAX_PAISE) throw BackupException("Invalid amount in $what")
        return v
    }

    private fun checkDay(v: Long, what: String): Long {
        if (!Dates.valid(v)) throw BackupException("Invalid date in $what")
        return v
    }

    private fun checkId(v: Long, seen: MutableSet<Long>, what: String): Long {
        if (v < 1L) throw BackupException("Invalid ID in $what")
        if (!seen.add(v)) throw BackupException("Duplicate ID in $what")
        return v
    }

    private fun checkTime(v: Long, what: String): Long {
        if (v < 0L) throw BackupException("Invalid timestamp in $what")
        return v
    }

    private fun parse(text: String): BackupData {
        val root = JSONObject(text)
        if (root.opt("format") != FORMAT) throw BackupException("This is not a Paisa Floww backup file.")
        val version = root.reqLong("schemaVersion").toInt()
        if (version < 1) throw BackupException("Invalid backup version.")
        if (version > SCHEMA_VERSION) {
            throw BackupException("This backup was made by a newer version of Paisa Floww. Update the app first.")
        }
        val exportedAt = checkTime(root.reqLong("exportedAt"), "export time")
        val appVersion = root.reqStr("appVersion")

        val txIds = mutableSetOf<Long>()
        val transactions = root.reqArray("transactions").objects("transactions").map {
            val type = it.reqStr("type")
            if (type != TxType.INCOME && type != TxType.EXPENSE) throw BackupException("Invalid transaction type")
            val category = it.reqStr("category").trim()
            if (category.isEmpty() || category.length > 40) throw BackupException("Invalid transaction category")
            TransactionEntity(
                id = checkId(it.reqLong("id"), txIds, "transactions"),
                type = type,
                amountPaise = checkAmount(it.reqLong("amountPaise"), "transactions"),
                category = category,
                note = it.reqStr("note"),
                dateEpochDay = checkDay(it.reqLong("dateEpochDay"), "transactions"),
                createdAt = checkTime(it.reqLong("createdAt"), "transactions")
            )
        }

        val loanIds = mutableSetOf<Long>()
        val loans = root.reqArray("loans").objects("loans").map {
            val direction = it.reqStr("direction")
            if (direction != Direction.BORROWED && direction != Direction.LENT) {
                throw BackupException("Invalid loan direction")
            }
            val name = it.reqStr("friendName").trim()
            if (name.isEmpty() || name.length > 60) throw BackupException("Invalid friend name")
            val principal = checkAmount(it.reqLong("principalPaise"), "loans")
            val writtenOff = it.reqLong("writtenOffPaise")
            if (writtenOff < 0L || writtenOff > principal) throw BackupException("Invalid settlement amount")
            val due = it.optLongOrNull("dueEpochDay")
            if (due != null) checkDay(due, "loans")
            LoanEntity(
                id = checkId(it.reqLong("id"), loanIds, "loans"),
                friendName = name,
                direction = direction,
                principalPaise = principal,
                dateEpochDay = checkDay(it.reqLong("dateEpochDay"), "loans"),
                dueEpochDay = due,
                phone = it.reqStr("phone"),
                reason = it.reqStr("reason"),
                notes = it.reqStr("notes"),
                archived = it.reqBool("archived"),
                writtenOffPaise = writtenOff,
                settlementReason = it.reqStr("settlementReason"),
                createdAt = checkTime(it.reqLong("createdAt"), "loans"),
                updatedAt = checkTime(it.reqLong("updatedAt"), "loans")
            )
        }

        val repIds = mutableSetOf<Long>()
        val repayments = root.reqArray("repayments").objects("repayments").map {
            val loanId = it.reqLong("loanId")
            if (loanId !in loanIds) throw BackupException("A repayment points to a loan that is not in the file")
            RepaymentEntity(
                id = checkId(it.reqLong("id"), repIds, "repayments"),
                loanId = loanId,
                amountPaise = checkAmount(it.reqLong("amountPaise"), "repayments"),
                dateEpochDay = checkDay(it.reqLong("dateEpochDay"), "repayments"),
                note = it.reqStr("note"),
                createdAt = checkTime(it.reqLong("createdAt"), "repayments")
            )
        }
        val repaidByLoan = repayments.groupBy { it.loanId }.mapValues { e -> e.value.sumOf { r -> r.amountPaise } }
        for (loan in loans) {
            val repaid = repaidByLoan[loan.id] ?: 0L
            if (repaid + loan.writtenOffPaise > loan.principalPaise) {
                throw BackupException("Repayments for '${loan.friendName}' are more than the loan amount")
            }
        }

        val savIds = mutableSetOf<Long>()
        val savings = root.reqArray("savings").objects("savings").map {
            val kind = it.reqStr("kind")
            if (kind != SavingsKind.DEPOSIT && kind != SavingsKind.WITHDRAW) throw BackupException("Invalid savings type")
            SavingsEntryEntity(
                id = checkId(it.reqLong("id"), savIds, "savings"),
                kind = kind,
                amountPaise = checkAmount(it.reqLong("amountPaise"), "savings"),
                dateEpochDay = checkDay(it.reqLong("dateEpochDay"), "savings"),
                note = it.reqStr("note"),
                createdAt = checkTime(it.reqLong("createdAt"), "savings")
            )
        }
        var saved = 0L
        for (s in savings) saved += if (s.kind == SavingsKind.DEPOSIT) s.amountPaise else -s.amountPaise
        if (saved < 0L) throw BackupException("Savings in the file add up to less than zero")

        val catKeys = mutableSetOf<String>()
        val categories = root.reqArray("categories").objects("categories").mapNotNull {
            val type = it.reqStr("type")
            if (type != TxType.INCOME && type != TxType.EXPENSE) throw BackupException("Invalid category type")
            val name = it.reqStr("name").trim()
            if (name.isEmpty() || name.length > 40) throw BackupException("Invalid category name")
            if (catKeys.add("$type|$name")) CategoryEntity(type, name) else null
        }

        val settingKeys = mutableSetOf<String>()
        val settings = root.reqArray("settings").objects("settings").map {
            val key = it.reqStr("key")
            val value = it.reqStr("value")
            if (!settingKeys.add(key)) throw BackupException("Duplicate setting '$key'")
            when (key) {
                SettingKeys.START -> {
                    val v = value.toLongOrNull()
                    if (v == null || v < 0L || v > Money.MAX_PAISE) throw BackupException("Invalid starting balance")
                }
                SettingKeys.GOAL -> {
                    val v = value.toLongOrNull()
                    if (v == null || v < 1L || v > Money.MAX_PAISE) throw BackupException("Invalid savings goal")
                }
                SettingKeys.THEME -> if (value !in SettingKeys.THEMES) throw BackupException("Invalid theme")
                else -> throw BackupException("Unknown setting '$key' - refusing to drop data silently")
            }
            SettingEntity(key, value)
        }

        return BackupData(version, exportedAt, appVersion, transactions, loans, repayments, savings, categories, settings)
    }
}

object CsvExport {
    private fun cell(raw: String): String {
        var s = raw
        // Neutralise spreadsheet formula injection.
        if (s.isNotEmpty() && s[0] in "=+-@") s = "'" + s
        return "\"" + s.replace("\"", "\"\"") + "\""
    }

    /** CSV is for viewing/analysis only. The JSON backup is the authoritative restore format. */
    fun transactions(list: List<TransactionEntity>): String {
        val sb = StringBuilder("Date,Type,Category,Amount (INR),Note\r\n")
        for (t in list.sortedWith(compareBy<TransactionEntity> { it.dateEpochDay }.thenBy { it.id })) {
            sb.append(LocalDate.ofEpochDay(t.dateEpochDay).toString()).append(',')
                .append(t.type).append(',')
                .append(cell(t.category)).append(',')
                .append(Money.toInput(t.amountPaise)).append(',')
                .append(cell(t.note)).append("\r\n")
        }
        return sb.toString()
    }
}

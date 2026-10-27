package com.paisafloww.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paisafloww.app.PaisaApp
import com.paisafloww.app.data.AppSettings
import com.paisafloww.app.data.BackupCodec
import com.paisafloww.app.data.BackupData
import com.paisafloww.app.data.BackupException
import com.paisafloww.app.data.CsvExport
import com.paisafloww.app.data.FinanceRepository
import com.paisafloww.app.data.Snapshot
import com.paisafloww.app.domain.Accounting
import com.paisafloww.app.domain.LoanView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class UiState(
    val snap: Snapshot,
    val loanViews: List<LoanView>,
    val available: Long,
    val saved: Long
) {
    val settings: AppSettings get() = snap.settings
    fun loan(id: Long): LoanView? = loanViews.firstOrNull { it.loan.id == id }

    companion object {
        fun from(s: Snapshot): UiState = UiState(
            snap = s,
            loanViews = Accounting.loanViews(s.loans, s.repayments),
            available = Accounting.availableBalance(
                s.settings.startingBalancePaise, s.transactions, s.loans, s.repayments, s.savings
            ),
            saved = Accounting.savedBalance(s.savings)
        )
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo: FinanceRepository = (app as PaisaApp).repository

    val ui: StateFlow<UiState?> = repo.snapshot
        .map { UiState.from(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var message by mutableStateOf<String?>(null)
    var pendingRestore by mutableStateOf<BackupData?>(null)

    private var busy = false

    /**
     * Runs one write at a time. A second tap while a write is running is ignored,
     * which prevents duplicate transactions / repayments from repeated button taps.
     */
    fun run(onResult: (String?) -> Unit, block: suspend () -> String?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                "Something went wrong. Nothing was changed."
            }
            busy = false
            onResult(result)
        }
    }

    // ------------------------------------------------------------ backup / restore / csv

    private fun appVersion(): String = try {
        val ctx = getApplication<Application>()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun describe(d: BackupData): String =
        "${d.transactions.size} transactions, ${d.loans.size} loans, ${d.repayments.size} repayments, ${d.savings.size} savings entries"

    fun exportBackup(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            message = try {
                val data = repo.exportData(appVersion())
                val json = BackupCodec.encode(data)
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("no output")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                // Read the file back and validate it, so a "saved" message really means it is restorable.
                val back = resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw IOException("no input")
                val check = BackupCodec.decode(back)
                if (check.transactions.size != data.transactions.size ||
                    check.loans.size != data.loans.size ||
                    check.repayments.size != data.repayments.size ||
                    check.savings.size != data.savings.size
                ) throw IOException("mismatch")
                "Backup saved and verified.\n" + describe(check) + "."
            } catch (e: BackupException) {
                "Backup file failed verification: " + (e.message ?: "")
            } catch (e: Exception) {
                "Backup failed: the file could not be written. Try another location."
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            message = try {
                val data = repo.exportData(appVersion())
                val csv = CsvExport.transactions(data.transactions)
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("no output")
                out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                "CSV saved: ${data.transactions.size} transactions.\n(CSV is for viewing only. Use the JSON backup to restore.)"
            } catch (e: Exception) {
                "CSV export failed: the file could not be written."
            }
        }
    }

    /** Step 1 of restore: read and fully validate the file. Nothing is changed yet. */
    fun prepareRestore(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("no input")
                if (bytes.size > 20 * 1024 * 1024) throw BackupException("This file is too large to be a Paisa Floww backup.")
                val data = BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                withContext(Dispatchers.Main) { pendingRestore = data }
            } catch (e: BackupException) {
                message = "Restore cancelled. " + (e.message ?: "") + "\nYour existing data was not changed."
            } catch (e: Exception) {
                message = "Restore cancelled. The file could not be read.\nYour existing data was not changed."
            }
        }
    }

    /** Step 2: after the user confirmed, replace everything in one transaction. */
    fun confirmRestore() {
        val data = pendingRestore ?: return
        pendingRestore = null
        run({ result ->
            message = result ?: ("Restore complete.\n" + describe(data) + ".")
        }) { repo.restore(data) }
    }

    fun describeBackup(d: BackupData): String = describe(d)
}

package com.expensemanager.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensemanager.app.data.AppDatabase
import com.expensemanager.app.data.CsvImport
import com.expensemanager.app.data.Favourite
import com.expensemanager.app.data.GroupMember
import com.expensemanager.app.data.GroupWithMembers
import com.expensemanager.app.data.PeopleGroup
import com.expensemanager.app.data.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.expensemanager.app.data.Prefs
import com.expensemanager.app.data.Settings
import com.expensemanager.app.data.SplitShare
import com.expensemanager.app.data.TransactionEntity
import com.expensemanager.app.data.TransactionWithShares
import com.expensemanager.app.data.TxType
import com.expensemanager.app.data.SmsParser
import com.expensemanager.app.data.SmsReader
import com.expensemanager.app.data.SmsSpend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class MonthStats(
    val income: Long,
    /** Spent so far: full amounts minus split money already received. */
    val expense: Long,
    /** Split money still to come back for this month's expenses. */
    val pendingSplits: Long,
    val byCategory: List<Pair<String, Long>>,
    val byMode: List<Pair<String, Long>>,
)

fun computeStats(all: List<TransactionWithShares>, month: YearMonth): MonthStats {
    val key = month.key()
    val inMonth = all.filter { it.tx.accountMonth == key }
    val expenses = inMonth.filter { it.isExpense }
    return MonthStats(
        income = inMonth.filter { !it.isExpense }.sumOf { it.tx.amount },
        expense = expenses.sumOf { it.effectiveExpense },
        pendingSplits = expenses.sumOf { it.pendingFromOthers },
        byCategory = expenses.groupBy { it.tx.category }
            .map { (k, v) -> k to v.sumOf { it.effectiveExpense } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second },
        byMode = expenses.groupBy { it.tx.paymentMode }
            .map { (k, v) -> k to v.sumOf { it.tx.amount } }
            .sortedByDescending { it.second },
    )
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val prefs = Prefs(app)

    val settings: StateFlow<Settings> = prefs.settings

    val transactions: StateFlow<List<TransactionWithShares>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** People previously used in splits — offered as quick picks. */
    val knownPeople: StateFlow<List<String>> =
        dao.observePeople().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateSettings(block: (Settings) -> Settings) = prefs.update(block)

    val groups: StateFlow<List<GroupWithMembers>> =
        dao.observeGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favourites: StateFlow<List<Person>> =
        dao.observeFavourites().map { list -> list.map { Person(it.name, it.phone) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun toggleFavourite(p: Person) = viewModelScope.launch {
        if (favourites.value.any { it.name.equals(p.name, ignoreCase = true) }) dao.deleteFavourite(p.name)
        else dao.insertFavourite(Favourite(p.name.trim(), p.phone))
    }

    /** Replaces the favourites list with [people] (used by the "edit favourites" picker). */
    fun setFavourites(people: List<Person>) = viewModelScope.launch {
        val current = favourites.value
        current.filter { c -> people.none { it.name.equals(c.name, true) } }.forEach { dao.deleteFavourite(it.name) }
        people.filter { p -> current.none { it.name.equals(p.name, true) } }.forEach { dao.insertFavourite(Favourite(it.name.trim(), it.phone)) }
    }

    fun saveGroup(id: Long, name: String, members: List<Person>) = viewModelScope.launch {
        dao.saveGroup(
            PeopleGroup(id = id, name = name.trim()),
            members.distinctBy { it.name.lowercase() }.map { GroupMember(groupId = id, name = it.name, phone = it.phone) },
        )
    }

    fun deleteGroup(id: Long) = viewModelScope.launch { dao.deleteGroup(id) }

    suspend fun load(id: Long): TransactionWithShares? = dao.getById(id)

    fun save(tx: TransactionEntity, shares: List<SplitShare>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            dao.upsertWithShares(tx, if (tx.type == TxType.INCOME.name) emptyList() else shares)
            onDone()
        }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.deleteTx(id) }

    /** Restores a deleted transaction (used for the undo snackbar). */
    fun restore(item: TransactionWithShares) = viewModelScope.launch {
        dao.upsertWithShares(item.tx.copy(id = 0), item.shares)
    }

    fun setSettled(ids: List<Long>, settled: Boolean) = viewModelScope.launch {
        dao.setSettled(ids, settled, if (settled) LocalDate.now().toEpochDay() else null)
    }

    fun exportCsv(uri: Uri, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.appendLine("Date,Counted In,Type,Category,Payment Mode,Amount,Counted As Spent,Pending From Others,Note,Split With")
                    transactions.value.forEach { t ->
                        val split = t.shares.joinToString("; ") {
                            "${it.personName} ${minorToInput(it.amount)}${if (it.settled) " (settled)" else ""}"
                        }
                        val cols = listOf(
                            LocalDate.ofEpochDay(t.tx.date).toString(),
                            monthFromKey(t.tx.accountMonth).toString(),
                            t.tx.type,
                            t.tx.category,
                            t.tx.paymentMode,
                            minorToInput(t.tx.amount),
                            minorToInput(if (t.isExpense) t.effectiveExpense else t.tx.amount),
                            minorToInput(t.pendingFromOthers),
                            t.tx.note,
                            split,
                        )
                        w.appendLine(cols.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
                    }
                } ?: error("Cannot open output")
            }.isSuccess
        }
        onResult(ok)
    }

    /**
     * Reads a CSV file and prepares an import without saving anything, so the user can
     * confirm first. Rows already in the database are dropped (see [CsvImport.withoutExisting]).
     */
    fun previewImport(uri: Uri, onResult: (Result<ImportPreview>) -> Unit) = viewModelScope.launch {
        val existing = dao.observeAll().first().map { it.tx }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Cannot open file")
                val parsed = CsvImport.parse(text)
                val fresh = CsvImport.withoutExisting(parsed.transactions, existing)
                ImportPreview(
                    toImport = fresh,
                    duplicates = parsed.transactions.size - fresh.size,
                    errors = parsed.errors,
                )
            }
        }
        onResult(result)
    }

    fun commitImport(preview: ImportPreview, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching { dao.insertTxs(preview.toImport) }.isSuccess
        onDone(ok)
    }

    // ---- Scan bank SMS ----

    private val _smsScan = MutableStateFlow(SmsScan(LocalDate.now(), LocalDate.now()))
    /** Lives in the ViewModel so the list survives opening a found spend to split it. */
    val smsScan: StateFlow<SmsScan> = _smsScan.asStateFlow()
    private var scanJob: Job? = null

    /** Finds bank spends received from [from] to [to] (inclusive) that aren't in the app yet. */
    fun scanSms(from: LocalDate, to: LocalDate) {
        scanJob?.cancel()
        _smsScan.value = SmsScan(from, to, loading = true)
        scanJob = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val spends = SmsReader.bankSpends(
                        getApplication(),
                        from.atStartOfDay(zone).toInstant().toEpochMilli(),
                        to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                    )
                    Triple(spends, dao.smsRefs().toSet(), dao.observeAll().first().map { it.tx })
                }
            }
            _smsScan.value = result.fold(
                onSuccess = { (spends, imported, existing) ->
                    // Reuse the category the user picked last time for the same payee.
                    val learned = existing.filter { it.type == TxType.EXPENSE.name && it.note.isNotBlank() }
                        .sortedBy { it.createdAt }
                        .associate { it.note.lowercase() to it.category }
                    val fresh = spends.filter { it.ref !in imported }
                    val items = fresh.sortedByDescending { it.time }.map { s ->
                        val day = Instant.ofEpochMilli(s.time).atZone(zone).toLocalDate().toEpochDay()
                        val typedIn = existing.any {
                            it.smsRef == null && it.type == TxType.EXPENSE.name && it.amount == s.amount && it.date == day
                        }
                        val warning = s.transferHint ?: if (typedIn) "You may have added this already" else null
                        SmsItem(
                            spend = s,
                            note = s.merchant,
                            category = learned[s.merchant.lowercase()] ?: SmsParser.guessCategory(s.merchant),
                            selected = warning == null,
                            warning = warning,
                        )
                    }
                    SmsScan(from, to, items = items, alreadyImported = spends.size - fresh.size, done = true)
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    SmsScan(from, to, error = if (e is SecurityException) "SMS permission is needed" else "Couldn't read messages", done = true)
                },
            )
        }
    }

    fun updateSmsItem(ref: String, block: (SmsItem) -> SmsItem) {
        _smsScan.update { s -> s.copy(items = s.items.map { if (it.spend.ref == ref) block(it) else it }) }
    }

    fun selectAllSms(selected: Boolean) {
        _smsScan.update { s -> s.copy(items = s.items.map { it.copy(selected = selected) }) }
    }

    private fun SmsItem.toEntity(): TransactionEntity {
        val day = Instant.ofEpochMilli(spend.time).atZone(ZoneId.systemDefault()).toLocalDate()
        return TransactionEntity(
            type = TxType.EXPENSE.name,
            amount = spend.amount,
            category = category,
            paymentMode = spend.mode.name,
            note = note.trim().take(80),
            date = day.toEpochDay(),
            accountMonth = YearMonth.from(day).key(),
            createdAt = spend.time,
            smsRef = spend.ref,
        )
    }

    private fun markSaved(refs: Set<String>) {
        _smsScan.update { s ->
            s.copy(items = s.items.filter { it.spend.ref !in refs }, alreadyImported = s.alreadyImported + refs.size)
        }
    }

    /** Saves the ticked spends. */
    fun saveSelectedSms(onDone: (Int) -> Unit) = viewModelScope.launch {
        val picked = _smsScan.value.items.filter { it.selected }
        if (picked.isEmpty()) return@launch
        val ok = runCatching { dao.insertTxs(picked.map { it.toEntity() }) }.isSuccess
        if (ok) markSaved(picked.map { it.spend.ref }.toSet())
        onDone(if (ok) picked.size else -1)
    }

    /** Saves one spend straight away and returns its id, so it can be opened to split. */
    fun saveSmsForSplit(ref: String, onSaved: (Long) -> Unit) = viewModelScope.launch {
        val item = _smsScan.value.items.firstOrNull { it.spend.ref == ref } ?: return@launch
        val id = dao.insertTx(item.toEntity())
        markSaved(setOf(ref))
        onSaved(id)
    }
}

data class SmsItem(
    val spend: SmsSpend,
    val note: String,
    val category: String,
    /** Ticked = will be added. */
    val selected: Boolean,
    /** Why it's unticked by default, e.g. a cash withdrawal or something typed in already. */
    val warning: String?,
)

data class SmsScan(
    val from: LocalDate,
    val to: LocalDate,
    val loading: Boolean = false,
    /** New spends found, newest first. */
    val items: List<SmsItem> = emptyList(),
    /** Spends in the period that were already added from SMS earlier. */
    val alreadyImported: Int = 0,
    val error: String? = null,
    /** A scan has finished (so an empty list means "nothing found"). */
    val done: Boolean = false,
)

data class ImportPreview(
    val toImport: List<TransactionEntity>,
    /** Rows skipped because an identical transaction already exists. */
    val duplicates: Int,
    /** Rows that couldn't be read, e.g. "Row 12: bad amount". */
    val errors: List<String>,
) {
    val expenses get() = toImport.count { it.type == TxType.EXPENSE.name }
    val incomes get() = toImport.size - expenses
    val firstDate: LocalDate? get() = toImport.minOfOrNull { it.date }?.let(LocalDate::ofEpochDay)
    val lastDate: LocalDate? get() = toImport.maxOfOrNull { it.date }?.let(LocalDate::ofEpochDay)
}

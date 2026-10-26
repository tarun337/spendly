package com.expensemanager.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.PaymentMode
import com.expensemanager.app.data.TransactionWithShares
import com.expensemanager.app.data.TxType
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.EmptyState
import com.expensemanager.app.ui.MonthSwitcher
import com.expensemanager.app.ui.TransactionRow
import com.expensemanager.app.ui.key
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.relativeLabel
import com.expensemanager.app.ui.theme.extraColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val all by vm.transactions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.currency
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var modeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var splitOnly by rememberSaveable { mutableStateOf(false) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().key()) }
    val month = YearMonth.of(monthKey / 100, monthKey % 100)

    val filtered = remember(all, query, typeFilter, modeFilter, splitOnly, allTime, monthKey) {
        val q = query.trim().lowercase()
        all.filter { t ->
            (allTime || t.tx.accountMonth == monthKey) &&
                (typeFilter == null || t.tx.type == typeFilter) &&
                (modeFilter == null || t.tx.paymentMode == modeFilter) &&
                (!splitOnly || t.isSplit) &&
                (q.isEmpty() || t.tx.note.lowercase().contains(q) || t.tx.category.lowercase().contains(q) ||
                    t.shares.any { it.personName.lowercase().contains(q) })
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.tx.date } }
    val totalIn = filtered.filter { !it.isExpense }.sumOf { it.tx.amount }
    val totalOut = filtered.filter { it.isExpense }.sumOf { it.effectiveExpense }

    fun deleteWithUndo(t: TransactionWithShares) {
        vm.delete(t.tx.id)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Transaction deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.restore(t)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, "Add transaction")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text(
                    "History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search notes, categories, people") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!allTime) MonthSwitcher(month, { monthKey = it.key() }, Modifier.weight(1f))
                    else Text("All time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    FilterChip(selected = allTime, onClick = { allTime = !allTime }, label = { Text("All time") })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(typeFilter == TxType.EXPENSE.name, { typeFilter = if (typeFilter == TxType.EXPENSE.name) null else TxType.EXPENSE.name }, { Text("Expenses") }) }
                    item { FilterChip(typeFilter == TxType.INCOME.name, { typeFilter = if (typeFilter == TxType.INCOME.name) null else TxType.INCOME.name }, { Text("Income") }) }
                    item { FilterChip(splitOnly, { splitOnly = !splitOnly }, { Text("Split") }) }
                    items(PaymentMode.entries) { m ->
                        FilterChip(modeFilter == m.name, { modeFilter = if (modeFilter == m.name) null else m.name }, { Text(m.label) },
                            leadingIcon = { Icon(modeIcon(m), null, Modifier.padding(0.dp)) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("In", style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
                        Text(money(totalIn, cur), color = extraColors.income, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Out", style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
                        Text(money(totalOut, cur), color = extraColors.expense, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Entries", style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
                        Text("${filtered.size}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (filtered.isEmpty()) {
                item { EmptyState(Icons.Rounded.SearchOff, "Nothing here", "Try another month or clear filters") }
            }

            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    val dayNet = list.sumOf { if (it.isExpense) -it.effectiveExpense else it.tx.amount }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)) {
                        Text(LocalDate.ofEpochDay(day).relativeLabel(), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle, modifier = Modifier.weight(1f))
                        Text(money(dayNet, cur, signed = true), style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
                    }
                }
                items(list, key = { it.tx.id }) { t ->
                    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) { deleteWithUndo(t); true } else false
                    })
                    SwipeToDismissBox(
                        state = state,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val bg by animateColorAsState(
                                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) extraColors.expense.copy(alpha = 0.25f) else Color.Transparent,
                                label = "swipe",
                            )
                            Box(
                                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(bg).padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) { Icon(Icons.Rounded.Delete, "Delete", tint = extraColors.expense) }
                        },
                    ) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                            TransactionRow(t, cur) { onOpen(t.tx.id) }
                        }
                    }
                }
            }
        }
    }
}

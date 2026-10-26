package com.expensemanager.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.expensemanager.app.data.detectGroup
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.SectionTitle
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.SplitShare
import com.expensemanager.app.data.TransactionEntity
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.Avatar
import com.expensemanager.app.ui.EmptyState
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.shortLabel
import com.expensemanager.app.ui.theme.extraColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class ShareItem(val share: SplitShare, val tx: TransactionEntity)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onOpen: (Long) -> Unit, onPeople: () -> Unit) {
    val all by vm.transactions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.currency
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmPerson by remember { mutableStateOf<Pair<String, List<ShareItem>>?>(null) }

    val items = remember(all) { all.flatMap { t -> t.shares.map { ShareItem(it, t.tx) } } }
    val pending = remember(items) {
        items.filter { !it.share.settled }.groupBy { it.share.personName }
            .toList().sortedByDescending { (_, l) -> l.sumOf { it.share.amount } }
    }
    val settled = remember(items) {
        items.filter { it.share.settled }.sortedByDescending { it.share.settledOn ?: 0 }
    }

    val groups by vm.groups.collectAsStateWithLifecycle()
    var byGroup by rememberSaveable { mutableStateOf(false) }

    /**
     * Pending shares grouped by the saved group each split was made for. Older splits
     * without a stored group are matched by who's in them. Returns (groups, ungrouped by person).
     */
    val pendingByGroup = remember(all, groups) {
        val byId = groups.associateBy { it.group.id }
        val grouped = LinkedHashMap<Long, MutableList<ShareItem>>()
        val loose = mutableListOf<ShareItem>()
        all.filter { it.pendingFromOthers > 0 }.forEach { t ->
            val g = t.tx.groupId?.let(byId::get) ?: detectGroup(t.shares.map { it.personName }, groups)
            val open = t.shares.filter { !it.settled }.map { ShareItem(it, t.tx) }
            if (g != null) grouped.getOrPut(g.group.id) { mutableListOf() }.addAll(open) else loose.addAll(open)
        }
        val groupList = grouped.map { (id, l) -> byId.getValue(id) to l.toList() }
            .sortedByDescending { (_, l) -> l.sumOf { it.share.amount } }
        val looseByPerson = loose.groupBy { it.share.personName }.toList()
            .sortedByDescending { (_, l) -> l.sumOf { it.share.amount } }
        groupList to looseByPerson
    }

    fun settle(list: List<ShareItem>, label: String) {
        val ids = list.map { it.share.id }
        vm.setSettled(ids, true)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(label, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) vm.setSettled(ids, false)
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Splits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onPeople) {
                    Icon(Icons.Rounded.Groups, null); Spacer(Modifier.width(6.dp)); Text("Groups")
                }
            }
        }
        item {
            AppCard {
                Text("Total you are owed", style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle)
                Text(
                    money(pending.sumOf { (_, l) -> l.sumOf { it.share.amount } }, cur),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = extraColors.income,
                )
                Text(
                    "${pending.size} ${if (pending.size == 1) "person" else "people"} · ${pending.sumOf { it.second.size }} open splits",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
        }
        item {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Pending") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Settled") })
            }
        }

        if (tab == 0) {
            if (pending.isEmpty()) {
                item { EmptyState(Icons.Rounded.Handshake, "All settled up", "Split an expense while adding it to track it here") }
            } else {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("By person", "By group").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = byGroup == (i == 1), onClick = { byGroup = i == 1 },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label) }
                        }
                    }
                }
            }
            if (!byGroup) {
                items(pending, key = { it.first }) { (name, list) ->
                    PersonCard(
                        name = name, list = list, cur = cur, onOpen = onOpen,
                        onSettleOne = { settle(listOf(it), "Marked ${money(it.share.amount, cur)} from $name as received") },
                        onSettleAll = { confirmPerson = name to list },
                    )
                }
            } else {
                items(pendingByGroup.first, key = { "grp${it.first.group.id}" }) { (g, list) ->
                    GroupCard(
                        name = g.group.name, list = list, cur = cur, onOpen = onOpen,
                        onSettlePerson = { person, l -> settle(l, "Marked ${money(l.sumOf { it.share.amount }, cur)} from $person as received") },
                        onSettleAll = { confirmPerson = g.group.name to list },
                    )
                }
                if (pendingByGroup.second.isNotEmpty()) {
                    item {
                        SectionTitle("Not in a group")
                    }
                    items(pendingByGroup.second, key = { "ng${it.first}" }) { (name, list) ->
                        PersonCard(
                            name = name, list = list, cur = cur, onOpen = onOpen,
                            onSettleOne = { settle(listOf(it), "Marked ${money(it.share.amount, cur)} from $name as received") },
                            onSettleAll = { confirmPerson = name to list },
                        )
                    }
                }
                if (pendingByGroup.first.isEmpty()) {
                    item {
                        AppCard(onClick = onPeople) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("No group splits yet", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Use a group chip (e.g. Flatmates) when splitting to see it here",
                                        style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (settled.isEmpty()) {
                item { EmptyState(Icons.Rounded.CheckCircle, "No settled splits", "Closed splits will show up here") }
            }
            items(settled, key = { it.share.id }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(s.tx.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(s.share.personName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.share.personName, fontWeight = FontWeight.Medium)
                        Text(
                            "${s.tx.note.ifBlank { s.tx.category }} · settled ${s.share.settledOn?.let { LocalDate.ofEpochDay(it).shortLabel() } ?: ""}",
                            style = MaterialTheme.typography.bodySmall, color = extraColors.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(money(s.share.amount, cur), color = extraColors.income, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { vm.setSettled(listOf(s.share.id), false) }) {
                        Icon(Icons.Rounded.Undo, "Reopen split", tint = extraColors.subtle)
                    }
                }
            }
        }
    }

    confirmPerson?.let { (name, list) ->
        AlertDialog(
            onDismissRequest = { confirmPerson = null },
            title = { Text("Settle up with $name?") },
            text = { Text("Mark ${money(list.sumOf { it.share.amount }, cur)} (${list.size} pending ${if (list.size == 1) "share" else "shares"}) as received.") },
            confirmButton = {
                Button(onClick = {
                    confirmPerson = null
                    settle(list, "Settled up with $name")
                }) { Text("Settle all") }
            },
            dismissButton = { TextButton(onClick = { confirmPerson = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PersonCard(
    name: String,
    list: List<ShareItem>,
    cur: String,
    onOpen: (Long) -> Unit,
    onSettleOne: (ShareItem) -> Unit,
    onSettleAll: () -> Unit,
) {
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "rot")
    AppCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${list.size} open ${if (list.size == 1) "split" else "splits"}", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("owes you", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                Text(money(list.sumOf { it.share.amount }, cur), style = MaterialTheme.typography.titleMedium, color = extraColors.income, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.ExpandMore, null, Modifier.rotate(rot), tint = extraColors.subtle)
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                list.sortedByDescending { it.tx.date }.forEach { s ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(s.tx.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.tx.note.ifBlank { s.tx.category }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${LocalDate.ofEpochDay(s.tx.date).shortLabel()} · total ${money(s.tx.amount, cur)}",
                                style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                            )
                        }
                        Text(money(s.share.amount, cur), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onSettleOne(s) }) { Text("Received") }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onSettleAll, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Settle up")
        }
    }
}

/** Pending money for one saved group: who owes what, and the expenses behind it. */
@Composable
private fun GroupCard(
    name: String,
    list: List<ShareItem>,
    cur: String,
    onOpen: (Long) -> Unit,
    onSettlePerson: (String, List<ShareItem>) -> Unit,
    onSettleAll: () -> Unit,
) {
    var expanded by rememberSaveable("g$name") { mutableStateOf(false) }
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "rot")
    val byPerson = list.groupBy { it.share.personName }.toList().sortedByDescending { (_, l) -> l.sumOf { it.share.amount } }
    val expenses = list.groupBy { it.tx.id }.values.map { it.first().tx to it.sumOf { s -> s.share.amount } }
        .sortedByDescending { it.first.date }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Groups, MaterialTheme.colorScheme.primary, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${expenses.size} open ${if (expenses.size == 1) "split" else "splits"} · ${byPerson.size} ${if (byPerson.size == 1) "person" else "people"}",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("owe you", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                Text(money(list.sumOf { it.share.amount }, cur), style = MaterialTheme.typography.titleMedium, color = extraColors.income, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        byPerson.forEach { (person, l) ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(person, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(person, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${l.size} ${if (l.size == 1) "split" else "splits"}", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                }
                Text(money(l.sumOf { it.share.amount }, cur), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onSettlePerson(person, l) }) { Text("Received") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Expenses", style = MaterialTheme.typography.labelLarge, color = extraColors.subtle, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ExpandMore, if (expanded) "Hide expenses" else "Show expenses", Modifier.rotate(rot), tint = extraColors.subtle)
        }
        AnimatedVisibility(expanded) {
            Column {
                expenses.forEach { (tx, owed) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(tx.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.note.ifBlank { tx.category }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${LocalDate.ofEpochDay(tx.date).shortLabel()} · total ${money(tx.amount, cur)}",
                                style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                            )
                        }
                        Text("${money(owed, cur)} pending", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(onClick = onSettleAll, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text("Settle up group")
        }
    }
}

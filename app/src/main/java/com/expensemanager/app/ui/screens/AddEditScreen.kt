package com.expensemanager.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.Categories
import com.expensemanager.app.data.PaymentMode
import com.expensemanager.app.data.SplitShare
import com.expensemanager.app.data.TransactionEntity
import com.expensemanager.app.data.TransactionWithShares
import com.expensemanager.app.data.GroupWithMembers
import com.expensemanager.app.data.Person
import com.expensemanager.app.data.detectGroup
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import com.expensemanager.app.data.TxType
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.Avatar
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.key
import com.expensemanager.app.ui.label
import com.expensemanager.app.ui.minorToInput
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.monthFromKey
import com.expensemanager.app.ui.parseMoney
import com.expensemanager.app.ui.AmountTransformation
import com.expensemanager.app.ui.sanitizeAmount
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contacts
import com.expensemanager.app.ui.theme.extraColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One row of the split editor. [locked] = user typed a custom amount, so equal-split leaves it alone. */
private data class Participant(
    val name: String,
    val phone: String = "",
    val isMe: Boolean = false,
    val text: String = "",
    val locked: Boolean = false,
    val settled: Boolean = false,
    val settledOn: Long? = null,
)

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    vm: AppViewModel,
    id: Long,
    initialType: TxType,
    copy: Boolean,
    onMessage: (String) -> Unit,
    onDeleted: (TransactionWithShares) -> Unit,
    onClose: () -> Unit,
    onDuplicate: (Long) -> Unit,
    onManagePeople: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val knownPeople by vm.knownPeople.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val cur = settings.currency
    val isEdit = id != 0L && !copy
    var original by remember { mutableStateOf<TransactionWithShares?>(null) }

    var loaded by remember { mutableStateOf(id == 0L) }
    var type by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.forType(initialType).first().name) }
    var mode by remember { mutableStateOf(PaymentMode.DEFAULT) }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var shiftMonth by remember { mutableStateOf(false) }
    var accountMonth by remember { mutableStateOf(YearMonth.now()) }
    var createdAt by remember { mutableStateOf(System.currentTimeMillis()) }

    var splitOn by remember { mutableStateOf(false) }
    var includeMe by remember { mutableStateOf(true) }
    val participants = remember { mutableStateListOf<Participant>() }

    var showDate by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val total = parseMoney(amountText) ?: 0L

    /** Rows shown in the split editor: "You" (optional) followed by other people. */
    fun rows(): List<Participant> = participants.toList()

    /** Distributes whatever isn't covered by custom (locked) amounts equally across the rest. */
    fun recalc() {
        val list = rows()
        if (list.isEmpty()) return
        val lockedSum = list.filter { it.locked }.sumOf { parseMoney(it.text) ?: 0L }
        val free = list.indices.filter { !list[it].locked }
        if (free.isEmpty()) return
        val remaining = (total - lockedSum).coerceAtLeast(0)
        val each = remaining / free.size
        var extra = remaining % free.size
        free.forEach { i ->
            val v = each + if (extra > 0) 1 else 0
            if (extra > 0) extra--
            participants[i] = list[i].copy(text = minorToInput(v))
        }
    }

    fun ensureMe() {
        val idx = participants.indexOfFirst { it.isMe }
        if (includeMe && idx < 0) participants.add(0, Participant("You", isMe = true))
        if (!includeMe && idx >= 0) participants.removeAt(idx)
    }

    fun resetEqual() {
        for (i in participants.indices) participants[i] = participants[i].copy(locked = false)
        recalc()
    }

    var newName by remember { mutableStateOf("") }

    /** Adds people (from a group / favourite) who aren't already in the split. */
    fun addPeople(people: List<Person>) {
        people.filter { p -> participants.none { !it.isMe && it.name.equals(p.name, ignoreCase = true) } }
            .forEach { participants.add(Participant(it.name, it.phone)) }
        ensureMe()
        recalc()
        error = null
    }

    fun removePeople(names: List<String>) {
        participants.removeAll { p -> !p.isMe && names.any { it.equals(p.name, ignoreCase = true) } }
        recalc()
    }

    /** Group this split is for (set when a group chip / group in the picker is used). */
    var splitGroupId by remember { mutableStateOf<Long?>(null) }

    fun addGroup(g: GroupWithMembers) {
        splitGroupId = g.group.id
        addPeople(g.members.map { Person(it.name, it.phone) })
    }

    fun removeGroup(g: GroupWithMembers) {
        removePeople(g.members.map { it.name })
        if (splitGroupId == g.group.id) splitGroupId = null
    }

    /** Adds a free-text person (not from contacts) to the split. */
    fun addByName() {
        val name = newName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return
        if (participants.none { !it.isMe && it.name.equals(name, ignoreCase = true) }) {
            participants.add(Participant(name))
            ensureMe()
            recalc()
        }
        newName = ""
        error = null
    }

    // Load existing transaction for edit / duplicate.
    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        val t = vm.load(id) ?: run { onClose(); return@LaunchedEffect }
        original = t
        type = TxType.valueOf(t.tx.type)
        amountText = minorToInput(t.tx.amount)
        category = t.tx.category
        mode = PaymentMode.of(t.tx.paymentMode)
        note = t.tx.note
        if (copy) {
            date = LocalDate.now()
            accountMonth = YearMonth.now()
            shiftMonth = false
        } else {
            date = LocalDate.ofEpochDay(t.tx.date)
            accountMonth = monthFromKey(t.tx.accountMonth)
            shiftMonth = accountMonth != YearMonth.from(date)
            createdAt = t.tx.createdAt
        }
        if (t.shares.isNotEmpty()) {
            splitOn = true
            splitGroupId = t.tx.groupId
            participants.clear()
            val mine = t.myShare
            includeMe = mine > 0
            if (includeMe) participants.add(Participant("You", isMe = true, text = minorToInput(mine)))
            t.shares.forEach {
                participants.add(
                    Participant(
                        it.personName, it.phone, text = minorToInput(it.amount),
                        settled = it.settled && !copy, settledOn = if (copy) null else it.settledOn,
                    ),
                )
            }
            // Keep custom amounts as-is if they weren't an equal split.
            val amounts = participants.map { parseMoney(it.text) ?: 0L }
            val unequal = amounts.max() - amounts.min() > 1
            if (unequal) for (i in participants.indices) participants[i] = participants[i].copy(locked = true)
        }
        loaded = true
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (id == 0L) runCatching { focus.requestFocus() } }

    val others = participants.filter { !it.isMe }
    val shareSum = participants.sumOf { parseMoney(it.text) ?: 0L }
    val splitMismatch = splitOn && type == TxType.EXPENSE && participants.isNotEmpty() && shareSum != total

    fun save() {
        error = when {
            total <= 0 -> "Enter an amount"
            splitOn && type == TxType.EXPENSE && others.isEmpty() -> "Add at least one person to split with"
            splitMismatch -> "Shares add up to ${money(shareSum, cur)} but total is ${money(total, cur)}"
            else -> null
        }
        if (error != null) return
        val month = if (shiftMonth) accountMonth else YearMonth.from(date)
        val isSplit = splitOn && type == TxType.EXPENSE
        val names = others.map { it.name }
        // Keep the chosen group while any of its members is still in the split
        // (e.g. one flatmate skipped this dinner); otherwise infer it from who's included.
        val chosen = groups.firstOrNull { it.group.id == splitGroupId }
            ?.takeIf { g -> g.members.any { m -> names.any { it.equals(m.name, ignoreCase = true) } } }
        val groupId = if (isSplit) (chosen ?: detectGroup(names, groups))?.group?.id else null
        val tx = TransactionEntity(
            id = if (isEdit) id else 0,
            type = type.name,
            amount = total,
            category = category,
            paymentMode = mode.name,
            note = note.trim(),
            date = date.toEpochDay(),
            accountMonth = month.key(),
            createdAt = if (isEdit) createdAt else System.currentTimeMillis(),
            groupId = groupId,
        )
        val shares = if (splitOn && type == TxType.EXPENSE) {
            others.mapNotNull { p ->
                val amt = parseMoney(p.text) ?: 0L
                if (amt <= 0) null
                else SplitShare(transactionId = 0, personName = p.name, phone = p.phone, amount = amt, settled = p.settled, settledOn = p.settledOn)
            }
        } else emptyList()
        vm.save(tx, shares) {
            onMessage(if (isEdit) "Saved" else "${if (type == TxType.EXPENSE) "Expense" else "Income"} added")
        }
        onClose()
    }

    /** Changes the date, keeping any "counts in" month offset relative to it. */
    fun changeDate(newDate: LocalDate) {
        if (shiftMonth) {
            val diff = YearMonth.from(date).until(accountMonth, java.time.temporal.ChronoUnit.MONTHS)
            accountMonth = YearMonth.from(newDate).plusMonths(diff)
        }
        date = newDate
    }

    val accent = if (type == TxType.EXPENSE) extraColors.expense else extraColors.income

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isEdit -> "Edit transaction"
                            copy -> "Duplicate"
                            else -> "New transaction"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") } },
                actions = {
                    if (isEdit) {
                        IconButton(onClick = { onDuplicate(id) }) { Icon(Icons.Rounded.ContentCopy, "Duplicate") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete", tint = extraColors.expense) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Button(
                        onClick = ::save, enabled = loaded,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(if (isEdit) "Save changes" else "Save", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t,
                        onClick = {
                            if (type != t) {
                                type = t
                                category = Categories.forType(t).first().name
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, TxType.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = (if (t == TxType.EXPENSE) extraColors.expense else extraColors.income).copy(alpha = 0.2f),
                            activeContentColor = if (t == TxType.EXPENSE) extraColors.expense else extraColors.income,
                        ),
                    ) { Text(if (t == TxType.EXPENSE) "Expense" else "Income") }
                }
            }

            // Amount
            TextField(
                value = amountText,
                onValueChange = {
                    amountText = sanitizeAmount(it)
                    error = null
                    if (splitOn) recalc()
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = accent),
                placeholder = { Text("0", fontSize = 40.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = extraColors.subtle) },
                prefix = { Text(cur, fontSize = 28.sp, color = extraColors.subtle) },
                singleLine = true,
                visualTransformation = AmountTransformation(indian = cur == "₹"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            OutlinedTextField(
                value = note, onValueChange = { note = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                placeholder = { Text(if (type == TxType.EXPENSE) "e.g. Dinner at Truffles" else "e.g. October salary") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.medium,
            )

            // Category
            Label("Category")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4,
            ) {
                Categories.forType(type).forEach { c ->
                    val sel = c.name == category
                    Column(
                        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                            .background(if (sel) c.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { category = c.name }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconBubble(c.icon, c.color, 36.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            c.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (sel) c.color else MaterialTheme.colorScheme.onSurface, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
                // Pad the last row so tiles keep equal widths.
                repeat((4 - Categories.forType(type).size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }

            // Payment mode
            Label(if (type == TxType.EXPENSE) "Paid via" else "Received in")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PaymentMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m, onClick = { mode = m },
                        label = { Text(m.label) },
                        leadingIcon = { Icon(modeIcon(m), null, Modifier.size(18.dp)) },
                    )
                }
            }

            // Date + month it counts in
            Label("Date")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.CalendarMonth, MaterialTheme.colorScheme.primary, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable { showDate = true }) {
                        Text("Transaction date", style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
                        Text(date.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { showDate = true }) { Icon(Icons.Rounded.Edit, "Change date") }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = LocalDate.now()
                    FilterChip(date == today, { changeDate(today) }, { Text("Today") })
                    FilterChip(date == today.minusDays(1), { changeDate(today.minusDays(1)) }, { Text("Yesterday") })
                    FilterChip(false, { showDate = true }, { Text("Pick date") })
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Schedule, MaterialTheme.colorScheme.tertiary, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Count in a different month", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(checked = shiftMonth, onCheckedChange = {
                        shiftMonth = it
                        if (it) accountMonth = YearMonth.from(date).plusMonths(1)
                    })
                }
                AnimatedVisibility(shiftMonth) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { accountMonth = accountMonth.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Counts in", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                            Text(accountMonth.label(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        }
                        IconButton(onClick = { accountMonth = accountMonth.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
                    }
                }
            }

            // Split
            if (type == TxType.EXPENSE) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.CallSplit, MaterialTheme.colorScheme.primary, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Split this expense", style = MaterialTheme.typography.bodyLarge)
                            Text("Track who owes you", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                        }
                        Switch(checked = splitOn, onCheckedChange = {
                            splitOn = it
                            error = null
                            if (it) {
                                ensureMe()
                                recalc()
                            }
                        })
                    }
                    AnimatedVisibility(splitOn) {
                        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAdd(
                                groups = groups,
                                favourites = favourites,
                                isIn = { name -> others.any { it.name.equals(name, ignoreCase = true) } },
                                onAdd = ::addPeople,
                                onRemove = ::removePeople,
                                onAddGroup = ::addGroup,
                                onRemoveGroup = ::removeGroup,
                                onMore = { showPicker = true },
                                onManage = onManagePeople,
                            )
                            participants.forEachIndexed { i, p ->
                                ShareRow(
                                    p = p, currency = cur,
                                    onAmount = { txt ->
                                        participants[i] = p.copy(text = sanitizeAmount(txt), locked = true)
                                        error = null
                                        recalc()
                                    },
                                    onUnlock = { participants[i] = p.copy(locked = false); recalc() },
                                    // Removing yourself means you paid entirely on others' behalf.
                                    onRemove = if (p.isMe) ({ includeMe = false; ensureMe(); recalc() })
                                    else ({ participants.removeAt(i); recalc() }),
                                )
                            }
                            if (!includeMe) {
                                TextButton(onClick = { includeMe = true; ensureMe(); recalc() }) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
                                    Text("Add my share back")
                                }
                            }
                            // Add someone who isn't in contacts by just typing their name.
                            OutlinedTextField(
                                value = newName, onValueChange = { newName = it.take(40) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Type a name to add") },
                                leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                                trailingIcon = {
                                    if (newName.isNotBlank()) IconButton(onClick = ::addByName) { Icon(Icons.Rounded.Add, "Add person") }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addByName() }),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Contacts, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Contacts")
                                }
                                OutlinedButton(onClick = ::resetEqual, modifier = Modifier.weight(1f), enabled = participants.size > 1) {
                                    Icon(Icons.Rounded.Balance, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Split equally")
                                }
                            }
                            if (participants.isNotEmpty()) {
                                val owed = others.sumOf { parseMoney(it.text) ?: 0L }
                                Text(
                                    if (splitMismatch) "Shares total ${money(shareSum, cur)} · ${money(total - shareSum, cur, signed = true)} unassigned"
                                    else "Others owe you ${money(owed, cur)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (splitMismatch) MaterialTheme.colorScheme.error else extraColors.income,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { changeDate(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (showPicker) {
        ContactPickerDialog(
            initial = others.map { Person(it.name, it.phone) },
            recentPeople = knownPeople,
            favourites = favourites,
            groups = groups,
            onToggleFavourite = { vm.toggleFavourite(it) },
            onDismiss = { showPicker = false },
            onDone = { picked ->
                showPicker = false
                val keep = participants.filter { p -> p.isMe || picked.any { it.name.equals(p.name, true) } }
                val added = picked.filter { n -> keep.none { it.name.equals(n.name, true) } }.map { Participant(it.name, it.phone) }
                participants.clear()
                participants.addAll(keep + added)
                ensureMe()
                recalc()
                error = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = { Text("This also removes any split records attached to it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                    original?.let(onDeleted)
                    onClose()
                }) { Text("Delete", color = extraColors.expense) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = extraColors.subtle)
}

@Composable
private fun ShareRow(
    p: Participant,
    currency: String,
    onAmount: (String) -> Unit,
    onUnlock: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p.name, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.locked) {
                    Row(
                        Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onUnlock),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(" Custom · tap to reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                } else {
                    Text("Equal share", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                }
                if (p.settled) Text("  · Settled", style = MaterialTheme.typography.labelSmall, color = extraColors.income)
            }
        }
        OutlinedTextField(
            value = p.text, onValueChange = onAmount,
            modifier = Modifier.width(120.dp),
            prefix = { Text(currency) },
            singleLine = true,
            visualTransformation = AmountTransformation(indian = currency == "₹"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove ${p.name}", tint = extraColors.subtle) }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}


/**
 * One scrollable row of chips: each saved group, a single "Favourites" chip that opens a
 * checklist of all favourites, and "More" for the full picker. Tapping a selected group
 * chip removes that group again.
 */
@Composable
private fun QuickAdd(
    groups: List<GroupWithMembers>,
    favourites: List<Person>,
    isIn: (String) -> Boolean,
    onAdd: (List<Person>) -> Unit,
    onRemove: (List<String>) -> Unit,
    onAddGroup: (GroupWithMembers) -> Unit,
    onRemoveGroup: (GroupWithMembers) -> Unit,
    onMore: () -> Unit,
    onManage: () -> Unit,
) {
    var showFavs by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quick add", style = MaterialTheme.typography.labelLarge, color = extraColors.subtle, modifier = Modifier.weight(1f))
            TextButton(onClick = onManage) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                Text(if (groups.isEmpty() && favourites.isEmpty()) "Set up groups & favourites" else "Manage")
            }
        }
        if (groups.isNotEmpty() || favourites.isNotEmpty()) {
            val favsIn = favourites.count { isIn(it.name) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { "g${it.group.id}" }) { g ->
                    val all = g.members.isNotEmpty() && g.members.all { isIn(it.name) }
                    FilterChip(
                        selected = all,
                        onClick = { if (all) onRemoveGroup(g) else onAddGroup(g) },
                        label = { Text("${g.group.name} (${g.members.size})") },
                        leadingIcon = { Icon(Icons.Rounded.Groups, null, Modifier.size(18.dp)) },
                    )
                }
                if (favourites.isNotEmpty()) {
                    item(key = "favs") {
                        FilterChip(
                            selected = favsIn > 0,
                            onClick = { showFavs = true },
                            label = { Text(if (favsIn > 0) "Favourites ($favsIn)" else "Favourites") },
                            leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary) },
                        )
                    }
                }
                item(key = "more") {
                    AssistChip(
                        onClick = onMore,
                        label = { Text("More") },
                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }

    if (showFavs) {
        FavouritesDialog(
            favourites = favourites,
            initiallyIn = favourites.filter { isIn(it.name) }.map { it.name },
            onDismiss = { showFavs = false },
            onDone = { chosen ->
                showFavs = false
                onAdd(favourites.filter { it.name in chosen && !isIn(it.name) })
                onRemove(favourites.filter { it.name !in chosen && isIn(it.name) }.map { it.name })
            },
        )
    }
}

/** Checklist of all favourites; ticked people are in the split. */
@Composable
private fun FavouritesDialog(
    favourites: List<Person>,
    initiallyIn: List<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initiallyIn) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text("Favourites") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                favourites.forEach { f ->
                    val checked = f.name in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { if (checked) chosen.remove(f.name) else chosen.add(f.name) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(f.name, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(f.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Checkbox(checked = checked, onCheckedChange = { if (it) chosen.add(f.name) else chosen.remove(f.name) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen.toSet()) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

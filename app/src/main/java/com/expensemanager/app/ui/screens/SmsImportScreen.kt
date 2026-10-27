package com.expensemanager.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.Categories
import com.expensemanager.app.data.TxType
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.EmptyState
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.SmsItem
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.shortLabel
import com.expensemanager.app.ui.theme.extraColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DAY_MS = 86_400_000L
private val timeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a")

/**
 * "Scan bank SMS": reads bank alerts for a chosen period, lists the spends that aren't
 * in the app yet, and lets the user fix the note / category, untick some, or split one.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmsImportScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onSplit: (Long) -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scan by vm.smsScan.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.currency

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    // Pick up a permission granted from the system settings page.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasPermission() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Scan today on first open; coming back from splitting keeps the current list.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (granted && !started) {
            started = true
            vm.scanSms(LocalDate.now(), LocalDate.now())
        }
    }

    var pickRange by remember { mutableStateOf(false) }
    val selected = scan.items.filter { it.selected }

    Scaffold(
        modifier = Modifier.imePadding(), // keep the note being edited above the keyboard
        topBar = {
            TopAppBar(
                title = { Text("Scan bank SMS") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (granted && scan.items.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = {
                            vm.saveSelectedSms { n ->
                                onMessage(if (n < 0) "Couldn't save" else "Added $n ${if (n == 1) "expense" else "expenses"}")
                            }
                        },
                        enabled = selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            if (selected.isEmpty()) "Nothing selected"
                            else "Add ${selected.size} · ${money(selected.sumOf { it.spend.amount }, cur)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!granted) {
                item {
                    PermissionCard(
                        asked = asked,
                        onAllow = { launcher.launch(Manifest.permission.READ_SMS) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                }
                return@LazyColumn
            }

            item {
                RangeChips(scan.from, scan.to, onPick = { f, t -> vm.scanSms(f, t) }, onCustom = { pickRange = true })
            }

            when {
                scan.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                scan.error != null -> item {
                    EmptyState(Icons.Rounded.Info, scan.error!!, "Check the SMS permission and try again")
                }
                scan.done && scan.items.isEmpty() -> item {
                    EmptyState(
                        Icons.Rounded.Sms, "No new bank spends",
                        if (scan.alreadyImported > 0) "${scan.alreadyImported} from this period already added" else "No spend alerts found for this period",
                    )
                }
                else -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${scan.items.size} found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (scan.alreadyImported > 0) {
                                    Text("${scan.alreadyImported} already added earlier", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                                }
                            }
                            val all = scan.items.all { it.selected }
                            TextButton(onClick = { vm.selectAllSms(!all) }) { Text(if (all) "Select none" else "Select all") }
                        }
                    }
                    items(scan.items, key = { it.spend.ref }) { item ->
                        SmsItemCard(
                            item, cur,
                            onChange = { block -> vm.updateSmsItem(item.spend.ref, block) },
                            onSplit = { vm.saveSmsForSplit(item.spend.ref, onSplit) },
                        )
                    }
                    item {
                        Text(
                            "Only debit and card spend alerts are shown. Some banks don't send an SMS for small UPI payments.",
                            style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                        )
                    }
                }
            }
        }
    }

    if (pickRange) {
        val todayEnd = (LocalDate.now().toEpochDay() + 1) * DAY_MS
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = scan.from.toEpochDay() * DAY_MS,
            initialSelectedEndDateMillis = scan.to.toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis < todayEnd
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis
                        if (start != null) {
                            val end = state.selectedEndDateMillis ?: start
                            vm.scanSms(LocalDate.ofEpochDay(Math.floorDiv(start, DAY_MS)), LocalDate.ofEpochDay(Math.floorDiv(end, DAY_MS)))
                        }
                        pickRange = false
                    },
                ) { Text("Scan") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, Modifier.weight(1f), title = { Text("Pick dates", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun PermissionCard(asked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Rounded.Sms, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Allow SMS access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Spendly reads bank alerts on this phone to find what you spent. Messages are only read on the phone and never leave it.",
            style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle,
        )
        if (asked) {
            Spacer(Modifier.height(10.dp))
            Text(
                "If no permission prompt appeared, open App info → ⋮ menu → Allow restricted settings, then Permissions → SMS → Allow.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAllow) { Text("Allow") }
            if (asked) OutlinedButton(onClick = onOpenSettings) { Text("Open app info") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RangeChips(from: LocalDate, to: LocalDate, onPick: (LocalDate, LocalDate) -> Unit, onCustom: () -> Unit) {
    val today = LocalDate.now()
    val presets = listOf(
        "Today" to (today to today),
        "Yesterday" to (today.minusDays(1) to today.minusDays(1)),
        "Last 7 days" to (today.minusDays(6) to today),
        "This month" to (today.withDayOfMonth(1) to today),
    )
    val preset = presets.firstOrNull { it.second == (from to to) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, range) ->
            FilterChip(preset?.first == label, { onPick(range.first, range.second) }, { Text(label) })
        }
        FilterChip(
            selected = preset == null,
            onClick = onCustom,
            label = {
                Text(
                    when {
                        preset != null -> "Pick dates"
                        from == to -> from.shortLabel()
                        else -> "${from.shortLabel()} – ${to.shortLabel()}"
                    },
                )
            },
        )
    }
}

@Composable
private fun SmsItemCard(item: SmsItem, cur: String, onChange: ((SmsItem) -> SmsItem) -> Unit, onSplit: () -> Unit) {
    val s = item.spend
    // Kept locally while typing so the cursor doesn't jump; every change is pushed up.
    var note by rememberSaveable(s.ref) { mutableStateOf(item.note) }
    var showSms by rememberSaveable(s.ref) { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(s.time).atZone(ZoneId.systemDefault())

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.selected, onCheckedChange = { c -> onChange { it.copy(selected = c) } })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(money(s.amount, cur), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = extraColors.expense)
                Text(
                    "${s.bank} · ${s.mode.label} · ${time.format(timeFmt)}",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
            }
            IconButton(onClick = { showSms = !showSms }) {
                Icon(if (showSms) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (showSms) "Hide message" else "Show message")
            }
        }
        item.warning?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
        if (showSms) {
            Spacer(Modifier.height(8.dp))
            Text(
                s.body,
                style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small).padding(10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { v ->
                note = v.take(80)
                onChange { it.copy(note = note) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            placeholder = { Text("e.g. Lunch with team") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(10.dp))
        val cats = Categories.forType(TxType.EXPENSE)
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = cats.indexOfFirst { it.name == item.category }.coerceAtLeast(0))
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(cats, key = { _, c -> c.name }) { _, c ->
                FilterChip(
                    selected = c.name == item.category,
                    onClick = { onChange { it.copy(category = c.name) } },
                    label = { Text(c.name) },
                    leadingIcon = { Icon(c.icon, null, Modifier.size(18.dp), tint = c.color) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSplit) {
                Icon(Icons.Rounded.CallSplit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add & split")
            }
        }
    }
}

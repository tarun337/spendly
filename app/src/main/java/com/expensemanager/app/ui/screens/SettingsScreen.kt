package com.expensemanager.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.ThemeMode
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.minorToInput
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.parseMoney
import com.expensemanager.app.ui.AmountTransformation
import com.expensemanager.app.ui.sanitizeAmount
import com.expensemanager.app.ui.theme.extraColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, snackbar: SnackbarHostState, onPeople: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val all by vm.transactions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editBudget by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCsv(uri) { ok ->
            scope.launch { snackbar.showSnackbar(if (ok) "Exported ${all.size} transactions" else "Export failed") }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SettingCard(Icons.Rounded.DarkMode, "Appearance", "Dark mode is the default") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val opts = listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System")
                opts.forEachIndexed { i, (m, label) ->
                    SegmentedButton(
                        selected = settings.theme == m,
                        onClick = { vm.updateSettings { it.copy(theme = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                    ) { Text(label) }
                }
            }
        }

        SettingCard(Icons.Rounded.CurrencyExchange, "Currency", "Symbol used across the app") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("₹", "$", "€", "£", "¥", "AED ").forEach { c ->
                    FilterChip(settings.currency == c, { vm.updateSettings { it.copy(currency = c) } }, { Text(c.trim()) })
                }
            }
        }

        SettingCard(
            Icons.Rounded.Savings, "Monthly budget",
            if (settings.monthlyBudget > 0) "${money(settings.monthlyBudget, settings.currency)} per month" else "Not set — track spending against a limit",
        ) {
            TextButton(onClick = { editBudget = true }) { Text(if (settings.monthlyBudget > 0) "Change" else "Set budget") }
        }

        SettingCard(Icons.Rounded.Groups, "People & groups", "Groups like Flatmates and favourite people for quick splits") {
            TextButton(onClick = onPeople) { Text("Manage") }
        }

        SettingCard(Icons.Rounded.FileDownload, "Export to CSV", "Open in Excel / Google Sheets") {
            TextButton(onClick = { exporter.launch("expenses-${LocalDate.now()}.csv") }) { Text("Export") }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "All data stays on this device. Tip: swipe left on a transaction in History to delete it, and use Duplicate when editing to repeat a recurring payment.",
            style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
        )
    }

    if (editBudget) {
        var text by remember { mutableStateOf(if (settings.monthlyBudget > 0) minorToInput(settings.monthlyBudget) else "") }
        AlertDialog(
            onDismissRequest = { editBudget = false },
            title = { Text("Monthly budget") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { v -> text = sanitizeAmount(v) },
                    prefix = { Text(settings.currency) }, singleLine = true,
                    visualTransformation = AmountTransformation(indian = settings.currency == "₹"),
                    placeholder = { Text("0 to disable") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseMoney(text) ?: 0
                    vm.updateSettings { it.copy(monthlyBudget = v) }
                    editBudget = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editBudget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingCard(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, MaterialTheme.colorScheme.primary, 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

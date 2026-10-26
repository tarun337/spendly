package com.expensemanager.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensemanager.app.data.Categories
import com.expensemanager.app.data.PaymentMode
import com.expensemanager.app.data.TxType
import com.expensemanager.app.ui.AppCard
import com.expensemanager.app.ui.AppViewModel
import com.expensemanager.app.ui.EmptyState
import com.expensemanager.app.ui.IconBubble
import com.expensemanager.app.ui.MonthSwitcher
import com.expensemanager.app.ui.SectionTitle
import com.expensemanager.app.ui.TransactionRow
import com.expensemanager.app.ui.computeStats
import com.expensemanager.app.ui.key
import com.expensemanager.app.ui.money
import com.expensemanager.app.ui.theme.extraColors
import java.time.LocalDate
import java.time.YearMonth

fun modeIcon(mode: PaymentMode): ImageVector = when (mode) {
    PaymentMode.CASH -> Icons.Rounded.Payments
    PaymentMode.DEBIT_CARD -> Icons.Rounded.Payment
    PaymentMode.CREDIT_CARD -> Icons.Rounded.CreditCard
    PaymentMode.UPI -> Icons.Rounded.PhoneAndroid
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAdd: (TxType) -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSplits: () -> Unit,
) {
    val all by vm.transactions.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().key()) }
    val month = YearMonth.of(monthKey / 100, monthKey % 100)
    val stats = remember(all, monthKey) { computeStats(all, month) }
    val recent = remember(all, monthKey) { all.filter { it.tx.accountMonth == monthKey }.take(6) }
    val pending = remember(all) { all.flatMap { it.shares }.filter { !it.settled } }
    val cur = settings.currency

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(TxType.EXPENSE) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(top = pad.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Overview", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    MonthSwitcher(month, { monthKey = it.key() })
                }
            }

            item { SummaryCard(stats.income, stats.expense, stats.pendingSplits, cur, onAdd) }

            if (settings.monthlyBudget > 0) {
                item { BudgetCard(stats.expense, settings.monthlyBudget, month, cur) }
            }

            if (pending.isNotEmpty()) {
                item {
                    val people = pending.map { it.personName }.distinct().size
                    AppCard(onClick = onSplits) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Rounded.CallSplit, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("You are owed", style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle)
                                Text(money(pending.sumOf { it.amount }, cur), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = extraColors.income)
                                Text("from $people ${if (people == 1) "person" else "people"}", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = extraColors.subtle)
                        }
                    }
                }
            }

            if (stats.byCategory.isNotEmpty()) {
                item { CategoryCard(stats.byCategory, stats.expense, cur) }
            }
            if (all.isNotEmpty()) {
                item { TrendCard(remember(all, monthKey) { (5 downTo 0).map { month.minusMonths(it.toLong()) }.map { it to computeStats(all, it) } }, month, cur) }
            }
            if (stats.byMode.isNotEmpty()) {
                item { ModeCard(stats.byMode, cur) }
            }

            item {
                SectionTitle("Recent") { TextButton(onClick = onSeeAll) { Text("See all") } }
            }
            if (recent.isEmpty()) {
                item { EmptyState(Icons.Rounded.ReceiptLong, "No transactions yet", "Tap Add to record your first one") }
            } else {
                items(recent, key = { it.tx.id }) { t -> TransactionRow(t, cur) { onOpen(t.tx.id) } }
            }
        }
    }
}

@Composable
private fun SummaryCard(income: Long, expense: Long, pendingSplits: Long, cur: String, onAdd: (TxType) -> Unit) {
    val net = income - expense
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.55f), primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.surfaceContainer)))
            .padding(20.dp),
    ) {
        Column {
            Text("Net this month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text(
                money(net, cur, signed = true),
                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPill(Icons.Rounded.ArrowDownward, "Income", money(income, cur), extraColors.income, Modifier.weight(1f))
                StatPill(Icons.Rounded.ArrowUpward, "Spent", money(expense, cur), extraColors.expense, Modifier.weight(1f))
            }
            if (pendingSplits > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Includes ${money(pendingSplits, cur)} of split money still to come back",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { onAdd(TxType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Expense")
                }
                FilledTonalButton(onClick = { onAdd(TxType.INCOME) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Income")
                }
            }
        }
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = extraColors.subtle)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun BudgetCard(spent: Long, budget: Long, month: YearMonth, cur: String) {
    val frac = (spent.toFloat() / budget).coerceAtLeast(0f)
    val over = spent > budget
    val color = when {
        over -> extraColors.expense
        frac > 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> extraColors.income
    }
    val today = LocalDate.now()
    val daysLeft = if (YearMonth.from(today) == month) month.lengthOfMonth() - today.dayOfMonth + 1 else 0
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly budget", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(frac * 100).toInt()}%", color = color, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { frac.coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(10.dp))
        val left = budget - spent
        Text(
            if (over) "Over budget by ${money(-left, cur)}"
            else buildString {
                append("${money(left, cur)} left of ${money(budget, cur)}")
                if (daysLeft > 0) append(" · ${money(left / daysLeft, cur)}/day for $daysLeft days")
            },
            style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
        )
    }
}

@Composable
private fun CategoryCard(data: List<Pair<String, Long>>, total: Long, cur: String) {
    val cats = data.map { Categories.find(TxType.EXPENSE.name, it.first) to it.second }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    AppCard {
        Text("Spending by category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 18.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    var start = -90f
                    val gap = if (cats.size > 1) 3f else 0f
                    cats.forEach { (c, v) ->
                        val sweep = 360f * v / total.coerceAtLeast(1)
                        drawArc(c.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Spent", style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
                    Text(money(total, cur), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.take(5).forEach { (c, v) -> LegendRow(c.color, c.name, "${(v * 100 / total.coerceAtLeast(1))}%") }
                if (cats.size > 5) LegendRow(extraColors.subtle, "+${cats.size - 5} more", "")
            }
        }
        Spacer(Modifier.height(16.dp))
        cats.forEach { (c, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(c.icon, c.color, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(money(v, cur), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { v.toFloat() / total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = c.color, trackColor = track, drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
    }
}

@Composable
private fun ModeCard(data: List<Pair<String, Long>>, cur: String) {
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    AppCard {
        Text("Paid via", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        data.forEach { (name, v) ->
            val mode = PaymentMode.of(name)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(modeIcon(mode), MaterialTheme.colorScheme.primary, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(money(v, cur), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { v.toFloat() / max },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** Side-by-side income / spending bars for the 6 months ending at [selected]. */
@Composable
private fun TrendCard(data: List<Pair<YearMonth, com.expensemanager.app.ui.MonthStats>>, selected: YearMonth, cur: String) {
    val max = data.maxOf { maxOf(it.second.income, it.second.expense) }.coerceAtLeast(1)
    val inc = extraColors.income
    val exp = extraColors.expense
    val avg = data.map { it.second.expense }.filter { it > 0 }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 6 months", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            LegendDot(inc, "Income"); Spacer(Modifier.width(12.dp)); LegendDot(exp, "Spent")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            data.forEach { (m, s) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Bar(s.income.toFloat() / max, inc)
                        Bar(s.expense.toFloat() / max, exp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m == selected) MaterialTheme.colorScheme.onSurface else extraColors.subtle,
                        fontWeight = if (m == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Average monthly spend: ${money(avg, cur)}", style = MaterialTheme.typography.bodySmall, color = extraColors.subtle)
    }
}

@Composable
private fun Bar(frac: Float, color: Color) {
    Box(
        Modifier.width(10.dp).fillMaxHeight(frac.coerceIn(0.02f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (frac > 0) color else color.copy(alpha = 0.2f)),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle)
    }
}

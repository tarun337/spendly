package com.expensemanager.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensemanager.app.data.Categories
import com.expensemanager.app.data.PaymentMode
import com.expensemanager.app.data.TransactionWithShares
import com.expensemanager.app.ui.theme.extraColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color, size: Dp = 42.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val palette = listOf(0xFF8B7CF6, 0xFF4ADE80, 0xFFFF8A65, 0xFF60A5FA, 0xFFF472B6, 0xFFFBBF24, 0xFF2DD4BF)
    val color = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
            color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.Rounded.ChevronLeft, "Previous month")
        }
        Text(
            month.label(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onChange(YearMonth.now()) }.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.Rounded.ChevronRight, "Next month")
        }
    }
}

@Composable
fun TransactionRow(t: TransactionWithShares, currency: String, onClick: () -> Unit) {
    val cat = Categories.find(t.tx.type, t.tx.category)
    val date = LocalDate.ofEpochDay(t.tx.date)
    val shifted = t.tx.accountMonth != YearMonth.from(date).key()
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(cat.icon, cat.color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.tx.note.ifBlank { t.tx.category },
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${PaymentMode.of(t.tx.paymentMode).label} · ${date.shortLabel()}",
                    style = MaterialTheme.typography.bodySmall, color = extraColors.subtle,
                )
                if (t.isSplit) Icon(Icons.Rounded.CallSplit, "Split", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (shifted) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(monthFromKey(t.tx.accountMonth).shortLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (t.isExpense) "-" else "+") + money(t.tx.amount, currency),
                color = if (t.isExpense) extraColors.expense else extraColors.income,
                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
            )
            if (t.isSplit) {
                val pending = t.pendingFromOthers
                Text(
                    if (pending > 0) "${money(pending, currency)} to get back" else "Yours ${money(t.myShare, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pending > 0) MaterialTheme.colorScheme.tertiary else extraColors.subtle,
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBubble(icon, MaterialTheme.colorScheme.primary, 64.dp)
        Spacer(Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

package com.expensemanager.app.ui

import android.icu.text.NumberFormat
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val inFormat = NumberFormat.getNumberInstance(ULocale("en_IN")).apply {
    minimumFractionDigits = 0; maximumFractionDigits = 2
}
private val usFormat = NumberFormat.getNumberInstance(ULocale.US).apply {
    minimumFractionDigits = 0; maximumFractionDigits = 2
}

/** Formats minor units (paise/cents) as a currency string, e.g. 1234550 -> "₹12,345.5". */
fun money(minor: Long, currency: String, signed: Boolean = false): String {
    val fmt = if (currency == "₹") inFormat else usFormat
    val body = fmt.format(abs(minor) / 100.0)
    val sign = when {
        minor < 0 -> "-"
        signed && minor > 0 -> "+"
        else -> ""
    }
    return "$sign$currency$body"
}

/** Parses user input like "1,234.5" into minor units; null if invalid. */
fun parseMoney(text: String): Long? {
    val clean = text.replace(",", "").trim()
    if (clean.isEmpty()) return null
    val v = clean.toBigDecimalOrNull() ?: return null
    if (v.signum() < 0) return null
    return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Minor units -> editable text without grouping, e.g. 12350 -> "123.5". */
fun minorToInput(minor: Long): String =
    java.math.BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()

fun YearMonth.key(): Int = year * 100 + monthValue
fun monthFromKey(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
private val shortMonthFmt = DateTimeFormatter.ofPattern("MMM yyyy")
private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
private val shortDayFmt = DateTimeFormatter.ofPattern("d MMM")

fun YearMonth.label(): String = format(monthFmt)
fun YearMonth.shortLabel(): String = format(shortMonthFmt)
fun LocalDate.label(): String = format(dayFmt)
fun LocalDate.shortLabel(): String = format(shortDayFmt)

fun LocalDate.relativeLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> label()
    }
}

/** Inserts thousands separators into a raw amount like "1234567.5" (Indian: 12,34,567.5). */
fun groupDigits(raw: String, indian: Boolean): String {
    val dot = raw.indexOf('.')
    val int = if (dot < 0) raw else raw.substring(0, dot)
    val frac = if (dot < 0) "" else raw.substring(dot)
    if (int.length <= 3) return raw
    val grouped = if (indian) {
        val head = int.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,${int.takeLast(3)}"
    } else {
        int.reversed().chunked(3).joinToString(",").reversed()
    }
    return grouped + frac
}

/**
 * Shows commas in amount fields while typing; the underlying text stays plain digits
 * so parsing and cursor handling are unaffected.
 */
class AmountTransformation(private val indian: Boolean) : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        val out = groupDigits(raw, indian)
        // Position in `out` of each raw character.
        val origToOut = IntArray(raw.length + 1)
        var o = 0
        out.forEachIndexed { i, c -> if (c != ',' && o < raw.length) origToOut[o++] = i }
        origToOut[raw.length] = out.length
        val mapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = origToOut[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int) = out.take(offset.coerceIn(0, out.length)).count { it != ',' }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out), mapping)
    }
}

/** Keeps only a valid amount: digits, one dot, max 2 decimals, no leading zeros. */
fun sanitizeAmount(input: String): String {
    val s = input.filter { it.isDigit() || it == '.' }
    val dot = s.indexOf('.')
    val intPart = (if (dot < 0) s else s.substring(0, dot)).trimStart('0').take(10)
    if (dot < 0) return intPart
    return intPart.ifEmpty { "0" } + "." + s.substring(dot + 1).replace(".", "").take(2)
}

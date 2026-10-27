package com.expensemanager.app.data

import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Imports CSV exports from other expense apps, e.g.
 *
 * ```
 * "TIME","TYPE","AMOUNT","CATEGORY","ACCOUNT","NOTES"
 * "Oct 25, 2024 8:09 PM","(-) Expense","4500.00","Investment","Savings",""
 * ```
 *
 * Source categories/accounts are mapped onto the app's own [Categories] and [PaymentMode]s.
 */
object CsvImport {

    data class Result(
        val transactions: List<TransactionEntity>,
        /** 1-based line numbers (data rows) that could not be read, with the reason. */
        val errors: List<String>,
    )

    class FormatException(message: String) : Exception(message)

    private val timeFormats: List<DateTimeFormatter> = listOf(
        "MMM d, yyyy h:mm a",
        "MMM d, yyyy h:mm:ss a",
        "MMM d, yyyy",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    /** Source category -> app category (see [Categories]). Keys are lower-case. */
    private val expenseCategoryMap = mapOf(
        "food" to "Food",
        "bills" to "Bills",
        "transportation" to "Transport",
        "transport" to "Transport",
        "friend" to "Friends",
        "friends" to "Friends",
        "entertainment" to "Entertainment",
        "sport" to "Sport",
        "sports" to "Sport",
        "health" to "Health",
        "clothing" to "Shopping",
        "shopping" to "Shopping",
        "investment" to "Investment",
        "beauty" to "Shopping",
        "car" to "Fuel",
        "fuel" to "Fuel",
        "home" to "Groceries",
        "groceries" to "Groceries",
        "rent" to "Rent",
        "education" to "Education",
        "travel" to "Travel",
        "gift" to "Gifts",
        "gifts" to "Gifts",
        "family" to "Family",
    )

    private val incomeCategoryMap = mapOf(
        "refunds" to "Refund",
        "refund" to "Refund",
        "salary" to "Salary",
        "business" to "Business",
        "freelance" to "Freelance",
        "interest" to "Interest",
        "gift" to "Gift",
        "gifts" to "Gift",
        "family" to "Family",
        "friend" to "Friends",
        "friends" to "Friends",
    )

    /** Source account -> [PaymentMode]. Unknown accounts default to UPI. */
    private fun paymentMode(account: String): PaymentMode = when (account.trim().lowercase()) {
        "cash" -> PaymentMode.CASH
        "card", "credit card", "credit" -> PaymentMode.CREDIT_CARD
        "debit card", "debit" -> PaymentMode.DEBIT_CARD
        else -> PaymentMode.UPI // "Savings" (bank account) and anything else
    }

    private fun mapCategory(type: TxType, source: String, note: String): String {
        val key = source.trim().lowercase()
        val known = Categories.forType(type).map { it.name }
        if (type == TxType.EXPENSE && key == "home") {
            // "Home" mixes groceries, rent, help and utilities — refine using the note.
            val n = note.lowercase()
            return when {
                Regex("\\brent").containsMatchIn(n) -> "Rent"
                Regex("\\b(cook|maid)").containsMatchIn(n) -> "Household Help"
                Regex("\\b(electricity|wifi|gas bill)").containsMatchIn(n) -> "Bills"
                else -> "Groceries"
            }
        }
        val mapped = (if (type == TxType.EXPENSE) expenseCategoryMap else incomeCategoryMap)[key]
            ?: known.firstOrNull { it.equals(key, ignoreCase = true) }
        return mapped ?: "Other"
    }

    /**
     * Parses [text] into transactions. Throws [FormatException] if the header is not
     * recognised; individual bad rows are skipped and reported in [Result.errors].
     */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val rows = parseCsv(text.removePrefix("\uFEFF")).filter { r -> r.any { it.isNotBlank() } }
        if (rows.isEmpty()) throw FormatException("The file is empty")

        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
        val iTime = col("time", "date")
        val iType = col("type")
        val iAmount = col("amount")
        val iCategory = col("category")
        val iAccount = col("account", "payment mode")
        val iNotes = col("notes", "note")
        if (iTime == null || iType == null || iAmount == null || iCategory == null) {
            throw FormatException("Unsupported file: expected TIME, TYPE, AMOUNT and CATEGORY columns")
        }

        val errors = mutableListOf<String>()
        val parsed = mutableListOf<Pair<LocalDateTime, TransactionEntity>>()
        rows.drop(1).forEachIndexed { idx, r ->
            val line = idx + 1
            fun cell(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            try {
                val time = parseTime(cell(iTime)) ?: error("bad date \"${cell(iTime)}\"")
                val type = parseType(cell(iType)) ?: error("unknown type \"${cell(iType)}\"")
                val amount = parseAmount(cell(iAmount)) ?: error("bad amount \"${cell(iAmount)}\"")
                val note = cleanNote(cell(iNotes))
                val date = time.toLocalDate()
                parsed += time to TransactionEntity(
                    type = type.name,
                    amount = amount,
                    category = mapCategory(type, cell(iCategory), note),
                    paymentMode = paymentMode(cell(iAccount)).name,
                    note = note,
                    date = date.toEpochDay(),
                    accountMonth = date.year * 100 + date.monthValue,
                    createdAt = time.atZone(zone).toInstant().toEpochMilli(),
                )
            } catch (e: Exception) {
                errors += "Row $line: ${e.message}"
            }
        }

        // Rows sharing the same minute get +1 ms each so their original order is kept
        // (the app sorts by createdAt within a day).
        val seen = HashMap<Long, Int>()
        val txs = parsed.map { (_, tx) ->
            val n = seen.merge(tx.createdAt, 1, Int::plus)!! - 1
            tx.copy(createdAt = tx.createdAt + n)
        }
        return Result(txs, errors)
    }

    /**
     * Identity used to skip rows that are already in the database (e.g. importing the
     * same file twice). Uses the minute of [TransactionEntity.createdAt] so the +n ms
     * ordering offset does not matter.
     */
    fun dedupKey(tx: TransactionEntity): String =
        listOf(tx.type, tx.amount, tx.date, tx.createdAt / 60_000, tx.category, tx.paymentMode, tx.note).joinToString("|")

    /** Removes transactions already present in [existing], counting duplicates (multiset). */
    fun withoutExisting(incoming: List<TransactionEntity>, existing: List<TransactionEntity>): List<TransactionEntity> {
        val counts = existing.groupingBy(::dedupKey).eachCount().toMutableMap()
        return incoming.filter { tx ->
            val k = dedupKey(tx)
            val c = counts[k] ?: 0
            if (c > 0) { counts[k] = c - 1; false } else true
        }
    }

    private fun parseTime(s: String): LocalDateTime? {
        for (f in timeFormats) {
            runCatching { return LocalDateTime.parse(s, f) }
            runCatching { return java.time.LocalDate.parse(s, f).atStartOfDay() }
        }
        return runCatching { java.time.LocalDate.parse(s).atStartOfDay() }.getOrNull() // ISO yyyy-MM-dd
    }

    private fun parseType(s: String): TxType? {
        val t = s.lowercase()
        return when {
            "expense" in t || t.startsWith("(-)") -> TxType.EXPENSE
            "income" in t || t.startsWith("(+)") -> TxType.INCOME
            else -> null
        }
    }

    /** "1,234.50" -> 123450 minor units; negatives are taken as absolute values. */
    private fun parseAmount(s: String): Long? {
        val v = s.replace(",", "").replace(Regex("[^0-9.\\-]"), "").toBigDecimalOrNull() ?: return null
        return v.abs().movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong().takeIf { it > 0 }
    }

    /** Multi-line notes become one line ("a\nb" -> "a, b"); extra spaces are collapsed. */
    private fun cleanNote(s: String): String =
        s.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.joinToString(", ")

    /**
     * RFC 4180-style CSV reader: quoted fields may contain commas, quotes ("") and
     * newlines. Whitespace outside quotes around a quoted field is ignored.
     */
    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var wasQuoted = false
        var i = 0
        fun endField() {
            row += if (wasQuoted) field.toString() else field.toString().trim()
            field.clear(); wasQuoted = false
        }
        fun endRow() { endField(); rows += row; row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> { if (!wasQuoted) field.clear(); inQuotes = true; wasQuoted = true }
                ',' -> endField()
                '\r' -> {}
                '\n' -> endRow()
                else -> if (!wasQuoted) field.append(c) // ignore stray chars after a closing quote
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || wasQuoted) endRow()
        return rows
    }
}

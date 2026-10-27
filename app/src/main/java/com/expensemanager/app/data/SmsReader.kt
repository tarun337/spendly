package com.expensemanager.app.data

import android.content.Context
import android.provider.Telephony

/** Reads the SMS inbox. Requires the READ_SMS permission. */
object SmsReader {

    /** Bank spends received between [fromMillis] (inclusive) and [toMillis] (exclusive), oldest first. */
    fun bankSpends(context: Context, fromMillis: Long, toMillis: Long): List<SmsSpend> {
        val out = ArrayList<SmsSpend>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} < ?",
            arrayOf(fromMillis.toString(), toMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val address = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                SmsParser.parse(address, body, c.getLong(iDate))?.let(out::add)
            }
        }
        return dropRepeats(out)
    }

    /**
     * Drops alerts that describe the same payment twice: an identical message delivered
     * again, or the same amount to the same payee within a few minutes.
     */
    fun dropRepeats(list: List<SmsSpend>): List<SmsSpend> {
        val kept = ArrayList<SmsSpend>()
        for (s in list.sortedBy { it.time }) {
            val repeat = kept.any { k ->
                k.body == s.body ||
                    (k.amount == s.amount && s.time - k.time <= 5 * 60_000 &&
                        (k.merchant.isEmpty() || s.merchant.isEmpty() || k.merchant.equals(s.merchant, ignoreCase = true)))
            }
            if (!repeat) kept += s
        }
        return kept
    }
}

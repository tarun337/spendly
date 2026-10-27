package com.expensemanager.app.data

import java.text.Normalizer

/** A spend found in a bank SMS. [amount] is in minor units (paise). */
data class SmsSpend(
    /** Stable id of the source message, stored on the saved transaction to avoid re-importing it. */
    val ref: String,
    val amount: Long,
    /** Payee / merchant as written in the SMS, tidied up; empty if none was found. */
    val merchant: String,
    val mode: PaymentMode,
    /** Display name of the bank, e.g. "HDFC Bank". */
    val bank: String,
    /** When the SMS was received (epoch millis). */
    val time: Long,
    /** The original message text, shown so the user can check what was read. */
    val body: String,
    /**
     * Money that left the account but probably isn't spending: ATM withdrawals,
     * credit card bill payments, transfers to your own account. Unticked by default.
     */
    val transferHint: String? = null,
)

/**
 * Reads debit / spend alerts sent by Indian banks and card issuers.
 *
 * Banks word their messages differently, so instead of one parser per bank this uses
 * generic rules that cover the common shapes, e.g.
 * - "Sent Rs.500.00 From HDFC Bank A/C *1234 To NAME On 01/01/26 Ref …"
 * - "Spent Rs.1,200 On HDFC Bank Card 1234 At MERCHANT On 2026-10-01:12:30:11 …"
 * - "Spent INR 623 using Axis Bank Credit Card no. XX1234 on 24-10-26 … IST at SWIGGY. Avl limit …"
 * - "Your A/c XX4614 debited by Rs. 1,172.06 on 15/01/26; REDBUS credited. RRN …"  (IDFC FIRST)
 * - "Transaction Successful! INR 500 spent on your IDFC FIRST Bank Credit Card ending XX1234 at …"
 * - "ICICI Bank Acct XX051 debited for Rs 180.00 on 10-Nov-25; NAME credited. UPI:…"
 * - "Dear UPI user A/C X1234 debited by 100.0 on date 01Jan24 trf to NAME Refno …"  (SBI)
 *
 * Only money going out is returned; credits, OTPs, reminders, failed or reversed
 * transactions and offers are ignored.
 */
object SmsParser {

    private val IC = setOf(RegexOption.IGNORE_CASE)

    /** Sender code (from DLT headers like "AX-HDFCBK-S") -> bank name. Matched with "contains". */
    private val banks = listOf(
        "HDFC" to "HDFC Bank",
        "IDFC" to "IDFC FIRST Bank",
        "AXIS" to "Axis Bank",
        "ICICI" to "ICICI Bank",
        "SBI" to "SBI",
        "KOTAK" to "Kotak Bank",
        "KMBL" to "Kotak Bank",
        "YESB" to "Yes Bank",
        "INDUS" to "IndusInd Bank",
        "AUBANK" to "AU Bank",
        "AUBNK" to "AU Bank",
        "AUSFB" to "AU Bank",
        "FEDBNK" to "Federal Bank",
        "FEDBK" to "Federal Bank",
        "FEDFIB" to "Federal Bank",
        "PNB" to "PNB",
        "BOBTXN" to "Bank of Baroda",
        "BOBCRD" to "Bank of Baroda",
        "BOBSMS" to "Bank of Baroda",
        "CANBNK" to "Canara Bank",
        "CNRBNK" to "Canara Bank",
        "UNIONB" to "Union Bank",
        "UBOI" to "Union Bank",
        "IDBI" to "IDBI Bank",
        "RBL" to "RBL Bank",
        "ONECRD" to "OneCard",
        "ONECARD" to "OneCard",
        "AMEX" to "Amex",
        "SCBANK" to "Standard Chartered",
        "STANCB" to "Standard Chartered",
        "HSBC" to "HSBC",
        "CITI" to "Citi",
        "DBSBNK" to "DBS Bank",
        "BOIIND" to "Bank of India",
        "IOBCHN" to "Indian Overseas Bank",
        "INDBNK" to "Indian Bank",
        "CENTBK" to "Central Bank",
        "PAYTMB" to "Paytm Payments Bank",
        "AIRBNK" to "Airtel Payments Bank",
        "JUPITR" to "Jupiter",
        "SLICE" to "slice",
    )

    /**
     * Bank name for an SMS sender, or null if it isn't a bank. Indian business SMS come
     * from headers like "VM-HDFCBK-S" (operator-CODE-type); personal numbers are ignored.
     */
    fun bankFor(address: String): String? {
        val a = address.trim().uppercase()
        val parts = a.split('-').filter { it.isNotEmpty() }
        val code = when {
            parts.size >= 2 && parts[0].length == 2 -> parts[1]
            else -> parts.firstOrNull() ?: return null
        }
        if (code.count { it.isLetter() } < 3) return null // phone numbers, short codes
        banks.firstOrNull { code.contains(it.first) }?.let { return it.second }
        return if (Regex("(BK|BNK|BANK)$").containsMatchIn(code)) code else null
    }

    private val otp = Regex("""\botp\b|one[- ]time password|verification code|\bcode is\b|\bpin\b.*\bgenerat""", IC)
    private val notYet = Regex(
        """will be (?:debited|charged|processed|deducted)|to be debited|would be debited|\bdue\b|statement|bill alert|""" +
            """ignore if (?:already )?paid|reminder|scheduled for""",
        IC,
    )
    private val request = Regex("""has requested|requested money|collect request|payment request|requesting""", IC)
    private val failed = Regex("""declined|\bfailed\b|unsuccessful|not successful|could not be|insufficient|rejected""", IC)
    private val reversed = Regex("""revers(?:ed|al)|refund|cash ?back""", IC)
    private val incoming = Regex(
        """credited (?:to|in|into) (?:your|ur|the|a/?c|acct|account|card)|has been credited|is credited to|""" +
            """received (?:in|on|into) your|\breceived (?:rs\.?|inr|₹)|\bdeposited\b""",
        IC,
    )
    private val billPaid = Regex(
        """payment .{0,50}\breceived\b|received .{0,30}payment|thank you for (?:your |the )?payment|payment .{0,40}credited""",
        IC,
    )
    private val mandateSetup = Regex(
        """mandate .{0,50}(?:created|registered|set ?up|activated|modified|revoked|cancel)|autopay .{0,40}(?:set ?up|activated|registered|enabled)""",
        IC,
    )
    private val promo = Regex("""pre-?approved|\boffer\b|apply now|get up to|upgrade|limit (?:increase|enhance)|\beligible\b|\bwin\b|congratulations""", IC)

    private val spendWord = Regex(
        """debited|\bdebit\b|\bspent\b|\bsent\b|\bpaid\b|withdrawn|withdrawal|\bpurchase|\btxn\b|\bused\b|""" +
            """payment successful|processed successfully|w/d@|transaction (?:number|of)""",
        IC,
    )

    private val foreign = Regex("""\b(?:USD|EUR|GBP|AED|SGD|AUD|CAD|JPY|CHF|THB|MYR|HKD|SAR|QAR|NZD|CNY|OMR|KWD|BHD)\s?[0-9]""")

    private val amountRx = Regex(
        """(?:\b(?:rs\.?|inr)|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|debited (?:by|for|with)\s+([0-9][0-9,]*(?:\.[0-9]{1,2})?)\b""",
        IC,
    )
    private val balanceBefore = Regex("""(?:bal|balance|limit|lmt)\b[^a-z]*$|(?:bal|balance|limit|lmt)\s+(?:is|of)\s*$""", IC)

    private val atm = Regex("""\batm\b|cash wdl|cash withdrawal|withdrawn|w/d@|nfs\*?cash""", IC)
    private val creditCard = Regex(
        """credit card|credit crd|\bcc\b|block ?p?cc|blkcc|avl\.? ?(?:limit|lmt)|avbl\.? ?(?:limit|lmt)|available (?:credit )?limit|onecard""",
        IC,
    )
    private val upi = Regex("""\bupi\b|\bvpa\b|@[a-z]{2,}""", IC)
    private val card = Regex("""debit card|\bcard\b|\bpos\b|block ?dc""", IC)

    private val cardBill = Regex("""credit card|\bcc ?(?:bill|pay)|card bill|\bcred\b|billdesk|\bbbps\b|ccpay""", IC)
    private val ownAccount = Regex("""a/?c(?:ct)?\.?\s*(?:no\.?\s*)?[x*]+\d+\s+(?:is\s+)?credited""", IC)

    /** Where the useful part of a message ends: balances, helpline text, references. */
    private val tail = Regex(
        """\b(?:not you|not done|avl|avbl|avb|available|if not|call|sms block|to dispute|updated bal|new bal|bal|ref|refno|rrn|""" +
            """upi ref|upi:|imps:|neft rrn|fraud)\b""",
        IC,
    )

    private val merchantPatterns = listOf(
        // "…; REDBUS credited" (ICICI, IDFC)
        Regex(""";\s*(.+?)\s+(?:is\s+)?credited""", IC),
        // "to VPA swiggy@icici"
        Regex("""\bvpa\s*:?\s*([\w.\-]+@[\w.\-]+)""", IC),
        // "towards Netflix Entertainment for AutoPay"
        Regex("""\btowards\s+(.+?)(?:\s+for\s+|\s+umrn|\s+id:|\.\s|\.$|$)""", IC),
        // "for Netflix via e-mandate"
        Regex("""\bfor\s+(.+?)\s+via\s+e-?mandate""", IC),
        // "At SWIGGY On 2026-…", "at INSTAMART.", "at merchant@ybl by UPI"
        Regex(
            """\bat\s+(.+?)(?=\s+on\s+\d|\s+on\s+[a-z]{3}\b|\s+\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\s+\d{1,2}[a-z]{3}\d{2}|""" +
                """\s+by\s+upi|\.\s|\.$|$)""",
            IC,
        ),
        // "To TEST MERCHANT On 01/01/26", "to merchant@upi (UPI Ref…", "trf to NAME"
        Regex("""\bto\s+(.+?)(?=\s+on\s+\d|\s+on\s+[a-z]{3}\b|\s+via\b|\s+\(|\(|\.\s|\.$|$)""", IC),
        // ICICI card: "on 06-Sep-25 on Swiggy."
        Regex("""\bon\s+\d{1,2}-[a-z]{3}-\d{2,4}\s+on\s+(.+?)(?:\.\s|\.$|$)""", IC),
        // Axis debit card: "from A/c no. XX1234 on BURGRILL 04-12-2025 13:13:27"
        Regex("""\bon\s+([a-z][^0-9]*?)\s+\d{2}-\d{2}-\d{2,4}\b""", IC),
        // Yes Bank: "@UPI_SHOP NAME 23-08-2025"
        Regex("""@\s*(?:upi_)?([a-z][^@]*?)\s+\d{2}-\d{2}-\d{2,4}""", IC),
        // Axis UPI: "UPI/P2M/630099998888/PHONEPE"
        Regex("""\bupi/p2[am]/\d+/([^/]+)""", IC),
        // Axis multi-line card alerts: "… 05-10-25 09:43:27 IST Swiggy Limi"
        Regex("""\d{2}:\d{2}(?::\d{2})?\s*(?:ist)?\s+(?:at\s+)?([a-z].+)$""", IC),
        // ICICI: "InfoBIL*Auto Loan."
        Regex("""\binfo[:\s\-]*([^.]+)""", IC),
    )

    fun parse(address: String, body: String, time: Long): SmsSpend? {
        val bank = bankFor(address) ?: return null
        // NFKC turns styled text (SBI Card uses 𝗌𝗉𝖾𝗇𝗍 etc.) back into plain letters.
        val text = Normalizer.normalize(body, Normalizer.Form.NFKC).replace(Regex("""\s+"""), " ").trim()
        if (!spendWord.containsMatchIn(text)) return null
        if (listOf(otp, notYet, request, failed, reversed, incoming, billPaid, mandateSetup, promo).any { it.containsMatchIn(text) }) return null
        if (foreign.containsMatchIn(text)) return null // amount in INR isn't in the message

        val amountMatch = amountRx.findAll(text).firstOrNull { m ->
            !balanceBefore.containsMatchIn(text.substring(maxOf(0, m.range.first - 25), m.range.first))
        } ?: return null
        val amount = (amountMatch.groupValues[1].ifEmpty { amountMatch.groupValues[2] })
            .replace(",", "").toBigDecimalOrNull()
            ?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()
            ?.takeIf { it > 0 } ?: return null

        val isAtm = atm.containsMatchIn(text)
        val mode = when {
            isAtm -> PaymentMode.CASH
            creditCard.containsMatchIn(text) -> PaymentMode.CREDIT_CARD
            upi.containsMatchIn(text) -> PaymentMode.UPI
            card.containsMatchIn(text) -> PaymentMode.DEBIT_CARD
            else -> PaymentMode.DEBIT_CARD // plain bank account debit (NEFT / IMPS / ACH …)
        }

        val merchant = if (isAtm) "ATM withdrawal" else findMerchant(text, amountMatch.range.last + 1)
        val hint = when {
            isAtm -> "Cash withdrawal"
            mode != PaymentMode.CREDIT_CARD && cardBill.containsMatchIn(text) -> "Looks like a credit card bill payment"
            ownAccount.containsMatchIn(text) -> "Looks like a transfer between accounts"
            else -> null
        }
        return SmsSpend(
            ref = "$time:${body.hashCode()}",
            amount = amount,
            merchant = merchant,
            mode = mode,
            bank = bank,
            time = time,
            body = body.trim(),
            transferHint = hint,
        )
    }

    /** Looks for the payee in the part of the message after the amount. */
    private fun findMerchant(text: String, from: Int): String {
        val rest = text.substring(from.coerceAtMost(text.length))
        val head = tail.find(rest)?.let { rest.substring(0, it.range.first) } ?: rest
        for (p in merchantPatterns) {
            for (m in p.findAll(head)) {
                val name = clean(m.groupValues[1])
                if (name != null) return name
            }
        }
        return ""
    }

    private val accountLike = Regex("""^(?:a/?c|acct|account|card|your|x+\d+|\*+\d+)\b|\bbank (?:a/?c|card)\b""", IC)

    private fun clean(raw: String): String? {
        var s = raw.replace('*', ' ').replace('_', ' ')
            .replace(Regex("""^upi[-/]\d+[-/]""", IC), "")
            .replace(Regex("""^vpa\s+""", IC), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '.', ',', ':', ';', '-', '/')
            .replace(Regex("""\s+\d{3,}$"""), "")
            .trim()
        if (s.length < 2 || s.none { it.isLetter() }) return null
        if (accountLike.containsMatchIn(s)) return null
        if (Regex("""^(?:am|pm|ist)$""", IC).matches(s)) return null
        if ('@' !in s && s == s.uppercase()) {
            s = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return s.take(40).trim()
    }

    /** Keyword -> expense category (see [Categories]). Checked against the merchant name. */
    private val categoryRules: List<Pair<Regex, String>> = listOf(
        "swiggy|zomato|restaurant|cafe|caf[eé]|hotel|food|pizza|burger|domino|kfc|mcdonald|starbucks|bakery|biryani|eats|" +
            "dine|kitchen|chai|tea|coffee|haldiram|barbeque|bistro|dhaba|sweets" to "Food",
        "blinkit|zepto|instamart|bigbasket|big basket|dmart|grocer|supermarket|jiomart|ratnadeep|kirana|fresh|" +
            "more retail|spencer|vegetable|fruits|milk|dairy|bb now|avenue supe" to "Groceries",
        "uber|\\bola\\b|rapido|metro|fastag|parking|namma yatri|\\bauto\\b|\\bcab\\b|toll|bmtc|best bus" to "Transport",
        "petrol|fuel|hpcl|bpcl|iocl|indian oil|\\bshell\\b|bharat petroleum|hp pay|filling station" to "Fuel",
        "decathlon|playo|cult\\.?fit|\\bgym\\b|turf|sports?\\b|fitness|hudle|badminton|cricket|football|swimming" to "Sport",
        "amazon|flipkart|myntra|ajio|meesho|nykaa|croma|reliance digital|lifestyle|\\bh&m\\b|zara|trends|" +
            "westside|pantaloons|tata cliq|ikea|max fashion|shoppers stop" to "Shopping",
        "netflix|spotify|prime video|hotstar|youtube|apple\\.com|google play|jiocinema|sonyliv|zee5|" +
            "subscription|audible|chatgpt|openai" to "Subscriptions",
        "electricity|bescom|tneb|msedcl|airtel|\\bjio\\b|vodafone|\\bvi\\b|bsnl|broadband|act fibernet|" +
            "\\bgas\\b|water|recharge|\\bbill\\b|tata power|adani" to "Bills",
        "bookmyshow|\\bpvr\\b|inox|cinema|movie|district|paytm insider|gaming|steam" to "Entertainment",
        "makemytrip|goibibo|irctc|indigo|air india|vistara|akasa|spicejet|cleartrip|redbus|\\boyo\\b|airbnb|" +
            "ixigo|yatra|booking\\.com|agoda" to "Travel",
        "pharmacy|apollo|medplus|hospital|clinic|\\b1mg\\b|pharmeasy|netmeds|diagnostic|\\blab\\b|medical|" +
            "dental|health" to "Health",
        "school|college|udemy|coursera|byju|\\bfees?\\b|academy|university|tuition" to "Education",
        "\\bemi\\b|loan|bajaj fin|home credit|\\bnach\\b" to "EMI / Loan",
        "zerodha|groww|upstox|mutual fund|\\bsip\\b|kuvera|indmoney|\\bnps\\b|angel one|\\bach\\b" to "Investment",
        "\\brent\\b|nobroker|housing\\.com|nestaway" to "Rent",
    ).map { (k, v) -> Regex(k, IC) to v }

    /** Best-guess category for a merchant name; "Other" if nothing matches. */
    fun guessCategory(merchant: String): String =
        categoryRules.firstOrNull { it.first.containsMatchIn(merchant) }?.second ?: "Other"
}

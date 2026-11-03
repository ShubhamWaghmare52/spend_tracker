package com.shubham.spendtracker.parser

import com.shubham.spendtracker.ui.bankName
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One or more alerts per Indian bank, written in each bank's real format with made-up amounts,
 * accounts, names and references. Formats were checked against public parser projects; no message
 * here is anyone's real SMS.
 *
 * Every case is checked and all mismatches are reported together, so a new bank format shows its
 * whole picture in one run. [merchant], [account] and [ref] are only checked when given.
 */
class BankCorpusTest {
    private data class Case(
        val bank: String,
        val sender: String,
        val body: String,
        /** null: this message must not be recorded as a payment. */
        val type: TxnType?,
        val paise: Long = 0,
        val merchant: String? = null,
        val account: String? = null,
        val ref: String? = null,
    )

    private val D = TxnType.DEBIT
    private val C = TxnType.CREDIT

    private val cases = listOf(
        // HDFC Bank
        Case("HDFC Bank", "VM-HDFCBK-S", "Sent Rs.640.00\nFrom HDFC Bank A/C *7712\nTo CHAI POINT\nOn 02/11/26\nRef 630112345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", D, 64000, "CHAI POINT", "7712", "630112345678"),
        Case("HDFC Bank", "AD-HDFCBK-S", "Spent Rs.1,249 On HDFC Bank Card 5521 At AMAZON PAY INDIA On 2026-11-02:18:22:10.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5521 to 7308080808", D, 124900, "AMAZON PAY INDIA", "5521"),
        Case("HDFC Bank", "VM-HDFCBK-S", "Credit Alert!\nRs.2,500.00 credited to HDFC Bank A/c XX7712 on 01-11-26 from VPA meera.n@okicici (UPI 630198765432)", C, 250000, "meera.n@okicici", "7712", "630198765432"),
        Case("HDFC Bank", "VM-HDFCBK-S", "Update! INR 48,000.00 deposited in HDFC Bank A/c XX7712 on 31-OCT-26 for NEFT Cr-CITI0000001-ACME SOFTWARE PVT LTD-SALARY OCT. Avl bal INR 61,230.55. Cheque deposits in A/C are subject to clearing", C, 4800000, account = "7712"),
        Case("HDFC Bank", "VM-HDFCBK-S", "Rs.300.00 withdrawn from HDFC Bank Card x5521 At +MG ROAD ATM On 2026-11-01:09:12:44 Bal Rs.12,410.20 Not you? Call 18002586161", D, 30000, account = "5521"),

        // State Bank of India
        Case("SBI", "AD-SBIUPI-S", "Dear UPI user A/C X3307 debited by 85.0 on date 02Nov26 trf to RAMESH TEA STALL Refno 630155554444. If not u? call 1800111109. -SBI", D, 8500, "RAMESH TEA STALL", "3307", "630155554444"),
        Case("SBI", "JD-SBIINB-S", "Dear SBI User, your A/c X3307-credited by Rs.1500 on 01Nov26 transfer from ANIL KUMAR Ref No 630166667777 -SBI", C, 150000, "ANIL KUMAR", "3307", "630166667777"),
        Case("SBI", "AD-SBIINB-S", "Your A/C XXXXX3307 has a debit by transfer of Rs 2,000.00 on 30/10/26. Avl Bal Rs 18,442.10.-SBI", D, 200000, account = "3307"),
        Case("SBI", "AD-SBIINB-S", "Your a/c no. XXXXXXXX3307 is debited for Rs.500.00 on 29-10-26 and credited to a/c no. XXXXXXXX9081 (UPI Ref no 630177778888)", D, 50000, account = "3307", ref = "630177778888"),
        Case("SBI Card", "VM-SBICRD-S", "Rs.799.00 spent on your SBI Credit Card ending 4410 at BOOKMYSHOW on 01/11/26. Trxn. not done by you? Report at https://sbicard.com/Dispute", D, 79900, "BOOKMYSHOW", "4410"),
        Case("SBI", "AD-SBIINB-S", "Rs 2000 withdrawn at SBI ATM S1NW000123 from A/cX3307 on 02Nov26 Transaction Number 6301. Available Balance Rs 9,880.40. If not withdrawn by you, call 1800111109 -SBI", D, 200000, account = "3307"),

        // ICICI Bank
        Case("ICICI Bank", "AX-ICICIT-S", "ICICI Bank Acct XX908 debited for Rs 349.00 on 02-Nov-26; ZEPTO MARKETPLACE credited. UPI:630144443333. Call 18002662 for dispute. SMS BLOCK 908 to 9215676766.", D, 34900, "ZEPTO MARKETPLACE", "908", "630144443333"),
        Case("ICICI Bank", "VM-ICICIB-S", "INR 2,150.00 spent using ICICI Bank Card XX6623 on 01-Nov-26 on IND*BIGBASKET. Avl Limit: INR 81,420.00. If not you, call 1800 2662/SMS BLOCK 6623 to 9215676766", D, 215000, "IND*BIGBASKET", "6623"),
        Case("ICICI Bank", "AX-ICICIT-S", "Dear Customer, Acct XX908 is credited with Rs 1,200.00 on 31-Oct-26 from SUNITA RAO. UPI:630133332222-ICICI Bank.", C, 120000, "SUNITA RAO", "908", "630133332222"),

        // Axis Bank
        Case("Axis Bank", "JD-AXISBK-S", "INR 1500.00 debited from A/c no. XX441902 on AXIS BANK L 02-11-2026 10:04:51 IST. Avl bal: INR 22410.75. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank", D, 150000, account = "1902"),
        Case("Axis Bank", "AX-AXISBK-S", "INR 275.00 debited\nA/c no. XX1902\n02-11-26, 13:40:12\nUPI/P2M/630122221111/SWIGGY LIMITED\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank", D, 27500, "SWIGGY LIMITED", "1902", "630122221111"),
        Case("Axis Bank", "AX-AXISBK-S", "INR 900.00 credited\nA/c no. XX1902\n01-11-26, 19:02:33 IST\nUPI/P2A/630111110000/PRIYA S\nAxis Bank", C, 90000, "PRIYA S", "1902", "630111110000"),

        // Kotak
        Case("Kotak", "JD-KOTAKB-S", "Sent Rs.410.00 from Kotak Bank AC X5150 to blinkit@hdfcbank on 02-11-26.UPI Ref 630199990000. Not you, https://kotak.com/KBANKT/Fraud", D, 41000, "blinkit@hdfcbank", "5150", "630199990000"),
        Case("Kotak", "JD-KOTAKB-S", "Received Rs.650.00 in your Kotak Bank AC X5150 from rohit.v@oksbi on 01-11-26.UPI Ref 630188889999. Not you, https://kotak.com/KBANKT/Fraud", C, 65000, "rohit.v@oksbi", "5150", "630188889999"),

        // Bank of Baroda
        Case("Bank of Baroda", "VM-BOBSMS-S", "Rs.120.00 Dr. from A/C XXXXXX6612 and Cr. to fruitwala@ybl. Ref:630177776666. AvlBal:Rs4510.20(2026:11:02 08:14:51) - Bank of Baroda", D, 12000, "fruitwala@ybl", "6612", "630177776666"),
        Case("Bank of Baroda", "VM-BOBSMS-S", "Rs.700.00 Cr. to A/c XX6612 on 01-11-2026 from deepak.j@okaxis. AvlBal:Rs5210.20. Ref:630166665555 -Bank of Baroda", C, 70000, account = "6612", ref = "630166665555"),

        // Punjab National Bank
        Case("PNB", "VM-PNBSMS-S", "A/c X4471 debited INR 260.00 Dt 02-11-26 thru UPI:630155554433. Bal INR 3,880.10 Not u? Fwd this SMS to 9264092640 to block UPI -PNB", D, 26000, account = "4471", ref = "630155554433"),
        Case("PNB", "VM-PNBSMS-S", "A/c X4471 credited INR 1,000.00 Dt 01-11-26 From suresh.p@okhdfcbank/UPI:630144443322. Bal INR 4,880.10-PNB", C, 100000, account = "4471"),

        // Canara Bank
        Case("Canara Bank", "VK-CANBNK-S", "Dear Customer, Acct XXX882 Dr. INR 180.00 on 02/11/26 to MOHAN GENERAL STORE; UPI: 630133332211; Bal INR 6,210.40.Not you? SMS BLOCKUPI to 9901771222-CanaraBank", D, 18000, "MOHAN GENERAL STORE", "882", "630133332211"),
        Case("Canara Bank", "VK-CANBNK-S", "An amount of INR 2,000.00 has been CREDITED to your account XXX882 on 01/11/2026 towards UPI from anita.k@ybl. Total Avail.bal INR 8,210.40 - Canara Bank", C, 200000, account = "882"),

        // Union Bank of India
        Case("Union Bank", "AD-UNIONB-S", "A/c *8820 Debited for Rs:150.00 on 02-11-2026 10:22:41 by Mob Bk ref no 630122223333 Avl Bal Rs:2,940.15.If not you, Call 1800222243 -Union Bank of India", D, 15000, account = "8820", ref = "630122223333"),
        Case("Union Bank", "AD-UNIONB-S", "A/c *8820 Credited for Rs:500.00 on 01-11-2026 18:05:12 by Mob Bk ref no 630111112222 Avl Bal Rs:3,090.15 -Union Bank of India", C, 50000, account = "8820"),

        // Bank of India
        Case("Bank of India", "VM-BOIIND-S", "Rs.230.00 debited A/cXX9015 and credited to tailorshop@paytm via UPI Ref No 630199998888 on 02Nov26. Call 18001031906, if not done by you. -BOI", D, 23000, "tailorshop@paytm", "9015", "630199998888"),
        Case("Bank of India", "VM-BOIIND-S", "BOI -  Cash Rs. 2000 deposited in your account XX9015 from Cash Acceptor Machine R0807031 at  STATION ROAD ON 01-11-2026. Available balance Rs. 11500.50", C, 200000, account = "9015"),

        // Indian Bank
        Case("Indian Bank", "AD-INDBNK-S", "Sent Rs.95.00 from A/c *2208 on 02-11-26 to MEDPLUS.RRN 630188887777.Avl Bal Rs.4410.65.Not you? SMS BLOCK to 9289592895-Indian Bank", D, 9500, "MEDPLUS", "2208", "630188887777"),

        // Indian Overseas Bank
        Case("Indian Overseas Bank", "VM-IOBCHN-S", "Your a/c no. XXXXX5530 is debited for Rs.340.00 on 02-11-2026 and credited to a/c no. XXXXX1209 (UPI Ref no 630177770000).-IOB", D, 34000, account = "5530", ref = "630177770000"),

        // UCO Bank
        Case("UCO Bank", "VM-UCOBNK-S", "A/c XX7743 Debited with Rs.450.00 on 02-11-2026 by UPI Ref No. 630166660000. Avl Bal Rs.1,220.35. If not you, SMS BLOCK to 9250078400 -UCO Bank", D, 45000, account = "7743", ref = "630166660000"),

        // Central Bank of India
        Case("Central Bank of India", "JD-CENTBK-S", "Rs. 7,500.00 credited to your A/c xxxxxx6621 on 01/11/2026 through NEFT vide Ref No./XUTR/CBIN26305000123 By.SHREE ENTERPRISES-CBoI", C, 750000, account = "6621"),

        // Bank of Maharashtra
        Case("Bank of Maharashtra", "AD-MAHABK-S", "Your A/c XX4820 is debited for Rs.60.00 on 02-11-2026 and credited to A/c linked to VPA vadapav@ybl (UPI Ref No 630155550000) - Bank of Maharashtra", D, 6000, account = "4820", ref = "630155550000"),

        // IDBI Bank
        Case("IDBI Bank", "VM-IDBIBK-S", "Your IDBI Bank A/C NN3391 is debited for Rs 1,100.00 towards UPI/630144440000/ELECTRICITY BOARD. Avl Bal Rs 6,320.00. Not you? Call 18002094324", D, 110000, account = "3391"),

        // IDFC First
        Case("IDFC First", "JM-IDFCFB-S", "Your A/c XX2290 debited by Rs. 899.00 on 02/11/26; REDBUS credited. RRN 630133330000. Available balance Rs. 7,410.15. Team IDFC FIRST Bank", D, 89900, "REDBUS", "2290", "630133330000"),

        // IndusInd
        Case("IndusInd", "AD-INDUSB-S", "A/C *XX6612 credited by Rs 3000.00 from nitin.k@ybl. RRN:630122220000. Avl Bal:15502.12. Not you? Call 18602677777 - IndusInd bank.", C, 300000, "nitin.k@ybl", "6612", "630122220000"),

        // Yes Bank
        Case("Yes Bank", "JX-YESBNK-S", "INR 60.00 spent on YES BANK Card X8812 @UPI_SHARMA KIRANA 02-11-2026 09:51:19 am. Avl Lmt INR 92,012.60. SMS BLKCC 8812 to 9840909000 if not you", D, 6000, account = "8812"),

        // Federal Bank
        Case("Federal Bank", "AD-FEDBNK-S", "Rs 220.00 debited from your A/c XX5560 to VPA cakeshop@okaxis on 02-11-2026 10:15:20. Ref 630111110099 -Federal Bank", D, 22000, "cakeshop@okaxis", "5560", "630111110099"),
        Case("Federal Bank", "AD-FEDBNK-T", "Debited Rs 4000 from a/c XX5560 on 01NOV2026 21:35 via NEFT to Karthik.Ref FDRLM4175000001.Bal Rs 1,276.82.Not you?Call 18004251199 -Federal Bank", D, 400000, account = "5560"),

        // South Indian Bank
        Case("South Indian Bank", "VM-SIBSMS-S", "Dear Customer, Your A/c X5541 is credited with Rs.1200.00 Info: IMPS/PUNB/630100001111/ VISHNU TRADERS. Final balance is Rs.8000.00-South Indian Bank", C, 120000, account = "5541"),

        // Karnataka Bank
        Case("Karnataka Bank", "BG-KBLBNK-T", "Your a/c XX7741 debited for Rs.40.00 on 02-11-26 trf to UDUPI CAFE. UPI:6*********55.For dispute SMS BLOCK 7741 to 1800000000 -KarnatakaBank", D, 4000, "UDUPI CAFE", "7741"),

        // Tamilnad Mercantile Bank
        Case("TMB", "VA-TMBANK-S", "Your A/c No.XXXX3381 is debited with Rs.152.00 on 02-11-2026 06:19 PM and UTIB A/c linked to sweetsandsnacks@axl is credited (UPI Ref No.630100002222).Current AVBL bal is Rs.2234.73 - TMB", D, 15200, "sweetsandsnacks@axl", "3381", "630100002222"),

        // City Union Bank
        Case("City Union Bank", "JX-CUBANK-S", "Your a/c no. XXXXXXXX4402 is credited for Rs.1500.00 on 01-11-2026 and debited from a/c no. XXXXXXXX9930 (UPI Ref no 630100003333) -CUB", C, 150000, account = "4402", ref = "630100003333"),

        // Dhanlaxmi Bank
        Case("Dhanlaxmi Bank", "VM-DHANBK-S", "INR 75.00 is debited from A/c XXXX6650 on 02-NOV-2026 - \"UPI TXN: /630100004444/payment to bakery@ybl\". Aval Bal is INR 4,278.92 -DhanlaxmiBank", D, 7500, account = "6650"),

        // Saraswat Bank
        Case("Saraswat Bank", "BV-SARBNK-S", "Dear Customer, Your account no. ending with 2468 is debited with INR 1,500.00 on 01-11-2026 for IMPS. Current Bal is INR 9,345.67CR. - Saraswat Bank", D, 150000, account = "2468"),

        // Small finance and payments banks
        Case("AU Small Finance Bank", "VM-AUBANK-S", "INR 199.00 spent at SPOTIFY on AU Bank Credit Card x9921 01-11-2026 05:49:40 PM. Not you? Call 180012001500 or SMS PBLOCK 9921 to 5676767", D, 19900, "SPOTIFY", "9921"),
        Case("Equitas", "CP-EQUTAS-S", "INR 350.00 debited via UPI from Equitas A/c 7781 -Ref:630100005555 on 02-11-26 to DOMINOS PIZZA. Avl Bal is INR 2,500.25.Not U?Call 18001031222", D, 35000, "DOMINOS PIZZA", "7781", "630100005555"),
        Case("Jana Bank", "JM-JANABK-S", "Dear Customer, Your acct XX118 is credited with INR 400.00 on 01-Nov-26 from LATA DESAI. UPI Ref no 630100006666 . JANA SFB", C, 40000, "LATA DESAI", "118", "630100006666"),
        Case("Bandhan Bank", "VM-BANDHN-S", "INR 600.00 debited from A/c XXXXXXXXXX4417 towards UPI/DR/630100007777/GREEN GROCERS Value 02-NOV-2026 . Clear Bal is INR 5,000.00. Bandhan Bank", D, 60000, "GREEN GROCERS", "4417", "630100007777"),
        Case("Suryoday SFB", "JM-SSFBNK-T", "Rs 553.00 spent on Suryoday SFB CC 5512 on 01-11-2026 at 23:38:50. Avlb Rs 41628.18 Not you SMS BLOCK CC 5512 to 9000000000", D, 55300, account = "5512"),
        Case("Airtel Payments Bank", "AD-AIRBNK-S", "Rs.150.00 debited from your Airtel Payments Bank a/c 9988 for UPI txn to mobilerecharge@airtel. Ref no 630100008888. Bal Rs.420.55", D, 15000, account = "9988", ref = "630100008888"),
        Case("NSDL Payments Bank", "AD-NSDLPB-S", "A/c XX5051 debited Rs 99.00 on 02-Nov-26 for linked pharmacy@oksbi. UPI Ref 630100009999 - NSDLPB", D, 9900, account = "5051", ref = "630100009999"),
        Case("India Post Payments Bank", "AX-IPPBMB-S", "A/C X4821 Debit Rs.150.00 for UPI to shankar anna p on 02-11-26 Ref 630100001234. Avl Bal Rs.2,310.45. If not you? SMS FREEZE \"full a/c\" to 7669300000-IPPB", D, 15000, "shankar anna p", "4821", "630100001234"),
        Case("Post Office Savings", "BV-DOPBNK-S", "Account  No. XXXXXXXX7710 CREDIT with amount Rs. 1550.00 on 01-11-2026. Balance: Rs.12000.00. [S92240001]", C, 155000, account = "7710"),
        Case("Slice", "JD-SLICEIT-S", "Rs. 1,000 received in slice A/c xx3301 on 01-Nov-26 from VIKRAM SINGH via UPI (Ref ID: 630100002345). Avl. Bal. Rs. 1,203.56 - slice", C, 100000, "VIKRAM SINGH", "3301", "630100002345"),

        // Not payments: these must stay out.
        Case("CRED card bill", "AD-CREDIN-S", "Payment of Rs.10000 has been successfully credited towards your SBI Credit Card.", null),
        Case("Balance only", "VM-HDFCBK-S", "Available balance in your A/c XX7712 is Rs.12,345.67 as on 02-11-26. -HDFC Bank", null),
        Case("Mandate", "AD-SBIUPI-S", "Your UPI-Mandate for Rs.199.00 is successfully created towards NETFLIX from A/c No: XXXXXX3307. -SBI", null),
        Case("Collect request", "VM-ICICIB-S", "meera@okaxis has requested money of Rs.500 from your ICICI Bank A/c. Pay only if you know the person.", null),
        Case("Bill due", "VM-SBICRD-S", "Total Amt Due Rs.5,430 on your SBI Card ending 4410 is due on 15-11-26. Pay now to avoid charges.", null),
    )

    @Test fun everyBankFormatParses() {
        val problems = cases.mapNotNull { c ->
            val t = SmsParser.parse(c.sender, c.body, 1_000L)
            val issues = buildList {
                if (c.type == null) {
                    if (t != null) add("recorded a non-payment as ${t.type} ${t.amountPaise}")
                } else if (t == null) {
                    add("missed")
                } else {
                    if (t.type != c.type) add("type ${t.type} != ${c.type}")
                    if (t.amountPaise != c.paise) add("amount ${t.amountPaise} != ${c.paise}")
                    if (c.merchant != null && t.merchant != c.merchant) add("merchant '${t.merchant}' != '${c.merchant}'")
                    if (c.account != null && t.accountLast4 != c.account) add("account ${t.accountLast4} != ${c.account}")
                    if (c.ref != null && t.upiRef != c.ref) add("ref ${t.upiRef} != ${c.ref}")
                }
            }
            if (issues.isEmpty()) null else "${c.bank}: ${issues.joinToString("; ")}\n    ${c.body.replace("\n", " | ").take(110)}"
        }
        assertTrue("${problems.size} of ${cases.size} cases:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyBankSenderHasAName() {
        val unnamed = cases.filter { it.type != null }.map { it.sender }.distinct().filter { bankName(it) == null }
        assertTrue("No bank name for: $unnamed", unnamed.isEmpty())
    }
}

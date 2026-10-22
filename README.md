# Spend Tracker

An Android app that tracks your spending automatically. Pay with any app (Google Pay, PhonePe, Paytm, a card, netbanking) and the payment shows up here on its own, with no manual entry.

It reads the payment trail every transaction already leaves on your phone:

- **Bank SMS alerts**, such as "Rs.250 debited from A/c XX1234 to swiggy@icici", for every bank, whichever app you paid with.
- **UPI app notifications**, such as "Paid ₹250 to Swiggy", from Google Pay, PhonePe, Paytm, BHIM, CRED and others.

One payment usually produces both an SMS and a notification. They are merged into a single entry.

## Features

- **Overview**: spending for today, this week or this month, compared with the same point in the previous period.
- **Tappable charts**: tap a day's bar, a category or a merchant to see the payments behind it.
- **Payment details**: merchant, time, bank or app, account, UPI reference and the original SMS. Changing a category is remembered for that merchant.
- **Daily limit**: a progress bar on the Overview, plus a notification at 80% and when you go over.
- **Monthly budgets per category**: alerts at 80% and when you go over, and the Overview's category bars track each budget.
- **Month-end summary**: a notification on the 1st with total spent, the change from the month before, your top category and any budgets you went over.
- **Upcoming payments**: finds monthly repeats such as EMIs, recharges and subscriptions, and shows when each is next due. It still matches a payee when the bank changes its name.
- **Home screen widget**: today's spending against your daily limit, shown as a ring that turns amber at 80% and coral when you're over.
- **Sort payments**: works through uncategorised spending merchant by merchant, biggest first. People are detected and suggested as Transfers.
- **CSV backup**: export to a spreadsheet-friendly file and import it later, skipping payments that are already there.
- **Self-transfer detection**: a debit in one of your accounts and a credit in another with the same UPI reference are marked automatically and left out of totals.
- **Auto-categorisation**: Food, Groceries, Shopping, Travel, Bills, and more.
- **Search and filters** on the Payments tab.
- **Material 3 UI**: dark mode, an adaptive and themed launcher icon, an animated splash screen, and motion throughout.

## Privacy

- All data stays on the device in a local Room database.
- The app **does not request the `INTERNET` permission**, so it cannot send anything anywhere.
- Backups are disabled (`allowBackup="false"`), so your transactions aren't copied to cloud backups. CSV export is the only way data leaves the app, and you choose where the file goes through Android's file picker.

## Requirements

- Android 8.0 (API 26) or newer. Tested on Android 17.
- Indian bank SMS formats. Parsers cover HDFC, SBI, ICICI, Axis, Kotak, Bank of Baroda, Jio Payments Bank, card spends and UPI apps.

iOS isn't supported, because iOS doesn't allow apps to read SMS or other apps' notifications.

## Build and install

You'll need JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew :app:testDebugUnitTest   # parser tests
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key, which is fine for sideloading onto your own phone. Create your own signing config before distributing it.

When you first open the app, grant:

1. **SMS access**: imports the last 90 days of bank alerts and catches new ones.
2. **Notification access**: reads payment notifications from UPI apps.
3. **Notifications** (optional): for daily-limit and budget alerts, and the monthly summary. Each has its own notification channel, so you can mute them separately.

On Android 13+, a sideloaded APK may show these as *Restricted settings*. To allow them, go to **Settings → Apps → Spend Tracker → ⋮ → Allow restricted settings**.

## How it works

```
app/src/main/java/com/shubham/spendtracker/
├── parser/   SmsParser, NotificationParser   → ParsedTxn (amount, type, merchant, account, UPI ref)
│             Deduper                          → merges SMS + notification, pairs self transfers
│             Categorizer                      → merchant → category
├── ingest/   SmsReceiver (live SMS), InboxBackfill (past 90 days),
│             PaymentNotificationListener (UPI apps)
├── data/     Room database, TransactionRepository, DailyLimit, Budgets, CsvBackup, Notifier
├── insights/ RecurringDetector, MonthlySummary (+ WorkManager job for the 1st of the month)
├── widget/   SpendWidget (home screen widget)
└── ui/       Jetpack Compose screens: Overview, Payments, Sort, Budgets, detail sheets, motion
```

Parsing, recurring-payment detection, the monthly summary and the CSV format are plain Kotlin with no Android dependencies, and they are covered by unit tests in `app/src/test`. To support a new bank format, add an example message (with the numbers changed) to `ParserTest.kt`, make it pass, and bump `PARSER_VERSION` in `MainActivity` so existing installs re-scan their SMS.

## Limitations

- Cash payments aren't tracked.
- Bank SMS formats change over time, and unusual ones may be missed or misread. Tap a payment and choose **Not a payment — remove** to drop a wrong entry.
- Transfers between your own accounts are only detected automatically when both sides share a UPI reference. Mark others as **Self transfer** by hand.

## License

[MIT](LICENSE)

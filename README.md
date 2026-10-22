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
- **Self-transfer detection**: a debit in one of your accounts and a credit in another with the same UPI reference are marked automatically and left out of totals.
- **Auto-categorisation**: Food, Groceries, Shopping, Travel, Bills, and more.
- **Search and filters** on the Payments tab.
- **Material 3 UI**: dark mode, an adaptive and themed launcher icon, an animated splash screen, and motion throughout.

## Privacy

- All data stays on the device in a local Room database.
- The app **does not request the `INTERNET` permission**, so it cannot send anything anywhere.
- Backups are disabled (`allowBackup="false"`), so your transactions aren't copied to cloud backups.

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
3. **Notifications** (optional): for the daily-limit alerts.

On Android 13+, a sideloaded APK may show these as *Restricted settings*. To allow them, go to **Settings → Apps → Spend Tracker → ⋮ → Allow restricted settings**.

## How it works

```
app/src/main/java/com/shubham/spendtracker/
├── parser/   SmsParser, NotificationParser   → ParsedTxn (amount, type, merchant, account, UPI ref)
│             Deduper                          → merges SMS + notification, pairs self transfers
│             Categorizer                      → merchant → category
├── ingest/   SmsReceiver (live SMS), InboxBackfill (past 90 days),
│             PaymentNotificationListener (UPI apps)
├── data/     Room database, TransactionRepository, DailyLimit
└── ui/       Jetpack Compose screens: Overview, Payments, detail sheets, motion
```

Parsing is plain Kotlin with no Android dependencies, and it is covered by unit tests in `app/src/test`. To support a new bank format, add an example message (with the numbers changed) to `ParserTest.kt`, make it pass, and bump `PARSER_VERSION` in `MainActivity` so existing installs re-scan their SMS.

## Limitations

- Cash payments aren't tracked.
- Bank SMS formats change over time, and unusual ones may be missed or misread. Tap a payment and choose **Not a payment — remove** to drop a wrong entry.
- Transfers between your own accounts are only detected automatically when both sides share a UPI reference. Mark others as **Self transfer** by hand.

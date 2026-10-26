# Spendly

**Spendly** is a clean, dark-mode-first Android expense tracker: log expenses and income, split bills with friends, and keep tabs on who owes you — all stored privately on your phone.

**[⬇️ Download the latest APK](https://github.com/tarun337/spendly/releases/latest/download/Spendly.apk)** (Android 8.0+) · [All releases](https://github.com/tarun337/spendly/releases). See [Installing on a phone](#installing-on-a-phone).

## Features

**Expenses & income**
- Add expenses and income with category, note, and payment mode (UPI, Cash, Debit Card, Credit Card — UPI by default)
- Change the transaction date (Today / Yesterday / any date)
- **Count in a different month** — e.g. a salary or bill paid on the 30th that belongs to next month
- Amounts are shown with comma grouping while typing (Indian `12,50,000` for ₹)

**Splits**
- Split any expense with people from your contacts, past split partners, or just a typed name
- Equal split by default; edit anyone's share and the rest re-balances automatically
- Remove yourself from the split if you paid entirely on others' behalf
- **Groups** (e.g. Flatmates) — add a whole group to a split in one tap
- **Favourites** — pick frequent people from a single Favourites checklist
- Until split money comes back, the full amount counts as your expense; marking a share as received reduces it
- Splits tab: see what's pending **by person** or **by group**, mark individual shares as received or settle up everyone at once (with undo), and reopen settled splits

**Insights**
- Monthly overview: net, income, spent, and split money still to come back
- Spending by category (donut chart) and by payment mode
- Last-6-months income vs spend trend
- Optional monthly budget with a per-day allowance for the rest of the month

**Other**
- History with search (notes, categories, people) and filters (type, split, payment mode, month / all time)
- Swipe to delete with undo; duplicate a transaction to repeat a payment
- Export everything to CSV
- Dark (default), light, or system theme; choice of currency symbol

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) for local storage, with migrations so updates keep your data
- Navigation Compose, ViewModel + StateFlow
- Min SDK 26 (Android 8.0), target SDK 35

## Building

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35).

1. Point Gradle to your SDK by creating `local.properties` in the project root:
   ```properties
   sdk.dir=/path/to/Android/sdk
   ```
2. Build a release APK:
   ```bash
   ./gradlew assembleRelease
   ```
   The APK is written to `app/build/outputs/apk/release/app-release.apk`.

> The release build is signed with the local **debug key** so it can be sideloaded directly. For a Play Store release, configure your own signing key in `app/build.gradle.kts`.

## Installing on a phone

[Download the APK](https://github.com/tarun337/spendly/releases/latest/download/Spendly.apk) on your phone and open it (allow "Install unknown apps" when prompted). To install a build from source with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Project structure

```
app/src/main/java/com/expensemanager/app/
├── data/            Room entities, DAO, database & migrations, categories, settings
└── ui/
    ├── AppRoot.kt       Navigation and bottom bar
    ├── AppViewModel.kt  State, monthly stats, CSV export
    ├── Components.kt    Shared UI pieces
    ├── Format.kt        Money / date formatting and input helpers
    ├── screens/         Home, History, Add/Edit, Splits, People & groups, Settings, contact picker
    └── theme/           Colours and theme
```

## Privacy

All data stays on the device — there is no account, server, or network access. The contacts permission is only requested when you open the people picker, and is used only to list names to split with.

# Paisa Floww

A private, offline personal-finance tracker for students and anyone sharing a PG or flat: income, expenses, savings goal, and a **friends' loan tracker** (borrowed / lent, partial repayments, due dates). Amounts are stored as exact integer paise and shown in Indian rupee format.

Two ways to use it, from one repository:

| Version | Where | Best for |
|---|---|---|
| **Web app (installable, works offline)** | `docs/` folder, hosted free by GitHub Pages | Anyone with a phone browser |
| **Native Android app (Kotlin, Compose, Room)** | rest of the repository, built by GitHub Actions | Sideloading an APK |

## Privacy
No accounts, no analytics, no ads, no server. The Android app has **no permissions at all**. Every person's data stays on their own device (browser storage for the web app, a local database for the Android app). The project owner never sees or pays for anyone's data.

Data is erased if the user clears browser data, uninstalls the app or resets the phone. Use the built-in backup regularly. Backups are plain JSON and are **not encrypted**.

## Use the web app
Open `https://YOUR-USERNAME.github.io/paisafloww/` in Chrome on Android, tap the menu, then **Install app** / **Add to Home screen**. After the first visit it also opens without internet.

## Get the Android app
- Easiest: open the **Releases** page of this repository and download `PaisaFloww.apk`, then open it on your phone and allow "Install unknown apps" if asked.
- Or build it yourself: open this folder in Android Studio and choose Build > Build APK(s), or fork the repository and let GitHub Actions build it (Actions tab > latest run > Artifacts).

The APK from Actions/Releases is signed with a standard debug key unless the owner adds private signing secrets. Updating to a build signed with a different key requires uninstalling first, so **export a backup before switching**.

## Accounting model
available = starting balance + income - expenses + borrowed - repaid by you - lent + repayments received - money reserved in savings.
Borrowing, lending, repayments and savings transfers are never counted as income or expenses. Manually settling a loan closes it without moving cash and requires a reason.

## Project layout
- `docs/` web app (single HTML file, manifest, service worker)
- `app/` Android app: `data/` (Room, repository, backup), `domain/` (money and accounting rules), `ui/` (Compose screens)
- `app/src/test/` unit tests for loans, savings, money parsing and backup validation
- `.github/workflows/build-apk.yml` builds the APK, runs tests, and publishes a Release when you push a tag like `v1.0.0`

## Status and known limits
The Android source has not yet been confirmed to compile on a clean machine; the first build may need small fixes. No app lock or encryption at rest. The web app cannot edit entries (delete and re-add) and backups are copy-paste text.

## License
MIT, see `LICENSE`.

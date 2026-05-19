# Build & Run — Loyalty POS (Java)

## Prerequisites

This project requires:

| Tool | Version | Why |
|---|---|---|
| **JDK** | 21 or newer | Database.java uses Java text blocks; pom targets Java 21 |
| **Maven** | 3.8+ | Builds the project and downloads dependencies |

> Your machine currently has only Java 8 and no Maven, so you'll need to install both before the project will compile.

## Install on Windows

### Option A — Recommended (single installer)

Use **winget** (built into Windows 10/11):

```powershell
winget install Microsoft.OpenJDK.21
winget install Apache.Maven
```

After install, open a NEW PowerShell window so PATH refreshes, then verify:

```powershell
java -version    # should show 21.x
mvn -version     # should show 3.8+
```

### Option B — Manual

1. Download a JDK 21 from [Eclipse Adoptium](https://adoptium.net/temurin/releases/?version=21) or [Microsoft OpenJDK](https://learn.microsoft.com/java/openjdk/download).
2. Install it; the installer should set `JAVA_HOME` and add `bin` to PATH.
3. Download [Apache Maven](https://maven.apache.org/download.cgi), extract to e.g. `C:\Tools\maven`, add `C:\Tools\maven\bin` to PATH.

## Build Commands

From `C:\Users\Aaron Sebastian\Desktop\Loyalty\loyalty-pos-java\`:

```powershell
# 1. Compile + run via JavaFX plugin (development)
mvn javafx:run

# 2. Build a fat JAR for distribution
mvn clean package

# 3. Run the JAR
java -jar target/loyalty-pos.jar
```

> First `mvn` invocation downloads ~200 MB of dependencies (JavaFX, SQLite, ZXing, TOTP, Gson) into `~\.m2\repository\`. Subsequent builds are fast.

## Bundling a Native Windows Installer (.exe / .msi)

JDK 21 ships with `jpackage`. After `mvn package`:

```powershell
jpackage `
  --name "Loyalty POS" `
  --input target `
  --main-jar loyalty-pos.jar `
  --main-class com.innov8.loyaltypos.Launcher `
  --type msi `
  --app-version 1.3.5 `
  --vendor "INNOV8 Development" `
  --win-shortcut --win-menu `
  --module-path "$env:JAVA_HOME\lib" `
  --add-modules javafx.controls,javafx.fxml,javafx.swing
```

This produces `Loyalty POS-1.3.5.msi` — installs as a normal Windows app, no JRE needed by end users.

## First-Run Notes

- Default admin PIN: **1234** (change in Settings → User Management)
- Dev PIN: **082827** (toggles separate dev DB; visible on login + sidebar)
- Database location:
  - Production: `%APPDATA%\loyalty-pos\loyalty-pos.db`
  - Dev: `%APPDATA%\loyalty-pos\loyalty-pos-dev.db`
- Thermal printer setup: Settings → Printers → set the Windows printer name (e.g. `EPSON TM-T20II`)
- TOTP 2FA setup: Settings → Admin 2FA → "Setup Google Authenticator" → scan QR. Required for voiding/deleting transactions.

## Pulling data from the API (mirror sync)

The app now follows a **mirror-mode** sync pattern:

1. On startup (~2 s after launch), it tries `GET /api/{entity}` against the URL in **Settings → API Integration → API URL**, falling back to `GET /api/sync/{entity}` if the first returns 404. Auth uses the `X-API-Key` header from `api_key` setting.
2. Returned JSON arrays are upserted into the local SQLite cache via `INSERT OR REPLACE`. Existing local rows with matching IDs are overwritten; rows that exist locally but not on the API are kept.
3. All page reads then come from the local cache — fast and offline-capable.
4. Writes still POST to `POST /api/sync/{entity}` (existing behavior) and queue when offline.

To force a fresh pull at any time: **Settings → API Sync → Pull from API (Refresh)**. The dialog reports rows pulled per entity and any errors per endpoint.

Entities pulled (in dependency order to satisfy FKs):

```
users → customers → products → po_accounts → po_payments
     → transactions → transaction_items → payments
     → trucks → truck_prices → expenses
```

If your backend exposes only one of the URL patterns, the app picks whichever returns 200 first. If neither works, the entity's row count is `0` in the dialog and the per-entity error is shown.

### Limitations of mirror mode

- **Deletes don't propagate down**: deleting a row on the API does not remove it from the local cache.
- Pull is non-blocking; if you log in within 2 seconds, your first page view may show the previous cache. Navigate away and back to refresh, or click "Pull from API".

## Verifying the right database is loaded

On startup, the app prints the active SQLite path to stdout:

```
[DB] Opening: C:\Users\<you>\AppData\Roaming\loyalty-pos\loyalty-pos.db
[DB]   users: 1
[DB]   products: 14
[DB]   transactions: 287
…
```

You can also see the path inside the app: **Settings → Database**, plus an **Open DB Folder** button. If the app appears to have "no data", check this first — you may have toggled Dev Mode (red `DEV` badge in the sidebar) which uses `loyalty-pos-dev.db`, a separate empty database.

## Known Limitations vs the original Electron app

- **Light mode**: now flips backgrounds, modals, tables, inputs, and the login card to a light palette via `light.css`. Some inline-styled accent labels stay their original color (JavaFX inline styles cannot be overridden by CSS) — readable but not a perfect mirror of the original `filter: invert(1)` trick.
- **Auto-updater** (`electron-updater`) is not ported — Java side has no equivalent. Distribute new MSI packages instead.
- **Receipt thermal printing** uses the same Windows raw-print path (PowerShell + winspool.drv) as the original; works only on Windows.
- **Online sync** uses Java 11 `HttpClient` — same wire format as original (`POST /api/sync/{entity}` with `X-API-Key` header).
- **Day Log PDF**: now opens a date picker before printing so you can choose any historical day, and refuses to print if there are no transactions or voids on that date.

# Loyalty POS — Java Edition

Desktop Point-of-Sale system for **Loyalty Gravel and Sand Trading**, built with **JavaFX 21**, **Maven**, and **SQLite**. A 1:1 port (and continued evolution) of the original Electron + React build.

> Designed around the real workflow of a construction-aggregate retailer: distinct grades of gravel and sand sold by the cubic metre, with per-truck pricing, PO accounts for repeat contractor customers, and a real-time masterlist → transaction → report data pipeline.

---

## Highlights

- **Distinct Item Codes** — every product carries a unique `item_code` so similar aggregates (e.g. `GRAVEL-1` vs `GRAVEL-2`) stay differentiated by grade, texture, or quality. Item codes are snapshotted onto each sale line so historical reports survive renames.
- **Real-time data pipeline** — `products → transaction_items → transactions → reports` is linked by FK + WAL + `ON DELETE CASCADE`. A completed checkout is visible to the sales report immediately; no manual sync.
- **Sales by Item Code report** — dedicated tab aggregating quantity + revenue per code over a date range.
- **Login UI** — modern dark interface with ambient amber glow, gradient business-name title, scrolling feature marquee, animated status pills, shield-icon chip, and a dev-mode visual swap.
- **TOTP 2FA** for void / delete operations on transactions.
- **Thermal + A4 printing** for receipts, invoices, reports, and day logs.

## Stack

| Layer | Tech |
|---|---|
| UI | JavaFX 21 (programmatic with shared CSS) |
| Storage | SQLite via `sqlite-jdbc` 3.46 (WAL mode, FK enforced) |
| 2FA | `dev.samstevens.totp` 1.7 + ZXing QR codes |
| HTTP / JSON | java.net.http.HttpClient + Gson |
| Build | Maven 3.8+, JDK 21 |

## Build & Run

```powershell
# Run via JavaFX Maven plugin
mvn javafx:run

# Build a fat JAR
mvn package
java -jar target/loyalty-pos.jar
```

Requires JDK 21+ and Maven 3.8+ on the path.

## Database Location

| Mode | Path (Windows) |
|---|---|
| Production | `%APPDATA%\loyalty-pos\loyalty-pos.db` |
| Dev | `%APPDATA%\loyalty-pos\loyalty-pos-dev.db` |

- Default admin PIN on first run: **`1234`**
- Dev-mode toggle PIN (on Login screen): **`082827`** — switches to the dev database without restart.

## Project Layout

```
src/main/java/com/innov8/loyaltypos/
├── App.java                # JavaFX entry; switches Login ↔ Shell
├── AppContext.java         # Shared state: currentUser, settings, theme
├── Launcher.java           # main() bootstrap for the fat JAR
├── db/
│   └── Database.java       # SQLite singleton, schema, idempotent migrations
├── model/                  # POJOs (User, Product, TransactionItem, …)
├── service/                # Business logic (mirrors Electron IPC handlers)
│   ├── ProductService      #   item_code uniqueness validation
│   ├── PosService          #   checkout w/ item_code snapshot
│   ├── ReportService       #   salesByItemCode aggregator
│   └── …
├── ui/                     # JavaFX views, modals, dialogs
│   ├── LoginView           #   1:1 with the React Electron design
│   ├── ProductsView        #   item code + description fields
│   ├── ReportsView         #   "By Item Code" tab
│   └── …
└── util/                   # Hashing, Money formatter, Barcode helpers
```

## Feature Matrix

| Module | Status |
|---|---|
| Login + PIN pad (1:1 with React design) | ✅ |
| Sidebar nav (role-gated) | ✅ |
| POS checkout (cart, customer, plate, payments) | ✅ |
| Products CRUD with **Item Code + Description** | ✅ |
| Customers CRUD | ✅ |
| PO Accounts + payments | ✅ |
| Transactions list / void / delete (TOTP-protected) | ✅ |
| Trucks + saved per-product prices | ✅ |
| Expenses | ✅ |
| Reports (Daily/Weekly/Monthly/Yearly + PO + **By Item Code**) | ✅ |
| Settings (business / printers / API / PO) | ✅ |
| Users (admin) | ✅ |
| TOTP 2FA + QR setup | ✅ |
| Thermal printing (Windows raw print via PowerShell) | ✅ |
| A4 invoice / report / day-log printing | ✅ (JavaFX Print API) |
| API sync queue | ✅ |
| Dark / light theme toggle | ✅ |
| Dev mode (separate DB) | ✅ |

## Data Pipeline Guarantee

The professor's revision was that the masterlist → sales → reports chain must be dynamic, not static, and that similar aggregates must be differentiable by code. This build enforces that with:

1. `products.item_code` — partial `UNIQUE INDEX … WHERE item_code IS NOT NULL`, required at the service layer.
2. `transaction_items.item_code` — snapshotted at sale time by `PosService.checkout`, so renames don't rewrite history.
3. `ON DELETE CASCADE` on `transaction_items.transaction_id` — child rows can't outlive their parent.
4. WAL journaling + `PRAGMA foreign_keys = ON` — committed transactions are visible to the next report read.
5. `ReportService.salesByItemCode(from, to)` — live JOIN of `transaction_items → transactions → products`, excluding voided sales. No caching, no manual refresh.

## License

MIT — see [LICENSE](LICENSE).

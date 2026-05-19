package com.innov8.loyaltypos.db;

import com.innov8.loyaltypos.util.Hashing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private static Connection conn;
    private static boolean devMode = false;

    public static synchronized Connection get() {
        return conn;
    }

    public static boolean isDevMode() {
        return devMode;
    }

    public static synchronized void setDevMode(boolean dev) {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException ignore) {}
        devMode = dev;
        init(dev);
    }

    private static String currentDbPath;

    public static String currentDbPath() { return currentDbPath; }

    public static synchronized void init(boolean dev) {
        try {
            String userData = userDataDir();
            String dbName = dev ? "loyalty-pos-dev.db" : "loyalty-pos.db";
            String dbPath = Paths.get(userData, dbName).toString();
            currentDbPath = dbPath;
            System.out.println("[DB] Opening: " + dbPath);
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA foreign_keys = ON");
            }
            createSchema();
            applyMigrations();
            seedSettings();
            seedAdminIfEmpty();
            logDbStats();
            devMode = dev;
        } catch (Exception e) {
            throw new RuntimeException("DB init failed: " + e.getMessage(), e);
        }
    }

    /** Mirror the ALTER TABLE migrations from electron/db.js so DBs created by old Electron builds still work. */
    private static void applyMigrations() {
        // Add plate_no to transactions if not present
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE transactions ADD COLUMN plate_no TEXT");
        } catch (SQLException ignore) { /* already exists */ }

        // Professor revision: products gets item_code (granularity for Gravel 1 vs Gravel 2) and description
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN item_code TEXT");
        } catch (SQLException ignore) { /* already exists */ }
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN description TEXT");
        } catch (SQLException ignore) { /* already exists */ }
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_products_item_code ON products(item_code) WHERE item_code IS NOT NULL");
        } catch (SQLException ignore) {}

        // Professor revision: transaction_items snapshots item_code so reports stay accurate
        // even if a product is later renamed/recoded.
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE transaction_items ADD COLUMN item_code TEXT");
        } catch (SQLException ignore) {}
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_items_item_code ON transaction_items(item_code)");
        } catch (SQLException ignore) {}
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_items_tx_id ON transaction_items(transaction_id)");
        } catch (SQLException ignore) {}
        // Backfill: copy item_code from products for any historical line items
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                UPDATE transaction_items
                SET item_code = (SELECT item_code FROM products WHERE products.id = transaction_items.product_id)
                WHERE item_code IS NULL AND product_id IS NOT NULL""");
        } catch (SQLException ignore) {}

        // Migration: ensure 'voided' is a valid payment_status. Rebuild table if old CHECK constraint omits it.
        // Wrapped in an explicit transaction so a crash mid-rebuild can't leave transactions_new orphaned.
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='transactions'")) {
            if (rs.next()) {
                String sql = rs.getString(1);
                if (sql != null && !sql.contains("'voided'")) {
                    boolean priorAutoCommit = conn.getAutoCommit();
                    st.execute("PRAGMA foreign_keys = OFF");
                    try {
                        conn.setAutoCommit(false);
                        st.executeUpdate("""
                            CREATE TABLE transactions_new (
                              id INTEGER PRIMARY KEY AUTOINCREMENT,
                              invoice_no TEXT NOT NULL UNIQUE,
                              date TEXT NOT NULL,
                              customer_id INTEGER REFERENCES customers(id),
                              cashier_id INTEGER NOT NULL REFERENCES users(id),
                              plate_no TEXT,
                              delivery_charge REAL NOT NULL DEFAULT 0,
                              discount REAL NOT NULL DEFAULT 0,
                              subtotal REAL NOT NULL DEFAULT 0,
                              total_amount REAL NOT NULL DEFAULT 0,
                              payment_status TEXT NOT NULL DEFAULT 'paid' CHECK(payment_status IN ('paid','partial','unpaid','voided'))
                            )""");
                        st.executeUpdate("INSERT INTO transactions_new SELECT id,invoice_no,date,customer_id,cashier_id,plate_no,delivery_charge,discount,subtotal,total_amount,payment_status FROM transactions");
                        st.executeUpdate("DROP TABLE transactions");
                        st.executeUpdate("ALTER TABLE transactions_new RENAME TO transactions");
                        conn.commit();
                    } catch (SQLException inner) {
                        try { conn.rollback(); } catch (Exception ignore) {}
                        throw inner;
                    } finally {
                        try { conn.setAutoCommit(priorAutoCommit); } catch (Exception ignore) {}
                        st.execute("PRAGMA foreign_keys = ON");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] voided migration error: " + e.getMessage());
            try (Statement s2 = conn.createStatement()) { s2.execute("PRAGMA foreign_keys = ON"); } catch (Exception ignore) {}
        }
    }

    /** One-shot diagnostic so users know what data is being read. */
    private static void logDbStats() {
        for (String t : new String[]{"users", "products", "customers", "po_accounts", "transactions", "expenses", "trucks"}) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + t)) {
                ResultSet rs = ps.executeQuery();
                if (rs.next()) System.out.println("[DB]   " + t + ": " + rs.getInt(1));
            } catch (SQLException ignore) {}
        }
    }

    private static String userDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        Path dir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            dir = Paths.get(appData == null ? home : appData, "loyalty-pos");
        } else if (os.contains("mac")) {
            dir = Paths.get(home, "Library", "Application Support", "loyalty-pos");
        } else {
            dir = Paths.get(home, ".loyalty-pos");
        }
        try { Files.createDirectories(dir); } catch (Exception ignore) {}
        return dir.toString();
    }

    private static void createSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  role TEXT NOT NULL CHECK(role IN ('admin','cashier')),
                  pin_hash TEXT NOT NULL,
                  is_active INTEGER NOT NULL DEFAULT 1
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS products (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  item_code TEXT,
                  name TEXT NOT NULL,
                  description TEXT,
                  unit TEXT NOT NULL DEFAULT 'm³',
                  price_per_unit REAL NOT NULL DEFAULT 0,
                  stock_qty REAL NOT NULL DEFAULT 0,
                  is_active INTEGER NOT NULL DEFAULT 1
                )""");
            // NOTE: idx_products_item_code is created in applyMigrations() AFTER the
            // ALTER TABLE that adds item_code to legacy DBs. Creating it here would
            // fail on existing prod DBs whose CREATE TABLE IF NOT EXISTS is a no-op.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS customers (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  tin TEXT,
                  address TEXT,
                  phone TEXT,
                  credit_limit REAL NOT NULL DEFAULT 0,
                  notes TEXT
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS po_accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  customer_id INTEGER NOT NULL REFERENCES customers(id),
                  reference_no TEXT,
                  issued_date TEXT NOT NULL,
                  expiry_date TEXT,
                  credit_limit REAL NOT NULL DEFAULT 0,
                  balance_used REAL NOT NULL DEFAULT 0,
                  status TEXT NOT NULL DEFAULT 'open' CHECK(status IN ('open','closed','expired'))
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS po_payments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  po_account_id INTEGER NOT NULL REFERENCES po_accounts(id),
                  amount REAL NOT NULL,
                  payment_date TEXT NOT NULL,
                  notes TEXT
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transactions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  invoice_no TEXT NOT NULL UNIQUE,
                  date TEXT NOT NULL,
                  customer_id INTEGER REFERENCES customers(id),
                  cashier_id INTEGER NOT NULL REFERENCES users(id),
                  delivery_charge REAL NOT NULL DEFAULT 0,
                  discount REAL NOT NULL DEFAULT 0,
                  subtotal REAL NOT NULL DEFAULT 0,
                  total_amount REAL NOT NULL DEFAULT 0,
                  payment_status TEXT NOT NULL DEFAULT 'paid' CHECK(payment_status IN ('paid','partial','unpaid','voided')),
                  plate_no TEXT
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transaction_items (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  transaction_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
                  product_id INTEGER REFERENCES products(id),
                  item_code TEXT,
                  product_name TEXT NOT NULL,
                  unit TEXT NOT NULL,
                  quantity REAL NOT NULL,
                  unit_price REAL NOT NULL,
                  custom_price REAL,
                  amount REAL NOT NULL
                )""");
            // NOTE: idx_tx_items_item_code / idx_tx_items_tx_id are created in
            // applyMigrations() AFTER the ALTER TABLE that adds item_code to legacy DBs.
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS payments (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  transaction_id INTEGER NOT NULL REFERENCES transactions(id),
                  method TEXT NOT NULL CHECK(method IN ('cash','gcash','maya','po')),
                  po_account_id INTEGER REFERENCES po_accounts(id),
                  amount REAL NOT NULL,
                  reference_no TEXT,
                  sender_name TEXT
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS settings (
                  key TEXT PRIMARY KEY,
                  value TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS trucks (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  plate_no TEXT NOT NULL UNIQUE,
                  default_price REAL,
                  updated_at TEXT
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS truck_prices (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  plate_no TEXT NOT NULL,
                  product_id INTEGER NOT NULL,
                  product_name TEXT NOT NULL,
                  default_price REAL NOT NULL,
                  updated_at TEXT NOT NULL,
                  UNIQUE(plate_no, product_id)
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS expenses (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  date TEXT NOT NULL,
                  category TEXT NOT NULL DEFAULT 'General',
                  description TEXT NOT NULL,
                  amount REAL NOT NULL,
                  created_at TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  entity_type TEXT NOT NULL,
                  entity_id INTEGER NOT NULL,
                  action TEXT NOT NULL DEFAULT 'create',
                  payload TEXT NOT NULL,
                  synced INTEGER NOT NULL DEFAULT 0,
                  synced_at TEXT,
                  created_at TEXT NOT NULL
                )""");
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS void_log (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  transaction_id INTEGER REFERENCES transactions(id),
                  invoice_no TEXT NOT NULL,
                  action TEXT NOT NULL DEFAULT 'void',
                  reason TEXT,
                  performed_by INTEGER REFERENCES users(id),
                  created_at TEXT NOT NULL
                )""");
        }
    }

    private static void seedSettings() throws SQLException {
        String[][] defaults = {
                {"business_name", "\"Loyalty Gravel and Sand Trading\""},
                {"business_address", "\"Address\""},
                {"business_contact", "\"Contact Number\""},
                {"invoice_prefix", "\"INV\""},
                {"invoice_start", "1"},
                {"invoice_counter", "1"},
                {"vat_enabled", "false"},
                {"vat_rate", "12"},
                {"currency_symbol", "\"₱\""},
                {"thermal_printer", "\"\""},
                {"normal_printer", "\"\""},
                {"po_default_credit_limit", "0"},
                {"po_default_expiry_days", "30"},
                {"log_printer", "\"\""},
                {"api_url", "\"https://loyalty-api.development.primelink.com.ph\""},
                {"api_key", "\"\""},
                {"totp_secret", "\"\""},
                {"theme", "\"dark\""},
                {"gemini_api_key", "\"\""},
                {"gemini_model", "\"gemini-3.1-flash-lite\""},
                {"ai_enabled", "false"},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO settings (key, value) VALUES (?, ?)")) {
            for (String[] row : defaults) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedAdminIfEmpty() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO users (name, role, pin_hash) VALUES (?,?,?)")) {
                    ins.setString(1, "Admin");
                    ins.setString(2, "admin");
                    ins.setString(3, Hashing.sha256("1234"));
                    ins.executeUpdate();
                }
            }
        }
    }
}

package com.innov8.loyaltypos.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.innov8.loyaltypos.db.Database;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SyncService {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static String lastError;
    private static volatile boolean isSyncing;
    private static ScheduledExecutorService SCHED;

    private SyncService() {}

    public static synchronized void startBackground() {
        if (SCHED != null) return;
        SCHED = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "sync-thread");
            t.setDaemon(true);
            return t;
        });
        // Pull-on-startup: 2s after launch, refresh local cache from API
        SCHED.schedule(() -> {
            if (ping()) {
                try { PullResult r = pullAll(); System.out.println("[sync] startup pull: " + r); } catch (Exception ignore) {}
            }
        }, 2, TimeUnit.SECONDS);
        SCHED.schedule(() -> {
            if (ping()) {
                try { fullSync(); } catch (Exception ignore) {}
            }
        }, 8, TimeUnit.SECONDS);
        SCHED.scheduleAtFixedRate(() -> {
            try { if (ping()) flushQueue(); } catch (Exception ignore) {}
        }, 60, 60, TimeUnit.SECONDS);
    }

    // ─── PULL (mirror sync) ──────────────────────────────────────────────────
    /** Entity types in dependency order — parents first so FK upserts succeed. */
    private static final String[] PULL_ENTITIES = {
            "users", "customers", "products", "po_accounts", "po_payments",
            "transactions", "transaction_items", "payments",
            "trucks", "truck_prices", "expenses"
    };

    public static class PullResult {
        public final Map<String, Integer> rowsByEntity = new LinkedHashMap<>();
        public final Map<String, String> errors = new LinkedHashMap<>();
        public boolean skipped;
        public int totalRows() { return rowsByEntity.values().stream().mapToInt(Integer::intValue).sum(); }
        @Override public String toString() {
            return "PullResult{rows=" + rowsByEntity + ", errors=" + errors + ", skipped=" + skipped + "}";
        }
    }

    /** GET each entity from the API and upsert into the local cache. */
    public static PullResult pullAll() {
        PullResult r = new PullResult();
        String url = getApiUrl();
        if (url == null) { r.skipped = true; return r; }
        for (String entity : PULL_ENTITIES) {
            try {
                int n = pullEntity(url, entity);
                r.rowsByEntity.put(entity, n);
            } catch (Exception e) {
                r.errors.put(entity, e.getMessage());
                lastError = "[pull " + entity + "] " + e.getMessage();
            }
        }
        return r;
    }

    private static int pullEntity(String apiUrl, String entity) throws Exception {
        // Try /api/{entity} first, then /api/sync/{entity} as fallback
        HttpResponse<String> res = tryGet(apiUrl + "/api/" + entity);
        if (res == null || res.statusCode() == 404) {
            res = tryGet(apiUrl + "/api/sync/" + entity);
        }
        if (res == null) throw new RuntimeException("network error");
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("HTTP " + res.statusCode());
        }
        Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
        List<Map<String, Object>> rows = GSON.fromJson(res.body(), listType);
        if (rows == null || rows.isEmpty()) return 0;
        return upsertRows(entity, rows);
    }

    private static HttpResponse<String> tryGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-API-Key", getApiKey())
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { return null; }
    }

    /** Upsert rows into a table; ignores keys not present as columns. */
    private static int upsertRows(String table, List<Map<String, Object>> rows) throws Exception {
        // Discover column names + types from local schema
        List<String> cols = new ArrayList<>();
        DatabaseMetaData md = Database.get().getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, null)) {
            while (rs.next()) cols.add(rs.getString("COLUMN_NAME"));
        }
        if (cols.isEmpty()) throw new RuntimeException("unknown table: " + table);

        StringBuilder colSql = new StringBuilder();
        StringBuilder qSql = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) { colSql.append(','); qSql.append(','); }
            colSql.append(cols.get(i));
            qSql.append('?');
        }
        String sql = "INSERT OR REPLACE INTO " + table + " (" + colSql + ") VALUES (" + qSql + ")";

        int n = 0;
        boolean autoCommit = Database.get().getAutoCommit();
        Database.get().setAutoCommit(false);
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < cols.size(); i++) {
                    Object v = row.get(cols.get(i));
                    setParam(ps, i + 1, v);
                }
                ps.addBatch();
                n++;
            }
            ps.executeBatch();
            Database.get().commit();
        } catch (Exception e) {
            Database.get().rollback();
            throw e;
        } finally {
            Database.get().setAutoCommit(autoCommit);
        }
        return n;
    }

    private static void setParam(PreparedStatement ps, int i, Object v) throws Exception {
        if (v == null) { ps.setObject(i, null); return; }
        if (v instanceof Number n) {
            // Gson parses all numbers as Double. Detect ints vs reals.
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) ps.setLong(i, (long) d);
            else ps.setDouble(i, d);
            return;
        }
        if (v instanceof Boolean b) { ps.setInt(i, b ? 1 : 0); return; }
        ps.setString(i, v.toString());
    }

    private static String getApiUrl() {
        String u = SettingsService.getString("api_url", "");
        if (u == null) return null;
        u = u.trim();
        if (u.isEmpty()) return null;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String getApiKey() {
        return SettingsService.getString("api_key", "");
    }

    public static void queue(String entityType, int entityId, String action, Object payload) {
        try (PreparedStatement del = Database.get().prepareStatement(
                "DELETE FROM sync_queue WHERE entity_type=? AND entity_id=? AND synced=0")) {
            del.setString(1, entityType);
            del.setString(2, String.valueOf(entityId));
            del.executeUpdate();
        } catch (Exception ignore) {}
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO sync_queue (entity_type, entity_id, action, payload, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, entityType);
            ps.setString(2, String.valueOf(entityId));
            ps.setString(3, action);
            ps.setString(4, GSON.toJson(payload));
            ps.setString(5, OffsetDateTime.now().toString());
            ps.executeUpdate();
        } catch (Exception ignore) {}
    }

    public static boolean ping() {
        String url = getApiUrl();
        if (url == null) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/health"))
                    .header("X-API-Key", getApiKey())
                    .timeout(Duration.ofSeconds(4))
                    .GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception e) { return false; }
    }

    public static class FlushResult {
        public int flushed;
        public int failed;
        public Integer pending;
        public boolean skipped;
        public String lastError;
    }

    public static FlushResult flushQueue() {
        FlushResult r = new FlushResult();
        String url = getApiUrl();
        if (url == null) { r.skipped = true; return r; }
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM sync_queue WHERE synced=0 ORDER BY created_at ASC LIMIT 100")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String entityType = rs.getString("entity_type");
                String entityId = rs.getString("entity_id");
                String action = rs.getString("action");
                String payload = rs.getString("payload");
                try {
                    String body = GSON.toJson(Map.of(
                            "action", action,
                            "id", entityId,
                            "data", GSON.fromJson(payload, Object.class)));
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/api/sync/" + entityType))
                            .header("Content-Type", "application/json")
                            .header("X-API-Key", getApiKey())
                            .timeout(Duration.ofSeconds(8))
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() >= 200 && res.statusCode() < 300) {
                        try (PreparedStatement upd = Database.get().prepareStatement(
                                "UPDATE sync_queue SET synced=1, synced_at=? WHERE id=?")) {
                            upd.setString(1, OffsetDateTime.now().toString());
                            upd.setInt(2, id);
                            upd.executeUpdate();
                        }
                        r.flushed++;
                    } else {
                        r.failed++;
                        r.lastError = "HTTP " + res.statusCode();
                        lastError = r.lastError;
                    }
                } catch (Exception e) {
                    r.failed++;
                    r.lastError = e.getMessage();
                    lastError = r.lastError;
                }
            }
        } catch (Exception e) {
            r.lastError = e.getMessage();
            lastError = r.lastError;
        }
        return r;
    }

    public static FlushResult fullSync() {
        FlushResult r = new FlushResult();
        String url = getApiUrl();
        if (url == null) { r.skipped = true; return r; }
        if (isSyncing) { r.skipped = true; r.lastError = "already running"; return r; }
        isSyncing = true;
        try {
            pushAll(url, "customers", "SELECT * FROM customers", r);
            pushAll(url, "products", "SELECT * FROM products", r);
            pushAll(url, "po_accounts", "SELECT * FROM po_accounts", r);
            pushAll(url, "po_payments", "SELECT * FROM po_payments", r);
            pushAll(url, "expenses", "SELECT * FROM expenses", r);
            pushAll(url, "trucks", "SELECT * FROM trucks", r);
            pushAll(url, "truck_prices", "SELECT * FROM truck_prices", r);
            try (PreparedStatement ps = Database.get().prepareStatement("DELETE FROM sync_queue WHERE synced=1")) {
                ps.executeUpdate();
            }
        } catch (Exception e) {
            r.lastError = e.getMessage();
            lastError = e.getMessage();
        } finally {
            isSyncing = false;
        }
        return r;
    }

    private static void pushAll(String url, String entityType, String selectSql, FlushResult r) {
        try (PreparedStatement ps = Database.get().prepareStatement(selectSql)) {
            ResultSet rs = ps.executeQuery();
            java.sql.ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    row.put(md.getColumnName(i), rs.getObject(i));
                }
                int id = (Integer) row.getOrDefault("id", -1);
                try {
                    String body = GSON.toJson(Map.of("action", "upsert", "id", String.valueOf(id), "data", row));
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/api/sync/" + entityType))
                            .header("Content-Type", "application/json")
                            .header("X-API-Key", getApiKey())
                            .timeout(Duration.ofSeconds(10))
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() >= 200 && res.statusCode() < 300) r.flushed++;
                    else { r.failed++; r.lastError = "HTTP " + res.statusCode(); }
                } catch (Exception e) {
                    r.failed++;
                    r.lastError = e.getMessage();
                }
            }
        } catch (Exception e) {
            r.lastError = e.getMessage();
        }
    }

    public static class Status {
        public int pending;
        public boolean online;
        public String lastError;
        public boolean syncing;
    }

    public static Status status() {
        Status s = new Status();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT COUNT(*) FROM sync_queue WHERE synced=0")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) s.pending = rs.getInt(1);
        } catch (Exception ignore) {}
        s.online = ping();
        s.lastError = lastError;
        s.syncing = isSyncing;
        return s;
    }
}

package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.DayLog;
import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;
import com.innov8.loyaltypos.model.VoidLogEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TransactionService {
    private TransactionService() {}

    public static List<Transaction> list(String from, String to) {
        List<Transaction> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder("""
                SELECT t.*, c.name AS customer_name, u.name AS cashier_name
                FROM transactions t
                LEFT JOIN customers c ON c.id=t.customer_id
                JOIN users u ON u.id=t.cashier_id""");
        boolean range = from != null && !from.isEmpty() && to != null && !to.isEmpty();
        if (range) sb.append(" WHERE date(t.date,'localtime') BETWEEN ? AND ?");
        sb.append(" ORDER BY t.date DESC LIMIT 500");
        try (PreparedStatement ps = Database.get().prepareStatement(sb.toString())) {
            if (range) { ps.setString(1, from); ps.setString(2, to); }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(mapRow(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static Transaction get(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT t.*, c.name AS customer_name, c.tin AS customer_tin, c.address AS customer_address, u.name AS cashier_name
                FROM transactions t
                LEFT JOIN customers c ON c.id=t.customer_id
                JOIN users u ON u.id=t.cashier_id
                WHERE t.id=?""")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Transaction t = mapRow(rs);
            try { t.customerTin = rs.getString("customer_tin"); } catch (Exception ignore) {}
            try { t.customerAddress = rs.getString("customer_address"); } catch (Exception ignore) {}
            t.items = items(id);
            t.payments = payments(id);
            return t;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static List<TransactionItem> items(int txId) {
        List<TransactionItem> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM transaction_items WHERE transaction_id=?")) {
            ps.setInt(1, txId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TransactionItem it = new TransactionItem();
                it.id = rs.getInt("id");
                it.transactionId = rs.getInt("transaction_id");
                int pid = rs.getInt("product_id");
                it.productId = rs.wasNull() ? null : pid;
                try { it.itemCode = rs.getString("item_code"); } catch (Exception ignore) {}
                it.productName = rs.getString("product_name");
                it.unit = rs.getString("unit");
                it.quantity = rs.getDouble("quantity");
                it.unitPrice = rs.getDouble("unit_price");
                double cp = rs.getDouble("custom_price");
                it.customPrice = rs.wasNull() ? null : cp;
                it.amount = rs.getDouble("amount");
                out.add(it);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static List<Payment> payments(int txId) {
        List<Payment> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM payments WHERE transaction_id=?")) {
            ps.setInt(1, txId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Payment p = new Payment();
                p.id = rs.getInt("id");
                p.transactionId = rs.getInt("transaction_id");
                p.method = rs.getString("method");
                int poId = rs.getInt("po_account_id");
                p.poAccountId = rs.wasNull() ? null : poId;
                p.amount = rs.getDouble("amount");
                p.referenceNo = rs.getString("reference_no");
                p.senderName = rs.getString("sender_name");
                out.add(p);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static void voidTransaction(int id, String reason, String token, Integer userId) {
        TotpService.VerifyResult check = TotpService.verify(token);
        if (!check.valid) {
            if ("no_secret".equals(check.error))
                throw new RuntimeException("2FA not configured. Admin must set up Google Authenticator in Settings.");
            throw new RuntimeException("Invalid 2FA code. Void rejected.");
        }
        try {
            Database.get().setAutoCommit(false);
            Transaction tx;
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT * FROM transactions WHERE id=?")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new RuntimeException("Transaction not found");
                tx = mapRow(rs);
            }
            if ("voided".equals(tx.paymentStatus)) throw new RuntimeException("Transaction is already voided");

            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT po_account_id, amount FROM payments WHERE transaction_id=? AND method='po' AND po_account_id IS NOT NULL")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try (PreparedStatement upd = Database.get().prepareStatement(
                            "UPDATE po_accounts SET balance_used = MAX(0, balance_used - ?) WHERE id=?")) {
                        upd.setDouble(1, rs.getDouble("amount"));
                        upd.setInt(2, rs.getInt("po_account_id"));
                        upd.executeUpdate();
                    }
                }
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT product_id, quantity FROM transaction_items WHERE transaction_id=? AND product_id IS NOT NULL")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try (PreparedStatement upd = Database.get().prepareStatement(
                            "UPDATE products SET stock_qty = stock_qty + ? WHERE id=?")) {
                        upd.setDouble(1, rs.getDouble("quantity"));
                        upd.setInt(2, rs.getInt("product_id"));
                        upd.executeUpdate();
                    }
                }
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "UPDATE transactions SET payment_status='voided' WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "INSERT INTO void_log (transaction_id, invoice_no, action, reason, performed_by, created_at) VALUES (?,?,?,?,?,?)")) {
                ps.setInt(1, id);
                ps.setString(2, tx.invoiceNo);
                ps.setString(3, "void");
                if (reason == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, reason);
                if (userId == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, userId);
                ps.setString(6, OffsetDateTime.now().toString());
                ps.executeUpdate();
            }
            Database.get().commit();
        } catch (Exception e) {
            try { Database.get().rollback(); } catch (Exception ignore) {}
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            try { Database.get().setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    public static void delete(int id, String token, Integer userId) {
        TotpService.VerifyResult check = TotpService.verify(token);
        if (!check.valid) {
            if ("no_secret".equals(check.error))
                throw new RuntimeException("2FA not configured. Admin must set up Google Authenticator in Settings.");
            throw new RuntimeException("Invalid 2FA code. Delete rejected.");
        }
        try {
            Database.get().setAutoCommit(false);
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT po_account_id, amount FROM payments WHERE transaction_id=? AND method='po' AND po_account_id IS NOT NULL")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try (PreparedStatement upd = Database.get().prepareStatement(
                            "UPDATE po_accounts SET balance_used = MAX(0, balance_used - ?) WHERE id=?")) {
                        upd.setDouble(1, rs.getDouble("amount"));
                        upd.setInt(2, rs.getInt("po_account_id"));
                        upd.executeUpdate();
                    }
                }
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT product_id, quantity FROM transaction_items WHERE transaction_id=? AND product_id IS NOT NULL")) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try (PreparedStatement upd = Database.get().prepareStatement(
                            "UPDATE products SET stock_qty = stock_qty + ? WHERE id=?")) {
                        upd.setDouble(1, rs.getDouble("quantity"));
                        upd.setInt(2, rs.getInt("product_id"));
                        upd.executeUpdate();
                    }
                }
            }
            for (String t : new String[]{"transaction_items", "payments", "void_log"}) {
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "DELETE FROM " + t + " WHERE transaction_id=?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "DELETE FROM transactions WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            Database.get().commit();
        } catch (Exception e) {
            try { Database.get().rollback(); } catch (Exception ignore) {}
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            try { Database.get().setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    public static List<VoidLogEntry> voidLogList(String from, String to) {
        List<VoidLogEntry> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder("""
                SELECT vl.*, u.name AS performed_by_name
                FROM void_log vl
                LEFT JOIN users u ON u.id=vl.performed_by""");
        boolean range = from != null && !from.isEmpty() && to != null && !to.isEmpty();
        if (range) sb.append(" WHERE date(vl.created_at,'localtime') BETWEEN ? AND ?");
        sb.append(" ORDER BY vl.created_at DESC");
        try (PreparedStatement ps = Database.get().prepareStatement(sb.toString())) {
            if (range) { ps.setString(1, from); ps.setString(2, to); }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                VoidLogEntry v = new VoidLogEntry();
                v.id = rs.getInt("id");
                int tid = rs.getInt("transaction_id");
                v.transactionId = rs.wasNull() ? null : tid;
                v.invoiceNo = rs.getString("invoice_no");
                v.action = rs.getString("action");
                v.reason = rs.getString("reason");
                int by = rs.getInt("performed_by");
                v.performedBy = rs.wasNull() ? null : by;
                v.performedByName = rs.getString("performed_by_name");
                v.createdAt = rs.getString("created_at");
                out.add(v);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static DayLog dayLog(String date) {
        DayLog log = new DayLog();
        log.date = date;
        log.transactions = new ArrayList<>();
        log.voidLog = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT t.*, c.name AS customer_name, u.name AS cashier_name
                FROM transactions t
                LEFT JOIN customers c ON c.id=t.customer_id
                JOIN users u ON u.id=t.cashier_id
                WHERE date(t.date,'localtime') = ?
                ORDER BY t.date ASC""")) {
            ps.setString(1, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) log.transactions.add(mapRow(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT vl.*, u.name AS performed_by_name
                FROM void_log vl
                LEFT JOIN users u ON u.id=vl.performed_by
                WHERE date(vl.created_at,'localtime') = ?
                ORDER BY vl.created_at ASC""")) {
            ps.setString(1, date);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                VoidLogEntry v = new VoidLogEntry();
                v.id = rs.getInt("id");
                v.invoiceNo = rs.getString("invoice_no");
                v.reason = rs.getString("reason");
                v.performedByName = rs.getString("performed_by_name");
                v.createdAt = rs.getString("created_at");
                log.voidLog.add(v);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return log;
    }

    static Transaction mapRow(ResultSet rs) throws java.sql.SQLException {
        Transaction t = new Transaction();
        t.id = rs.getInt("id");
        t.invoiceNo = rs.getString("invoice_no");
        t.date = rs.getString("date");
        int cid = rs.getInt("customer_id");
        t.customerId = rs.wasNull() ? null : cid;
        t.cashierId = rs.getInt("cashier_id");
        t.deliveryCharge = rs.getDouble("delivery_charge");
        t.discount = rs.getDouble("discount");
        t.subtotal = rs.getDouble("subtotal");
        t.totalAmount = rs.getDouble("total_amount");
        t.paymentStatus = rs.getString("payment_status");
        try { t.plateNo = rs.getString("plate_no"); } catch (Exception ignore) {}
        try { t.customerName = rs.getString("customer_name"); } catch (Exception ignore) {}
        try { t.cashierName = rs.getString("cashier_name"); } catch (Exception ignore) {}
        return t;
    }
}

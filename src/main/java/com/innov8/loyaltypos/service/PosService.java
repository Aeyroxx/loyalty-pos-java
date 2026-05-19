package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public final class PosService {
    private PosService() {}

    public static class CheckoutResult {
        public int id;
        public String invoiceNo;
        public String date;
    }

    public static CheckoutResult checkout(
            int cashierId,
            Integer customerId,
            List<TransactionItem> items,
            double deliveryCharge,
            double discount,
            List<Payment> payments,
            String plateNo,
            String customDate
    ) {
        CheckoutResult res = new CheckoutResult();
        // Pre-flight stock check: refuse the sale if any line would drive stock negative.
        // Per professor revision: orders must stop when quantity reaches 0.
        for (TransactionItem item : items) {
            if (item.productId == null) continue;
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT name, stock_qty FROM products WHERE id=?")) {
                ps.setInt(1, item.productId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    double available = rs.getDouble("stock_qty");
                    if (item.quantity > available + 1e-9) {
                        String name = rs.getString("name");
                        throw new RuntimeException("Insufficient stock for \"" + name + "\": "
                                + "requested " + item.quantity + " " + item.unit
                                + ", only " + available + " available.");
                    }
                }
            } catch (RuntimeException re) { throw re; }
              catch (Exception ex) { throw new RuntimeException(ex); }
        }
        try {
            Database.get().setAutoCommit(false);

            double subtotal = items.stream().mapToDouble(i -> i.amount).sum();
            double total = subtotal + deliveryCharge - discount;
            double paid = payments.stream().mapToDouble(p -> p.amount).sum();
            String status;
            if (paid == 0) status = "unpaid";
            else if (paid < total - 0.01) status = "partial";
            else status = "paid";

            // Always tag with the system's local zone offset so SQLite's
            // date(date,'localtime') produces the same wall-clock the user typed
            // (was: a naive ISO string would be treated as UTC and shifted).
            ZoneId zone = ZoneId.systemDefault();
            String date;
            if (customDate == null || customDate.isEmpty()) {
                date = OffsetDateTime.now(zone).toString();
            } else if (customDate.contains("+") || customDate.endsWith("Z")
                    || customDate.matches(".*[+-]\\d{2}:\\d{2}$")) {
                date = customDate;
            } else {
                // "2026-05-20T14:30:00" → "2026-05-20T14:30:00+08:00"
                try {
                    var ldt = java.time.LocalDateTime.parse(customDate.length() == 16
                            ? customDate + ":00" : customDate);
                    date = ldt.atZone(zone).toOffsetDateTime().toString();
                } catch (Exception ex) {
                    date = OffsetDateTime.now(zone).toString();
                }
            }

            String invoice = nextInvoiceNo(date);

            int txId;
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "INSERT INTO transactions (invoice_no, date, customer_id, cashier_id, delivery_charge, discount, subtotal, total_amount, payment_status, plate_no) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, invoice);
                ps.setString(2, date);
                if (customerId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, customerId);
                ps.setInt(4, cashierId);
                ps.setDouble(5, deliveryCharge);
                ps.setDouble(6, discount);
                ps.setDouble(7, subtotal);
                ps.setDouble(8, total);
                ps.setString(9, status);
                if (plateNo == null || plateNo.isEmpty()) ps.setNull(10, java.sql.Types.VARCHAR); else ps.setString(10, plateNo);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                txId = rs.next() ? rs.getInt(1) : -1;
            }

            for (TransactionItem item : items) {
                // Per professor revision: ensure item_code snapshot is captured so reports
                // stay accurate even if the source product is later renamed or recoded.
                if ((item.itemCode == null || item.itemCode.isEmpty()) && item.productId != null) {
                    try (PreparedStatement ps = Database.get().prepareStatement(
                            "SELECT item_code FROM products WHERE id=?")) {
                        ps.setInt(1, item.productId);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) item.itemCode = rs.getString(1);
                    }
                }
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "INSERT INTO transaction_items (transaction_id, product_id, item_code, product_name, unit, quantity, unit_price, custom_price, amount) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, txId);
                    if (item.productId == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, item.productId);
                    if (item.itemCode == null || item.itemCode.isEmpty()) ps.setNull(3, java.sql.Types.VARCHAR); else ps.setString(3, item.itemCode);
                    ps.setString(4, item.productName);
                    ps.setString(5, item.unit);
                    ps.setDouble(6, item.quantity);
                    ps.setDouble(7, item.unitPrice);
                    if (item.customPrice == null) ps.setNull(8, java.sql.Types.REAL); else ps.setDouble(8, item.customPrice);
                    ps.setDouble(9, item.amount);
                    ps.executeUpdate();
                }
                if (item.productId != null) {
                    // Cascading stock depletion: keeps masterlist in sync with the moment
                    // the transaction is committed (professor requirement #4).
                    try (PreparedStatement ps = Database.get().prepareStatement(
                            "UPDATE products SET stock_qty = stock_qty - ? WHERE id=?")) {
                        ps.setDouble(1, item.quantity);
                        ps.setInt(2, item.productId);
                        ps.executeUpdate();
                    }
                }
            }

            for (Payment p : payments) {
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "INSERT INTO payments (transaction_id, method, po_account_id, amount, reference_no, sender_name) VALUES (?,?,?,?,?,?)")) {
                    ps.setInt(1, txId);
                    ps.setString(2, p.method);
                    if (p.poAccountId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, p.poAccountId);
                    ps.setDouble(4, p.amount);
                    if (p.referenceNo == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, p.referenceNo);
                    if (p.senderName == null) ps.setNull(6, java.sql.Types.VARCHAR); else ps.setString(6, p.senderName);
                    ps.executeUpdate();
                }
                if ("po".equals(p.method) && p.poAccountId != null) {
                    try (PreparedStatement ps = Database.get().prepareStatement(
                            "UPDATE po_accounts SET balance_used = balance_used + ? WHERE id=?")) {
                        ps.setDouble(1, p.amount);
                        ps.setInt(2, p.poAccountId);
                        ps.executeUpdate();
                    }
                }
            }

            Database.get().commit();
            res.id = txId;
            res.invoiceNo = invoice;
            res.date = date;
            SyncService.queue("transactions", txId, "create", res);
            return res;
        } catch (Exception e) {
            try { Database.get().rollback(); } catch (Exception ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { Database.get().setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    private static String nextInvoiceNo(String date) throws java.sql.SQLException {
        String prefix = SettingsService.getString("invoice_prefix", "INV");
        int counter = SettingsService.getInt("invoice_counter", 1);
        int year;
        try { year = Integer.parseInt(date.substring(0, 4)); } catch (Exception e) { year = java.time.Year.now().getValue(); }
        String padded = String.format("%05d", counter);
        SettingsService.set("invoice_counter", counter + 1);
        return prefix + "-" + year + "-" + padded;
    }
}

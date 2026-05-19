package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.PoAccount;
import com.innov8.loyaltypos.model.PoPayment;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class PoService {
    private PoService() {}

    public static List<PoAccount> list() {
        List<PoAccount> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT p.*, c.name AS customer_name
                FROM po_accounts p JOIN customers c ON c.id = p.customer_id
                ORDER BY p.issued_date DESC""")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static List<PoAccount> openByCustomer(int customerId) {
        List<PoAccount> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM po_accounts WHERE customer_id=? AND status='open' ORDER BY issued_date DESC")) {
            ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static int create(PoAccount p) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO po_accounts (customer_id, reference_no, issued_date, expiry_date, credit_limit, balance_used, status) VALUES (?,?,?,?,?,0,'open')",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.customerId);
            ps.setString(2, p.referenceNo == null ? "" : p.referenceNo);
            ps.setString(3, p.issuedDate);
            if (p.expiryDate != null && !p.expiryDate.isEmpty()) ps.setString(4, p.expiryDate);
            else ps.setNull(4, java.sql.Types.VARCHAR);
            ps.setDouble(5, p.creditLimit);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            SyncService.queue("po_accounts", id, "create", p);
            return id;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void update(PoAccount p) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE po_accounts SET reference_no=?, expiry_date=?, credit_limit=?, status=? WHERE id=?")) {
            ps.setString(1, p.referenceNo == null ? "" : p.referenceNo);
            if (p.expiryDate != null && !p.expiryDate.isEmpty()) ps.setString(2, p.expiryDate);
            else ps.setNull(2, java.sql.Types.VARCHAR);
            ps.setDouble(3, p.creditLimit);
            ps.setString(4, p.status);
            ps.setInt(5, p.id);
            ps.executeUpdate();
            SyncService.queue("po_accounts", p.id, "update", p);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static List<PoPayment> payments(int poId) {
        List<PoPayment> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM po_payments WHERE po_account_id=? ORDER BY payment_date DESC")) {
            ps.setInt(1, poId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                PoPayment p = new PoPayment();
                p.id = rs.getInt("id");
                p.poAccountId = rs.getInt("po_account_id");
                p.amount = rs.getDouble("amount");
                p.paymentDate = rs.getString("payment_date");
                p.notes = rs.getString("notes");
                out.add(p);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static void addPayment(int poAccountId, double amount, String paymentDate, String notes) {
        try {
            Database.get().setAutoCommit(false);
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "INSERT INTO po_payments (po_account_id, amount, payment_date, notes) VALUES (?,?,?,?)")) {
                ps.setInt(1, poAccountId);
                ps.setDouble(2, amount);
                ps.setString(3, paymentDate);
                ps.setString(4, notes == null ? "" : notes);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "UPDATE po_accounts SET balance_used = balance_used - ? WHERE id=?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, poAccountId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT balance_used FROM po_accounts WHERE id=?")) {
                ps.setInt(1, poAccountId);
                ResultSet rs = ps.executeQuery();
                if (rs.next() && rs.getDouble(1) <= 0) {
                    try (PreparedStatement closer = Database.get().prepareStatement(
                            "UPDATE po_accounts SET status='closed' WHERE id=?")) {
                        closer.setInt(1, poAccountId);
                        closer.executeUpdate();
                    }
                }
            }
            Database.get().commit();
        } catch (Exception e) {
            try { Database.get().rollback(); } catch (Exception ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { Database.get().setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    static PoAccount map(ResultSet rs) throws java.sql.SQLException {
        PoAccount p = new PoAccount();
        p.id = rs.getInt("id");
        p.customerId = rs.getInt("customer_id");
        try { p.customerName = rs.getString("customer_name"); } catch (Exception ignore) {}
        p.referenceNo = rs.getString("reference_no");
        p.issuedDate = rs.getString("issued_date");
        p.expiryDate = rs.getString("expiry_date");
        p.creditLimit = rs.getDouble("credit_limit");
        p.balanceUsed = rs.getDouble("balance_used");
        p.status = rs.getString("status");
        return p;
    }
}

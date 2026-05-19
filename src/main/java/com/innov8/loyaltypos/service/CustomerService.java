package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Customer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class CustomerService {
    private CustomerService() {}

    public static List<Customer> list() {
        List<Customer> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM customers ORDER BY name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static int create(Customer c) {
        try {
            Database.get().setAutoCommit(false);
            int customerId;
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "INSERT INTO customers (name, tin, address, phone, credit_limit, notes) VALUES (?,?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, c.name);
                ps.setString(2, c.tin == null ? "" : c.tin);
                ps.setString(3, c.address == null ? "" : c.address);
                ps.setString(4, c.phone == null ? "" : c.phone);
                ps.setDouble(5, c.creditLimit);
                ps.setString(6, c.notes == null ? "" : c.notes);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                customerId = rs.next() ? rs.getInt(1) : -1;
            }
            if (c.creditLimit > 0) {
                String today = LocalDate.now().toString();
                int expiryDays = SettingsService.getInt("po_default_expiry_days", 0);
                String expiryDate = expiryDays > 0 ? LocalDate.now().plusDays(expiryDays).toString() : null;
                try (PreparedStatement po = Database.get().prepareStatement(
                        "INSERT INTO po_accounts (customer_id, reference_no, issued_date, expiry_date, credit_limit, balance_used, status) VALUES (?,?,?,?,?,0,'open')")) {
                    po.setInt(1, customerId);
                    po.setString(2, "");
                    po.setString(3, today);
                    if (expiryDate != null) po.setString(4, expiryDate); else po.setNull(4, java.sql.Types.VARCHAR);
                    po.setDouble(5, c.creditLimit);
                    po.executeUpdate();
                }
            }
            Database.get().commit();
            SyncService.queue("customers", customerId, "create", c);
            return customerId;
        } catch (Exception e) {
            try { Database.get().rollback(); } catch (Exception ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { Database.get().setAutoCommit(true); } catch (Exception ignore) {}
        }
    }

    public static void update(Customer c) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE customers SET name=?, tin=?, address=?, phone=?, credit_limit=?, notes=? WHERE id=?")) {
            ps.setString(1, c.name);
            ps.setString(2, c.tin == null ? "" : c.tin);
            ps.setString(3, c.address == null ? "" : c.address);
            ps.setString(4, c.phone == null ? "" : c.phone);
            ps.setDouble(5, c.creditLimit);
            ps.setString(6, c.notes == null ? "" : c.notes);
            ps.setInt(7, c.id);
            ps.executeUpdate();
            SyncService.queue("customers", c.id, "update", c);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** Returns null on success, or an error message string. */
    public static String delete(int id) {
        try {
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT 1 FROM transactions WHERE customer_id=? LIMIT 1")) {
                ps.setInt(1, id);
                if (ps.executeQuery().next()) return "Cannot delete customer with existing transactions or PO accounts.";
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT 1 FROM po_accounts WHERE customer_id=? LIMIT 1")) {
                ps.setInt(1, id);
                if (ps.executeQuery().next()) return "Cannot delete customer with existing transactions or PO accounts.";
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "DELETE FROM customers WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return null;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static Customer map(ResultSet rs) throws java.sql.SQLException {
        Customer c = new Customer();
        c.id = rs.getInt("id");
        c.name = rs.getString("name");
        c.tin = nz(rs.getString("tin"));
        c.address = nz(rs.getString("address"));
        c.phone = nz(rs.getString("phone"));
        c.creditLimit = rs.getDouble("credit_limit");
        c.notes = nz(rs.getString("notes"));
        return c;
    }
    private static String nz(String s) { return s == null ? "" : s; }
}

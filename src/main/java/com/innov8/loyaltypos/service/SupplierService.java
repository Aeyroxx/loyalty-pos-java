package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Supplier;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** CRUD for suppliers — separate table referenced by products.supplier_id. */
public final class SupplierService {
    private SupplierService() {}

    public static List<Supplier> list() {
        List<Supplier> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM suppliers WHERE is_active=1 ORDER BY name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static Supplier get(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM suppliers WHERE id=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static int create(Supplier s) {
        validate(s, 0);
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO suppliers (name, contact_person, phone, email, address, notes) VALUES (?,?,?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.name);
            ps.setString(2, nz(s.contactPerson));
            ps.setString(3, nz(s.phone));
            ps.setString(4, nz(s.email));
            ps.setString(5, nz(s.address));
            ps.setString(6, nz(s.notes));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            s.id = id;
            SyncService.queue("suppliers", id, "create", s);
            return id;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void update(Supplier s) {
        validate(s, s.id);
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE suppliers SET name=?, contact_person=?, phone=?, email=?, address=?, notes=? WHERE id=?")) {
            ps.setString(1, s.name);
            ps.setString(2, nz(s.contactPerson));
            ps.setString(3, nz(s.phone));
            ps.setString(4, nz(s.email));
            ps.setString(5, nz(s.address));
            ps.setString(6, nz(s.notes));
            ps.setInt(7, s.id);
            ps.executeUpdate();
            SyncService.queue("suppliers", s.id, "update", s);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void delete(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE suppliers SET is_active=0 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void validate(Supplier s, int existingId) {
        if (s.name == null || s.name.trim().isEmpty())
            throw new RuntimeException("Supplier name is required.");
        s.name = s.name.trim();
        // Reject pure-numeric names (per professor: "bawal numbers-only")
        if (s.name.matches("^[\\d\\s.,-]+$"))
            throw new RuntimeException("Supplier name cannot be numbers only.");
        // Unique check
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id FROM suppliers WHERE name = ? COLLATE NOCASE AND id <> ? AND is_active = 1")) {
            ps.setString(1, s.name);
            ps.setInt(2, existingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new RuntimeException("A supplier named '" + s.name + "' already exists.");
        } catch (RuntimeException re) { throw re; } catch (Exception e) { throw new RuntimeException(e); }
    }

    static Supplier map(ResultSet rs) throws java.sql.SQLException {
        Supplier s = new Supplier();
        s.id = rs.getInt("id");
        s.name = nz(rs.getString("name"));
        try { s.contactPerson = nz(rs.getString("contact_person")); } catch (Exception ignore) {}
        try { s.phone = nz(rs.getString("phone")); } catch (Exception ignore) {}
        try { s.email = nz(rs.getString("email")); } catch (Exception ignore) {}
        try { s.address = nz(rs.getString("address")); } catch (Exception ignore) {}
        try { s.notes = nz(rs.getString("notes")); } catch (Exception ignore) {}
        try { s.isActive = rs.getInt("is_active") == 1; } catch (Exception ignore) {}
        return s;
    }

    private static String nz(String s) { return s == null ? "" : s; }
}

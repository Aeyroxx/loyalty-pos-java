package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Product;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class ProductService {
    private ProductService() {}

    public static List<Product> list() {
        List<Product> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM products WHERE is_active=1 ORDER BY item_code, name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static Product findByItemCode(String itemCode) {
        if (itemCode == null || itemCode.isEmpty()) return null;
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM products WHERE item_code=? AND is_active=1 LIMIT 1")) {
            ps.setString(1, itemCode);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? map(rs) : null;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static int create(Product p) {
        validate(p, 0);
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO products (item_code, name, description, unit, price_per_unit, stock_qty) VALUES (?,?,?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nz(p.itemCode));
            ps.setString(2, p.name);
            ps.setString(3, nz(p.description));
            ps.setString(4, p.unit);
            ps.setDouble(5, p.pricePerUnit);
            ps.setDouble(6, p.stockQty);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            p.id = id;
            SyncService.queue("products", id, "create", p);
            return id;
        } catch (Exception e) { throw new RuntimeException(translate(e)); }
    }

    public static void update(Product p) {
        validate(p, p.id);
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE products SET item_code=?, name=?, description=?, unit=?, price_per_unit=?, stock_qty=? WHERE id=?")) {
            ps.setString(1, nz(p.itemCode));
            ps.setString(2, p.name);
            ps.setString(3, nz(p.description));
            ps.setString(4, p.unit);
            ps.setDouble(5, p.pricePerUnit);
            ps.setDouble(6, p.stockQty);
            ps.setInt(7, p.id);
            ps.executeUpdate();
            SyncService.queue("products", p.id, "update", p);
        } catch (Exception e) { throw new RuntimeException(translate(e)); }
    }

    public static void delete(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE products SET is_active=0 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static Product map(ResultSet rs) throws java.sql.SQLException {
        Product p = new Product();
        p.id = rs.getInt("id");
        try { p.itemCode = rs.getString("item_code"); if (p.itemCode == null) p.itemCode = ""; } catch (Exception ignore) {}
        p.name = rs.getString("name");
        try { p.description = rs.getString("description"); if (p.description == null) p.description = ""; } catch (Exception ignore) {}
        p.unit = rs.getString("unit");
        p.pricePerUnit = rs.getDouble("price_per_unit");
        p.stockQty = rs.getDouble("stock_qty");
        try { p.isActive = rs.getInt("is_active") == 1; } catch (Exception ignore) {}
        return p;
    }

    /** Enforce required fields and uniqueness (case-insensitive) before SQL hits the UNIQUE index. */
    private static void validate(Product p, int existingId) {
        if (p.name == null || p.name.trim().isEmpty()) throw new RuntimeException("Product name is required.");
        if (p.itemCode == null || p.itemCode.trim().isEmpty())
            throw new RuntimeException("Item code is required (per professor revision: every product must have a distinct code).");
        p.itemCode = p.itemCode.trim();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id FROM products WHERE item_code = ? COLLATE NOCASE AND id <> ?")) {
            ps.setString(1, p.itemCode);
            ps.setInt(2, existingId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) throw new RuntimeException("Item code '" + p.itemCode + "' already exists. Item codes must be unique.");
        } catch (RuntimeException re) { throw re; } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String translate(Exception e) {
        String m = e.getMessage();
        if (m != null && m.contains("UNIQUE") && m.contains("item_code"))
            return "Item code already exists. Item codes must be unique.";
        return m == null ? e.getClass().getSimpleName() : m;
    }
}

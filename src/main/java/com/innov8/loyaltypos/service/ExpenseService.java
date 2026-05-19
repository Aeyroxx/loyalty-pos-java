package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Expense;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExpenseService {
    private ExpenseService() {}

    public static List<Expense> list(String from, String to) {
        List<Expense> out = new ArrayList<>();
        String sql = (from == null || to == null)
                ? "SELECT * FROM expenses ORDER BY date DESC, id DESC"
                : "SELECT * FROM expenses WHERE date BETWEEN ? AND ? ORDER BY date DESC, id DESC";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            if (from != null && to != null) {
                ps.setString(1, from); ps.setString(2, to);
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) out.add(map(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static int create(Expense e) {
        e.createdAt = OffsetDateTime.now().toString();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO expenses (date, category, description, amount, created_at) VALUES (?,?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, e.date);
            ps.setString(2, e.category);
            ps.setString(3, e.description == null ? "" : e.description);
            ps.setDouble(4, e.amount);
            ps.setString(5, e.createdAt);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            int id = rs.next() ? rs.getInt(1) : -1;
            e.id = id;
            SyncService.queue("expenses", id, "create", e);
            return id;
        } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    public static void delete(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM expenses WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Map<String, Double> summary(String from, String to) {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT date, SUM(amount) AS total FROM expenses WHERE date BETWEEN ? AND ? GROUP BY date ORDER BY date")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) map.put(rs.getString("date"), rs.getDouble("total"));
        } catch (Exception e) { throw new RuntimeException(e); }
        return map;
    }

    private static Expense map(ResultSet rs) throws java.sql.SQLException {
        Expense e = new Expense();
        e.id = rs.getInt("id");
        e.date = rs.getString("date");
        e.category = rs.getString("category");
        e.description = rs.getString("description");
        e.amount = rs.getDouble("amount");
        e.createdAt = rs.getString("created_at");
        return e;
    }
}

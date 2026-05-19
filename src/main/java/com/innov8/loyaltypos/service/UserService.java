package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.User;
import com.innov8.loyaltypos.util.Hashing;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class UserService {
    private UserService() {}

    public static User login(String pin) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, name, role FROM users WHERE pin_hash = ? AND is_active = 1")) {
            ps.setString(1, Hashing.sha256(pin));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"));
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<User> list() {
        List<User> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, name, role, is_active FROM users ORDER BY name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"));
                u.isActive = rs.getInt("is_active") == 1;
                out.add(u);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public static int create(String name, String role, String pin) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO users (name, role, pin_hash) VALUES (?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, role);
            ps.setString(3, Hashing.sha256(pin));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void update(int id, String name, String role, String pinOrNull) {
        String sql = pinOrNull != null && !pinOrNull.isEmpty()
                ? "UPDATE users SET name=?, role=?, pin_hash=? WHERE id=?"
                : "UPDATE users SET name=?, role=? WHERE id=?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, role);
            if (pinOrNull != null && !pinOrNull.isEmpty()) {
                ps.setString(3, Hashing.sha256(pinOrNull));
                ps.setInt(4, id);
            } else {
                ps.setInt(3, id);
            }
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void deactivate(int id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE users SET is_active=0 WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}

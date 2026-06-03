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
                "SELECT id, name, role, email FROM users WHERE pin_hash = ? AND is_active = 1")) {
            ps.setString(1, Hashing.sha256(pin));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                User u = new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"));
                try { u.email = rs.getString("email"); if (u.email == null) u.email = ""; } catch (Exception ignore) {}
                return u;
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<User> list() {
        List<User> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, name, role, email, is_active FROM users ORDER BY name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                User u = new User(rs.getInt("id"), rs.getString("name"), rs.getString("role"));
                try { u.email = rs.getString("email"); if (u.email == null) u.email = ""; } catch (Exception ignore) {}
                u.isActive = rs.getInt("is_active") == 1;
                out.add(u);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public static int create(String name, String role, String pin) { return create(name, role, pin, null); }

    public static int create(String name, String role, String pin, String email) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO users (name, role, pin_hash, email) VALUES (?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, role);
            ps.setString(3, Hashing.sha256(pin));
            if (email == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, email);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void update(int id, String name, String role, String pinOrNull) {
        update(id, name, role, pinOrNull, null);
    }

    public static void update(int id, String name, String role, String pinOrNull, String emailOrNull) {
        StringBuilder sql = new StringBuilder("UPDATE users SET name=?, role=?");
        if (pinOrNull != null && !pinOrNull.isEmpty()) sql.append(", pin_hash=?");
        if (emailOrNull != null) sql.append(", email=?");
        sql.append(" WHERE id=?");
        try (PreparedStatement ps = Database.get().prepareStatement(sql.toString())) {
            int i = 1;
            ps.setString(i++, name);
            ps.setString(i++, role);
            if (pinOrNull != null && !pinOrNull.isEmpty()) ps.setString(i++, Hashing.sha256(pinOrNull));
            if (emailOrNull != null) ps.setString(i++, emailOrNull);
            ps.setInt(i, id);
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

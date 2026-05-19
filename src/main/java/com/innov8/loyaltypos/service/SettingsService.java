package com.innov8.loyaltypos.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.innov8.loyaltypos.db.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SettingsService {
    private static final Gson GSON = new Gson();

    private SettingsService() {}

    public static Map<String, Object> getAll() {
        Map<String, Object> out = new LinkedHashMap<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT key, value FROM settings")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String k = rs.getString("key");
                String v = rs.getString("value");
                out.put(k, parseJson(v));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private static Object parseJson(String v) {
        if (v == null) return null;
        try {
            JsonElement el = JsonParser.parseString(v);
            if (el.isJsonPrimitive()) {
                if (el.getAsJsonPrimitive().isString()) return el.getAsString();
                if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
                if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
            }
            if (el.isJsonNull()) return null;
            return v;
        } catch (Exception e) {
            return v;
        }
    }

    public static void set(String key, Object value) {
        String json;
        if (value == null) json = "null";
        else if (value instanceof Number || value instanceof Boolean) json = value.toString();
        else json = GSON.toJson(value.toString());
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String getString(String key, String fallback) {
        Object v = getAll().get(key);
        return v == null ? fallback : v.toString();
    }

    public static int getInt(String key, int fallback) {
        Object v = getAll().get(key);
        if (v == null) return fallback;
        try { return (int) Math.round(Double.parseDouble(v.toString())); } catch (Exception e) { return fallback; }
    }
}

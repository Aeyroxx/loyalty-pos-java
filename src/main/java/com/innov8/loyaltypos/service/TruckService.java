package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.Truck;
import com.innov8.loyaltypos.model.TruckPrice;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class TruckService {
    private TruckService() {}

    public static Truck getByPlate(String plate) {
        String norm = plate == null ? "" : plate.trim().toUpperCase();
        if (norm.isEmpty()) return null;
        Truck truck = null;
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM trucks WHERE plate_no=?")) {
            ps.setString(1, norm);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            truck = new Truck();
            truck.id = rs.getInt("id");
            truck.plateNo = rs.getString("plate_no");
            double dp = rs.getDouble("default_price");
            truck.defaultPrice = rs.wasNull() ? null : dp;
            truck.updatedAt = rs.getString("updated_at");
        } catch (Exception e) { throw new RuntimeException(e); }
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM truck_prices WHERE plate_no=? ORDER BY product_name")) {
            ps.setString(1, norm);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) truck.prices.add(mapPrice(rs));
        } catch (Exception e) { throw new RuntimeException(e); }
        return truck;
    }

    public static List<Truck> list() {
        List<Truck> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM trucks ORDER BY updated_at DESC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Truck t = new Truck();
                t.id = rs.getInt("id");
                t.plateNo = rs.getString("plate_no");
                double dp = rs.getDouble("default_price");
                t.defaultPrice = rs.wasNull() ? null : dp;
                t.updatedAt = rs.getString("updated_at");
                out.add(t);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT * FROM truck_prices ORDER BY product_name")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                TruckPrice tp = mapPrice(rs);
                for (Truck t : out) {
                    if (t.plateNo.equals(tp.plateNo)) { t.prices.add(tp); break; }
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static void upsert(String plate, List<TruckPrice> prices) {
        String norm = plate.trim().toUpperCase();
        String now = OffsetDateTime.now().toString();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                INSERT INTO trucks (plate_no, updated_at) VALUES (?,?)
                ON CONFLICT(plate_no) DO UPDATE SET updated_at=excluded.updated_at""")) {
            ps.setString(1, norm);
            ps.setString(2, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }

        for (TruckPrice item : prices) {
            if (item.productId <= 0) continue;
            try (PreparedStatement ps = Database.get().prepareStatement("""
                    INSERT INTO truck_prices (plate_no, product_id, product_name, default_price, updated_at) VALUES (?,?,?,?,?)
                    ON CONFLICT(plate_no, product_id) DO UPDATE SET default_price=excluded.default_price, product_name=excluded.product_name, updated_at=excluded.updated_at""")) {
                ps.setString(1, norm);
                ps.setInt(2, item.productId);
                ps.setString(3, item.productName);
                ps.setDouble(4, item.defaultPrice);
                ps.setString(5, now);
                ps.executeUpdate();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    public static void upsertPrice(String plate, int productId, String productName, double price) {
        String norm = plate.trim().toUpperCase();
        String now = OffsetDateTime.now().toString();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                INSERT INTO truck_prices (plate_no, product_id, product_name, default_price, updated_at) VALUES (?,?,?,?,?)
                ON CONFLICT(plate_no, product_id) DO UPDATE SET default_price=excluded.default_price, updated_at=excluded.updated_at""")) {
            ps.setString(1, norm);
            ps.setInt(2, productId);
            ps.setString(3, productName);
            ps.setDouble(4, price);
            ps.setString(5, now);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE trucks SET updated_at=? WHERE plate_no=?")) {
            ps.setString(1, now);
            ps.setString(2, norm);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void delete(String plate) {
        String norm = plate.trim().toUpperCase();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM truck_prices WHERE plate_no=?")) {
            ps.setString(1, norm);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
        try (PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM trucks WHERE plate_no=?")) {
            ps.setString(1, norm);
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static TruckPrice mapPrice(ResultSet rs) throws java.sql.SQLException {
        TruckPrice tp = new TruckPrice();
        tp.id = rs.getInt("id");
        tp.plateNo = rs.getString("plate_no");
        tp.productId = rs.getInt("product_id");
        tp.productName = rs.getString("product_name");
        tp.defaultPrice = rs.getDouble("default_price");
        tp.updatedAt = rs.getString("updated_at");
        return tp;
    }
}

package com.innov8.loyaltypos.service;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.ItemCodeSalesRow;
import com.innov8.loyaltypos.model.PoPayment;
import com.innov8.loyaltypos.model.PoStatRow;
import com.innov8.loyaltypos.model.RevenueRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReportService {
    private ReportService() {}

    public static List<RevenueRow> revenue(String from, String to, String groupBy) {
        String groupExpr = "month".equals(groupBy)
                ? "strftime('%Y-%m', t.date, 'localtime')"
                : "date(t.date, 'localtime')";

        Map<String, RevenueRow> map = new HashMap<>();
        List<RevenueRow> rows = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT " + groupExpr + " AS day, SUM(t.total_amount) AS total, SUM(t.delivery_charge) AS delivery_total " +
                        "FROM transactions t WHERE date(t.date,'localtime') BETWEEN ? AND ? AND t.payment_status != 'voided' " +
                        "GROUP BY " + groupExpr + " ORDER BY day")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                RevenueRow r = new RevenueRow(rs.getString("day"));
                r.total = rs.getDouble("total");
                r.deliveryTotal = rs.getDouble("delivery_total");
                map.put(r.day, r);
                rows.add(r);
            }
        } catch (Exception e) { throw new RuntimeException(e); }

        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT " + groupExpr + " AS day, " +
                        "SUM(CASE WHEN p.method='cash' THEN p.amount ELSE 0 END) AS cash, " +
                        "SUM(CASE WHEN p.method='gcash' THEN p.amount ELSE 0 END) AS gcash, " +
                        "SUM(CASE WHEN p.method='maya' THEN p.amount ELSE 0 END) AS maya, " +
                        "SUM(CASE WHEN p.method='po' THEN p.amount ELSE 0 END) AS po_amount " +
                        "FROM payments p JOIN transactions t ON t.id = p.transaction_id " +
                        "WHERE date(t.date,'localtime') BETWEEN ? AND ? AND t.payment_status != 'voided' " +
                        "GROUP BY " + groupExpr)) {
            ps.setString(1, from);
            ps.setString(2, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                RevenueRow r = map.get(rs.getString("day"));
                if (r == null) continue;
                r.cash = rs.getDouble("cash");
                r.gcash = rs.getDouble("gcash");
                r.maya = rs.getDouble("maya");
                r.poAmount = rs.getDouble("po_amount");
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return rows;
    }

    /**
     * Sales aggregated by item_code in [from..to], excluding voided transactions.
     *
     * <p>Pipeline guarantee (professor requirement #1, #3, #4):
     * <ul>
     *   <li>FK <code>transaction_items.product_id → products.id</code> + the snapshot
     *       <code>item_code</code> column means item identity survives renames/recodes.</li>
     *   <li>WAL journaling + auto-commit checkout transaction means a new sale is
     *       visible to this query the instant {@link PosService#checkout} returns.</li>
     *   <li>{@code payment_status != 'voided'} keeps voided sales out of revenue.</li>
     * </ul>
     * The query joins live tables — no manual sync, no stale cache.
     */
    public static List<ItemCodeSalesRow> salesByItemCode(String from, String to) {
        List<ItemCodeSalesRow> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT
                  COALESCE(ti.item_code, p.item_code, '—') AS item_code,
                  COALESCE(p.name, ti.product_name)        AS product_name,
                  p.description                            AS description,
                  ti.unit                                  AS unit,
                  SUM(ti.quantity)                         AS qty,
                  SUM(ti.amount)                           AS revenue,
                  COUNT(DISTINCT ti.transaction_id)        AS tx_count
                FROM transaction_items ti
                JOIN transactions t ON t.id = ti.transaction_id
                LEFT JOIN products p ON p.id = ti.product_id
                WHERE date(t.date,'localtime') BETWEEN ? AND ?
                  AND t.payment_status != 'voided'
                GROUP BY COALESCE(ti.item_code, p.item_code, ti.product_name)
                ORDER BY revenue DESC""")) {
            ps.setString(1, from);
            ps.setString(2, to);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ItemCodeSalesRow r = new ItemCodeSalesRow();
                r.itemCode = rs.getString("item_code");
                r.productName = rs.getString("product_name");
                r.description = rs.getString("description");
                r.unit = rs.getString("unit");
                r.quantitySold = rs.getDouble("qty");
                r.revenue = rs.getDouble("revenue");
                r.transactionCount = rs.getInt("tx_count");
                out.add(r);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static List<PoStatRow> poStats() {
        List<PoStatRow> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement("""
                SELECT c.name AS customer_name, p.reference_no, p.credit_limit, p.balance_used,
                  (p.credit_limit - p.balance_used) AS available, p.status, p.expiry_date
                FROM po_accounts p JOIN customers c ON c.id=p.customer_id
                ORDER BY c.name""")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                PoStatRow r = new PoStatRow();
                r.customerName = rs.getString("customer_name");
                r.referenceNo = rs.getString("reference_no");
                r.creditLimit = rs.getDouble("credit_limit");
                r.balanceUsed = rs.getDouble("balance_used");
                r.available = rs.getDouble("available");
                r.status = rs.getString("status");
                r.expiryDate = rs.getString("expiry_date");
                out.add(r);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public static List<PoPayment> poPaymentHistory(Integer customerId) {
        String sql = customerId == null
                ? "SELECT pp.*, c.name AS customer_name, po.reference_no FROM po_payments pp JOIN po_accounts po ON po.id=pp.po_account_id JOIN customers c ON c.id=po.customer_id ORDER BY pp.payment_date DESC"
                : "SELECT pp.*, c.name AS customer_name, po.reference_no FROM po_payments pp JOIN po_accounts po ON po.id=pp.po_account_id JOIN customers c ON c.id=po.customer_id WHERE po.customer_id=? ORDER BY pp.payment_date DESC";
        List<PoPayment> out = new ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            if (customerId != null) ps.setInt(1, customerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                PoPayment p = new PoPayment();
                p.id = rs.getInt("id");
                p.poAccountId = rs.getInt("po_account_id");
                p.amount = rs.getDouble("amount");
                p.paymentDate = rs.getString("payment_date");
                p.notes = rs.getString("notes");
                p.customerName = rs.getString("customer_name");
                p.referenceNo = rs.getString("reference_no");
                out.add(p);
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }
}

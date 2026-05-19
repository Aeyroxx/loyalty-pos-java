package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.model.DayLog;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.VoidLogEntry;
import com.innov8.loyaltypos.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class DayLogView {
    private DayLogView() {}

    public static void printDayLog(Window owner, DayLog data, Map<String, Object> settings) {
        VBox view = build(data, settings);
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) { new Modal(owner, "Error", new Label("No printer available")).show(); return; }
        boolean ok = job.showPrintDialog(owner);
        if (!ok) return;
        Printer printer = job.getPrinter();
        PageLayout layout = printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        double scale = Math.min(1.0, Math.min(layout.getPrintableWidth() / view.prefWidth(-1), layout.getPrintableHeight() / view.prefHeight(-1)));
        if (scale < 1.0) view.getTransforms().add(new Scale(scale, scale));
        if (job.printPage(layout, view)) job.endJob();
    }

    private static VBox build(DayLog data, Map<String, Object> settings) {
        VBox root = new VBox(6);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(720);

        VBox header = new VBox(2); header.setAlignment(Pos.CENTER);
        Label biz = new Label(settings.getOrDefault("business_name", "Loyalty POS").toString().toUpperCase());
        biz.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 18;");
        header.getChildren().add(biz);
        if (settings.get("business_address") != null) header.getChildren().add(plain(settings.get("business_address").toString()));
        if (settings.get("business_contact") != null) header.getChildren().add(plain(settings.get("business_contact").toString()));
        Label t = new Label("END OF DAY LOG"); t.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 13;");
        Label d = new Label(data.date); d.setStyle("-fx-text-fill: black; -fx-font-size: 12;");
        header.getChildren().addAll(t, d);
        Region rule = new Region(); rule.setStyle("-fx-background-color: black;"); rule.setPrefHeight(2);
        root.getChildren().addAll(header, rule);

        // Summary
        double totalRev = 0; int voided = 0;
        for (Transaction tx : data.transactions) {
            if (!"voided".equals(tx.paymentStatus)) totalRev += tx.totalAmount;
            else voided++;
        }
        Label sumTitle = new Label("SUMMARY");
        sumTitle.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11;");
        root.getChildren().add(sumTitle);
        sumRow(root, "Total Transactions", String.valueOf(data.transactions.size()), false);
        if (voided > 0) sumRow(root, "  Voided", String.valueOf(voided), false);
        sumRow(root, "TOTAL REVENUE", "PHP " + Money.fmt(totalRev), true);

        // Transactions
        Label tt = new Label("TRANSACTIONS (" + data.transactions.size() + ")");
        tt.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 8 0 4 0;");
        root.getChildren().add(tt);
        root.getChildren().add(txHeader());
        for (Transaction tx : data.transactions) {
            HBox row = new HBox();
            String time;
            try { time = OffsetDateTime.parse(tx.date).format(DateTimeFormatter.ofPattern("hh:mm a")); }
            catch (Exception e) { time = ""; }
            boolean voidedTx = "voided".equals(tx.paymentStatus);
            String style = "-fx-text-fill: " + (voidedTx ? "#aaa" : "black") + "; -fx-font-size: 9; -fx-padding: 3 6;";
            row.getChildren().addAll(
                    cell(tx.invoiceNo, style, true, 110),
                    cell(time, style, true, 60),
                    cell(tx.customerName == null ? "Walk-in" : tx.customerName, style, true, 130),
                    cell(tx.cashierName == null ? "—" : tx.cashierName, style, true, 100),
                    cell(tx.plateNo == null ? "—" : tx.plateNo, style, true, 80),
                    cell("PHP " + Money.fmt(tx.totalAmount), style, false, 100),
                    cell(voidedTx ? "VOID" : "OK", "-fx-text-fill: " + (voidedTx ? "#e33" : "#060") + "; -fx-font-size: 9; -fx-font-weight: bold; -fx-padding: 3 6;", false, 60)
            );
            row.setStyle("-fx-border-color: transparent transparent #eee transparent;");
            root.getChildren().add(row);
        }

        // Void log
        if (data.voidLog != null && !data.voidLog.isEmpty()) {
            Label vt = new Label("VOID LOG (" + data.voidLog.size() + ")");
            vt.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 8 0 4 0;");
            root.getChildren().add(vt);
            for (VoidLogEntry v : data.voidLog) {
                String time;
                try { time = OffsetDateTime.parse(v.createdAt).format(DateTimeFormatter.ofPattern("hh:mm a")); }
                catch (Exception e) { time = ""; }
                HBox row = new HBox();
                String style = "-fx-text-fill: black; -fx-font-size: 9; -fx-padding: 3 6;";
                row.getChildren().addAll(
                        cell(v.invoiceNo, style, true, 130),
                        cell(time, style, true, 80),
                        cell(v.performedByName == null ? "—" : v.performedByName, style, true, 130),
                        cell(v.reason == null ? "—" : v.reason, style, true, 240)
                );
                root.getChildren().add(row);
            }
        }
        return root;
    }

    private static HBox txHeader() {
        HBox row = new HBox();
        row.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: transparent transparent #999 transparent;");
        for (Object[] e : new Object[][]{
                {"Invoice No", true, 110}, {"Time", true, 60}, {"Customer", true, 130},
                {"Cashier", true, 100}, {"Plate", true, 80}, {"Amount", false, 100}, {"Status", false, 60}}) {
            Label l = new Label((String) e[0]);
            l.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 10; -fx-padding: 4 6;");
            l.setMinWidth((Integer) e[2]); l.setMaxWidth((Integer) e[2]);
            l.setAlignment((Boolean) e[1] ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            row.getChildren().add(l);
        }
        return row;
    }

    private static Label cell(String text, String style, boolean left, double width) {
        Label l = new Label(text == null ? "" : text);
        l.setStyle(style);
        l.setMinWidth(width); l.setMaxWidth(width);
        l.setAlignment(left ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        return l;
    }

    private static Label plain(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: black; -fx-font-size: 11;");
        return l;
    }

    private static void sumRow(VBox root, String label, String value, boolean bold) {
        HBox row = new HBox();
        Label l = new Label(label); l.setStyle("-fx-text-fill: black; -fx-font-size: " + (bold ? 12 : 11) + "; " + (bold ? "-fx-font-weight: bold;" : ""));
        Region s = new Region(); HBox.setHgrow(s, javafx.scene.layout.Priority.ALWAYS);
        Label v = new Label(value); v.setStyle("-fx-text-fill: black; -fx-font-family: monospace; -fx-font-size: " + (bold ? 12 : 11) + "; " + (bold ? "-fx-font-weight: bold;" : ""));
        row.getChildren().addAll(l, s, v);
        root.getChildren().add(row);
    }
}

package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.model.RevenueRow;
import com.innov8.loyaltypos.util.Money;
import javafx.collections.ObservableList;
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

import java.util.Map;

public final class ReportPrintView {
    private ReportPrintView() {}

    public static void print(Window owner, String period, Map<String, String> range,
                              ObservableList<RevenueRow> rows, Map<String, Double> totals,
                              Map<String, Object> settings) {
        VBox view = build(period, range, rows, totals, settings);
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

    private static VBox build(String period, Map<String, String> range, ObservableList<RevenueRow> rows, Map<String, Double> totals, Map<String, Object> settings) {
        String sym = settings.getOrDefault("currency_symbol", "₱").toString();
        VBox root = new VBox(8);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(720);

        VBox header = new VBox(2);
        header.setAlignment(Pos.CENTER);
        Label biz = new Label(settings.getOrDefault("business_name", "Loyalty POS").toString().toUpperCase());
        biz.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 16;");
        header.getChildren().add(biz);
        if (settings.get("business_address") != null) header.getChildren().add(plain(settings.get("business_address").toString()));
        if (settings.get("business_contact") != null) header.getChildren().add(plain("Tel: " + settings.get("business_contact")));
        Label section = new Label((period + " Sales Report").toUpperCase());
        section.setStyle("-fx-text-fill: black; -fx-font-size: 13; -fx-font-weight: bold;");
        Label rangeLbl = new Label(range.get("from").equals(range.get("to")) ? range.get("from") : range.get("from") + " to " + range.get("to"));
        rangeLbl.setStyle("-fx-text-fill: #444; -fx-font-size: 10;");
        header.getChildren().addAll(section, rangeLbl);
        Region rule = new Region(); rule.setStyle("-fx-background-color: black;"); rule.setPrefHeight(2);
        root.getChildren().addAll(header, rule);

        // Summary
        Label summaryTitle = new Label("SUMMARY");
        summaryTitle.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11;");
        root.getChildren().add(summaryTitle);
        addSummaryRow(root, "Total Revenue", sym + Money.fmt(totals.getOrDefault("total", 0.0)), true);
        addSummaryRow(root, "Cash", sym + Money.fmt(totals.getOrDefault("cash", 0.0)), false);
        addSummaryRow(root, "GCash", sym + Money.fmt(totals.getOrDefault("gcash", 0.0)), false);
        addSummaryRow(root, "Maya", sym + Money.fmt(totals.getOrDefault("maya", 0.0)), false);
        addSummaryRow(root, "PO Charged", sym + Money.fmt(totals.getOrDefault("po", 0.0)), false);
        addSummaryRow(root, "Delivery Fees", sym + Money.fmt(totals.getOrDefault("delivery", 0.0)), false);

        Label brTitle = new Label("DAY-BY-DAY BREAKDOWN");
        brTitle.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 8 0 4 0;");
        root.getChildren().add(brTitle);
        HBox h = headerRow();
        root.getChildren().add(h);
        Region top2 = new Region(); top2.setStyle("-fx-background-color: black;"); top2.setPrefHeight(2);
        root.getChildren().add(top2);
        for (RevenueRow r : rows) {
            HBox row = new HBox();
            row.getChildren().addAll(
                    cell(r.day, true, 100),
                    cell(sym + Money.fmt(r.total), false, 90),
                    cell(sym + Money.fmt(r.cash), false, 90),
                    cell(sym + Money.fmt(r.gcash), false, 90),
                    cell(sym + Money.fmt(r.maya), false, 90),
                    cell(sym + Money.fmt(r.poAmount), false, 90),
                    cell(sym + Money.fmt(r.deliveryTotal), false, 90)
            );
            row.setStyle("-fx-border-color: transparent transparent #eee transparent;");
            root.getChildren().add(row);
        }
        HBox totalRow = new HBox();
        totalRow.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: black transparent black transparent; -fx-border-width: 1;");
        totalRow.getChildren().addAll(
                cell("TOTAL", true, 100),
                cell(sym + Money.fmt(totals.getOrDefault("total", 0.0)), false, 90),
                cell(sym + Money.fmt(totals.getOrDefault("cash", 0.0)), false, 90),
                cell(sym + Money.fmt(totals.getOrDefault("gcash", 0.0)), false, 90),
                cell(sym + Money.fmt(totals.getOrDefault("maya", 0.0)), false, 90),
                cell(sym + Money.fmt(totals.getOrDefault("po", 0.0)), false, 90),
                cell(sym + Money.fmt(totals.getOrDefault("delivery", 0.0)), false, 90)
        );
        root.getChildren().add(totalRow);
        return root;
    }

    private static HBox headerRow() {
        HBox row = new HBox();
        row.setStyle("-fx-background-color: #f5f5f5;");
        for (Object[] e : new Object[][]{
                {"Date", true, 100}, {"Total", false, 90}, {"Cash", false, 90}, {"GCash", false, 90},
                {"Maya", false, 90}, {"PO", false, 90}, {"Delivery", false, 90}}) {
            Label l = new Label((String) e[0]);
            l.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 10; -fx-padding: 4 6;");
            l.setMinWidth((Integer) e[2]); l.setMaxWidth((Integer) e[2]);
            l.setAlignment((Boolean) e[1] ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            row.getChildren().add(l);
        }
        return row;
    }

    private static Label cell(String text, boolean left, double width) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: black; -fx-font-size: 10; -fx-padding: 4 6;");
        l.setMinWidth(width); l.setMaxWidth(width);
        l.setAlignment(left ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        return l;
    }

    private static void addSummaryRow(VBox root, String label, String value, boolean bold) {
        HBox row = new HBox();
        Label l = new Label(label); l.setStyle("-fx-text-fill: black; -fx-font-size: " + (bold ? 12 : 11) + "; " + (bold ? "-fx-font-weight: bold;" : ""));
        Region s = new Region(); HBox.setHgrow(s, javafx.scene.layout.Priority.ALWAYS);
        Label v = new Label(value); v.setStyle("-fx-text-fill: black; -fx-font-size: " + (bold ? 12 : 11) + "; " + (bold ? "-fx-font-weight: bold;" : ""));
        row.getChildren().addAll(l, s, v);
        row.setStyle("-fx-border-color: transparent transparent #eee transparent; -fx-padding: 2 0;");
        root.getChildren().add(row);
    }

    private static Label plain(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: black; -fx-font-size: 10;");
        return l;
    }
}

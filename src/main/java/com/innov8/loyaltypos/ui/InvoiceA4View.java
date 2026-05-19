package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;
import com.innov8.loyaltypos.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Window;

import java.util.Map;

public final class InvoiceA4View {
    private InvoiceA4View() {}

    public static void printInvoice(Window owner, Transaction tx, Map<String, Object> settings) {
        if (tx == null) return;
        VBox content = build(tx, settings);
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Modal(owner, "Error", new Label("No printer available")).show();
            return;
        }
        boolean ok = job.showPrintDialog(owner);
        if (!ok) return;
        Printer printer = job.getPrinter();
        PageLayout layout = printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        double scaleX = layout.getPrintableWidth() / content.prefWidth(-1);
        double scaleY = layout.getPrintableHeight() / content.prefHeight(-1);
        double scale = Math.min(1.0, Math.min(scaleX, scaleY));
        if (scale < 1.0) content.getTransforms().add(new Scale(scale, scale));
        boolean printed = job.printPage(layout, content);
        if (printed) job.endJob();
    }

    private static VBox build(Transaction tx, Map<String, Object> settings) {
        String sym = settings.getOrDefault("currency_symbol", "₱").toString();
        VBox root = new VBox(8);
        root.setPadding(new Insets(40));
        root.setStyle("-fx-background-color: white;");
        root.setPrefWidth(720);

        // Header
        VBox header = new VBox(2);
        header.setAlignment(Pos.CENTER);
        Label biz = new Label((settings.getOrDefault("business_name", "Loyalty POS")).toString().toUpperCase());
        biz.setStyle("-fx-text-fill: black; -fx-font-size: 18; -fx-font-weight: bold;");
        Label addr = new Label(String.valueOf(settings.getOrDefault("business_address", "")));
        addr.setStyle("-fx-text-fill: black; -fx-font-size: 11;");
        Label contact = new Label("Tel: " + settings.getOrDefault("business_contact", ""));
        contact.setStyle("-fx-text-fill: black; -fx-font-size: 11;");
        header.getChildren().addAll(biz, addr, contact);
        Region rule = new Region(); rule.setStyle("-fx-background-color: black;"); rule.setPrefHeight(2);
        root.getChildren().addAll(header, rule);

        HBox info = new HBox();
        Label inv = new Label("Invoice No: " + tx.invoiceNo); inv.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11;");
        Region s1 = new Region(); HBox.setHgrow(s1, Priority.ALWAYS);
        Label date = new Label("Date: " + (tx.date == null ? "" : tx.date.substring(0, Math.min(10, tx.date.length()))));
        date.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11;");
        info.getChildren().addAll(inv, s1, date);
        root.getChildren().add(info);

        VBox cust = new VBox(2);
        cust.getChildren().add(label("Sold To: " + (tx.customerName == null ? "Walk-in Customer" : tx.customerName)));
        if (tx.customerTin != null) cust.getChildren().add(label("TIN: " + tx.customerTin));
        if (tx.customerAddress != null) cust.getChildren().add(label("Address: " + tx.customerAddress));
        root.getChildren().add(cust);

        // Items table (using Region grid since we can't easily inline a printable JFX TableView)
        VBox items = new VBox();
        Region top = new Region(); top.setStyle("-fx-background-color: black;"); top.setPrefHeight(2);
        items.getChildren().add(top);
        items.getChildren().add(itemsHeader());
        Region underHeader = new Region(); underHeader.setStyle("-fx-background-color: black;"); underHeader.setPrefHeight(2);
        items.getChildren().add(underHeader);
        for (TransactionItem it : tx.items) {
            HBox row = new HBox();
            row.getChildren().addAll(
                    cell(it.productName, true, 280),
                    cell(Money.fmt(it.quantity), false, 80),
                    cell(it.unit, false, 80),
                    cell(sym + Money.fmt(it.customPrice == null ? it.unitPrice : it.customPrice), false, 110),
                    cell(sym + Money.fmt(it.amount), false, 130)
            );
            row.setStyle("-fx-border-color: transparent transparent #ccc transparent;");
            items.getChildren().add(row);
        }
        root.getChildren().add(items);

        // Totals
        VBox totals = new VBox(2);
        totals.setAlignment(Pos.CENTER_RIGHT);
        totals.getChildren().add(label("Total Sales: " + sym + Money.fmt(tx.subtotal)));
        if (tx.deliveryCharge > 0) totals.getChildren().add(label("Delivery Charge: " + sym + Money.fmt(tx.deliveryCharge)));
        if (tx.discount > 0) totals.getChildren().add(label("Less: (" + sym + Money.fmt(tx.discount) + ")"));
        Label totalDue = new Label("Total Amount Due: " + sym + Money.fmt(tx.totalAmount));
        totalDue.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 13;");
        totals.getChildren().add(totalDue);
        root.getChildren().add(totals);

        // Footer
        Region bottom = new Region(); bottom.setStyle("-fx-background-color: black;"); bottom.setPrefHeight(1);
        VBox foot = new VBox(2);
        foot.getChildren().add(label("Cashier: " + (tx.cashierName == null ? "" : tx.cashierName)));
        for (Payment p : tx.payments) {
            if ("po".equals(p.method)) continue;
            String line = "Payment (" + p.method.toUpperCase() + "): " + sym + Money.fmt(p.amount);
            if (p.referenceNo != null && !p.referenceNo.isEmpty()) line += " | Ref: " + p.referenceNo;
            if (p.senderName != null && !p.senderName.isEmpty()) line += " | From: " + p.senderName;
            foot.getChildren().add(label(line));
        }
        double unpaidAmount = tx.payments.stream().filter(p -> "po".equals(p.method)).mapToDouble(p -> p.amount).sum();
        if (unpaidAmount > 0) {
            Label up = new Label("Unpaid (PO): " + sym + Money.fmt(unpaidAmount));
            up.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11;");
            foot.getChildren().add(up);
        }
        root.getChildren().addAll(bottom, foot);
        return root;
    }

    private static HBox itemsHeader() {
        HBox header = new HBox();
        for (Object[] entry : new Object[][]{
                {"Items", true, 280}, {"Quantity", false, 80}, {"Unit", false, 80}, {"Price", false, 110}, {"Amount", false, 130}}) {
            Label l = new Label((String) entry[0]);
            l.setStyle("-fx-text-fill: black; -fx-font-weight: bold; -fx-font-size: 11; -fx-padding: 4 8;");
            l.setMinWidth((Integer) entry[2]);
            l.setMaxWidth((Integer) entry[2]);
            l.setAlignment((Boolean) entry[1] ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
            header.getChildren().add(l);
        }
        return header;
    }

    private static Label cell(String text, boolean left, double width) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: black; -fx-font-size: 11; -fx-padding: 4 8;");
        l.setMinWidth(width); l.setMaxWidth(width);
        l.setAlignment(left ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT);
        return l;
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: black; -fx-font-size: 11;");
        return l;
    }
}

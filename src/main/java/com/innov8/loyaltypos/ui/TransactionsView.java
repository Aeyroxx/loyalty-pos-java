package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;
import com.innov8.loyaltypos.service.PrinterService;
import com.innov8.loyaltypos.service.TransactionService;
import com.innov8.loyaltypos.util.Money;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionsView {
    private final VBox root = new VBox(16);
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();
    private final TableView<Transaction> table = new TableView<>();
    private final DatePicker fromDp = new DatePicker();
    private final DatePicker toDp = new DatePicker();
    private final Label countLabel = new Label("0 records");
    private final boolean isAdmin;
    private final String sym;

    public TransactionsView() {
        isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        sym = App.ctx.getCurrencySymbol();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1200);

        Label title = new Label("TRANSACTIONS"); title.getStyleClass().add("page-title");
        root.getChildren().add(title);

        // Filter row
        HBox filter = new HBox(12);
        filter.setAlignment(Pos.CENTER_LEFT);
        // Show MM/dd/yy format hint and parse user input in that format
        applyDateFormat(fromDp);
        applyDateFormat(toDp);
        VBox fromBox = new VBox(4); Label fl = new Label("FROM (MM/DD/YY)"); fl.getStyleClass().add("field-label"); fromBox.getChildren().addAll(fl, fromDp);
        VBox toBox = new VBox(4); Label tl = new Label("TO (MM/DD/YY)"); tl.getStyleClass().add("field-label"); toBox.getChildren().addAll(tl, toDp);
        Button apply = new Button("Filter"); apply.getStyleClass().addAll("btn", "btn-primary"); apply.setOnAction(e -> load());
        Button clear = new Button("Clear"); clear.getStyleClass().addAll("btn", "btn-ghost"); clear.setOnAction(e -> { fromDp.setValue(null); toDp.setValue(null); load(); });
        countLabel.setStyle("-fx-text-fill: #52525b;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dayLogThermal = new Button("Day Log: Thermal");
        dayLogThermal.getStyleClass().addAll("btn", "btn-ghost");
        dayLogThermal.setStyle("-fx-padding: 8 14; -fx-font-size: 12;");
        dayLogThermal.setOnAction(e -> printDayLog(true));

        Button dayLogPdf = new Button("Day Log: PDF");
        dayLogPdf.getStyleClass().addAll("btn", "btn-success-ghost");
        dayLogPdf.setStyle("-fx-padding: 8 14; -fx-font-size: 12;");
        dayLogPdf.setOnAction(e -> printDayLog(false));

        filter.getChildren().addAll(fromBox, toBox, apply, clear, countLabel, spacer, dayLogThermal, dayLogPdf);
        root.getChildren().add(filter);

        // Table
        table.getStyleClass().add("data-table");
        table.setItems(rows);
        table.setPlaceholder(new Label("No transactions found."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        addCol("INVOICE NO", t -> t.invoiceNo);
        addCol("DATE", t -> t.date == null ? "—" : t.date.substring(0, Math.min(10, t.date.length())));
        addCol("TIME", t -> {
            try { return OffsetDateTime.parse(t.date).format(DateTimeFormatter.ofPattern("hh:mm a")); }
            catch (Exception e) { return ""; }
        });
        addCol("PLATE NO", t -> t.plateNo == null ? "—" : t.plateNo);
        addCol("CUSTOMER", t -> t.customerName == null ? "Walk-in" : t.customerName);
        addCol("CASHIER", t -> t.cashierName == null ? "" : t.cashierName);
        addCol("TOTAL", t -> sym + Money.fmt(t.totalAmount));

        TableColumn<Transaction, Transaction> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                Label badge = new Label(t.paymentStatus.toUpperCase());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(switch (t.paymentStatus) {
                    case "paid" -> "badge-success";
                    case "partial" -> "badge-warning";
                    case "unpaid" -> "badge-danger";
                    default -> "badge-muted";
                });
                setGraphic(badge);
            }
        });
        table.getColumns().add(statusCol);

        TableColumn<Transaction, Transaction> actions = new TableColumn<>("");
        actions.setMinWidth(220);
        actions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        actions.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                HBox box = new HBox(6);
                Button view = new Button("View");
                view.getStyleClass().addAll("btn", "btn-ghost"); view.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                view.setOnAction(e -> openDetail(t.id));
                box.getChildren().add(view);
                if (!"voided".equals(t.paymentStatus)) {
                    Button voidBtn = new Button("Void");
                    voidBtn.getStyleClass().addAll("btn", "btn-warning-ghost");
                    voidBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                    voidBtn.setOnAction(e -> TotpDialog.show(root.getScene().getWindow(), "Void Transaction", true, r -> {
                        try {
                            TransactionService.voidTransaction(t.id, r.reason, r.code, App.ctx.currentUser == null ? null : App.ctx.currentUser.id);
                            load();
                        } catch (Exception ex) { showError(ex); }
                    }));
                    box.getChildren().add(voidBtn);
                }
                if (isAdmin) {
                    Button del = new Button("Delete");
                    del.getStyleClass().addAll("btn", "btn-danger-ghost");
                    del.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                    del.setOnAction(e -> TotpDialog.show(root.getScene().getWindow(), "Delete " + t.invoiceNo, false, r -> {
                        try {
                            TransactionService.delete(t.id, r.code, App.ctx.currentUser == null ? null : App.ctx.currentUser.id);
                            load();
                        } catch (Exception ex) { showError(ex); }
                    }));
                    box.getChildren().add(del);
                }
                setGraphic(box);
            }
        });
        table.getColumns().add(actions);

        VBox.setVgrow(table, Priority.ALWAYS);
        root.getChildren().add(table);
        load();
    }

    private static void applyDateFormat(DatePicker dp) {
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy");
        dp.setPromptText("MM/DD/YY");
        dp.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : f.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.trim().isEmpty()) return null;
                try { return LocalDate.parse(s.trim(), f); } catch (Exception e) { return null; }
            }
        });
    }

    private void addCol(String label, java.util.function.Function<Transaction, String> getter) {
        TableColumn<Transaction, String> col = new TableColumn<>(label);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        table.getColumns().add(col);
    }

    private void load() {
        try {
            String f = fromDp.getValue() == null ? "" : fromDp.getValue().toString();
            String t = toDp.getValue() == null ? "" : toDp.getValue().toString();
            rows.setAll(TransactionService.list(f, t));
            countLabel.setText(rows.size() + " record" + (rows.size() != 1 ? "s" : ""));
        } catch (Exception e) { showError(e); }
    }

    private void openDetail(int id) {
        try {
            Transaction tx = TransactionService.get(id);
            if (tx == null) return;
            VBox content = new VBox(16);
            GridPane grid = new GridPane();
            grid.setHgap(24); grid.setVgap(8);
            grid.add(infoLabel("Date: " + (tx.date == null ? "" : tx.date.substring(0, Math.min(16, tx.date.length()))).replace('T',' ')), 0, 0);
            grid.add(infoLabel("Cashier: " + (tx.cashierName == null ? "" : tx.cashierName)), 1, 0);
            grid.add(infoLabel("Customer: " + (tx.customerName == null ? "Walk-in" : tx.customerName)), 0, 1);
            if (tx.plateNo != null) grid.add(infoLabel("Plate: " + tx.plateNo), 1, 1);
            grid.add(infoLabel("Status: " + tx.paymentStatus), 0, 2);
            content.getChildren().add(grid);

            TableView<TransactionItem> items = new TableView<>(FXCollections.observableArrayList(tx.items));
            items.getStyleClass().add("data-table");
            items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
            items.setPrefHeight(180);
            items.getColumns().addAll(
                    col("PRODUCT", (TransactionItem i) -> i.productName),
                    col("UNIT", i -> i.unit),
                    col("QTY", i -> Money.fmt(i.quantity)),
                    col("UNIT PRICE", i -> sym + Money.fmt(i.unitPrice)),
                    col("CUSTOM PRICE", i -> i.customPrice == null ? "—" : sym + Money.fmt(i.customPrice)),
                    col("AMOUNT", i -> sym + Money.fmt(i.amount))
            );
            content.getChildren().add(items);

            VBox totals = new VBox(4);
            totals.setAlignment(Pos.CENTER_RIGHT);
            totals.getChildren().add(infoLabel("Subtotal: " + sym + Money.fmt(tx.subtotal)));
            if (tx.deliveryCharge > 0) totals.getChildren().add(infoLabel("Delivery: " + sym + Money.fmt(tx.deliveryCharge)));
            if (tx.discount > 0) totals.getChildren().add(infoLabel("Less: (" + sym + Money.fmt(tx.discount) + ")"));
            Label totalDue = new Label("TOTAL: " + sym + Money.fmt(tx.totalAmount));
            totalDue.setStyle("-fx-text-fill: #f4f4f5; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 16; -fx-font-weight: 700;");
            totals.getChildren().add(totalDue);
            content.getChildren().add(totals);

            if (tx.payments != null && !tx.payments.isEmpty()) {
                Label pLbl = new Label("PAYMENTS"); pLbl.getStyleClass().add("section-title");
                content.getChildren().add(pLbl);
                for (Payment p : tx.payments) {
                    HBox row = new HBox(12);
                    Label m = new Label(p.method.toUpperCase()); m.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-weight: 700; -fx-min-width: 60;");
                    Label a = new Label(sym + Money.fmt(p.amount)); a.setStyle("-fx-text-fill: #f4f4f5; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-weight: 600;");
                    row.getChildren().addAll(m, a);
                    if (p.referenceNo != null && !p.referenceNo.isEmpty()) row.getChildren().add(infoLabel("Ref: " + p.referenceNo));
                    if (p.senderName != null && !p.senderName.isEmpty()) row.getChildren().add(infoLabel("From: " + p.senderName));
                    content.getChildren().add(row);
                }
            }

            HBox btns = new HBox(10);
            btns.setAlignment(Pos.CENTER_LEFT);
            Button printA4 = new Button("Print A4 Invoice"); printA4.getStyleClass().addAll("btn", "btn-primary");
            Button printT = new Button("Print Thermal"); printT.getStyleClass().addAll("btn", "btn-secondary");
            Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
            Button close = new Button("Close"); close.getStyleClass().addAll("btn", "btn-ghost");
            btns.getChildren().addAll(printA4, printT, s, close);
            content.getChildren().add(btns);

            Modal modal = new Modal(root.getScene().getWindow(), "Invoice " + tx.invoiceNo, content, true);
            close.setOnAction(e -> modal.close());
            printA4.setOnAction(e -> InvoiceA4View.printInvoice(root.getScene().getWindow(), tx, App.ctx.settings));
            printT.setOnAction(e -> {
                try {
                    String printer = (String) App.ctx.settings.getOrDefault("thermal_printer", "");
                    PrinterService.printReceipt(tx, App.ctx.settings, printer);
                } catch (Exception ex) { showError(ex); }
            });
            modal.show();
        } catch (Exception e) { showError(e); }
    }

    private static <T> TableColumn<T, String> col(String label, java.util.function.Function<T, String> g) {
        TableColumn<T, String> c = new TableColumn<>(label);
        c.setCellValueFactory(cd -> new SimpleStringProperty(g.apply(cd.getValue())));
        return c;
    }

    private void printDayLog(boolean thermal) {
        // Pre-fill picker with the from-date if user has filtered, otherwise today
        LocalDate initial = fromDp.getValue() != null ? fromDp.getValue() : LocalDate.now();
        pickDate(initial, picked -> {
            if (picked == null) return;
            doPrintDayLog(picked.toString(), thermal);
        });
    }

    private void pickDate(LocalDate initial, java.util.function.Consumer<LocalDate> onPick) {
        VBox content = new VBox(16);
        Label info = new Label("Choose the date to print the End-of-Day log for:");
        info.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 13;");
        DatePicker dp = new DatePicker(initial);
        dp.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().addAll(info, dp);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button ok = new Button("Print"); ok.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, ok);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), "Day Log Date", content);
        cancel.setOnAction(e -> { modal.close(); onPick.accept(null); });
        ok.setOnAction(e -> { modal.close(); onPick.accept(dp.getValue()); });
        modal.show();
    }

    private void doPrintDayLog(String date, boolean thermal) {
        try {
            var data = TransactionService.dayLog(date);
            if (data.transactions.isEmpty() && data.voidLog.isEmpty()) {
                showError(new Exception("No transactions or voids on " + date + ". Pick a different date."));
                return;
            }
            if (thermal) {
                String printer = (String) App.ctx.settings.getOrDefault("log_printer", "");
                if (printer == null || printer.isEmpty()) printer = (String) App.ctx.settings.getOrDefault("thermal_printer", "");
                if (printer == null || printer.isEmpty()) {
                    showError(new Exception("No thermal printer configured. Go to Settings → Printers."));
                    return;
                }
                PrinterService.printDayLog(date, data.transactions, data.voidLog, App.ctx.settings, printer);
            } else {
                DayLogView.printDayLog(root.getScene().getWindow(), data, App.ctx.settings);
            }
        } catch (Exception e) { showError(e); }
    }

    private Label infoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 13;");
        return l;
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #09090b; -fx-background: #09090b;");
        return sp;
    }
}

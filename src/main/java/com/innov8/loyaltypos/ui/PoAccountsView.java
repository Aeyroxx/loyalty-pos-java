package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Customer;
import com.innov8.loyaltypos.model.PoAccount;
import com.innov8.loyaltypos.model.PoPayment;
import com.innov8.loyaltypos.service.CustomerService;
import com.innov8.loyaltypos.service.PoService;
import com.innov8.loyaltypos.util.Money;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public class PoAccountsView {
    private final VBox root = new VBox(20);
    private final ObservableList<PoAccount> accounts = FXCollections.observableArrayList();
    private final TableView<PoAccount> table = new TableView<>();
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final boolean isAdmin;
    private final String sym;

    public PoAccountsView() {
        isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        sym = App.ctx.getCurrencySymbol();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1100);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("PO ACCOUNTS"); title.getStyleClass().add("page-title");
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        header.getChildren().addAll(title, s);
        if (isAdmin) {
            Button add = new Button("+ New PO Account");
            add.getStyleClass().addAll("btn", "btn-primary");
            add.setOnAction(e -> openNew());
            header.getChildren().add(add);
        }
        root.getChildren().add(header);

        table.getStyleClass().add("data-table");
        table.setItems(accounts);
        table.setPlaceholder(new Label("No PO accounts yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        addCol("CUSTOMER", a -> a.customerName);
        addCol("REFERENCE", a -> a.referenceNo == null || a.referenceNo.isEmpty() ? "—" : a.referenceNo);
        addCol("ISSUED", a -> a.issuedDate == null ? "—" : a.issuedDate.substring(0, Math.min(10, a.issuedDate.length())));
        addCol("EXPIRY", a -> a.expiryDate == null ? "—" : a.expiryDate);
        addCol("CREDIT LIMIT", a -> sym + Money.fmt(a.creditLimit));
        addCol("USED", a -> sym + Money.fmt(a.balanceUsed));
        // Colored AVAILABLE column — green when positive, red when 0 or overdrawn
        TableColumn<PoAccount, PoAccount> availCol = new TableColumn<>("AVAILABLE");
        availCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        availCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(PoAccount a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setText(null); setStyle(""); return; }
                double v = a.available();
                setText(sym + Money.fmt(v));
                setStyle("-fx-text-fill: " + (v > 0 ? "#22c55e" : "#ef4444") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-weight: 600;");
            }
        });
        table.getColumns().add(availCol);

        TableColumn<PoAccount, PoAccount> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(PoAccount a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setGraphic(null); return; }
                Label badge = new Label(a.status == null ? "—" : a.status.toUpperCase());
                String css = switch (a.status == null ? "" : a.status) {
                    case "open" -> "badge-success";
                    case "expired" -> "badge-danger";
                    case "closed" -> "badge-muted";
                    default -> "badge-warning";
                };
                badge.getStyleClass().addAll("badge", css);
                setGraphic(badge);
            }
        });
        table.getColumns().add(statusCol);

        TableColumn<PoAccount, PoAccount> view = new TableColumn<>("");
        view.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        view.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(PoAccount a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) { setGraphic(null); return; }
                Button v = new Button("View");
                v.getStyleClass().addAll("btn", "btn-ghost");
                v.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                v.setOnAction(e -> openDetail(a));
                setGraphic(v);
            }
        });
        table.getColumns().add(view);

        root.getChildren().add(table);
        load();
    }

    private void addCol(String label, java.util.function.Function<PoAccount, String> getter) {
        TableColumn<PoAccount, String> col = new TableColumn<>(label);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        table.getColumns().add(col);
    }

    private void load() {
        try { accounts.setAll(PoService.list()); customers.setAll(CustomerService.list()); }
        catch (Exception e) { showError(e); }
    }

    private void openNew() {
        VBox content = new VBox(16);
        Label err = new Label(); err.getStyleClass().add("error-banner");
        err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);

        Label cl = new Label("CUSTOMER"); cl.getStyleClass().add("field-label");
        ComboBox<Customer> cb = new ComboBox<>(customers); cb.setMaxWidth(Double.MAX_VALUE);
        cb.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "Select customer..." : c.name; }
            @Override public Customer fromString(String s) { return null; }
        });
        content.getChildren().add(new VBox(6, cl, cb));

        TextField refTf = ProductsView.labeledField(content, "Reference No. (optional)", "");
        TextField creditTf = ProductsView.labeledField(content, "Credit Limit", "");
        Label dl = new Label("EXPIRY DATE (OPTIONAL)"); dl.getStyleClass().add("field-label");
        DatePicker dp = new DatePicker();
        content.getChildren().add(new VBox(6, dl, dp));

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button create = new Button("Create"); create.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, create);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), "New PO Account", content);
        cancel.setOnAction(e -> modal.close());
        create.setOnAction(e -> {
            try {
                if (cb.getValue() == null) { err.setText("Please select a customer."); err.setVisible(true); err.setManaged(true); return; }
                double limit = ProductsView.parseD(creditTf.getText());
                if (limit <= 0) { err.setText("Credit limit must be greater than 0."); err.setVisible(true); err.setManaged(true); return; }
                PoAccount p = new PoAccount();
                p.customerId = cb.getValue().id;
                p.referenceNo = refTf.getText();
                p.issuedDate = OffsetDateTime.now().toString();
                p.expiryDate = dp.getValue() == null ? null : dp.getValue().toString();
                p.creditLimit = limit;
                PoService.create(p);
                modal.close();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    private void openDetail(PoAccount selected) {
        VBox content = new VBox(20);
        // Summary cards
        GridPane summary = new GridPane();
        summary.setHgap(8);
        summary.add(summaryCard("CREDIT LIMIT", sym + Money.fmt(selected.creditLimit), false), 0, 0);
        summary.add(summaryCard("BALANCE USED", sym + Money.fmt(selected.balanceUsed), false), 1, 0);
        summary.add(summaryCard("AVAILABLE", sym + Money.fmt(selected.available()), true), 2, 0);
        for (int i = 0; i < 3; i++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setPercentWidth(33.33);
            summary.getColumnConstraints().add(cc);
        }
        content.getChildren().add(summary);

        // Info row
        GridPane info = new GridPane(); info.setHgap(12); info.setVgap(8);
        info.add(infoLabel("Reference: " + (selected.referenceNo == null || selected.referenceNo.isEmpty() ? "—" : selected.referenceNo)), 0, 0);
        info.add(infoLabel("Status: " + selected.status), 1, 0);
        info.add(infoLabel("Issued: " + (selected.issuedDate == null ? "" : selected.issuedDate.substring(0, Math.min(10, selected.issuedDate.length())))), 0, 1);
        info.add(infoLabel("Expiry: " + (selected.expiryDate == null ? "None" : selected.expiryDate)), 1, 1);
        content.getChildren().add(info);

        // Edit credit limit / details (admin only)
        if (isAdmin) {
            HBox actionRow = new HBox(10);
            actionRow.setAlignment(Pos.CENTER_LEFT);
            Button editBtn = new Button("Edit Details");
            editBtn.getStyleClass().addAll("btn", "btn-secondary");
            editBtn.setStyle("-fx-padding: 6 16; -fx-font-size: 12;");
            editBtn.setOnAction(e -> openEditDetails(selected));
            actionRow.getChildren().add(editBtn);
            content.getChildren().add(actionRow);
        }

        // Payment history header
        HBox phHeader = new HBox();
        phHeader.setAlignment(Pos.CENTER_LEFT);
        Label phTitle = new Label("PAYMENT HISTORY");
        phTitle.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 13; -fx-font-weight: 700;");
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        phHeader.getChildren().addAll(phTitle, s);
        if (isAdmin && "open".equals(selected.status)) {
            Button addPay = new Button("+ Add Payment");
            addPay.getStyleClass().addAll("btn", "btn-primary");
            addPay.setStyle("-fx-padding: 6 16; -fx-font-size: 12;");
            addPay.setOnAction(e -> openPayment(selected));
            phHeader.getChildren().add(addPay);
        }
        content.getChildren().add(phHeader);

        // Payment table
        TableView<PoPayment> ptable = new TableView<>();
        ptable.getStyleClass().add("data-table");
        ptable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        ptable.setPrefHeight(220);
        ptable.setItems(FXCollections.observableArrayList(PoService.payments(selected.id)));
        TableColumn<PoPayment, String> dCol = new TableColumn<>("DATE");
        dCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().paymentDate == null ? "—" : c.getValue().paymentDate.substring(0, Math.min(10, c.getValue().paymentDate.length()))));
        TableColumn<PoPayment, String> aCol = new TableColumn<>("AMOUNT");
        aCol.setCellValueFactory(c -> new SimpleStringProperty(sym + Money.fmt(c.getValue().amount)));
        TableColumn<PoPayment, String> nCol = new TableColumn<>("NOTES");
        nCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().notes == null || c.getValue().notes.isEmpty() ? "—" : c.getValue().notes));
        ptable.getColumns().addAll(dCol, aCol, nCol);
        content.getChildren().add(ptable);

        new Modal(root.getScene().getWindow(), "PO — " + selected.customerName, content, true).show();
    }

    private VBox summaryCard(String label, String value, boolean accent) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: " + (accent ? "#22c55e" : "-faint") + "; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700;");
        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + (accent ? "#22c55e" : "-ink") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 18; -fx-font-weight: 600;");
        VBox box = new VBox(6, l, v);
        box.setPadding(new Insets(16, 20, 16, 20));
        box.setStyle("-fx-background-color: " + (accent ? "rgba(34,197,94,0.1)" : "-overlay-subtle") + "; -fx-border-color: " + (accent ? "rgba(34,197,94,0.2)" : "-border") + "; -fx-background-radius: 8; -fx-border-radius: 8;");
        return box;
    }

    private Label infoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: -ink-soft; -fx-font-size: 13;");
        return l;
    }

    private void openEditDetails(PoAccount selected) {
        VBox content = new VBox(16);
        Label err = new Label(); err.getStyleClass().add("error-banner");
        err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);

        TextField refTf = ProductsView.labeledField(content, "Reference No.", selected.referenceNo == null ? "" : selected.referenceNo);
        TextField creditTf = ProductsView.labeledField(content, "Credit Limit", String.valueOf(selected.creditLimit));
        Label dl = new Label("EXPIRY DATE (OPTIONAL)"); dl.getStyleClass().add("field-label");
        DatePicker dp = new DatePicker();
        if (selected.expiryDate != null && !selected.expiryDate.isEmpty()) {
            try { dp.setValue(LocalDate.parse(selected.expiryDate.substring(0, Math.min(10, selected.expiryDate.length())))); } catch (Exception ignore) {}
        }
        content.getChildren().add(new VBox(6, dl, dp));

        Label sl = new Label("STATUS"); sl.getStyleClass().add("field-label");
        ComboBox<String> statusCb = new ComboBox<>(FXCollections.observableArrayList("open", "closed", "expired"));
        statusCb.setValue(selected.status);
        statusCb.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(new VBox(6, sl, statusCb));

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button("Save Changes"); save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, save);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), "Edit PO Account", content, true);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            try {
                double limit = ProductsView.parseD(creditTf.getText());
                if (limit < selected.balanceUsed) {
                    err.setText("Credit limit can't be less than balance used (" + sym + Money.fmt(selected.balanceUsed) + ").");
                    err.setVisible(true); err.setManaged(true);
                    return;
                }
                selected.referenceNo = refTf.getText();
                selected.creditLimit = limit;
                selected.expiryDate = dp.getValue() == null ? null : dp.getValue().toString();
                selected.status = statusCb.getValue();
                PoService.update(selected);
                modal.close();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    private void openPayment(PoAccount selected) {
        VBox content = new VBox(16);
        Label note = new Label("Recording payment for: " + selected.customerName);
        note.setStyle("-fx-text-fill: -ink-soft; -fx-font-size: 13;");
        content.getChildren().add(note);

        Label err = new Label(); err.getStyleClass().add("error-banner");
        err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);

        TextField amtTf = ProductsView.labeledField(content, "Amount", "");
        TextField notesTf = ProductsView.labeledField(content, "Notes (optional)", "");

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button("Save Payment"); save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, save);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), "Record Payment", content);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            try {
                double amt = ProductsView.parseD(amtTf.getText());
                if (amt <= 0) { err.setText("Enter a valid payment amount."); err.setVisible(true); err.setManaged(true); return; }
                PoService.addPayment(selected.id, amt, OffsetDateTime.now().toString(), notesTf.getText());
                modal.close();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

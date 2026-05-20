package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.model.Customer;
import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.PoAccount;
import com.innov8.loyaltypos.service.PoService;
import com.innov8.loyaltypos.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class PaymentDialog {
    private static final String[] METHODS = {"cash", "gcash", "maya", "po"};
    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("cash", "Cash");
        LABELS.put("gcash", "GCash");
        LABELS.put("maya", "Maya");
        LABELS.put("po", "PO Credit");
    }

    public static class Result {
        public List<Payment> payments;
        public double change;
    }

    private final double total;
    private final Customer customer;
    private final Window owner;
    private final Map<String, Boolean> selected = new LinkedHashMap<>();
    private final Map<String, TextField> amounts = new LinkedHashMap<>();
    private final Map<String, TextField[]> meta = new LinkedHashMap<>();
    private ComboBox<PoAccount> poSelect;
    private List<PoAccount> poAccounts = new ArrayList<>();
    private final VBox detailBox = new VBox(12);
    private final Label runningLabel = new Label();
    private final Label changeLabel = new Label();
    private final Label errorBanner = new Label();
    private final VBox runningBox = new VBox(2);
    private final VBox changeBox = new VBox(4);
    private Modal modal;

    public PaymentDialog(Window owner, double total, Customer customer) {
        this.owner = owner;
        this.total = total;
        this.customer = customer;
        for (String m : METHODS) selected.put(m, false);
    }

    public void show(Consumer<Result> onResult) {
        VBox root = new VBox(16);

        // Total row
        HBox totalRow = new HBox();
        totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.setPadding(new Insets(16, 20, 16, 20));
        totalRow.setStyle("-fx-background-color: -panel-2; -fx-background-radius: 8; -fx-border-color: -border; -fx-border-radius: 8;");
        Label tl = new Label("TOTAL AMOUNT DUE");
        tl.setStyle("-fx-text-fill: -muted; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 13; -fx-font-weight: 700;");
        Region s1 = new Region(); HBox.setHgrow(s1, Priority.ALWAYS);
        Label tv = new Label("₱" + Money.fmt(total));
        tv.setStyle("-fx-text-fill: -ink; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 28; -fx-font-weight: 600;");
        totalRow.getChildren().addAll(tl, s1, tv);
        root.getChildren().add(totalRow);

        // Method toggle row
        HBox methodRow = new HBox(8);
        for (String m : METHODS) {
            Button b = new Button(LABELS.get(m).toUpperCase());
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            updateMethodButton(b, m);
            b.setOnAction(e -> { toggle(m); updateMethodButton(b, m); refreshDetail(); });
            methodRow.getChildren().add(b);
        }
        root.getChildren().add(methodRow);

        root.getChildren().add(detailBox);

        // Running / Change
        runningBox.setStyle("-fx-padding: 12 0; -fx-border-color: -border transparent transparent transparent;");
        Label rl1 = new Label("REMAINING BALANCE"); rl1.setStyle("-fx-text-fill: -muted; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 13; -fx-font-weight: 700;");
        runningLabel.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 18; -fx-font-weight: 600;");
        runningBox.getChildren().addAll(rl1, runningLabel);
        runningBox.setVisible(false); runningBox.setManaged(false);
        root.getChildren().add(runningBox);

        changeBox.setStyle("-fx-padding: 16 20; -fx-background-color: rgba(34,197,94,0.12); -fx-border-color: rgba(34,197,94,0.3); -fx-background-radius: 8; -fx-border-radius: 8;");
        Label cl1 = new Label("CHANGE"); cl1.setStyle("-fx-text-fill: #22c55e; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700;");
        changeLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 36; -fx-font-weight: 700;");
        changeBox.getChildren().addAll(cl1, changeLabel);
        changeBox.setVisible(false); changeBox.setManaged(false);
        root.getChildren().add(changeBox);

        errorBanner.getStyleClass().add("error-banner");
        errorBanner.setMaxWidth(Double.MAX_VALUE);
        errorBanner.setWrapText(true);
        errorBanner.setVisible(false); errorBanner.setManaged(false);
        root.getChildren().add(errorBanner);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button confirm = new Button("Confirm Payment"); confirm.getStyleClass().addAll("btn", "btn-primary");
        confirm.setStyle("-fx-padding: 10 28; -fx-font-size: 14;");
        btns.getChildren().addAll(sp, cancel, confirm);
        root.getChildren().add(btns);

        modal = new Modal(owner, "Payment", root, true);
        cancel.setOnAction(e -> { modal.close(); onResult.accept(null); });
        confirm.setOnAction(e -> {
            Result r = build();
            if (r == null) return;
            modal.close();
            onResult.accept(r);
        });
        modal.show();
    }

    private void updateMethodButton(Button b, String method) {
        boolean sel = selected.get(method);
        b.setStyle("-fx-padding: 12 8; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 14; -fx-font-weight: 700; -fx-background-radius: 8; -fx-border-radius: 8; -fx-cursor: hand; "
                + "-fx-background-color: " + (sel ? "rgba(212,105,10,0.20)" : "-overlay-subtle") + "; "
                + "-fx-border-color: " + (sel ? "#d4690a" : "-overlay-card") + "; "
                + "-fx-border-width: 1; "
                + "-fx-text-fill: " + (sel ? "-ink" : "-muted") + ";");
    }

    private void toggle(String method) {
        if ("po".equals(method) && customer == null) {
            errorBanner.setText("Select a customer first to use PO payment.");
            errorBanner.setVisible(true); errorBanner.setManaged(true);
            return;
        }
        errorBanner.setVisible(false); errorBanner.setManaged(false);
        boolean newSel = !selected.get(method);
        selected.put(method, newSel);
        if (newSel) {
            double currentPaid = 0;
            for (String m : METHODS) {
                if (!m.equals(method) && selected.getOrDefault(m, false)) {
                    TextField tf = amounts.get(m);
                    if (tf != null) currentPaid += ProductsView.parseD(tf.getText());
                }
            }
            double fill = Math.max(0, total - currentPaid);
            // Will be set after refreshDetail builds the field
            if ("po".equals(method) && customer != null) {
                try { poAccounts = PoService.openByCustomer(customer.id); } catch (Exception ignore) {}
            }
        }
    }

    private void refreshDetail() {
        detailBox.getChildren().clear();
        amounts.clear();
        meta.clear();
        for (String m : METHODS) {
            if (!selected.get(m)) continue;
            VBox card = new VBox(12);
            card.setStyle("-fx-padding: 16 20; -fx-border-color: -border; -fx-background-color: -overlay-subtle; -fx-background-radius: 8; -fx-border-radius: 8;");

            HBox header = new HBox(12);
            header.setAlignment(Pos.CENTER_LEFT);
            Label l = new Label(LABELS.get(m).toUpperCase());
            l.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700;");
            Region sep = new Region(); HBox.setHgrow(sep, Priority.ALWAYS);
            sep.setStyle("-fx-background-color: -border;"); sep.setPrefHeight(1);
            header.getChildren().addAll(l, sep);
            card.getChildren().add(header);

            TextField tf = new TextField();
            tf.setPromptText("0.00");
            tf.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent rgba(255,255,255,0.2) transparent; -fx-text-fill: -ink; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 22; -fx-font-weight: 600; -fx-alignment: CENTER-RIGHT; -fx-padding: 6 0;");
            // Pre-fill with remaining
            double currentPaid = 0;
            for (String mm : METHODS) {
                if (!mm.equals(m) && amounts.containsKey(mm)) currentPaid += ProductsView.parseD(amounts.get(mm).getText());
            }
            double fill = Math.max(0, total - currentPaid);
            if (fill > 0) tf.setText(Money.fmt(fill));
            tf.textProperty().addListener((o, a, b) -> recalc());
            card.getChildren().add(tf);
            amounts.put(m, tf);

            if ("gcash".equals(m) || "maya".equals(m)) {
                HBox row = new HBox(12);
                TextField ref = new TextField(); ref.getStyleClass().add("text-field");
                TextField sender = new TextField(); sender.getStyleClass().add("text-field");
                Label refL = new Label("REFERENCE NO."); refL.getStyleClass().add("field-label");
                Label sendL = new Label("SENDER NAME"); sendL.getStyleClass().add("field-label");
                VBox refBox = new VBox(4, refL, ref); HBox.setHgrow(refBox, Priority.ALWAYS);
                VBox sendBox = new VBox(4, sendL, sender); HBox.setHgrow(sendBox, Priority.ALWAYS);
                row.getChildren().addAll(refBox, sendBox);
                card.getChildren().add(row);
                meta.put(m, new TextField[]{ref, sender});
            }

            if ("po".equals(m)) {
                Label pl = new Label("PO ACCOUNT"); pl.getStyleClass().add("field-label");
                if (poAccounts.isEmpty()) {
                    Label none = new Label("No open PO accounts for this customer."); none.setStyle("-fx-text-fill: #ef4444;");
                    card.getChildren().addAll(pl, none);
                } else {
                    poSelect = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(poAccounts));
                    poSelect.setMaxWidth(Double.MAX_VALUE);
                    poSelect.setConverter(new javafx.util.StringConverter<>() {
                        @Override public String toString(PoAccount p) {
                            if (p == null) return "";
                            return (p.referenceNo == null || p.referenceNo.isEmpty() ? "PO #" + p.id : p.referenceNo)
                                    + " — Available: ₱" + Money.fmt(p.available());
                        }
                        @Override public PoAccount fromString(String s) { return null; }
                    });
                    poSelect.setValue(poAccounts.get(0));
                    card.getChildren().addAll(pl, poSelect);
                }
            }

            detailBox.getChildren().add(card);
        }
        recalc();
    }

    private void recalc() {
        double paid = 0;
        for (String m : METHODS) {
            if (selected.get(m) && amounts.containsKey(m)) paid += ProductsView.parseD(amounts.get(m).getText());
        }
        boolean cashSel = selected.getOrDefault("cash", false);
        double remaining = total - paid;
        double change = cashSel ? Math.max(0, paid - total) : 0;
        if (change > 0.009) {
            changeLabel.setText("₱" + Money.fmt(change));
            changeBox.setVisible(true); changeBox.setManaged(true);
            runningBox.setVisible(false); runningBox.setManaged(false);
        } else if (selected.values().stream().anyMatch(Boolean::booleanValue)) {
            runningLabel.setText("₱" + Money.fmt(Math.abs(remaining)));
            runningBox.setVisible(true); runningBox.setManaged(true);
            changeBox.setVisible(false); changeBox.setManaged(false);
        }
    }

    private Result build() {
        double paid = 0;
        for (String m : METHODS) if (selected.get(m) && amounts.containsKey(m)) paid += ProductsView.parseD(amounts.get(m).getText());
        boolean cashSel = selected.getOrDefault("cash", false);
        double change = cashSel ? Math.max(0, paid - total) : 0;
        boolean overpaid = paid > total + 0.01 && !cashSel;
        if (overpaid) {
            errorBanner.setText("Amount exceeds total by ₱" + Money.fmt(paid - total) + ". Add Cash to give change.");
            errorBanner.setVisible(true); errorBanner.setManaged(true);
            return null;
        }
        List<Payment> payments = new ArrayList<>();
        for (String m : METHODS) {
            if (!selected.getOrDefault(m, false)) continue;
            TextField tf = amounts.get(m);
            if (tf == null) continue;
            double a = ProductsView.parseD(tf.getText());
            if (a <= 0) continue;
            Payment p = new Payment();
            p.method = m;
            p.amount = "cash".equals(m) ? Math.max(0, a - change) : a;
            if (meta.containsKey(m)) {
                TextField[] tfs = meta.get(m);
                p.referenceNo = tfs[0].getText().isEmpty() ? null : tfs[0].getText();
                p.senderName = tfs[1].getText().isEmpty() ? null : tfs[1].getText();
            }
            if ("po".equals(m) && poSelect != null && poSelect.getValue() != null) {
                p.poAccountId = poSelect.getValue().id;
                if (a > poSelect.getValue().available()) {
                    errorBanner.setText("PO balance insufficient. Available: ₱" + Money.fmt(poSelect.getValue().available()));
                    errorBanner.setVisible(true); errorBanner.setManaged(true);
                    return null;
                }
            }
            if (p.amount > 0) payments.add(p);
        }
        if (payments.isEmpty()) {
            errorBanner.setText("Enter an amount for at least one payment method.");
            errorBanner.setVisible(true); errorBanner.setManaged(true);
            return null;
        }
        Result r = new Result();
        r.payments = payments;
        r.change = change;
        return r;
    }
}

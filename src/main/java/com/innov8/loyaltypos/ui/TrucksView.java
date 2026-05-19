package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Truck;
import com.innov8.loyaltypos.model.TruckPrice;
import com.innov8.loyaltypos.service.TruckService;
import com.innov8.loyaltypos.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TrucksView {
    private final VBox root = new VBox(0);
    private final VBox listBox = new VBox(0);
    private final String sym;

    public TrucksView() {
        sym = App.ctx.getCurrencySymbol();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(960);

        Label title = new Label("TRUCKS"); title.getStyleClass().add("page-title");
        Label desc = new Label("Default prices per product for each registered plate number. Prices are saved automatically at checkout and can be updated here.");
        desc.setStyle("-fx-text-fill: #71717a; -fx-font-size: 13;");
        desc.setWrapText(true);

        VBox top = new VBox(8, title, desc);
        top.setPadding(new Insets(0, 0, 24, 0));
        root.getChildren().add(top);

        listBox.getStyleClass().add("bordered-card");
        listBox.setStyle("-fx-background-color: #18181b; -fx-border-color: rgba(255,255,255,0.08); -fx-background-radius: 8; -fx-border-radius: 8;");
        root.getChildren().add(listBox);

        load();
    }

    private void load() {
        listBox.getChildren().clear();
        try {
            List<Truck> trucks = TruckService.list();
            // Header
            HBox header = headerRow();
            listBox.getChildren().add(header);
            if (trucks.isEmpty()) {
                Label empty = new Label("No trucks recorded yet. Plate numbers are saved automatically after checkout.");
                empty.setStyle("-fx-text-fill: #52525b; -fx-padding: 48; -fx-font-style: italic; -fx-alignment: center;");
                empty.setMaxWidth(Double.MAX_VALUE);
                empty.setAlignment(Pos.CENTER);
                listBox.getChildren().add(empty);
            } else {
                for (Truck t : trucks) {
                    if (t.prices.isEmpty()) {
                        listBox.getChildren().add(emptyRow(t));
                    } else {
                        for (int i = 0; i < t.prices.size(); i++) {
                            listBox.getChildren().add(priceRow(t, t.prices.get(i), i == 0));
                        }
                    }
                }
            }
            Label foot = new Label(trucks.size() + " TRUCK" + (trucks.size() != 1 ? "S" : "") + " REGISTERED");
            foot.setStyle("-fx-text-fill: #3f3f46; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 10; -fx-padding: 16 0 0 0;");
            root.getChildren().removeIf(n -> n != listBox && n.getProperties().get("foot") != null);
            foot.getProperties().put("foot", true);
            root.getChildren().add(foot);
        } catch (Exception e) { showError(e); }
    }

    private HBox headerRow() {
        HBox h = new HBox();
        h.setStyle("-fx-background-color: #0a0a0a; -fx-padding: 10 14;");
        h.setSpacing(10);
        h.getChildren().addAll(
                colHeader("PLATE NO.", 160),
                colHeader("PRODUCT", -1),
                colHeader("DEFAULT PRICE / UNIT", 180),
                colHeader("LAST UPDATED", 160),
                colHeader("", 200)
        );
        return h;
    }

    private Label colHeader(String text, double width) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #52525b; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700;");
        if (width > 0) l.setMinWidth(width);
        else HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private HBox emptyRow(Truck t) {
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 10 14; -fx-border-color: rgba(255,255,255,0.06) transparent transparent transparent;");
        Label plate = new Label(t.plateNo);
        plate.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13; -fx-font-weight: 700;");
        plate.setMinWidth(160);
        Label info = new Label("No prices recorded yet");
        info.setStyle("-fx-text-fill: #3f3f46; -fx-font-style: italic; -fx-font-size: 12;");
        HBox.setHgrow(info, Priority.ALWAYS);
        Button del = new Button("Delete");
        del.getStyleClass().addAll("btn", "btn-danger-ghost");
        del.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
        del.setOnAction(e -> {
            if (ConfirmDialog.show(root.getScene().getWindow(), "Delete truck " + t.plateNo + " and all its saved prices?")) {
                TruckService.delete(t.plateNo);
                load();
            }
        });
        row.getChildren().addAll(plate, info, del);
        return row;
    }

    private HBox priceRow(Truck t, TruckPrice price, boolean firstRow) {
        HBox row = new HBox(10);
        row.setStyle("-fx-padding: 10 14; -fx-border-color: " + (firstRow ? "rgba(255,255,255,0.08)" : "rgba(255,255,255,0.03)") + " transparent transparent transparent;");
        row.setAlignment(Pos.CENTER_LEFT);

        Label plate = new Label(firstRow ? t.plateNo : "");
        plate.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13; -fx-font-weight: 700;");
        plate.setMinWidth(160);

        Label name = new Label(price.productName);
        name.setStyle("-fx-text-fill: #f4f4f5;");
        HBox.setHgrow(name, Priority.ALWAYS);

        TextField priceField = new TextField(Money.fmt(price.defaultPrice));
        priceField.getStyleClass().add("text-field");
        priceField.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-padding: 4 8; -fx-max-width: 180;");
        priceField.setPrefWidth(180);
        priceField.setVisible(false);
        priceField.setManaged(false);

        Label priceLbl = new Label(sym + Money.fmt(price.defaultPrice));
        priceLbl.setStyle("-fx-text-fill: #f4f4f5; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13; -fx-font-weight: 600;");
        priceLbl.setMinWidth(180);

        Label updated = new Label(formatDate(price.updatedAt));
        updated.setStyle("-fx-text-fill: #52525b; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 11;");
        updated.setMinWidth(160);

        Button edit = new Button("Edit Price");
        edit.getStyleClass().addAll("btn", "btn-ghost");
        edit.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
        Button save = new Button("Save");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setStyle("-fx-padding: 4 14; -fx-font-size: 11;");
        save.setVisible(false); save.setManaged(false);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        cancel.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
        cancel.setVisible(false); cancel.setManaged(false);

        edit.setOnAction(e -> {
            priceField.setVisible(true); priceField.setManaged(true);
            priceLbl.setVisible(false); priceLbl.setManaged(false);
            edit.setVisible(false); edit.setManaged(false);
            save.setVisible(true); save.setManaged(true);
            cancel.setVisible(true); cancel.setManaged(true);
            priceField.requestFocus();
        });
        cancel.setOnAction(e -> {
            priceField.setVisible(false); priceField.setManaged(false);
            priceLbl.setVisible(true); priceLbl.setManaged(true);
            edit.setVisible(true); edit.setManaged(true);
            save.setVisible(false); save.setManaged(false);
            cancel.setVisible(false); cancel.setManaged(false);
        });
        save.setOnAction(e -> {
            try {
                double v = ProductsView.parseD(priceField.getText());
                if (v < 0) return;
                TruckService.upsertPrice(t.plateNo, price.productId, price.productName, v);
                load();
            } catch (Exception ex) { showError(ex); }
        });

        HBox actions = new HBox(6, priceLbl, priceField, edit, save, cancel);
        actions.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(plate, name, priceLbl, priceField, updated);
        HBox btnGroup = new HBox(6, edit, save, cancel);
        if (firstRow) {
            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn", "btn-danger-ghost");
            delete.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
            delete.setOnAction(e -> {
                if (ConfirmDialog.show(root.getScene().getWindow(), "Delete truck " + t.plateNo + " and all its saved prices?")) {
                    TruckService.delete(t.plateNo);
                    load();
                }
            });
            btnGroup.getChildren().add(delete);
        }
        btnGroup.setMinWidth(200);
        row.getChildren().add(btnGroup);
        return row;
    }

    private static String formatDate(String iso) {
        if (iso == null) return "—";
        try { return OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMM d, yyyy")); }
        catch (Exception e) { return iso; }
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #09090b; -fx-background: #09090b;");
        return sp;
    }
}

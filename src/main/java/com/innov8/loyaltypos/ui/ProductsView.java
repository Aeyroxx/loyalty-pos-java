package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Product;
import com.innov8.loyaltypos.service.ProductService;
import com.innov8.loyaltypos.util.Money;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public class ProductsView {
    private static final String[] UNITS = {"m³", "bag", "ton", "truckload", "cubic yard", "piece"};
    private final VBox root = new VBox(20);
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final TableView<Product> table = new TableView<>();
    private String currencySym;

    public ProductsView() {
        currencySym = App.ctx.getCurrencySymbol();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1000);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("PRODUCTS");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("+ Add Product");
        add.getStyleClass().addAll("btn", "btn-primary");
        add.setOnAction(e -> openForm(null));
        header.getChildren().addAll(title, spacer, add);
        root.getChildren().add(header);

        buildTable();
        root.getChildren().add(table);

        load();
    }

    private void buildTable() {
        table.getStyleClass().add("data-table");
        table.setItems(products);
        table.setPlaceholder(new Label("No products yet."));

        TableColumn<Product, String> code = new TableColumn<>("ITEM CODE");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().itemCode == null ? "" : c.getValue().itemCode));
        code.setPrefWidth(110);

        TableColumn<Product, String> name = new TableColumn<>("NAME");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));

        TableColumn<Product, String> desc = new TableColumn<>("DESCRIPTION / GRADE");
        desc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description == null ? "" : c.getValue().description));

        TableColumn<Product, String> unit = new TableColumn<>("UNIT");
        unit.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unit));

        TableColumn<Product, String> price = new TableColumn<>("PRICE / UNIT");
        price.setCellValueFactory(c -> new SimpleStringProperty(currencySym + Money.fmt(c.getValue().pricePerUnit)));

        TableColumn<Product, String> stock = new TableColumn<>("QUANTITY");
        stock.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf((long) c.getValue().stockQty) + " " + c.getValue().unit));

        TableColumn<Product, Product> actions = new TableColumn<>("");
        actions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        actions.setMinWidth(180);
        actions.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setGraphic(null); return; }
                Button edit = new Button("Edit");
                edit.getStyleClass().addAll("btn", "btn-ghost");
                edit.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                edit.setOnAction(e -> openForm(p));
                Button del = new Button("Delete");
                del.getStyleClass().addAll("btn", "btn-ghost");
                del.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                del.setOnAction(e -> {
                    if (ConfirmDialog.show(root.getScene().getWindow(), "Delete this product? This cannot be undone.")) {
                        ProductService.delete(p.id);
                        load();
                    }
                });
                HBox box = new HBox(6, edit, del);
                setGraphic(box);
            }
        });

        table.getColumns().addAll(code, name, desc, unit, price, stock, actions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void load() {
        try {
            List<Product> all = ProductService.list();
            products.setAll(all);
        } catch (Exception e) { showError(e); }
    }

    private void openForm(Product editing) {
        Product form = editing != null ? editing : new Product();
        VBox content = new VBox(14);

        Field code = field(content, "Item Code (unique, e.g. ITM-001)", form.itemCode == null ? "" : form.itemCode);
        Field name = field(content, "Name", form.name);
        Field desc = field(content, "Description / Grade / Texture (max 255 chars)", form.description == null ? "" : form.description);

        // AI helper: generate a one-line product description from code + name + unit
        Button aiDescBtn = new Button("✨ Suggest description");
        aiDescBtn.getStyleClass().addAll("btn", "btn-secondary");
        aiDescBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
        HBox aiRow = new HBox(aiDescBtn);
        aiRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(aiRow);

        Field price = field(content, "Price per Unit", form.pricePerUnit > 0 ? String.valueOf(form.pricePerUnit) : "");
        Field stock = field(content, "Quantity", form.stockQty > 0 ? String.valueOf((long) form.stockQty) : "");

        Label unitLbl = new Label("UNIT"); unitLbl.getStyleClass().add("field-label");
        ComboBox<String> unitCb = new ComboBox<>(FXCollections.observableArrayList(UNITS));
        unitCb.setValue(form.unit == null ? "m³" : form.unit);
        unitCb.setMaxWidth(Double.MAX_VALUE);
        VBox unitBox = new VBox(6, unitLbl, unitCb);
        content.getChildren().add(unitBox);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button("Save Product");
        save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(spacer, cancel, save);
        content.getChildren().add(btns);

        aiDescBtn.setOnAction(e -> {
            String c = code.tf.getText() == null ? "" : code.tf.getText().trim();
            String n = name.tf.getText() == null ? "" : name.tf.getText().trim();
            String unit = unitCb.getValue();
            if (c.isEmpty() || n.isEmpty()) {
                code.setError(c.isEmpty() ? "Required first." : null);
                name.setError(n.isEmpty() ? "Required first." : null);
                return;
            }
            aiDescBtn.setText("✨ Asking AI…");
            aiDescBtn.setDisable(true);
            new Thread(() -> {
                try {
                    String out = com.innov8.loyaltypos.service.AIService.suggestProductDescription(c, n, unit);
                    javafx.application.Platform.runLater(() -> {
                        desc.tf.setText(out);
                        aiDescBtn.setText("✨ Suggest description");
                        aiDescBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        aiDescBtn.setText("✨ Suggest description");
                        aiDescBtn.setDisable(false);
                        desc.setError("AI: " + ex.getMessage());
                    });
                }
            }, "ai-prod-desc").start();
        });

        Modal modal = new Modal(root.getScene().getWindow(), editing == null ? "Add Product" : "Edit Product", content);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            // clear all inline errors first
            code.clearError(); name.clearError(); desc.clearError(); price.clearError(); stock.clearError();

            String cVal = code.tf.getText() == null ? "" : code.tf.getText().trim().toUpperCase();
            String nVal = name.tf.getText() == null ? "" : name.tf.getText().trim();
            String dVal = desc.tf.getText() == null ? "" : desc.tf.getText().trim();
            String pStr = price.tf.getText() == null ? "" : price.tf.getText().trim();
            String sStr = stock.tf.getText() == null ? "" : stock.tf.getText().trim();
            boolean ok = true;

            // Item code: required, format PREFIX-NUMBER (e.g. ITM-001, GRAVEL-2)
            if (cVal.isEmpty()) { code.setError("Item code is required."); ok = false; }
            else if (!cVal.matches("^[A-Z0-9]{2,}-[A-Z0-9]+$")) {
                code.setError("Format: prefix-suffix, e.g. ITM-001 or GRAVEL-2.");
                ok = false;
            }

            // Name: required, not all special characters
            if (nVal.isEmpty()) { name.setError("Product name is required."); ok = false; }
            else if (!nVal.matches(".*[A-Za-z0-9].*")) {
                name.setError("Name must contain letters or numbers, not only symbols.");
                ok = false;
            }

            // Description: 255 char limit
            if (dVal.length() > 255) {
                desc.setError("Description is " + dVal.length() + "/255 characters. Trim it down.");
                ok = false;
            }

            // Price: positive number
            if (pStr.isEmpty()) { price.setError("Price is required."); ok = false; }
            else {
                double pVal = parseD(pStr);
                if (pVal <= 0) { price.setError("Price must be greater than 0."); ok = false; }
                form.pricePerUnit = pVal;
            }

            // Stock: whole number >= 0
            if (sStr.isEmpty()) { stock.setError("Quantity is required."); ok = false; }
            else if (!sStr.matches("^\\d+$")) {
                stock.setError("Quantity must be a whole number (no decimals, no negatives).");
                ok = false;
            } else {
                form.stockQty = Long.parseLong(sStr);
            }

            if (!ok) return;

            form.itemCode = cVal;
            form.name = nVal;
            form.description = dVal;
            form.unit = unitCb.getValue();

            try {
                if (editing == null) ProductService.create(form);
                else ProductService.update(form);
                modal.close();
                load();
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "Save failed." : ex.getMessage();
                if (msg.toLowerCase().contains("item code")) code.setError(msg);
                else name.setError(msg);
            }
        });
        modal.show();
    }

    /** Field bundle: label + textfield + below-line error label. */
    static class Field {
        final TextField tf;
        final Label err;
        Field(TextField tf, Label err) { this.tf = tf; this.err = err; }
        void setError(String msg) {
            if (msg == null || msg.isEmpty()) { clearError(); return; }
            err.setText(msg);
            err.setVisible(true); err.setManaged(true);
            tf.setStyle("-fx-border-color: transparent transparent #ef4444 transparent;");
        }
        void clearError() {
            err.setVisible(false); err.setManaged(false);
            tf.setStyle("");
        }
    }

    static Field field(VBox parent, String label, String value) {
        Label l = new Label(label.toUpperCase()); l.getStyleClass().add("field-label");
        TextField tf = new TextField(value == null ? "" : value);
        tf.getStyleClass().add("field-input");
        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11; -fx-padding: 2 0 0 0;");
        err.setVisible(false); err.setManaged(false);
        err.setWrapText(true);
        VBox box = new VBox(4, l, tf, err);
        parent.getChildren().add(box);
        return new Field(tf, err);
    }

    /** Parse a user-typed number, stripping commas/currency so "₱1,500.00" or "1,500" works. */
    public static double parseD(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s.trim().replaceAll("[,₱$\\s]", "")); }
        catch (Exception e) { return 0; }
    }

    public static TextField labeledField(VBox parent, String label, String value) {
        Label l = new Label(label.toUpperCase()); l.getStyleClass().add("field-label");
        TextField tf = new TextField(value == null ? "" : value);
        tf.getStyleClass().add("field-input");
        parent.getChildren().add(new VBox(6, l, tf));
        return tf;
    }

    private void showError(Exception e) {
        new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show();
    }

    public Region getRoot() {
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

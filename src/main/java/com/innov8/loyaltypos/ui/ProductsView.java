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

        TableColumn<Product, String> stock = new TableColumn<>("STOCK");
        stock.setCellValueFactory(c -> new SimpleStringProperty(Money.fmt(c.getValue().stockQty) + " " + c.getValue().unit));

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
        VBox content = new VBox(16);
        TextField codeTf = labeledField(content, "Item Code (unique)", form.itemCode == null ? "" : form.itemCode);
        TextField nameTf = labeledField(content, "Name", form.name);
        TextField descTf = labeledField(content, "Description / Grade / Texture", form.description == null ? "" : form.description);
        TextField priceTf = labeledField(content, "Price per Unit", form.pricePerUnit > 0 ? String.valueOf(form.pricePerUnit) : "");
        TextField stockTf = labeledField(content, "Stock Quantity", form.stockQty > 0 ? String.valueOf(form.stockQty) : "");

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

        Modal modal = new Modal(root.getScene().getWindow(), editing == null ? "Add Product" : "Edit Product", content);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            try {
                form.itemCode = codeTf.getText().trim();
                form.name = nameTf.getText().trim();
                form.description = descTf.getText().trim();
                form.unit = unitCb.getValue();
                form.pricePerUnit = parseD(priceTf.getText());
                form.stockQty = parseD(stockTf.getText());
                if (form.itemCode.isEmpty()) { showError(new Exception("Item code is required (distinct codes per grade — e.g. GRAVEL-1, GRAVEL-2).")); return; }
                if (form.name.isEmpty() || form.pricePerUnit <= 0) return;
                if (editing == null) ProductService.create(form);
                else ProductService.update(form);
                modal.close();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    static double parseD(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; } }

    static TextField labeledField(VBox parent, String label, String value) {
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
        sp.setStyle("-fx-background-color: #09090b; -fx-background: #09090b;");
        return sp;
    }
}

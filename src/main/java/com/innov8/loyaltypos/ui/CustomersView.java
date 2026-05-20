package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Customer;
import com.innov8.loyaltypos.service.CustomerService;
import com.innov8.loyaltypos.util.Money;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

public class CustomersView {
    private final VBox root = new VBox(20);
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final TableView<Customer> table = new TableView<>();
    private final boolean isAdmin;
    private final String sym;

    public CustomersView() {
        isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        sym = App.ctx.getCurrencySymbol();

        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1100);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("CUSTOMERS");
        title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer);
        if (isAdmin) {
            Button add = new Button("+ Add Customer");
            add.getStyleClass().addAll("btn", "btn-primary");
            add.setOnAction(e -> openForm(null));
            header.getChildren().add(add);
        }
        root.getChildren().add(header);

        table.getStyleClass().add("data-table");
        table.setItems(customers);
        table.setPlaceholder(new Label("No customers yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        addCol("NAME", c -> c.name);
        addCol("TIN", c -> nz(c.tin));
        addCol("ADDRESS", c -> nz(c.address));
        addCol("PHONE", c -> nz(c.phone));
        addCol("CREDIT LIMIT", c -> sym + Money.fmt(c.creditLimit));
        if (isAdmin) {
            TableColumn<Customer, Customer> actions = new TableColumn<>("");
            actions.setMinWidth(160);
            actions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
            actions.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                @Override protected void updateItem(Customer c, boolean empty) {
                    super.updateItem(c, empty);
                    if (empty || c == null) { setGraphic(null); return; }
                    Button edit = ghostBtn("Edit"); edit.setOnAction(e -> openForm(c));
                    Button del = ghostBtn("Delete"); del.setOnAction(e -> {
                        if (ConfirmDialog.show(root.getScene().getWindow(), "Delete this customer? This cannot be undone.")) {
                            String err = CustomerService.delete(c.id);
                            if (err != null) new Modal(root.getScene().getWindow(), "Cannot Delete", new Label(err)).show();
                            else load();
                        }
                    });
                    setGraphic(new HBox(6, edit, del));
                }
            });
            table.getColumns().add(actions);
        }
        root.getChildren().add(table);
        load();
    }

    private static Button ghostBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", "btn-ghost");
        b.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
        return b;
    }

    private void addCol(String label, java.util.function.Function<Customer, String> getter) {
        TableColumn<Customer, String> col = new TableColumn<>(label);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        table.getColumns().add(col);
    }

    private void load() {
        try { customers.setAll(CustomerService.list()); } catch (Exception e) { showError(e); }
    }

    private void openForm(Customer editing) {
        Customer form = editing != null ? editing : new Customer();
        VBox content = new VBox(16);
        GridPane grid = new GridPane();
        grid.setHgap(24); grid.setVgap(16);
        TextField nameTf = grid(grid, 0, "Name", form.name);
        TextField tinTf = grid(grid, 1, "TIN", form.tin);
        TextField phoneTf = grid(grid, 2, "Phone", form.phone);
        TextField creditTf = grid(grid, 3, "Credit Limit", form.creditLimit > 0 ? String.valueOf(form.creditLimit) : "");
        content.getChildren().add(grid);
        TextField addressTf = wideField(content, "Address", form.address);
        TextField notesTf = wideField(content, "Notes", form.notes);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button("Save Customer"); save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, save);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), editing == null ? "Add Customer" : "Edit Customer", content, true);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            try {
                form.name = nameTf.getText().trim();
                form.tin = tinTf.getText() == null ? "" : tinTf.getText().trim();
                form.phone = phoneTf.getText() == null ? "" : phoneTf.getText().trim();
                form.address = addressTf.getText();
                form.notes = notesTf.getText();
                form.creditLimit = ProductsView.parseD(creditTf.getText());
                if (form.name.isEmpty()) {
                    showError(new Exception("Name is required."));
                    return;
                }
                // 12-digit numeric validation: ignore spaces / dashes when counting
                String phoneDigits = form.phone.replaceAll("\\D", "");
                String tinDigits = form.tin.replaceAll("\\D", "");
                if (!form.phone.isEmpty() && phoneDigits.length() != 12) {
                    showError(new Exception("Phone must be exactly 12 digits (e.g. 639XXXXXXXXX). You entered " + phoneDigits.length() + "."));
                    phoneTf.requestFocus();
                    return;
                }
                if (!form.tin.isEmpty() && tinDigits.length() != 12) {
                    showError(new Exception("TIN must be exactly 12 digits. You entered " + tinDigits.length() + "."));
                    tinTf.requestFocus();
                    return;
                }
                if (editing == null) CustomerService.create(form);
                else CustomerService.update(form);
                modal.close();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    private TextField grid(GridPane g, int row, String label, String value) {
        Label l = new Label(label.toUpperCase()); l.getStyleClass().add("field-label");
        TextField tf = new TextField(nz(value));
        tf.getStyleClass().add("field-input");
        VBox box = new VBox(6, l, tf);
        int col = row % 2;
        int r = row / 2;
        g.add(box, col, r);
        return tf;
    }

    private TextField wideField(VBox parent, String label, String value) {
        Label l = new Label(label.toUpperCase()); l.getStyleClass().add("field-label");
        TextField tf = new TextField(nz(value));
        tf.getStyleClass().add("field-input");
        parent.getChildren().add(new VBox(6, l, tf));
        return tf;
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }
    private static String nz(String s) { return s == null ? "" : s; }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

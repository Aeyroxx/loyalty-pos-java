package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Supplier;
import com.innov8.loyaltypos.service.SupplierService;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/** CRUD UI for the suppliers table. Mirrors CustomersView shape with inline validation. */
public class SuppliersView {
    private final VBox root = new VBox(20);
    private final ObservableList<Supplier> rows = FXCollections.observableArrayList();
    private final TableView<Supplier> table = new TableView<>();
    private final boolean isAdmin;

    public SuppliersView() {
        isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1100);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("SUPPLIERS"); title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer);
        if (isAdmin) {
            Button add = new Button("+ Add Supplier");
            add.getStyleClass().addAll("btn", "btn-primary");
            add.setOnAction(e -> openForm(null));
            header.getChildren().add(add);
        }
        root.getChildren().add(header);

        table.getStyleClass().add("data-table");
        table.setItems(rows);
        table.setPlaceholder(new Label("No suppliers yet."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        addCol("NAME", s -> s.name);
        addCol("CONTACT PERSON", s -> nz(s.contactPerson));
        addCol("PHONE", s -> nz(s.phone));
        addCol("EMAIL", s -> nz(s.email));
        addCol("ADDRESS", s -> nz(s.address));

        if (isAdmin) {
            TableColumn<Supplier, Supplier> actions = new TableColumn<>("");
            actions.setMinWidth(160);
            actions.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
            actions.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                @Override protected void updateItem(Supplier s, boolean empty) {
                    super.updateItem(s, empty);
                    if (empty || s == null) { setGraphic(null); return; }
                    Button edit = ghostBtn("Edit"); edit.setOnAction(e -> openForm(s));
                    Button del = ghostBtn("Delete"); del.setOnAction(e -> {
                        if (ConfirmDialog.show(root.getScene().getWindow(), "Delete supplier \"" + s.name + "\"?")) {
                            SupplierService.delete(s.id);
                            load();
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

    private void addCol(String label, java.util.function.Function<Supplier, String> getter) {
        TableColumn<Supplier, String> col = new TableColumn<>(label);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        table.getColumns().add(col);
    }

    private void load() {
        try { rows.setAll(SupplierService.list()); }
        catch (Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }
    }

    private void openForm(Supplier editing) {
        Supplier form = editing != null ? editing : new Supplier();
        VBox content = new VBox(14);

        ProductsView.Field nameF = ProductsView.field(content, "Name", form.name);
        ProductsView.Field contactF = ProductsView.field(content, "Contact Person", form.contactPerson);
        ProductsView.Field phoneF = ProductsView.field(content, "Phone (11 digits, optional)", form.phone);
        ProductsView.Field emailF = ProductsView.field(content, "Email (optional)", form.email);
        ProductsView.Field addressF = ProductsView.field(content, "Address", form.address);
        ProductsView.Field notesF = ProductsView.field(content, "Notes", form.notes);

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button("Save Supplier"); save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, save);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), editing == null ? "Add Supplier" : "Edit Supplier", content, true);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            nameF.clearError(); contactF.clearError(); phoneF.clearError();
            emailF.clearError(); addressF.clearError(); notesF.clearError();

            String nameVal = nameF.tf.getText() == null ? "" : nameF.tf.getText().trim();
            String contactVal = contactF.tf.getText() == null ? "" : contactF.tf.getText().trim();
            String phoneVal = phoneF.tf.getText() == null ? "" : phoneF.tf.getText().trim();
            String emailVal = emailF.tf.getText() == null ? "" : emailF.tf.getText().trim();
            boolean ok = true;

            if (nameVal.isEmpty()) { nameF.setError("Supplier name is required."); ok = false; }
            else if (nameVal.matches("^[\\d\\s.,-]+$")) {
                nameF.setError("Name cannot be numbers only.");
                ok = false;
            }

            if (!phoneVal.isEmpty()) {
                String digits = phoneVal.replaceAll("\\D", "");
                if (digits.length() != 11) {
                    phoneF.setError("Phone must be 11 digits (e.g. 09171234567). You entered " + digits.length() + ".");
                    ok = false;
                }
            }
            if (!emailVal.isEmpty() && !emailVal.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                emailF.setError("Email format looks off.");
                ok = false;
            }
            // At least one contact method required (per professor revision)
            if (phoneVal.isEmpty() && emailVal.isEmpty()) {
                phoneF.setError("At least one contact method required — fill in phone OR email.");
                emailF.setError("At least one contact method required — fill in phone OR email.");
                ok = false;
            }
            // Address required
            String addrVal = addressF.tf.getText() == null ? "" : addressF.tf.getText().trim();
            if (addrVal.isEmpty()) {
                addressF.setError("Supplier address is required.");
                ok = false;
            }
            if (!ok) return;

            form.name = nameVal;
            form.contactPerson = contactVal;
            form.phone = phoneVal;
            form.email = emailVal;
            form.address = addressF.tf.getText();
            form.notes = notesF.tf.getText();

            try {
                boolean isNew = editing == null;
                if (isNew) SupplierService.create(form);
                else SupplierService.update(form);
                modal.close();
                load();
                new Modal(root.getScene().getWindow(),
                        isNew ? "Supplier Added" : "Supplier Updated",
                        new Label(form.name + (isNew ? " was added." : " was updated."))).show();
            } catch (Exception ex) {
                nameF.setError(ex.getMessage() == null ? "Save failed." : ex.getMessage());
            }
        });
        modal.show();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

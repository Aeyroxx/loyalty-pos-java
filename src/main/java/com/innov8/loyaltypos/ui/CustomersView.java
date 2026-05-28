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
        VBox content = new VBox(14);

        ProductsView.Field nameF = ProductsView.field(content, "Name", form.name);
        ProductsView.Field tinF = ProductsView.field(content, "TIN (12 digits, optional)", form.tin);
        ProductsView.Field phoneF = ProductsView.field(content, "Phone (11 digits, e.g. 09171234567)", form.phone);
        ProductsView.Field creditF = ProductsView.field(content, "Credit Limit (₱)", form.creditLimit > 0 ? String.valueOf(form.creditLimit) : "");

        // Optional credit-limit expiration date — drives PO expiry sync when present
        Label expL = new Label("CREDIT EXPIRATION (OPTIONAL)"); expL.getStyleClass().add("field-label");
        javafx.scene.control.DatePicker expDp = new javafx.scene.control.DatePicker();
        expDp.setPromptText("MM/DD/YY");
        expDp.setMaxWidth(Double.MAX_VALUE);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy");
        expDp.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(java.time.LocalDate d) { return d == null ? "" : fmt.format(d); }
            @Override public java.time.LocalDate fromString(String s) {
                if (s == null || s.trim().isEmpty()) return null;
                try { return java.time.LocalDate.parse(s.trim(), fmt); } catch (Exception e) { return null; }
            }
        });
        Label expErr = new Label();
        expErr.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11; -fx-padding: 2 0 0 0;");
        expErr.setVisible(false); expErr.setManaged(false);
        VBox expBox = new VBox(4, expL, expDp, expErr);
        content.getChildren().add(expBox);

        ProductsView.Field addressF = ProductsView.field(content, "Address", form.address == null ? "" : form.address);
        ProductsView.Field notesF = ProductsView.field(content, "Notes", form.notes == null ? "" : form.notes);

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
            nameF.clearError(); tinF.clearError(); phoneF.clearError();
            creditF.clearError(); addressF.clearError(); notesF.clearError();
            expErr.setVisible(false); expErr.setManaged(false);

            String nameVal = nameF.tf.getText() == null ? "" : nameF.tf.getText().trim();
            String tinVal = tinF.tf.getText() == null ? "" : tinF.tf.getText().trim();
            String phoneVal = phoneF.tf.getText() == null ? "" : phoneF.tf.getText().trim();
            String creditStr = creditF.tf.getText() == null ? "" : creditF.tf.getText().trim();
            String tinDigits = tinVal.replaceAll("\\D", "");
            String phoneDigits = phoneVal.replaceAll("\\D", "");
            boolean ok = true;

            if (nameVal.isEmpty()) { nameF.setError("Customer name is required."); ok = false; }

            if (!phoneVal.isEmpty() && phoneDigits.length() != 11) {
                phoneF.setError("Phone must be 11 digits (e.g. 09171234567). You entered " + phoneDigits.length() + ".");
                ok = false;
            }
            if (!tinVal.isEmpty() && tinDigits.length() != 12) {
                tinF.setError("TIN must be 12 digits. You entered " + tinDigits.length() + ".");
                ok = false;
            }

            double credit = 0;
            if (!creditStr.isEmpty()) {
                credit = ProductsView.parseD(creditStr);
                if (credit < 0) {
                    creditF.setError("Credit limit cannot be negative.");
                    ok = false;
                }
            }

            if (!ok) return;

            form.name = nameVal;
            form.tin = tinVal;
            form.phone = phoneVal;
            form.address = addressF.tf.getText();
            form.notes = notesF.tf.getText();
            double oldCredit = form.creditLimit;
            form.creditLimit = credit;

            boolean isNew = editing == null;
            try {
                if (isNew) CustomerService.create(form);
                else CustomerService.update(form);

                // Sync the new credit limit + optional expiration to open PO accounts owned by this customer.
                if (editing != null && Math.abs(oldCredit - credit) > 0.0001 || expDp.getValue() != null) {
                    try {
                        for (com.innov8.loyaltypos.model.PoAccount po : com.innov8.loyaltypos.service.PoService.openByCustomer(form.id)) {
                            po.creditLimit = credit;
                            if (expDp.getValue() != null) po.expiryDate = expDp.getValue().toString();
                            com.innov8.loyaltypos.service.PoService.update(po);
                        }
                    } catch (Exception syncEx) {
                        // Non-fatal: surface as a soft warning under credit field
                        creditF.setError("Saved customer, but PO sync failed: " + syncEx.getMessage());
                    }
                }
                modal.close();
                load();
                new Modal(root.getScene().getWindow(),
                        isNew ? "Customer Added" : "Customer Updated",
                        new Label(form.name + (isNew ? " was added." : " was updated."))).show();
            } catch (Exception ex) {
                nameF.setError(ex.getMessage() == null ? "Save failed." : ex.getMessage());
            }
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

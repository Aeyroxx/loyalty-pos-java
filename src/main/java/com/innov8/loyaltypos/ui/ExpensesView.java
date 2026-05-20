package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Expense;
import com.innov8.loyaltypos.service.ExpenseService;
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

public class ExpensesView {
    private static final String[] CATEGORIES = {"General","Supplies","Utilities","Salary","Fuel","Maintenance","Rent","Food","Other"};
    private final VBox root = new VBox(20);
    private final ObservableList<Expense> rows = FXCollections.observableArrayList();
    private final TableView<Expense> table = new TableView<>();
    private final DatePicker filterDate = new DatePicker(LocalDate.now());
    private final boolean isAdmin;
    private final Label totalLabel = new Label("₱0.00");

    public ExpensesView() {
        isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(800);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("EXPENSES"); title.getStyleClass().add("page-title");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        VBox totalBox = new VBox(2);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Label totLabel = new Label("TOTAL FOR " + filterDate.getValue());
        totLabel.setStyle("-fx-text-fill: -faint; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700;");
        totalLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 24; -fx-font-weight: 700;");
        totalBox.getChildren().addAll(totLabel, totalLabel);
        header.getChildren().addAll(title, spacer, totalBox);
        root.getChildren().add(header);

        // Add form
        VBox form = new VBox(12);
        form.getStyleClass().add("surface");
        form.setPadding(new Insets(24));

        Label formTitle = new Label("ADD EXPENSE");
        formTitle.getStyleClass().add("section-title");
        form.getChildren().add(formTitle);

        DatePicker dateTf = new DatePicker(LocalDate.now());
        ComboBox<String> categoryCb = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryCb.setValue("General");
        categoryCb.setMaxWidth(Double.MAX_VALUE);
        TextField descTf = new TextField(); descTf.setPromptText("Optional description");
        TextField amountTf = new TextField(); amountTf.setPromptText("0.00");

        GridPane grid = new GridPane();
        grid.setHgap(24); grid.setVgap(12);
        grid.add(field("Date", dateTf), 0, 0);
        grid.add(field("Category", categoryCb), 1, 0);
        form.getChildren().addAll(grid);
        form.getChildren().add(field("Description", descTf));

        HBox amountRow = new HBox(12);
        amountRow.setAlignment(Pos.BOTTOM_LEFT);
        VBox amountBox = field("Amount (₱)", amountTf);
        HBox.setHgrow(amountBox, Priority.ALWAYS);
        Button addBtn = new Button("Add");
        addBtn.getStyleClass().addAll("btn", "btn-primary");
        addBtn.setOnAction(e -> {
            try {
                double v = ProductsView.parseD(amountTf.getText());
                if (v <= 0) return;
                Expense ex = new Expense();
                ex.date = dateTf.getValue().toString();
                ex.category = categoryCb.getValue();
                ex.description = descTf.getText();
                ex.amount = v;
                ExpenseService.create(ex);
                amountTf.clear(); descTf.clear();
                load();
            } catch (Exception ex) { showError(ex); }
        });
        amountRow.getChildren().addAll(amountBox, addBtn);
        form.getChildren().add(amountRow);
        root.getChildren().add(form);

        // Filter
        HBox filter = new HBox(12);
        filter.setAlignment(Pos.CENTER_LEFT);
        Label fl = new Label("FILTER BY DATE"); fl.getStyleClass().add("section-title");
        filterDate.setOnAction(e -> { totLabel.setText("TOTAL FOR " + filterDate.getValue()); load(); });
        filter.getChildren().addAll(fl, filterDate);
        root.getChildren().add(filter);

        // Table
        table.getStyleClass().add("data-table");
        table.setItems(rows);
        table.setPlaceholder(new Label("No expenses for selected date"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        TableColumn<Expense, String> cat = new TableColumn<>("CATEGORY");
        cat.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().category));
        TableColumn<Expense, String> desc = new TableColumn<>("DESCRIPTION");
        desc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().description == null ? "—" : c.getValue().description));
        TableColumn<Expense, String> amt = new TableColumn<>("AMOUNT");
        amt.setCellValueFactory(c -> new SimpleStringProperty("₱" + Money.fmt(c.getValue().amount)));
        amt.setStyle("-fx-alignment: CENTER-RIGHT;");
        table.getColumns().addAll(cat, desc, amt);

        if (isAdmin) {
            TableColumn<Expense, Expense> del = new TableColumn<>("");
            del.setMinWidth(60);
            del.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
            del.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
                @Override protected void updateItem(Expense ex, boolean empty) {
                    super.updateItem(ex, empty);
                    if (empty || ex == null) { setGraphic(null); return; }
                    Button x = new Button("×");
                    x.setStyle("-fx-background-color: transparent; -fx-text-fill: #3f3f46; -fx-font-size: 16; -fx-cursor: hand;");
                    x.setOnAction(e -> {
                        if (ConfirmDialog.show(root.getScene().getWindow(), "Delete \"" + ex.category + "\" expense of ₱" + Money.fmt(ex.amount) + "?")) {
                            ExpenseService.delete(ex.id);
                            load();
                        }
                    });
                    setGraphic(x);
                }
            });
            table.getColumns().add(del);
        }
        root.getChildren().add(table);
        load();
    }

    private static VBox field(String label, javafx.scene.Node control) {
        Label l = new Label(label.toUpperCase()); l.getStyleClass().add("field-label");
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        return new VBox(6, l, control);
    }

    private void load() {
        try {
            String d = filterDate.getValue().toString();
            rows.setAll(ExpenseService.list(d, d));
            double total = rows.stream().mapToDouble(e -> e.amount).sum();
            totalLabel.setText("₱" + Money.fmt(total));
        } catch (Exception e) { showError(e); }
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

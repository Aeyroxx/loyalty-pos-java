package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Customer;
import com.innov8.loyaltypos.model.ItemCodeSalesRow;
import com.innov8.loyaltypos.model.PoPayment;
import com.innov8.loyaltypos.model.PoStatRow;
import com.innov8.loyaltypos.model.RevenueRow;
import com.innov8.loyaltypos.service.CustomerService;
import com.innov8.loyaltypos.service.ExpenseService;
import com.innov8.loyaltypos.service.PrinterService;
import com.innov8.loyaltypos.service.ReportService;
import com.innov8.loyaltypos.util.Money;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportsView {
    private static final String[] TABS = {"Daily","Weekly","Monthly","Yearly","By Item Code","PO Statistics","PO Payments"};
    private final VBox root = new VBox(20);
    private final HBox tabBar = new HBox();
    private final VBox content = new VBox(20);
    private final ObservableList<RevenueRow> revRows = FXCollections.observableArrayList();
    private final ObservableList<PoStatRow> poStats = FXCollections.observableArrayList();
    private final ObservableList<PoPayment> poPayments = FXCollections.observableArrayList();
    private final ObservableList<Customer> customers = FXCollections.observableArrayList();
    private final ObservableList<ItemCodeSalesRow> itemRows = FXCollections.observableArrayList();
    private String currentTab = "Daily";
    private final String sym;

    public ReportsView() {
        sym = App.ctx.getCurrencySymbol();
        root.setPadding(new Insets(32, 40, 32, 40));
        root.setMaxWidth(1100);

        Label title = new Label("REPORTS"); title.getStyleClass().add("page-title");
        root.getChildren().add(title);

        tabBar.setStyle("-fx-border-color: transparent transparent rgba(255,255,255,0.08) transparent;");
        for (String t : TABS) {
            Button b = new Button(t.toUpperCase());
            b.getStyleClass().add("tab-button");
            b.setUserData(t);
            b.setOnAction(e -> select(t));
            tabBar.getChildren().add(b);
        }
        root.getChildren().add(tabBar);
        root.getChildren().add(content);

        select("Daily");
    }

    private void select(String tab) {
        currentTab = tab;
        for (var n : tabBar.getChildren()) {
            if (n instanceof Button b) {
                if (tab.equals(b.getUserData())) {
                    if (!b.getStyleClass().contains("active")) b.getStyleClass().add("active");
                } else {
                    b.getStyleClass().remove("active");
                }
            }
        }
        content.getChildren().clear();
        try {
            if (tab.equals("Daily") || tab.equals("Weekly") || tab.equals("Monthly") || tab.equals("Yearly")) {
                renderRevenue();
            } else if (tab.equals("By Item Code")) {
                renderItemCodeSales();
            } else if (tab.equals("PO Statistics")) {
                renderPoStats();
            } else {
                renderPoPayments();
            }
        } catch (Exception e) {
            content.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private Map<String, String> getRange(String tab) {
        LocalDate now = LocalDate.now();
        Map<String, String> r = new HashMap<>();
        switch (tab) {
            case "Daily" -> { r.put("from", now.toString()); r.put("to", now.toString()); }
            case "Weekly" -> {
                LocalDate mon = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                r.put("from", mon.toString()); r.put("to", mon.plusDays(6).toString());
            }
            case "Monthly" -> {
                r.put("from", now.withDayOfMonth(1).toString());
                r.put("to", now.withDayOfMonth(now.lengthOfMonth()).toString());
            }
            case "Yearly" -> {
                r.put("from", now.getYear() + "-01-01"); r.put("to", now.getYear() + "-12-31");
            }
        }
        return r;
    }

    private void renderRevenue() {
        Map<String, String> range = getRange(currentTab);
        String groupBy = "Yearly".equals(currentTab) ? "month" : "day";
        revRows.setAll(ReportService.revenue(range.get("from"), range.get("to"), groupBy));
        Map<String, Double> expensesByDay = "Yearly".equals(currentTab)
                ? aggregateByMonth(ExpenseService.summary(range.get("from"), range.get("to")))
                : ExpenseService.summary(range.get("from"), range.get("to"));

        double total = revRows.stream().mapToDouble(r -> r.total).sum();
        double cash = revRows.stream().mapToDouble(r -> r.cash).sum();
        double gcash = revRows.stream().mapToDouble(r -> r.gcash).sum();
        double maya = revRows.stream().mapToDouble(r -> r.maya).sum();
        double po = revRows.stream().mapToDouble(r -> r.poAmount).sum();
        double delivery = revRows.stream().mapToDouble(r -> r.deliveryTotal).sum();
        double totalExpenses = expensesByDay.values().stream().mapToDouble(Double::doubleValue).sum();
        double profit = total - totalExpenses;

        // Print buttons
        HBox printRow = new HBox(8);
        printRow.setAlignment(Pos.CENTER_RIGHT);
        Button pdf = new Button("Save / Print PDF"); pdf.getStyleClass().addAll("btn", "btn-secondary"); pdf.setStyle("-fx-padding: 6 16; -fx-font-size: 12;");
        pdf.setOnAction(e -> ReportPrintView.print(root.getScene().getWindow(), currentTab, range, revRows, totals(total, cash, gcash, maya, po, delivery), App.ctx.settings));
        Button thermal = new Button("Print Thermal"); thermal.getStyleClass().addAll("btn", "btn-ghost"); thermal.setStyle("-fx-padding: 6 16; -fx-font-size: 12;");
        thermal.setOnAction(e -> {
            try {
                String printer = (String) App.ctx.settings.getOrDefault("thermal_printer", "");
                if (printer == null || printer.isEmpty()) { new Modal(root.getScene().getWindow(), "Error", new Label("No thermal printer configured.")).show(); return; }
                PrinterService.printReport(currentTab, range, revRows, totals(total, cash, gcash, maya, po, delivery), App.ctx.settings, printer);
            } catch (Exception ex) { new Modal(root.getScene().getWindow(), "Error", new Label(ex.getMessage())).show(); }
        });
        printRow.getChildren().addAll(pdf, thermal);
        content.getChildren().add(printRow);

        // Summary cards
        GridPane summary = new GridPane();
        summary.setHgap(2);
        String[][] cards = {
                {"Total Revenue", sym + Money.fmt(total), "primary"},
                {"Cash", sym + Money.fmt(cash), ""},
                {"GCash", sym + Money.fmt(gcash), ""},
                {"Maya", sym + Money.fmt(maya), ""},
                {"PO Charged", sym + Money.fmt(po), ""},
                {"Delivery Fees", sym + Money.fmt(delivery), ""},
                {"Expenses", sym + Money.fmt(totalExpenses), "danger"},
                {"Net Profit", sym + Money.fmt(profit), profit >= 0 ? "success" : "danger"},
        };
        for (int i = 0; i < cards.length; i++) {
            VBox card = makeSummaryCard(cards[i][0], cards[i][1], cards[i][2]);
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS); cc.setPercentWidth(12.5);
            summary.getColumnConstraints().add(cc);
            summary.add(card, i, 0);
        }
        content.getChildren().add(summary);

        Label sectionTitle = new Label(currentTab.equals("Yearly") ? "MONTHLY BREAKDOWN" : "DAY-BY-DAY BREAKDOWN");
        sectionTitle.getStyleClass().add("section-title");
        content.getChildren().add(sectionTitle);

        TableView<RevenueRow> table = new TableView<>(revRows);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.setPrefHeight(380);
        table.getColumns().addAll(
                colS("DATE", (RevenueRow r) -> r.day),
                colS("TOTAL", r -> sym + Money.fmt(r.total)),
                colS("CASH", r -> sym + Money.fmt(r.cash)),
                colS("GCASH", r -> sym + Money.fmt(r.gcash)),
                colS("MAYA", r -> sym + Money.fmt(r.maya)),
                colS("PO", r -> sym + Money.fmt(r.poAmount)),
                colS("DELIVERY", r -> sym + Money.fmt(r.deliveryTotal)),
                colS("EXPENSES", r -> sym + Money.fmt(expensesByDay.getOrDefault(r.day, 0.0))),
                colS("PROFIT", r -> sym + Money.fmt(r.total - expensesByDay.getOrDefault(r.day, 0.0)))
        );
        content.getChildren().add(table);
    }

    private Map<String, Double> aggregateByMonth(Map<String, Double> daily) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (var entry : daily.entrySet()) {
            String key = entry.getKey().length() >= 7 ? entry.getKey().substring(0, 7) : entry.getKey();
            m.merge(key, entry.getValue(), Double::sum);
        }
        return m;
    }

    private VBox makeSummaryCard(String label, String value, String variant) {
        VBox card = new VBox(8);
        card.getStyleClass().add("summary-card");
        if ("primary".equals(variant)) card.getStyleClass().add("summary-card-primary");
        else if ("danger".equals(variant)) card.getStyleClass().add("summary-card-danger");
        else if ("success".equals(variant)) card.getStyleClass().add("summary-card-success");
        Label l = new Label(label.toUpperCase());
        l.setStyle("-fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700; -fx-text-fill: " +
                ("primary".equals(variant) ? "#d4690a" : ("danger".equals(variant) ? "#ef4444" : ("success".equals(variant) ? "#22c55e" : "#52525b"))) + ";");
        Label v = new Label(value);
        v.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 16; -fx-font-weight: 600; -fx-text-fill: " +
                ("danger".equals(variant) ? "#ef4444" : ("success".equals(variant) ? "#22c55e" : "#f4f4f5")) + ";");
        card.getChildren().addAll(l, v);
        return card;
    }

    private Map<String, Double> totals(double t, double c, double g, double m, double p, double d) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("total", t); map.put("cash", c); map.put("gcash", g); map.put("maya", m);
        map.put("po", p); map.put("delivery", d);
        return map;
    }

    private void renderItemCodeSales() {
        // Date range pickers (default: current month)
        LocalDate now = LocalDate.now();
        javafx.scene.control.DatePicker fromDp = new javafx.scene.control.DatePicker(now.withDayOfMonth(1));
        javafx.scene.control.DatePicker toDp = new javafx.scene.control.DatePicker(now);
        Button apply = new Button("Run Report"); apply.getStyleClass().addAll("btn", "btn-primary");

        HBox filter = new HBox(12);
        filter.setAlignment(Pos.BOTTOM_LEFT);
        VBox fb = new VBox(4); Label fl = new Label("FROM"); fl.getStyleClass().add("field-label"); fb.getChildren().addAll(fl, fromDp);
        VBox tb = new VBox(4); Label tl = new Label("TO");   tl.getStyleClass().add("field-label"); tb.getChildren().addAll(tl, toDp);
        filter.getChildren().addAll(fb, tb, apply);
        content.getChildren().add(filter);

        Label hint = new Label("Live view — joins transaction_items → transactions → products. Updates the moment a sale is committed (no manual refresh).");
        hint.getStyleClass().add("text-muted");
        hint.setStyle("-fx-font-size: 11; -fx-font-style: italic;");
        content.getChildren().add(hint);

        TableView<ItemCodeSalesRow> table = new TableView<>(itemRows);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.setPrefHeight(440);
        table.setPlaceholder(new Label("No sales in selected range."));
        table.getColumns().addAll(
                colS("ITEM CODE", (ItemCodeSalesRow r) -> r.itemCode == null ? "—" : r.itemCode),
                colS("PRODUCT", r -> r.productName == null ? "—" : r.productName),
                colS("DESCRIPTION / GRADE", r -> r.description == null ? "—" : r.description),
                colS("UNIT", r -> r.unit == null ? "—" : r.unit),
                colS("QTY SOLD", r -> Money.fmt(r.quantitySold)),
                colS("REVENUE", r -> sym + Money.fmt(r.revenue)),
                colS("# SALES", r -> String.valueOf(r.transactionCount)),
                colS("FIRST SALE", r -> r.firstSale == null ? "—" : r.firstSale),
                colS("LAST SALE", r -> r.lastSale == null ? "—" : r.lastSale)
        );
        content.getChildren().add(table);

        Runnable run = () -> {
            try {
                String from = fromDp.getValue() == null ? now.withDayOfMonth(1).toString() : fromDp.getValue().toString();
                String to   = toDp.getValue()   == null ? now.toString()                  : toDp.getValue().toString();
                itemRows.setAll(ReportService.salesByItemCode(from, to));
            } catch (Exception e) {
                new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show();
            }
        };
        apply.setOnAction(e -> run.run());
        run.run();
    }

    private void renderPoStats() {
        poStats.setAll(ReportService.poStats());
        TableView<PoStatRow> t = new TableView<>(poStats);
        t.getStyleClass().add("data-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        t.setPrefHeight(420);
        t.getColumns().addAll(
                colS("CUSTOMER", (PoStatRow r) -> r.customerName),
                colS("PO REF", r -> r.referenceNo == null ? "—" : r.referenceNo),
                colS("CREDIT LIMIT", r -> sym + Money.fmt(r.creditLimit)),
                colS("USED", r -> sym + Money.fmt(r.balanceUsed))
        );
        // Colored AVAILABLE column
        TableColumn<PoStatRow, PoStatRow> availCol = new TableColumn<>("AVAILABLE");
        availCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        availCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(PoStatRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setText(null); setStyle(""); return; }
                setText(sym + Money.fmt(r.available));
                setStyle("-fx-text-fill: " + (r.available > 0 ? "#22c55e" : "#ef4444") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-weight: 600;");
            }
        });
        t.getColumns().add(availCol);
        // Colored STATUS column
        TableColumn<PoStatRow, PoStatRow> statusCol = new TableColumn<>("STATUS");
        statusCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        statusCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(PoStatRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setGraphic(null); return; }
                Label b = new Label(r.status == null ? "—" : r.status.toUpperCase());
                String css = switch (r.status == null ? "" : r.status) {
                    case "open" -> "badge-success";
                    case "expired" -> "badge-danger";
                    case "closed" -> "badge-muted";
                    default -> "badge-warning";
                };
                b.getStyleClass().addAll("badge", css);
                setGraphic(b);
            }
        });
        t.getColumns().add(statusCol);
        t.getColumns().add(
                colS("EXPIRY", (PoStatRow r) -> r.expiryDate == null ? "—" : r.expiryDate)
        );
        content.getChildren().add(t);
    }

    private void renderPoPayments() {
        customers.setAll(CustomerService.list());
        ComboBox<Customer> cb = new ComboBox<>(FXCollections.concat(FXCollections.observableArrayList((Customer) null), customers));
        cb.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "All Customers" : c.name; }
            @Override public Customer fromString(String s) { return null; }
        });
        cb.setMinWidth(200);

        Button apply = new Button("Filter"); apply.getStyleClass().addAll("btn", "btn-primary");
        apply.setOnAction(e -> {
            poPayments.setAll(ReportService.poPaymentHistory(cb.getValue() == null ? null : cb.getValue().id));
        });

        HBox filter = new HBox(12);
        filter.setAlignment(Pos.BOTTOM_LEFT);
        VBox cbBox = new VBox(4);
        Label lbl = new Label("CUSTOMER"); lbl.getStyleClass().add("field-label");
        cbBox.getChildren().addAll(lbl, cb);
        filter.getChildren().addAll(cbBox, apply);
        content.getChildren().add(filter);

        TableView<PoPayment> t = new TableView<>(poPayments);
        t.getStyleClass().add("data-table");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        t.setPrefHeight(380);
        t.getColumns().addAll(
                colS("DATE", (PoPayment r) -> r.paymentDate == null ? "—" : r.paymentDate.substring(0, Math.min(10, r.paymentDate.length()))),
                colS("CUSTOMER", r -> r.customerName == null ? "—" : r.customerName),
                colS("PO REF", r -> r.referenceNo == null ? "—" : r.referenceNo),
                colS("AMOUNT", r -> sym + Money.fmt(r.amount)),
                colS("NOTES", r -> r.notes == null ? "—" : r.notes)
        );
        content.getChildren().add(t);
        poPayments.setAll(ReportService.poPaymentHistory(null));
    }

    private static <T> TableColumn<T, String> colS(String label, java.util.function.Function<T, String> g) {
        TableColumn<T, String> c = new TableColumn<>(label);
        c.setCellValueFactory(cd -> new SimpleStringProperty(g.apply(cd.getValue())));
        return c;
    }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #09090b; -fx-background: #09090b;");
        return sp;
    }
}

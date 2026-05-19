package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.db.Database;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ShellView {
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<String, Supplier<Region>> pages = new LinkedHashMap<>();

    public ShellView() {
        root.getStyleClass().add("bg-paper");
        pages.put("pos",          () -> new PosView().getRoot());
        pages.put("products",     () -> new ProductsView().getRoot());
        pages.put("customers",    () -> new CustomersView().getRoot());
        pages.put("po",           () -> new PoAccountsView().getRoot());
        pages.put("transactions", () -> new TransactionsView().getRoot());
        pages.put("trucks",       () -> new TrucksView().getRoot());
        pages.put("expenses",     () -> new ExpensesView().getRoot());
        pages.put("reports",      () -> new ReportsView().getRoot());
        pages.put("settings",     () -> new SettingsView().getRoot());

        Sidebar sidebar = new Sidebar(this::navigate);
        root.setLeft(sidebar);
        root.setCenter(content);
        navigate("pos");
    }

    public void navigate(String page) {
        Supplier<Region> sup = pages.get(page);
        if (sup == null) return;
        content.getChildren().setAll(sup.get());
    }

    public Region getRoot() { return root; }
}

package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.Customer;
import com.innov8.loyaltypos.model.Payment;
import com.innov8.loyaltypos.model.Product;
import com.innov8.loyaltypos.model.Transaction;
import com.innov8.loyaltypos.model.TransactionItem;
import com.innov8.loyaltypos.model.Truck;
import com.innov8.loyaltypos.model.TruckPrice;
import com.innov8.loyaltypos.service.CustomerService;
import com.innov8.loyaltypos.service.PosService;
import com.innov8.loyaltypos.service.PrinterService;
import com.innov8.loyaltypos.service.ProductService;
import com.innov8.loyaltypos.service.TransactionService;
import com.innov8.loyaltypos.service.TruckService;
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
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PosView {
    private final BorderPane root = new BorderPane();
    private final ObservableList<Product> productList = FXCollections.observableArrayList();
    private final ObservableList<Product> filteredProducts = FXCollections.observableArrayList();
    private final ObservableList<TransactionItem> cart = FXCollections.observableArrayList();
    private final ObservableList<Customer> allCustomers = FXCollections.observableArrayList();
    private Customer selectedCustomer;
    private final ComboBox<Customer> customerCombo = new ComboBox<>(allCustomers);
    private final TextField plateTf = new TextField();
    private final TextField deliveryTf = new TextField();
    private final TextField discountTf = new TextField();
    private final Label totalLabel = new Label("₱0.00");
    private final Label truckInfoLabel = new Label();
    private final TableView<TransactionItem> cartTable = new TableView<>();
    private final TextField searchTf = new TextField();
    private Map<Integer, Double> truckPriceMap = new HashMap<>();
    private boolean truckIsNew = false;
    private final TextField dateTf = new TextField();
    private final String sym;

    public PosView() {
        sym = App.ctx.getCurrencySymbol();
        root.setStyle("-fx-background-color: #09090b;");

        // ── Left product panel ──────────────────────────────────────────────
        VBox left = new VBox();
        left.setPrefWidth(260);
        left.setMinWidth(260);
        left.setStyle("-fx-background-color: #111; -fx-border-color: transparent rgba(255,255,255,0.08) transparent transparent;");

        VBox searchBox = new VBox();
        searchBox.setPadding(new Insets(12));
        searchBox.setStyle("-fx-border-color: transparent transparent rgba(255,255,255,0.08) transparent;");
        searchTf.setPromptText("Search products...");
        searchTf.getStyleClass().add("text-field");
        searchTf.textProperty().addListener((obs, o, v) -> applyFilter());
        searchBox.getChildren().add(searchTf);
        left.getChildren().add(searchBox);

        ListView<Product> productListView = new ListView<>(filteredProducts);
        productListView.setStyle("-fx-background-color: #111;");
        productListView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setGraphic(null); setStyle(""); return; }
                VBox box = new VBox(2);
                Label name = new Label(p.name);
                name.setStyle("-fx-text-fill: #f4f4f5; -fx-font-weight: 600; -fx-font-size: 13;");
                Label info = new Label(sym + Money.fmt(p.pricePerUnit) + " / " + p.unit);
                info.setStyle("-fx-text-fill: #71717a; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 11;");
                box.getChildren().addAll(name, info);
                setGraphic(box);
                setStyle("-fx-background-color: transparent; -fx-padding: 8 16; -fx-border-color: transparent transparent rgba(255,255,255,0.04) transparent;");
            }
        });
        productListView.setOnMouseClicked(e -> {
            Product p = productListView.getSelectionModel().getSelectedItem();
            if (p != null) addToCart(p);
        });
        VBox.setVgrow(productListView, Priority.ALWAYS);
        left.getChildren().add(productListView);
        root.setLeft(left);

        // ── Center: customer/date/plate, cart, totals ────────────────────────
        VBox center = new VBox();
        center.setStyle("-fx-background-color: #09090b;");

        // Top row: customer / date / plate
        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setPadding(new Insets(10, 20, 10, 20));
        topRow.setStyle("-fx-background-color: #111; -fx-border-color: transparent transparent rgba(255,255,255,0.08) transparent;");

        // Stack each field as label-above-control so the plate textbox sits at the
        // same baseline as customer/date (was: plate VBox rode high above its label).
        VBox customerBox = labelStack("CUSTOMER", customerCombo);
        customerCombo.setPrefWidth(240);
        customerCombo.setPromptText("Walk-in Customer");
        customerCombo.setEditable(false);
        customerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "Walk-in Customer" : c.name; }
            @Override public Customer fromString(String s) { return null; }
        });
        customerCombo.valueProperty().addListener((obs, o, v) -> selectedCustomer = v);

        dateTf.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dateTf.setPrefWidth(170);
        dateTf.getStyleClass().add("text-field");
        dateTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13;");
        VBox dateBox = labelStack("DATE", dateTf);

        plateTf.setPromptText("e.g. ABC 1234");
        plateTf.setPrefWidth(180);
        plateTf.getStyleClass().add("text-field");
        plateTf.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13;");
        plateTf.focusedProperty().addListener((obs, o, focused) -> { if (!focused) handlePlateBlur(); });

        truckInfoLabel.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700;");

        VBox plateControl = new VBox(2, plateTf, truckInfoLabel);
        VBox plateBox = labelStack("PLATE NO.", plateControl);

        topRow.setAlignment(Pos.BOTTOM_LEFT);
        topRow.getChildren().addAll(customerBox, sep(), dateBox, sep(), plateBox);
        center.getChildren().add(topRow);

        // Cart table
        cartTable.setItems(cart);
        cartTable.getStyleClass().add("data-table");
        cartTable.setPlaceholder(new Label("Select a product from the left panel to add it to the cart."));
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);

        TableColumn<TransactionItem, String> nameCol = new TableColumn<>("PRODUCT");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().productName));
        TableColumn<TransactionItem, String> unitCol = new TableColumn<>("UNIT");
        unitCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().unit));
        unitCol.setPrefWidth(100);
        unitCol.setMinWidth(80);
        TableColumn<TransactionItem, TransactionItem> qtyCol = new TableColumn<>("QTY");
        qtyCol.setPrefWidth(130);
        qtyCol.setMinWidth(110);
        qtyCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        qtyCol.setCellFactory(col -> editCell(true));

        TableColumn<TransactionItem, String> upCol = new TableColumn<>("UNIT PRICE");
        upCol.setCellValueFactory(c -> new SimpleStringProperty(sym + Money.fmt(c.getValue().unitPrice)));
        upCol.setPrefWidth(150);
        upCol.setMinWidth(120);
        upCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<TransactionItem, TransactionItem> cpCol = new TableColumn<>("CUSTOM PRICE");
        cpCol.setPrefWidth(170);
        cpCol.setMinWidth(140);
        cpCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        cpCol.setCellFactory(col -> editCell(false));

        TableColumn<TransactionItem, String> amtCol = new TableColumn<>("AMOUNT");
        amtCol.setCellValueFactory(c -> new SimpleStringProperty(sym + Money.fmt(c.getValue().amount)));
        amtCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        amtCol.setPrefWidth(170);
        amtCol.setMinWidth(140);

        TableColumn<TransactionItem, TransactionItem> rmCol = new TableColumn<>("");
        rmCol.setMaxWidth(50);
        rmCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        rmCol.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(TransactionItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setGraphic(null); return; }
                Button x = new Button("×");
                x.setStyle("-fx-background-color: transparent; -fx-text-fill: #3f3f46; -fx-font-size: 18; -fx-cursor: hand;");
                x.setOnAction(e -> cart.remove(it));
                setGraphic(x);
            }
        });

        cartTable.getColumns().addAll(nameCol, unitCol, qtyCol, upCol, cpCol, amtCol, rmCol);
        VBox.setVgrow(cartTable, Priority.ALWAYS);
        center.getChildren().add(cartTable);

        // Totals + Charge button
        HBox bottom = new HBox(32);
        bottom.setPadding(new Insets(16, 20, 16, 20));
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.setStyle("-fx-background-color: #111; -fx-border-color: rgba(255,255,255,0.08) transparent transparent transparent;");

        VBox feesBox = new VBox(10);
        feesBox.getChildren().addAll(feeRow("Delivery Charge", deliveryTf), feeRow("Discount", discountTf));
        deliveryTf.textProperty().addListener((o, a, b) -> recalc());
        discountTf.textProperty().addListener((o, a, b) -> recalc());

        VBox totalBox = new VBox(2);
        totalBox.setAlignment(Pos.CENTER_RIGHT);
        Label totLbl = new Label("TOTAL"); totLbl.setStyle("-fx-text-fill: #52525b; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700;");
        totalLabel.setStyle("-fx-text-fill: #f4f4f5; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 32; -fx-font-weight: 600;");
        totalBox.getChildren().addAll(totLbl, totalLabel);

        Button charge = new Button("CHARGE");
        charge.setStyle("-fx-background-color: #d4690a; -fx-text-fill: white; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800; -fx-padding: 16 36; -fx-background-radius: 8; -fx-cursor: hand;");
        charge.setOnAction(e -> {
            if (cart.isEmpty()) return;
            openPayment();
        });

        bottom.getChildren().addAll(feesBox, totalBox, charge);
        center.getChildren().add(bottom);
        root.setCenter(center);

        loadData();
    }

    private static VBox labelStack(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("section-title");
        VBox v = new VBox(4, l, control);
        v.setAlignment(Pos.TOP_LEFT);
        return v;
    }

    private static Region sep() {
        Region r = new Region();
        r.setPrefSize(1, 28);
        r.setStyle("-fx-background-color: rgba(255,255,255,0.08);");
        return r;
    }

    private HBox feeRow(String label, TextField tf) {
        Label l = new Label(label.toUpperCase());
        l.setStyle("-fx-text-fill: #52525b; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700;");
        l.setMinWidth(120);
        l.setStyle(l.getStyle() + " -fx-alignment: CENTER-RIGHT;");
        tf.setPromptText("0.00");
        tf.setMaxWidth(120);
        tf.setStyle("-fx-background-color: transparent; -fx-border-color: transparent transparent rgba(255,255,255,0.15) transparent; -fx-text-fill: #f4f4f5; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13; -fx-alignment: CENTER-RIGHT;");
        HBox h = new HBox(12, l, tf);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private javafx.scene.control.TableCell<TransactionItem, TransactionItem> editCell(boolean isQty) {
        return new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(TransactionItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) { setGraphic(null); return; }
                TextField tf = new TextField(isQty ? Money.fmt(it.quantity) : (it.customPrice == null ? "" : Money.fmt(it.customPrice)));
                tf.setPromptText(isQty ? "" : Money.fmt(it.unitPrice));
                tf.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-border-color: rgba(255,255,255,0.10); -fx-text-fill: " + (it.customPrice != null && !isQty ? "#d4690a" : "#f4f4f5") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13; -fx-padding: 5 8; -fx-background-radius: 4; -fx-border-radius: 4;");
                tf.setMaxWidth(isQty ? 100 : 140);
                tf.setPrefWidth(isQty ? 100 : 140);
                tf.textProperty().addListener((o, a, b) -> {
                    if (isQty) it.quantity = ProductsView.parseD(b);
                    else it.customPrice = b == null || b.isEmpty() ? null : ProductsView.parseD(b);
                    double p = it.customPrice != null ? it.customPrice : it.unitPrice;
                    it.amount = it.quantity * p;
                    cartTable.refresh();
                    recalc();
                });
                setGraphic(tf);
            }
        };
    }

    private void loadData() {
        try {
            productList.setAll(ProductService.list());
            allCustomers.setAll(CustomerService.list());
            applyFilter();
        } catch (Exception e) { showError(e); }
    }

    private void applyFilter() {
        String q = searchTf.getText() == null ? "" : searchTf.getText().toLowerCase();
        if (q.isEmpty()) filteredProducts.setAll(productList);
        else filteredProducts.setAll(productList.stream().filter(p -> p.name.toLowerCase().contains(q)).toList());
    }

    private void addToCart(Product p) {
        for (TransactionItem it : cart) {
            if (it.productId != null && it.productId == p.id) {
                it.quantity += 1;
                double price = it.customPrice != null ? it.customPrice : it.unitPrice;
                it.amount = it.quantity * price;
                cartTable.refresh();
                recalc();
                return;
            }
        }
        TransactionItem it = new TransactionItem();
        it.productId = p.id;
        it.productName = p.name;
        it.unit = p.unit;
        it.quantity = 1;
        it.unitPrice = p.pricePerUnit;
        Double customPrice = truckPriceMap.get(p.id);
        it.customPrice = customPrice;
        double effective = customPrice != null ? customPrice : p.pricePerUnit;
        it.amount = effective;
        cart.add(it);
        recalc();
    }

    private void recalc() {
        double subtotal = cart.stream().mapToDouble(i -> i.amount).sum();
        double delivery = ProductsView.parseD(deliveryTf.getText());
        double discount = ProductsView.parseD(discountTf.getText());
        double total = subtotal + delivery - discount;
        totalLabel.setText(sym + Money.fmt(total));
    }

    private void handlePlateBlur() {
        String pn = plateTf.getText() == null ? "" : plateTf.getText().trim().toUpperCase();
        truckPriceMap.clear();
        truckIsNew = false;
        if (pn.isEmpty()) { truckInfoLabel.setText(""); return; }
        plateTf.setText(pn);
        try {
            Truck t = TruckService.getByPlate(pn);
            if (t != null && !t.prices.isEmpty()) {
                for (TruckPrice tp : t.prices) truckPriceMap.put(tp.productId, tp.defaultPrice);
                truckInfoLabel.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700;");
                int n = t.prices.size();
                truckInfoLabel.setText("◆ Saved — " + n + " product price" + (n != 1 ? "s" : ""));
                for (TransactionItem it : cart) {
                    Double saved = truckPriceMap.get(it.productId);
                    if (saved != null) {
                        it.customPrice = saved;
                        it.amount = it.quantity * saved;
                    }
                }
                cartTable.refresh();
                recalc();
            } else {
                truckIsNew = (t == null);
                truckInfoLabel.setStyle("-fx-text-fill: #52525b; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 10; -fx-font-weight: 700;");
                truckInfoLabel.setText(truckIsNew ? "◇ New truck" : "");
            }
        } catch (Exception e) { showError(e); }
    }

    private void openPayment() {
        double subtotal = cart.stream().mapToDouble(i -> i.amount).sum();
        double delivery = ProductsView.parseD(deliveryTf.getText());
        double discount = ProductsView.parseD(discountTf.getText());
        double total = subtotal + delivery - discount;

        PaymentDialog dlg = new PaymentDialog(root.getScene().getWindow(), total, selectedCustomer);
        dlg.show(result -> {
            if (result == null) return;
            try {
                List<TransactionItem> snapshot = new ArrayList<>(cart);
                String pn = plateTf.getText() == null ? null : plateTf.getText().trim().toUpperCase();
                if (pn != null && pn.isEmpty()) pn = null;
                String date = dateTf.getText().trim();
                if (date.isEmpty()) date = null; else date = date.replace(' ', 'T') + ":00";
                PosService.CheckoutResult res = PosService.checkout(
                        App.ctx.currentUser.id,
                        selectedCustomer == null ? null : selectedCustomer.id,
                        new ArrayList<>(cart),
                        delivery, discount,
                        result.payments,
                        pn, date
                );
                Transaction full = TransactionService.get(res.id);

                if (pn != null && !snapshot.isEmpty()) {
                    List<TruckPrice> savePrices = new ArrayList<>();
                    for (TransactionItem it : snapshot) {
                        if (it.productId == null) continue;
                        TruckPrice tp = new TruckPrice();
                        tp.productId = it.productId;
                        tp.productName = it.productName;
                        tp.defaultPrice = it.customPrice != null ? it.customPrice : it.unitPrice;
                        savePrices.add(tp);
                    }
                    final String finalPn = pn;
                    new Thread(() -> { try { TruckService.upsert(finalPn, savePrices); } catch (Exception ignore) {} }, "truck-save").start();
                }

                cart.clear();
                deliveryTf.clear();
                discountTf.clear();
                customerCombo.setValue(null);
                selectedCustomer = null;
                plateTf.clear();
                truckPriceMap.clear();
                truckInfoLabel.setText("");
                dateTf.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                recalc();

                showCompletion(full, result.change);
            } catch (Exception e) { showError(e); }
        });
    }

    private void showCompletion(Transaction tx, double change) {
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);

        Label saved = new Label("Invoice saved");
        saved.setStyle("-fx-text-fill: #71717a; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 13;");
        Label inv = new Label(tx.invoiceNo);
        inv.setStyle("-fx-text-fill: #d4690a; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 28; -fx-font-weight: 800;");
        content.getChildren().addAll(saved, inv);

        if (change > 0.009) {
            VBox changeBox = new VBox(4);
            changeBox.setAlignment(Pos.CENTER);
            changeBox.setStyle("-fx-background-color: rgba(34,197,94,0.12); -fx-border-color: rgba(34,197,94,0.3); -fx-padding: 20; -fx-background-radius: 8; -fx-border-radius: 8;");
            Label cl = new Label("CHANGE TO RETURN");
            cl.setStyle("-fx-text-fill: #22c55e; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700;");
            Label ca = new Label(sym + Money.fmt(change));
            ca.setStyle("-fx-text-fill: #22c55e; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 48; -fx-font-weight: 700;");
            changeBox.getChildren().addAll(cl, ca);
            content.getChildren().add(changeBox);
        }

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER);
        Button printA4 = new Button("Print A4 Invoice"); printA4.getStyleClass().addAll("btn", "btn-primary");
        Button printT = new Button("Print Thermal"); printT.getStyleClass().addAll("btn", "btn-secondary");
        Button skip = new Button("Skip"); skip.getStyleClass().addAll("btn", "btn-ghost");
        btns.getChildren().addAll(printA4, printT, skip);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), "Transaction Complete", content);
        printA4.setOnAction(e -> InvoiceA4View.printInvoice(root.getScene().getWindow(), tx, App.ctx.settings));
        printT.setOnAction(e -> {
            try {
                String printer = (String) App.ctx.settings.getOrDefault("thermal_printer", "");
                if (printer == null || printer.isEmpty()) { showError(new Exception("No thermal printer configured. Set the Thermal Printer Name in Settings.")); return; }
                PrinterService.printReceipt(tx, App.ctx.settings, printer);
            } catch (Exception ex) { showError(ex); }
        });
        skip.setOnAction(e -> modal.close());
        modal.show();
    }

    private void showError(Exception e) {
        Label l = new Label(e.getMessage()); l.setWrapText(true);
        new Modal(root.getScene().getWindow(), "Error", l).show();
    }

    public Region getRoot() { return root; }
}

package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.model.User;
import com.innov8.loyaltypos.service.SettingsService;
import com.innov8.loyaltypos.service.SyncService;
import com.innov8.loyaltypos.service.TotpService;
import com.innov8.loyaltypos.service.UserService;
import java.awt.Desktop;
import java.io.File;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SettingsView {
    private final VBox root = new VBox(20);
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final TableView<User> userTable = new TableView<>();
    private final Map<String, javafx.scene.Node> fields = new LinkedHashMap<>();

    private static class Section {
        String title;
        String[][] fields; // {key, label, type}
        Section(String t, String[][] f) { title = t; fields = f; }
    }

    private static final Section[] SECTIONS = {
            new Section("Business Information", new String[][]{
                    {"business_name", "Business Name", "text"},
                    {"business_address", "Address", "text"},
                    {"business_contact", "Contact Number", "text"}
            }),
            new Section("Invoice", new String[][]{
                    {"invoice_prefix", "Invoice Prefix", "text"},
                    {"invoice_counter", "Next Invoice Number", "number"}
            }),
            new Section("Currency & Tax", new String[][]{
                    {"currency_symbol", "Currency Symbol", "text"},
                    {"vat_enabled", "VAT Enabled", "boolean"},
                    {"vat_rate", "VAT Rate (%)", "number"}
            }),
            new Section("Appearance", new String[][]{
                    {"theme", "Theme (dark / light)", "text"}
            }),
            new Section("Printers", new String[][]{
                    {"thermal_printer", "Thermal Printer Name", "text"},
                    {"normal_printer", "Normal (A4) Printer Name", "text"},
                    {"log_printer", "Log Printer Name (2nd Thermal)", "text"}
            }),
            new Section("API Integration", new String[][]{
                    {"api_url", "API URL (leave blank for offline-only)", "text"},
                    {"api_key", "API Key (X-API-Key)", "text"}
            }),
            new Section("Google Gemini AI", new String[][]{
                    {"ai_enabled", "Enable AI features", "boolean"},
                    {"gemini_api_key", "Gemini API Key (https://aistudio.google.com/app/apikey)", "text"},
                    {"gemini_model", "Model (default: gemini-3.1-flash-lite)", "text"}
            }),
            new Section("PO Defaults", new String[][]{
                    {"po_default_credit_limit", "Default Credit Limit", "number"},
                    {"po_default_expiry_days", "Default Expiry (days)", "number"}
            })
    };

    public SettingsView() {
        root.setPadding(new Insets(36, 56, 48, 56));
        root.setSpacing(28);
        root.setMaxWidth(1100);

        Label title = new Label("SETTINGS"); title.getStyleClass().add("page-title");
        root.getChildren().add(title);

        for (Section s : SECTIONS) renderSection(s);

        Button save = new Button("Save Settings");
        save.getStyleClass().addAll("btn", "btn-primary");
        save.setStyle("-fx-padding: 12 32; -fx-font-size: 14;");
        save.setOnAction(e -> saveAll(save));
        root.getChildren().add(save);

        renderUsers();
        renderDbInfo();
        renderTotp();
        renderSync();
    }

    private void renderDbInfo() {
        Region div = new Region(); div.setStyle("-fx-background-color: -border;"); div.setPrefHeight(1);
        root.getChildren().add(div);
        Label t = new Label("DATABASE"); t.getStyleClass().add("section-title");
        root.getChildren().add(t);

        String path = Database.currentDbPath();
        Label pathLbl = new Label(path == null ? "(unknown)" : path);
        pathLbl.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 11;");
        pathLbl.setWrapText(true);

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Button openFolder = new Button("Open DB Folder");
        openFolder.getStyleClass().addAll("btn", "btn-secondary");
        openFolder.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        openFolder.setOnAction(e -> {
            try {
                if (path == null) return;
                File parent = new File(path).getParentFile();
                if (parent != null && Desktop.isDesktopSupported()) Desktop.getDesktop().open(parent);
            } catch (Exception ex) { showError(ex); }
        });
        row.getChildren().add(openFolder);
        root.getChildren().addAll(pathLbl, row);
    }

    private void renderSection(Section section) {
        Label title = new Label(section.title.toUpperCase());
        title.getStyleClass().add("section-title");
        title.setStyle(title.getStyle() + " -fx-padding: 0 0 8 0; -fx-border-color: transparent transparent -border transparent;");
        root.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(40); grid.setVgap(20);
        javafx.scene.layout.ColumnConstraints c1 = new javafx.scene.layout.ColumnConstraints();
        javafx.scene.layout.ColumnConstraints c2 = new javafx.scene.layout.ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS); c1.setPercentWidth(50);
        c2.setHgrow(Priority.ALWAYS); c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);
        for (int i = 0; i < section.fields.length; i++) {
            String[] f = section.fields[i];
            String key = f[0], label = f[1], type = f[2];
            Object curVal = App.ctx.settings.get(key);
            Label lbl = new Label(label.toUpperCase()); lbl.getStyleClass().add("field-label");
            javafx.scene.Node ctl;
            if ("boolean".equals(type)) {
                CheckBox cb = new CheckBox();
                cb.setSelected(curVal instanceof Boolean ? (Boolean) curVal : Boolean.parseBoolean(String.valueOf(curVal)));
                ctl = cb;
            } else {
                TextField tf = new TextField(curVal == null ? "" : curVal.toString());
                tf.getStyleClass().add("field-input");
                ctl = tf;
            }
            fields.put(key, ctl);
            if (ctl instanceof Region rc) rc.setMaxWidth(Double.MAX_VALUE);
            VBox box = new VBox(6, lbl, ctl);
            box.setMaxWidth(Double.MAX_VALUE);
            grid.add(box, i % 2, i / 2);
        }
        root.getChildren().add(grid);
    }

    private void saveAll(Button btn) {
        try {
            for (var entry : fields.entrySet()) {
                String key = entry.getKey();
                Object value;
                javafx.scene.Node n = entry.getValue();
                if (n instanceof CheckBox cb) value = cb.isSelected();
                else if (n instanceof TextField tf) {
                    String text = tf.getText();
                    if (key.endsWith("_counter") || key.endsWith("_days") || key.endsWith("_limit") || key.endsWith("_rate")) {
                        try { value = Double.parseDouble(text); } catch (Exception e) { value = 0; }
                    } else value = text;
                } else value = "";
                SettingsService.set(key, value);
            }
            App.ctx.settings = SettingsService.getAll();
            // If theme key changed, re-apply CSS on the live scene
            String theme = String.valueOf(App.ctx.settings.getOrDefault("theme", "dark"));
            if (!theme.equals(App.ctx.theme)) {
                App.ctx.theme = "light".equalsIgnoreCase(theme) ? "light" : "dark";
                java.util.prefs.Preferences.userNodeForPackage(com.innov8.loyaltypos.AppContext.class).put("pos_theme", App.ctx.theme);
                if (btn.getScene() != null) App.applyTheme(btn.getScene());
            }
            btn.setText("✓ Saved");
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignore) {}
                javafx.application.Platform.runLater(() -> btn.setText("Save Settings"));
            }).start();
        } catch (Exception e) { showError(e); }
    }

    private void renderUsers() {
        Region div = new Region(); div.setStyle("-fx-background-color: -border;"); div.setPrefHeight(1);
        root.getChildren().add(div);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label t = new Label("USER MANAGEMENT"); t.getStyleClass().add("section-title");
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button add = new Button("+ Add User"); add.getStyleClass().addAll("btn", "btn-primary"); add.setStyle("-fx-padding: 6 16; -fx-font-size: 12;");
        add.setOnAction(e -> openUserForm(null));
        header.getChildren().addAll(t, s, add);
        root.getChildren().add(header);

        userTable.getStyleClass().add("data-table");
        userTable.setItems(users);
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        TableColumn<User, String> nameC = new TableColumn<>("NAME");
        nameC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        TableColumn<User, String> roleC = new TableColumn<>("ROLE");
        roleC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().role));
        TableColumn<User, String> statusC = new TableColumn<>("STATUS");
        statusC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive ? "Active" : "Inactive"));
        TableColumn<User, User> actC = new TableColumn<>("");
        actC.setMinWidth(160);
        actC.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        actC.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setGraphic(null); return; }
                HBox box = new HBox(6);
                Button edit = new Button("Edit"); edit.getStyleClass().addAll("btn", "btn-ghost"); edit.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                edit.setOnAction(e -> openUserForm(u));
                box.getChildren().add(edit);
                if (u.isActive) {
                    Button d = new Button("Deactivate"); d.getStyleClass().addAll("btn", "btn-ghost"); d.setStyle("-fx-padding: 4 12; -fx-font-size: 11;");
                    d.setOnAction(e -> {
                        if (ConfirmDialog.show(root.getScene().getWindow(), "Deactivate \"" + u.name + "\"?")) {
                            UserService.deactivate(u.id);
                            loadUsers();
                        }
                    });
                    box.getChildren().add(d);
                }
                setGraphic(box);
            }
        });
        userTable.getColumns().addAll(nameC, roleC, statusC, actC);
        root.getChildren().add(userTable);
        loadUsers();
    }

    private void loadUsers() {
        try { users.setAll(UserService.list()); } catch (Exception e) { showError(e); }
    }

    private void openUserForm(User editing) {
        VBox content = new VBox(16);
        Label err = new Label(); err.getStyleClass().add("error-banner"); err.setVisible(false); err.setManaged(false);
        content.getChildren().add(err);
        TextField nameTf = ProductsView.labeledField(content, "Name", editing == null ? "" : editing.name);
        Label pl = new Label(editing == null ? "PIN (4–8 DIGITS)" : "NEW PIN (LEAVE BLANK TO KEEP)"); pl.getStyleClass().add("field-label");
        PasswordField pinTf = new PasswordField(); pinTf.getStyleClass().add("text-field");
        Label cpl = new Label("CONFIRM PIN"); cpl.getStyleClass().add("field-label");
        PasswordField confirmTf = new PasswordField(); confirmTf.getStyleClass().add("text-field");
        content.getChildren().addAll(new VBox(6, pl, pinTf), new VBox(6, cpl, confirmTf));
        Label rl = new Label("ROLE"); rl.getStyleClass().add("field-label");
        ComboBox<String> roleCb = new ComboBox<>(FXCollections.observableArrayList("cashier", "admin"));
        roleCb.setValue(editing == null ? "cashier" : editing.role);
        roleCb.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(new VBox(6, rl, roleCb));

        HBox btns = new HBox(10);
        btns.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, Priority.ALWAYS);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button save = new Button(editing == null ? "Create" : "Update"); save.getStyleClass().addAll("btn", "btn-primary");
        btns.getChildren().addAll(s, cancel, save);
        content.getChildren().add(btns);

        Modal modal = new Modal(root.getScene().getWindow(), editing == null ? "New User" : "Edit User", content);
        cancel.setOnAction(e -> modal.close());
        save.setOnAction(e -> {
            try {
                String name = nameTf.getText().trim();
                String pin = pinTf.getText();
                String confirmPin = confirmTf.getText();
                if (name.isEmpty()) { err.setText("Name is required."); err.setVisible(true); err.setManaged(true); return; }
                if (editing == null && pin.isEmpty()) { err.setText("PIN is required."); err.setVisible(true); err.setManaged(true); return; }
                if (!pin.isEmpty() && !pin.equals(confirmPin)) { err.setText("PINs do not match."); err.setVisible(true); err.setManaged(true); return; }
                if (!pin.isEmpty() && !pin.matches("\\d{4,8}")) { err.setText("PIN must be 4–8 digits."); err.setVisible(true); err.setManaged(true); return; }
                if (editing != null) UserService.update(editing.id, name, roleCb.getValue(), pin.isEmpty() ? null : pin);
                else UserService.create(name, roleCb.getValue(), pin);
                modal.close();
                loadUsers();
            } catch (Exception ex) { showError(ex); }
        });
        modal.show();
    }

    private final ImageView totpQrView = new ImageView();

    private void renderTotp() {
        Region div = new Region(); div.setStyle("-fx-background-color: -border;"); div.setPrefHeight(1);
        root.getChildren().add(div);
        Label t = new Label("ADMIN 2FA (GOOGLE AUTHENTICATOR)"); t.getStyleClass().add("section-title");
        Label desc = new Label("Required for voiding or deleting transactions. Admin scans the QR once with Google Authenticator.");
        desc.setStyle("-fx-text-fill: -faint; -fx-font-size: 13;");
        desc.setWrapText(true);
        root.getChildren().addAll(t, desc);

        totpQrView.setFitWidth(200); totpQrView.setFitHeight(200);
        totpQrView.setStyle("-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.5), 4, 1, 0, 0);");
        totpQrView.setVisible(false); totpQrView.setManaged(false);
        root.getChildren().add(totpQrView);

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        Label status = new Label(TotpService.isConfigured() ? "● Configured" : "○ Not configured");
        status.setStyle("-fx-text-fill: " + (TotpService.isConfigured() ? "#22c55e" : "-faint") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 12;");
        Button setup = new Button(TotpService.isConfigured() ? "Reset & Regenerate QR" : "Setup Google Authenticator");
        setup.getStyleClass().addAll("btn", "btn-primary"); setup.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        setup.setOnAction(e -> {
            try {
                TotpService.SetupResult r = TotpService.setup();
                String b64 = r.qrDataUrl.substring(r.qrDataUrl.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(b64);
                totpQrView.setImage(new Image(new ByteArrayInputStream(bytes)));
                totpQrView.setVisible(true); totpQrView.setManaged(true);
                status.setText("● Configured");
                status.setStyle("-fx-text-fill: #22c55e; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 12;");
            } catch (Exception ex) { showError(ex); }
        });
        Button reset = new Button("Remove 2FA");
        reset.getStyleClass().addAll("btn", "btn-ghost"); reset.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        reset.setOnAction(e -> {
            if (ConfirmDialog.show(root.getScene().getWindow(), "Remove 2FA? Voids will be blocked until re-configured.")) {
                TotpService.reset();
                status.setText("○ Not configured");
                status.setStyle("-fx-text-fill: -faint; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 12;");
                totpQrView.setVisible(false); totpQrView.setManaged(false);
            }
        });
        row.getChildren().addAll(status, setup, reset);
        root.getChildren().add(row);
    }

    private void renderSync() {
        Region div = new Region(); div.setStyle("-fx-background-color: -border;"); div.setPrefHeight(1);
        root.getChildren().add(div);
        Label t = new Label("API SYNC"); t.getStyleClass().add("section-title");
        root.getChildren().add(t);

        Label statusLbl = new Label();
        statusLbl.setStyle("-fx-text-fill: -ink-soft; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 12;");
        Runnable refresh = () -> {
            try {
                SyncService.Status s = SyncService.status();
                statusLbl.setText((s.online ? "● Online" : "○ Offline") + "  ·  " + s.pending + " pending");
            } catch (Exception ignore) {}
        };
        refresh.run();

        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Button check = new Button("Check Status"); check.getStyleClass().addAll("btn", "btn-secondary"); check.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        check.setOnAction(e -> refresh.run());
        Button now = new Button("Sync Now"); now.getStyleClass().addAll("btn", "btn-ghost"); now.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        now.setOnAction(e -> {
            new Thread(() -> { SyncService.flushQueue(); javafx.application.Platform.runLater(refresh); }, "sync-now").start();
        });
        Button full = new Button("Full Sync (All Data)"); full.getStyleClass().addAll("btn", "btn-success-ghost"); full.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        full.setOnAction(e -> {
            new Thread(() -> { SyncService.fullSync(); javafx.application.Platform.runLater(refresh); }, "sync-full").start();
        });
        Button pull = new Button("Pull from API (Refresh)"); pull.getStyleClass().addAll("btn", "btn-primary"); pull.setStyle("-fx-padding: 8 20; -fx-font-size: 13;");
        pull.setOnAction(e -> {
            pull.setDisable(true);
            pull.setText("Pulling…");
            new Thread(() -> {
                SyncService.PullResult r = SyncService.pullAll();
                javafx.application.Platform.runLater(() -> {
                    pull.setDisable(false);
                    pull.setText("Pull from API (Refresh)");
                    StringBuilder summary = new StringBuilder();
                    if (r.skipped) summary.append("API URL not configured.");
                    else {
                        summary.append("Pulled ").append(r.totalRows()).append(" row(s) across ").append(r.rowsByEntity.size()).append(" entities.\n\n");
                        r.rowsByEntity.forEach((k, v) -> summary.append("  ").append(k).append(": ").append(v).append("\n"));
                        if (!r.errors.isEmpty()) {
                            summary.append("\nErrors:\n");
                            r.errors.forEach((k, v) -> summary.append("  ").append(k).append(": ").append(v).append("\n"));
                        }
                    }
                    Label lbl = new Label(summary.toString());
                    lbl.setStyle("-fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 12;");
                    lbl.setWrapText(true);
                    new Modal(root.getScene().getWindow(), "Pull complete", lbl).show();
                    refresh.run();
                });
            }, "api-pull").start();
        });
        row.getChildren().addAll(statusLbl, check, now, full, pull);
        root.getChildren().add(row);
    }

    private void showError(Exception e) { new Modal(root.getScene().getWindow(), "Error", new Label(e.getMessage())).show(); }

    public Region getRoot() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: -paper; -fx-background: -paper;");
        return sp;
    }
}

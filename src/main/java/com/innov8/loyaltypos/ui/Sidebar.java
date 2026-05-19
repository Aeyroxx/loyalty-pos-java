package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.service.SyncService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    private static class NavItem {
        final String label, page, icon;
        final boolean adminOnly;
        NavItem(String l, String p, String i, boolean a) { label=l; page=p; icon=i; adminOnly=a; }
    }

    private static final List<NavItem> NAV_ITEMS = List.of(
            new NavItem("POS", "pos", "⊕", false),
            new NavItem("Products", "products", "◈", true),
            new NavItem("Customers", "customers", "◎", false),
            new NavItem("PO Accounts", "po", "◇", false),
            new NavItem("Transactions", "transactions", "≡", false),
            new NavItem("Trucks", "trucks", "▷", true),
            new NavItem("Expenses", "expenses", "◉", false),
            new NavItem("Reports", "reports", "▦", true),
            new NavItem("Settings", "settings", "⚙", true)
    );

    private final Consumer<String> onNavigate;
    private String currentPage = "pos";
    private final Circle syncDot = new Circle(3.5);
    private final List<Button> navButtons = new ArrayList<>();

    public Sidebar(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        getStyleClass().add("sidebar");
        setMinWidth(220);
        setPrefWidth(220);

        // Logo block
        VBox logoBlock = new VBox(2);
        logoBlock.setPadding(new Insets(28, 24, 20, 24));
        logoBlock.setStyle("-fx-border-color: transparent transparent #1e1e1e transparent;");

        HBox logoRow = new HBox(8);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        Label logo = new Label("LOYALTY");
        logo.getStyleClass().add("sidebar-logo");
        syncDot.setFill(javafx.scene.paint.Color.web("#3f3f46"));
        logoRow.getChildren().addAll(logo, syncDot);
        if (Database.isDevMode()) {
            Label devBadge = new Label("DEV");
            devBadge.setStyle("-fx-text-fill: #ef4444; -fx-background-color: rgba(239,68,68,0.15); -fx-border-color: rgba(239,68,68,0.4); -fx-padding: 1 5; -fx-background-radius: 4; -fx-border-radius: 4; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 9; -fx-font-weight: 700;");
            logoRow.getChildren().add(devBadge);
        }
        logoBlock.getChildren().add(logoRow);
        Label sub = new Label(Database.isDevMode() ? "DEV ENVIRONMENT" : "POS SYSTEM");
        sub.setStyle("-fx-text-fill: #444; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 9;");
        logoBlock.getChildren().add(sub);
        getChildren().add(logoBlock);

        // User block
        VBox userBlock = new VBox(2);
        userBlock.setPadding(new Insets(12, 24, 12, 24));
        userBlock.setStyle("-fx-border-color: transparent transparent #1a1a1a transparent;");
        Label userName = new Label(App.ctx.currentUser != null ? App.ctx.currentUser.name : "");
        userName.setStyle("-fx-text-fill: #ccc; -fx-font-family: 'Barlow',sans-serif; -fx-font-size: 13; -fx-font-weight: 500;");
        Label userRole = new Label((App.ctx.currentUser != null ? App.ctx.currentUser.role : "").toUpperCase());
        userRole.setStyle("-fx-text-fill: #555; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 10;");
        userBlock.getChildren().addAll(userName, userRole);
        getChildren().add(userBlock);

        // Nav buttons
        VBox nav = new VBox();
        nav.setPadding(new Insets(12, 0, 12, 0));
        VBox.setVgrow(nav, Priority.ALWAYS);
        boolean isAdmin = App.ctx.currentUser != null && App.ctx.currentUser.isAdmin();
        for (NavItem item : NAV_ITEMS) {
            if (item.adminOnly && !isAdmin) continue;
            Button b = new Button(item.icon + "   " + item.label);
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> select(item.page));
            navButtons.add(b);
            b.setUserData(item.page);
            nav.getChildren().add(b);
        }
        getChildren().add(nav);

        // Bottom controls
        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(16, 24, 16, 24));
        bottom.setStyle("-fx-border-color: #1a1a1a transparent transparent transparent;");

        Button themeBtn = new Button((App.ctx.theme.equals("dark") ? "☀  LIGHT MODE" : "◑  DARK MODE"));
        themeBtn.setStyle("-fx-background-color: rgba(255,255,255,0.04); -fx-border-color: rgba(255,255,255,0.08); -fx-text-fill: #555; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700; -fx-padding: 6 12; -fx-background-radius: 6; -fx-border-radius: 6;");
        themeBtn.setMaxWidth(Double.MAX_VALUE);
        themeBtn.setOnAction(e -> { App.ctx.toggleTheme(); App.showShell(); });
        bottom.getChildren().add(themeBtn);

        Button devBtn = new Button(Database.isDevMode() ? "◉ EXIT DEV MODE" : "⟨/⟩ DEV MODE");
        devBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: " + (Database.isDevMode() ? "#ef4444" : "#2a2a2a") + "; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 10; -fx-font-weight: 700; -fx-alignment: center-left; -fx-padding: 4 0;");
        devBtn.setMaxWidth(Double.MAX_VALUE);
        devBtn.setOnAction(e -> {
            DevPinDialog dlg = new DevPinDialog(getScene().getWindow(), Database.isDevMode());
            dlg.showAndWait();
            if (dlg.isAuthorized()) {
                Database.setDevMode(!Database.isDevMode());
                App.logout();
            }
        });
        bottom.getChildren().add(devBtn);

        Button logout = new Button("← LOGOUT");
        logout.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-text-fill: #444; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 12; -fx-font-weight: 700; -fx-alignment: center-left; -fx-padding: 0;");
        logout.setOnAction(e -> App.logout());
        bottom.getChildren().add(logout);

        getChildren().add(bottom);

        select("pos");

        // Sync poll every 30s
        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(30), e -> refreshSync()));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
        refreshSync();
    }

    private void select(String page) {
        currentPage = page;
        for (Button b : navButtons) {
            if (page.equals(b.getUserData())) {
                if (!b.getStyleClass().contains("active")) b.getStyleClass().add("active");
            } else {
                b.getStyleClass().remove("active");
            }
        }
        onNavigate.accept(page);
    }

    private void refreshSync() {
        new Thread(() -> {
            try {
                SyncService.Status s = SyncService.status();
                Platform.runLater(() -> {
                    String color;
                    if (!s.online) color = "#3f3f46";
                    else if (s.pending > 0) color = "#f59e0b";
                    else color = "#22c55e";
                    syncDot.setFill(javafx.scene.paint.Color.web(color));
                });
                if (s.online && s.pending > 0) SyncService.flushQueue();
            } catch (Exception ignore) {}
        }, "sync-poll").start();
    }
}

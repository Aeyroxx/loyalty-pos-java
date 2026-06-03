package com.innov8.loyaltypos;

import com.innov8.loyaltypos.db.Database;
import com.innov8.loyaltypos.service.SettingsService;
import com.innov8.loyaltypos.service.SyncService;
import com.innov8.loyaltypos.ui.LoginView;
import com.innov8.loyaltypos.ui.ShellView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class App extends Application {
    public static AppContext ctx;
    public static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        Database.init(false);
        ctx = new AppContext();
        ctx.settings = SettingsService.getAll();
        // Reconcile theme: if the persisted Setting differs from the Preferences value
        // (e.g. user toggled it on another install), the Setting wins.
        String settingsTheme = String.valueOf(ctx.settings.getOrDefault("theme", ctx.theme));
        if (settingsTheme != null && !settingsTheme.isEmpty()
                && !"null".equals(settingsTheme) && !settingsTheme.equals(ctx.theme)) {
            ctx.theme = "light".equalsIgnoreCase(settingsTheme) ? "light" : "dark";
            java.util.prefs.Preferences.userNodeForPackage(AppContext.class).put("pos_theme", ctx.theme);
        }
        SyncService.startBackground();

        showLogin();

        stage.setTitle("Loyalty POS");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setWidth(1280);
        stage.setHeight(800);

        // Exit confirmation — prevent accidental closure
        stage.setOnCloseRequest(event -> {
            boolean confirmed = com.innov8.loyaltypos.ui.ConfirmDialog.show(
                    stage, "Are you sure you want to exit Loyalty POS?");
            if (!confirmed) event.consume();
        });

        stage.show();
    }

    public static void showLogin() {
        LoginView view = new LoginView();
        Scene scene = new Scene(view.getRoot(), 1280, 800);
        applyTheme(scene);
        primaryStage.setScene(scene);
    }

    public static void showShell() {
        ShellView view = new ShellView();
        Scene scene = new Scene(view.getRoot(), 1280, 800);
        applyTheme(scene);
        primaryStage.setScene(scene);
        // After login, surface POs that expire within 7 days (admins only).
        if (ctx != null && ctx.currentUser != null && ctx.currentUser.isAdmin()) {
            javafx.application.Platform.runLater(com.innov8.loyaltypos.ui.PoExpiryNotifier::checkAndShow);
        }
    }

    /** Loads base + theme-specific CSS. Call after any theme change. */
    public static void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(
                App.class.getResource("/com/innov8/loyaltypos/css/app.css")).toExternalForm());
        if (ctx != null && "light".equals(ctx.theme)) {
            scene.getStylesheets().add(Objects.requireNonNull(
                    App.class.getResource("/com/innov8/loyaltypos/css/light.css")).toExternalForm());
        }
        // Tag root for CSS class-based theming
        if (scene.getRoot() != null) {
            scene.getRoot().getStyleClass().removeAll("theme-light", "theme-dark");
            scene.getRoot().getStyleClass().add(ctx != null && "light".equals(ctx.theme) ? "theme-light" : "theme-dark");
        }
    }

    public static void logout() {
        ctx.currentUser = null;
        showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

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
        SyncService.startBackground();

        showLogin();

        stage.setTitle("Loyalty POS");
        stage.setMinWidth(1024);
        stage.setMinHeight(700);
        stage.setWidth(1280);
        stage.setHeight(800);
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

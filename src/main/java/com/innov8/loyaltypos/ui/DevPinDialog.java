package com.innov8.loyaltypos.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class DevPinDialog {
    private static final String DEV_PIN = "082827";
    private final Stage stage = new Stage();
    private boolean authorized = false;

    public DevPinDialog(Window owner, boolean currentDevMode) {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28));
        card.setStyle("-fx-background-color: #111; -fx-border-color: #222; -fx-border-radius: 16; -fx-background-radius: 16;");

        Label title = new Label(currentDevMode ? "EXIT DEV MODE" : "DEV MODE");
        title.setStyle("-fx-text-fill: " + (currentDevMode ? "#22c55e" : "#d4690a") + "; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 13; -fx-font-weight: 700;");
        Label sub = new Label("Enter developer PIN");
        sub.setStyle("-fx-text-fill: #3f3f46; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 11;");
        card.getChildren().addAll(title, sub);

        PinPad pad = new PinPad(pin -> {
            if (DEV_PIN.equals(pin)) { authorized = true; stage.close(); }
            else { /* swallow incorrect; dots reset */ }
        });
        card.getChildren().add(pad);

        Button cancel = new Button("CANCEL");
        cancel.setStyle("-fx-background-color: transparent; -fx-text-fill: #444; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700;");
        cancel.setOnAction(e -> stage.close());
        card.getChildren().add(cancel);

        VBox overlay = new VBox(card);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");
        overlay.setPrefSize(800, 600);

        Scene scene = new Scene(overlay, 800, 600);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/innov8/loyaltypos/css/app.css").toExternalForm());
        stage.setScene(scene);
    }

    public void showAndWait() { stage.showAndWait(); }
    public boolean isAuthorized() { return authorized; }
}

package com.innov8.loyaltypos.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public class ConfirmDialog {

    public static boolean show(Window owner, String message) {
        boolean[] confirmed = { false };
        VBox content = new VBox(20);
        Label msg = new Label(message);
        msg.setWrapText(true);
        msg.setStyle("-fx-text-fill: #a1a1aa; -fx-font-size: 14;");
        content.getChildren().add(msg);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region(); HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().addAll("btn", "btn-ghost");
        Button confirm = new Button("Confirm");
        confirm.getStyleClass().addAll("btn", "btn-primary");
        buttons.getChildren().addAll(spacer, cancel, confirm);
        content.getChildren().add(buttons);

        Modal m = new Modal(owner, "Confirm", content);
        cancel.setOnAction(e -> m.close());
        confirm.setOnAction(e -> { confirmed[0] = true; m.close(); });
        m.stage.showAndWait();
        return confirmed[0];
    }
}

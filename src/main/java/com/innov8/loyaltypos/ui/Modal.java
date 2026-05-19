package com.innov8.loyaltypos.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/** Reusable modal dialog wrapper. */
public class Modal {
    public final Stage stage = new Stage();
    private final VBox card = new VBox();
    private final VBox body = new VBox();

    public Modal(Window owner, String title, Node content) { this(owner, title, content, false); }

    public Modal(Window owner, String title, Node content, boolean wide) {
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initStyle(StageStyle.TRANSPARENT);

        card.getStyleClass().add("modal-card");
        card.setMinWidth(wide ? 760 : 480);
        card.setMaxWidth(wide ? 760 : 480);
        card.setMaxHeight(720);

        HBox header = new HBox();
        header.getStyleClass().add("modal-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("modal-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("×");
        close.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-border-color: rgba(255,255,255,0.10); -fx-text-fill: #71717a; -fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; -fx-background-radius: 6; -fx-border-radius: 6; -fx-padding: 0; -fx-font-size: 16;");
        close.setOnAction(e -> stage.close());
        header.getChildren().addAll(titleLabel, spacer, close);
        card.getChildren().add(header);

        body.setPadding(new Insets(24));
        body.setSpacing(0);
        body.getChildren().add(content);
        VBox.setVgrow(body, Priority.ALWAYS);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scroll.setMaxHeight(640);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        card.getChildren().add(scroll);

        StackPane overlay = new StackPane(card);
        overlay.getStyleClass().add("modal-overlay");
        overlay.setPrefSize(1200, 800);
        StackPane.setAlignment(card, Pos.CENTER);

        Scene scene = new Scene(overlay, 1200, 800);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/innov8/loyaltypos/css/app.css").toExternalForm());
        stage.setScene(scene);
    }

    public void show() { stage.show(); }
    public void close() { stage.close(); }
}

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
        card.setMaxHeight(Region.USE_PREF_SIZE);

        HBox header = new HBox();
        header.getStyleClass().add("modal-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("modal-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("×");
        close.setStyle("-fx-background-color: -overlay-mid; -fx-border-color: -overlay-card; -fx-text-fill: -muted; -fx-min-width: 28; -fx-min-height: 28; -fx-max-width: 28; -fx-max-height: 28; -fx-background-radius: 6; -fx-border-radius: 6; -fx-padding: 0; -fx-font-size: 16;");
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
        StackPane.setAlignment(card, Pos.CENTER);

        Scene scene = new Scene(overlay);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        
        if (owner != null) {
            stage.setX(owner.getX());
            stage.setY(owner.getY());
            stage.setWidth(owner.getWidth());
            stage.setHeight(owner.getHeight());
            owner.xProperty().addListener((obs, oldV, newV) -> stage.setX(newV.doubleValue()));
            owner.yProperty().addListener((obs, oldV, newV) -> stage.setY(newV.doubleValue()));
            owner.widthProperty().addListener((obs, oldV, newV) -> stage.setWidth(newV.doubleValue()));
            owner.heightProperty().addListener((obs, oldV, newV) -> stage.setHeight(newV.doubleValue()));
        } else {
            overlay.setPrefSize(1200, 800);
            stage.setWidth(1200);
            stage.setHeight(800);
        }
        scene.getStylesheets().add(getClass().getResource("/com/innov8/loyaltypos/css/app.css").toExternalForm());
        // Apply light theme CSS + class when in light mode so modals follow the toggle
        if (com.innov8.loyaltypos.App.ctx != null && "light".equals(com.innov8.loyaltypos.App.ctx.theme)) {
            scene.getStylesheets().add(getClass().getResource("/com/innov8/loyaltypos/css/light.css").toExternalForm());
            overlay.getStyleClass().add("theme-light");
        } else {
            overlay.getStyleClass().add("theme-dark");
        }
        stage.setScene(scene);
    }

    public void show() { stage.show(); }
    public void close() { stage.close(); }
}

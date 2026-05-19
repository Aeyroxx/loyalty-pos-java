package com.innov8.loyaltypos.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

/** PIN keypad component matching the React PinPad layout. */
public class PinPad extends VBox {
    private final StringBuilder pin = new StringBuilder();
    private final Circle[] dots = new Circle[6];
    private final Label errorLabel = new Label();
    private final Button enter;
    private final Consumer<String> onSubmit;
    private final int maxLen;

    public PinPad(Consumer<String> onSubmit) { this(onSubmit, 6); }

    public PinPad(Consumer<String> onSubmit, int maxLen) {
        this.onSubmit = onSubmit;
        this.maxLen = maxLen;
        setSpacing(24);
        setAlignment(Pos.CENTER);

        HBox dotRow = new HBox(10);
        dotRow.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            Circle c = new Circle(5.5);
            c.getStyleClass().add("pin-dot");
            dots[i] = c;
            dotRow.getChildren().add(c);
        }
        getChildren().add(dotRow);

        errorLabel.getStyleClass().add("text-danger");
        errorLabel.setStyle("-fx-font-size: 13;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        getChildren().add(errorLabel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER);
        String[] keys = {"1","2","3","4","5","6","7","8","9","C","0","⌫"};
        int i = 0;
        for (String k : keys) {
            Button b = new Button(k);
            b.getStyleClass().add("pin-key");
            b.setOnAction(e -> press(k));
            grid.add(b, i % 3, i / 3);
            i++;
        }
        getChildren().add(grid);

        enter = new Button("Enter");
        enter.getStyleClass().add("pin-enter");
        enter.setMaxWidth(236);
        enter.setPrefWidth(236);
        enter.setOnAction(e -> press("OK"));
        enter.setDisable(true);
        getChildren().add(enter);
    }

    public void press(String val) {
        if ("C".equals(val)) { pin.setLength(0); }
        else if ("⌫".equals(val)) { if (pin.length() > 0) pin.setLength(pin.length() - 1); }
        else if ("OK".equals(val)) {
            if (pin.length() >= 4) {
                String value = pin.toString();
                pin.setLength(0);
                onSubmit.accept(value);
            }
        } else {
            if (pin.length() >= maxLen) return;
            pin.append(val);
            if (pin.length() == maxLen) {
                String value = pin.toString();
                pin.setLength(0);
                onSubmit.accept(value);
            }
        }
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < dots.length; i++) {
            if (i < pin.length()) {
                if (!dots[i].getStyleClass().contains("filled")) dots[i].getStyleClass().add("filled");
            } else {
                dots[i].getStyleClass().remove("filled");
            }
        }
        enter.setDisable(pin.length() < 4);
    }

    public void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    public void clear() {
        pin.setLength(0);
        refresh();
    }
}

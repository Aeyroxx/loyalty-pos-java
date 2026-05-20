package com.innov8.loyaltypos.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.function.Consumer;

public class TotpDialog {
    public static class Result {
        public String reason;
        public String code;
    }

    public static void show(Window owner, String title, boolean showReason, Consumer<Result> onConfirm) {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);
        Label icon = new Label("🔐");
        icon.setStyle("-fx-font-size: 36;");
        Label tl = new Label(title.toUpperCase());
        tl.setStyle("-fx-text-fill: -ink; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 17; -fx-font-weight: 800;");
        Label sub = new Label("Enter the 6-digit code from admin's Google Authenticator");
        sub.setStyle("-fx-text-fill: -faint; -fx-font-size: 13;");
        sub.setWrapText(true);
        sub.setMaxWidth(300);
        sub.setAlignment(Pos.CENTER);
        sub.setStyle(sub.getStyle() + " -fx-text-alignment: CENTER;");
        VBox header = new VBox(6, icon, tl, sub);
        header.setAlignment(Pos.CENTER);
        content.getChildren().add(header);

        TextField reasonTf = new TextField();
        if (showReason) {
            Label rl = new Label("REASON (OPTIONAL)"); rl.getStyleClass().add("field-label");
            reasonTf.setPromptText("e.g. Wrong item entered");
            reasonTf.getStyleClass().add("text-field");
            content.getChildren().add(new VBox(6, rl, reasonTf));
        }

        Label cl = new Label("AUTH CODE"); cl.getStyleClass().add("field-label");
        TextField codeTf = new TextField();
        codeTf.setPromptText("000000");
        codeTf.setStyle("-fx-background-color: -surface; -fx-text-fill: -ink; -fx-font-family: 'IBM Plex Mono',monospace; -fx-font-size: 32; -fx-padding: 14 12; -fx-alignment: CENTER; -fx-border-color: rgba(255,255,255,0.12); -fx-background-radius: 6; -fx-border-radius: 6;");
        Label err = new Label();
        err.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12;");
        err.setVisible(false); err.setManaged(false);
        codeTf.textProperty().addListener((o, a, b) -> {
            String s = b == null ? "" : b.replaceAll("\\D", "");
            if (!s.equals(b)) codeTf.setText(s);
            err.setVisible(false); err.setManaged(false);
        });
        content.getChildren().addAll(new VBox(6, cl, codeTf), err);

        HBox btns = new HBox(10);
        Button cancel = new Button("Cancel"); cancel.getStyleClass().addAll("btn", "btn-ghost");
        cancel.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(cancel, Priority.ALWAYS);
        Button confirm = new Button("Confirm"); confirm.getStyleClass().addAll("btn", "btn-primary");
        confirm.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(confirm, Priority.ALWAYS);
        btns.getChildren().addAll(cancel, confirm);
        content.getChildren().add(btns);

        Modal modal = new Modal(owner, title, content);
        cancel.setOnAction(e -> modal.close());
        confirm.setOnAction(e -> {
            if (codeTf.getText().length() != 6) {
                err.setText("Enter the 6-digit code from Google Authenticator.");
                err.setVisible(true); err.setManaged(true);
                return;
            }
            Result r = new Result();
            r.reason = reasonTf.getText();
            r.code = codeTf.getText();
            modal.close();
            onConfirm.accept(r);
        });
        modal.show();
        codeTf.requestFocus();
    }
}

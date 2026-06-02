package com.innov8.loyaltypos.ui;

import com.innov8.loyaltypos.App;
import com.innov8.loyaltypos.model.PoAccount;
import com.innov8.loyaltypos.service.AIService;
import com.innov8.loyaltypos.service.PoService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * On-launch warning for PO accounts that expire soon. Pulls open POs whose
 * expiry_date falls within the next 7 days, shows a single modal summarizing
 * them, and (if AI is enabled) drops in a one-paragraph Gemini-generated
 * action recommendation.
 */
public final class PoExpiryNotifier {
    private PoExpiryNotifier() {}

    private static final int WARN_DAYS = 7;
    private static boolean shownThisSession = false;

    public static void checkAndShow() {
        if (shownThisSession) return;
        shownThisSession = true;
        if (App.primaryStage == null) return;
        try {
            List<PoAccount> expiring = new ArrayList<>();
            LocalDate today = LocalDate.now();
            LocalDate cutoff = today.plusDays(WARN_DAYS);
            for (PoAccount a : PoService.list()) {
                if (!"open".equals(a.status) || a.expiryDate == null || a.expiryDate.isEmpty()) continue;
                try {
                    LocalDate exp = LocalDate.parse(a.expiryDate.substring(0, Math.min(10, a.expiryDate.length())));
                    if (!exp.isBefore(today) && !exp.isAfter(cutoff)) expiring.add(a);
                } catch (Exception ignore) {}
            }
            if (expiring.isEmpty()) return;
            buildAndShow(expiring);
        } catch (Exception ignore) {}
    }

    private static void buildAndShow(List<PoAccount> expiring) {
        VBox content = new VBox(14);

        Label header = new Label(expiring.size() + " PO account"
                + (expiring.size() == 1 ? "" : "s") + " expiring within " + WARN_DAYS + " days");
        header.setStyle("-fx-text-fill: -accent; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 18; -fx-font-weight: 800;");
        content.getChildren().add(header);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
        for (PoAccount a : expiring) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label("● " + (a.customerName == null ? "PO #" + a.id : a.customerName));
            name.setStyle("-fx-text-fill: -ink; -fx-font-weight: 600;");
            String exp = a.expiryDate == null ? "" : a.expiryDate.substring(0, Math.min(10, a.expiryDate.length()));
            String date;
            try { date = LocalDate.parse(exp).format(fmt); } catch (Exception e) { date = exp; }
            Label info = new Label("expires " + date + " · balance " + App.ctx.getCurrencySymbol() + String.format("%,.2f", a.balanceUsed));
            info.setStyle("-fx-text-fill: -muted; -fx-font-size: 12;");
            row.getChildren().addAll(name, info);
            content.getChildren().add(row);
        }

        // AI recommendation (best-effort; silent if disabled)
        if (AIService.isReady()) {
            Label aiHeader = new Label("✨ AI RECOMMENDATION");
            aiHeader.setStyle("-fx-text-fill: -accent; -fx-font-family: 'Barlow Condensed','Arial Narrow',sans-serif; -fx-font-size: 11; -fx-font-weight: 700; -fx-padding: 8 0 0 0;");
            Label aiBody = new Label("Asking Gemini...");
            aiBody.setWrapText(true);
            aiBody.setMaxWidth(520);
            aiBody.setStyle("-fx-text-fill: -ink-soft; -fx-font-size: 12; -fx-line-spacing: 3;");
            content.getChildren().addAll(aiHeader, aiBody);

            StringBuilder prompt = new StringBuilder();
            prompt.append("You are advising a small construction-aggregate retailer. The following PO accounts expire within ").append(WARN_DAYS).append(" days. Write ONE short paragraph (max 60 words) on the best course of action — typically collection priority.\n");
            for (PoAccount a : expiring) {
                prompt.append("- ").append(a.customerName == null ? ("PO #" + a.id) : a.customerName)
                      .append(" expires ").append(a.expiryDate)
                      .append(", balance ").append(App.ctx.getCurrencySymbol()).append(String.format("%,.2f", a.balanceUsed))
                      .append("\n");
            }
            new Thread(() -> {
                try {
                    String out = AIService.generate(prompt.toString());
                    javafx.application.Platform.runLater(() -> aiBody.setText(out));
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() -> aiBody.setText("(AI unavailable: " + e.getMessage() + ")"));
                }
            }, "po-expiry-ai").start();
        }

        HBox foot = new HBox();
        foot.setAlignment(Pos.CENTER_RIGHT);
        Region s = new Region(); HBox.setHgrow(s, javafx.scene.layout.Priority.ALWAYS);
        Button ok = new Button("Got it");
        ok.getStyleClass().addAll("btn", "btn-primary");
        ok.setDefaultButton(true);
        foot.getChildren().addAll(s, ok);
        foot.setPadding(new Insets(8, 0, 0, 0));
        content.getChildren().add(foot);

        Modal modal = new Modal(App.primaryStage, "PO Expiry Warning", content);
        ok.setOnAction(e -> modal.close());
        modal.show();
    }
}

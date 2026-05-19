package com.innov8.loyaltypos.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.innov8.loyaltypos.App;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin Google Gemini client. Uses the v1beta REST API with the user's API key
 * (set in Settings → Google Gemini AI). All UI surfaces fall back gracefully when
 * disabled or when the key is missing.
 *
 * Default model: gemini-3.1-flash-lite (cheap + fast, good enough for summaries
 * and category suggestions).
 */
public final class AIService {
    private AIService() {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    public static boolean isEnabled() {
        Object enabled = App.ctx.settings.get("ai_enabled");
        if (enabled == null) return false;
        if (enabled instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    public static String apiKey() {
        Object k = App.ctx.settings.get("gemini_api_key");
        return k == null ? "" : String.valueOf(k).trim();
    }

    public static String model() {
        Object m = App.ctx.settings.get("gemini_model");
        String s = m == null ? "" : String.valueOf(m).trim();
        return s.isEmpty() ? "gemini-3.1-flash-lite" : s;
    }

    public static boolean isReady() {
        return isEnabled() && !apiKey().isEmpty();
    }

    /**
     * Send a prompt and return the model's plain-text response. Throws on
     * network/API failure with a user-friendly message.
     */
    public static String generate(String prompt) throws Exception {
        if (!isEnabled()) throw new Exception("AI features are disabled. Enable them in Settings → Google Gemini AI.");
        String key = apiKey();
        if (key.isEmpty()) throw new Exception("Gemini API key is not configured. Add it in Settings → Google Gemini AI.");

        String body = GSON.toJson(Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{ Map.of("text", prompt) })
                },
                "generationConfig", Map.of(
                        "temperature", 0.4,
                        "maxOutputTokens", 1024
                )
        ));

        URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                + java.net.URLEncoder.encode(model(), java.nio.charset.StandardCharsets.UTF_8)
                + ":generateContent?key="
                + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new Exception("Gemini API error (" + resp.statusCode() + "): " + truncate(resp.body(), 280));
        }
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        try {
            return json.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString().trim();
        } catch (Exception e) {
            throw new Exception("Unexpected Gemini response shape: " + truncate(resp.body(), 280));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience helpers — used by UI surfaces.
    // ──────────────────────────────────────────────────────────────────────

    /** Suggest a one-line product description for a gravel/sand SKU given its code + grade. */
    public static String suggestProductDescription(String itemCode, String name, String unit) throws Exception {
        return generate("""
                You are helping a Philippine construction-aggregate retailer write a short, factual,
                ONE-SENTENCE product description (<= 100 chars) suitable for a POS line item.
                Item code: %s
                Product name: %s
                Unit: %s
                Reply with just the description text — no quotes, no labels.
                """.formatted(itemCode, name, unit));
    }

    /** Categorize a free-text expense into one of the canonical categories. */
    public static String suggestExpenseCategory(String description) throws Exception {
        return generate("""
                Classify this expense into ONE category from this list and reply with only that word:
                General, Supplies, Utilities, Salary, Fuel, Maintenance, Rent, Food, Other
                Expense: "%s"
                """.formatted(description == null ? "" : description.replace("\"", "'")));
    }

    /** Natural-language sales summary from a structured snapshot. */
    public static String summarizeSales(String snapshotMarkdown) throws Exception {
        return generate("""
                You are a business analyst summarizing daily sales for a gravel & sand retail POS.
                Write a brief (3-5 bullet) summary highlighting top revenue, busiest hours, anomalies,
                and one suggestion. Use the data below.

                %s
                """.formatted(snapshotMarkdown));
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}

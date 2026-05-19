package com.innov8.loyaltypos;

import com.innov8.loyaltypos.model.User;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

public class AppContext {
    public User currentUser;
    public Map<String, Object> settings = new HashMap<>();
    public String theme;
    public boolean devMode;

    public AppContext() {
        Preferences prefs = Preferences.userNodeForPackage(AppContext.class);
        this.theme = prefs.get("pos_theme", "dark");
    }

    public void toggleTheme() {
        this.theme = "dark".equals(theme) ? "light" : "dark";
        Preferences.userNodeForPackage(AppContext.class).put("pos_theme", theme);
    }

    public String getCurrencySymbol() {
        Object sym = settings.get("currency_symbol");
        return sym == null || sym.toString().isEmpty() ? "₱" : sym.toString();
    }
}

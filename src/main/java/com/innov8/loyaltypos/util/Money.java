package com.innov8.loyaltypos.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Money {
    private static final DecimalFormat DF;
    static {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DF = new DecimalFormat("#,##0.00", sym);
    }
    private Money() {}
    public static String fmt(double n) { return DF.format(n); }
    public static String fmt(double n, String currencySymbol) {
        return (currencySymbol == null ? "₱" : currencySymbol) + DF.format(n);
    }
}

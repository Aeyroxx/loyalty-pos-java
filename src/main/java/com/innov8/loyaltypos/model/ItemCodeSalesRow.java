package com.innov8.loyaltypos.model;

/**
 * One row in the "Sales by Item Code" report. Produced by joining
 * transaction_items → transactions and filtering out voided sales, so it
 * reflects the live state of the masterlist → transaction → report pipeline.
 */
public class ItemCodeSalesRow {
    public String itemCode;
    public String productName;
    public String description;
    public String unit;
    public double quantitySold;
    public double revenue;
    public int transactionCount;
    /** First sale date within the range, formatted yyyy-MM-dd. */
    public String firstSale;
    /** Last sale date within the range, formatted yyyy-MM-dd. */
    public String lastSale;
}

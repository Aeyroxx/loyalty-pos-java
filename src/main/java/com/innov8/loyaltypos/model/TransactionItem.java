package com.innov8.loyaltypos.model;

public class TransactionItem {
    public int id;
    public int transactionId;
    public Integer productId;
    /** Snapshot of the product's item_code at sale time (per professor revision). */
    public String itemCode;
    public String productName;
    public String unit;
    public double quantity;
    public double unitPrice;
    public Double customPrice;
    public double amount;
}

package com.innov8.loyaltypos.model;

public class Product {
    public int id;
    /** Unique item code (per professor revision) — differentiates Gravel 1 vs Gravel 2 etc. */
    public String itemCode = "";
    public String name = "";
    /** Free-form description / grade / texture for granularity. */
    public String description = "";
    public String unit = "m³";
    public double pricePerUnit;
    public double stockQty;
    /** Optional foreign key to suppliers.id; null when unsourced. */
    public Integer supplierId;
    /** Convenience: supplier name joined from the suppliers table on list/get. */
    public String supplierName;
    public boolean isActive = true;
}

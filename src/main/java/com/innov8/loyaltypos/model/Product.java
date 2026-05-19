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
    public boolean isActive = true;
}

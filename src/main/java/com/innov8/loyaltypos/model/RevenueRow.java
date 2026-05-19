package com.innov8.loyaltypos.model;

public class RevenueRow {
    public String day;
    public double total;
    public double cash;
    public double gcash;
    public double maya;
    public double poAmount;
    public double deliveryTotal;

    public RevenueRow() {}
    public RevenueRow(String day) { this.day = day; }
}

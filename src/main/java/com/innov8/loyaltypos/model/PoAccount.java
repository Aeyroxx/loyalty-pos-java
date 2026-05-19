package com.innov8.loyaltypos.model;

public class PoAccount {
    public int id;
    public int customerId;
    public String customerName;
    public String referenceNo = "";
    public String issuedDate;
    public String expiryDate;
    public double creditLimit;
    public double balanceUsed;
    public String status = "open";

    public double available() { return creditLimit - balanceUsed; }
}

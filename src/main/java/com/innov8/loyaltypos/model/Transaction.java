package com.innov8.loyaltypos.model;

import java.util.ArrayList;
import java.util.List;

public class Transaction {
    public int id;
    public String invoiceNo;
    public String date;
    public Integer customerId;
    public int cashierId;
    public String plateNo;
    public double deliveryCharge;
    public double discount;
    public double subtotal;
    public double totalAmount;
    public String paymentStatus = "paid";
    public String customerName;
    public String customerTin;
    public String customerAddress;
    public String cashierName;
    public List<TransactionItem> items = new ArrayList<>();
    public List<Payment> payments = new ArrayList<>();
}

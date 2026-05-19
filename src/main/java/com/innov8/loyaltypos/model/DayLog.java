package com.innov8.loyaltypos.model;

import java.util.List;

public class DayLog {
    public String date;
    public List<Transaction> transactions;
    public List<VoidLogEntry> voidLog;
}

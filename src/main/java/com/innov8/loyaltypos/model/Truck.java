package com.innov8.loyaltypos.model;

import java.util.ArrayList;
import java.util.List;

public class Truck {
    public int id;
    public String plateNo;
    public Double defaultPrice;
    public String updatedAt;
    public List<TruckPrice> prices = new ArrayList<>();
}

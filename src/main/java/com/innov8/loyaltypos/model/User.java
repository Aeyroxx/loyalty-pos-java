package com.innov8.loyaltypos.model;

public class User {
    public int id;
    public String name;
    public String role;
    public boolean isActive = true;

    public User() {}
    public User(int id, String name, String role) {
        this.id = id; this.name = name; this.role = role;
    }
    public boolean isAdmin() { return "admin".equals(role); }
}

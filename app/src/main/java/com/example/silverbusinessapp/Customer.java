package com.example.silverbusinessapp;

import java.io.Serializable;

public class Customer implements Serializable {
    private String id;
    private String name;
    private String phone;
    private String date;
    private String address;
    private int transactionCount;

    // Default constructor required for Firebase
    public Customer() {
    }

    // Constructor with all fields
    public Customer(String id, String name, String phone, String date, String address, int transactionCount) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.date = date;
        this.address = address;
        this.transactionCount = transactionCount;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }
}
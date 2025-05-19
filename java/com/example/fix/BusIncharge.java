package com.example.fix;

import com.google.gson.annotations.SerializedName;

public class BusIncharge {
    @SerializedName("user_id")
    private int userId;

    @SerializedName("first_name")
    private String firstName;

    @SerializedName("last_name")
    private String lastName;

    @SerializedName("email")
    private String email;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("role")
    private String role;

    // Add other fields based on your API response

    // Constructor
    public BusIncharge(int userId, String firstName, String lastName, String email, String phoneNumber) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    // Get full name
    public String getName() {
        return firstName + " " + lastName;
    }

    // Getters and setters
    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
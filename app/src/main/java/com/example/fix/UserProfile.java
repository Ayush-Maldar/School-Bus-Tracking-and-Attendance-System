package com.example.fix;

import com.google.gson.annotations.SerializedName;

// Class to represent the user profile data received from /api/user/me
public class UserProfile {

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

    @SerializedName("created_at")
    private String createdAt; // Keep as String or use Date/Timestamp with Gson config

    @SerializedName("last_login")
    private String lastLogin; // Keep as String or use Date/Timestamp with Gson config

    // Getters for all fields (required for accessing the data)

    public int getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRole() {
        return role;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getLastLogin() {
        return lastLogin;
    }

    // Optional: Getter for full name
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}
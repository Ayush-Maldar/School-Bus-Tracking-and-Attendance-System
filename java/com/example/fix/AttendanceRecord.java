package com.example.fix;

import com.google.gson.annotations.SerializedName;
public class AttendanceRecord {
    @SerializedName("date") private String date;
    @SerializedName("status") private String status;
    @SerializedName("timestamp") private String timestamp; // Optional

    // Getters
    public String getDate() { return date; }
    public String getStatus() { return status; }
    public String getTimestamp() { return timestamp; }

    // Optional: Add setters or a constructor if needed elsewhere
}


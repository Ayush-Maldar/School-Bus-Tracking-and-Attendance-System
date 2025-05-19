package com.example.fix;

import com.google.gson.annotations.SerializedName;
import java.util.List; // Import List

// Data model for the API response containing attendance lists
public class AttendanceResponse {
    @SerializedName("weeklyAttendance")
    private List<AttendanceRecord> weeklyAttendance; // Use the separate AttendanceRecord class

    @SerializedName("monthlyAttendance")
    private List<AttendanceRecord> monthlyAttendance; // Use the separate AttendanceRecord class

    // Getters
    public List<AttendanceRecord> getWeeklyAttendance() { return weeklyAttendance; }
    public List<AttendanceRecord> getMonthlyAttendance() { return monthlyAttendance; }

    // Optional: Add setters or a constructor if needed
}

package com.example.fix; // Ensure this package name is correct

import com.google.gson.annotations.SerializedName;

// This class maps to the SINGLE JSON object returned by /api/bus/:busId/staff
public class BusStaffDetailsResponse {

    @SerializedName("bus_id") // Assuming bus_id is also returned, adjust if not
    private Integer busId;

    @SerializedName("driver_staff_id")
    private Integer driverStaffId; // Use Integer for potential nulls

    @SerializedName("driver_first_name")
    private String driverFirstName;

    @SerializedName("driver_last_name")
    private String driverLastName;

    @SerializedName("driver_phone_number")
    private String driverPhoneNumber;

    @SerializedName("attendant_staff_id")
    private Integer attendantStaffId;

    @SerializedName("attendant_first_name")
    private String attendantFirstName;

    @SerializedName("attendant_last_name")
    private String attendantLastName;

    @SerializedName("attendant_phone_number")
    private String attendantPhoneNumber;

    // --- Add Getters for all fields ---
    // Example:
    public Integer getBusId() { return busId; }
    public Integer getDriverStaffId() { return driverStaffId; }
    public String getDriverFirstName() { return driverFirstName; }
    public String getDriverLastName() { return driverLastName; }
    public String getDriverPhoneNumber() { return driverPhoneNumber; }
    public Integer getAttendantStaffId() { return attendantStaffId; }
    public String getAttendantFirstName() { return attendantFirstName; }
    public String getAttendantLastName() { return attendantLastName; }
    public String getAttendantPhoneNumber() { return attendantPhoneNumber; }

    // Optional: Helper methods to get full names
    public String getDriverFullName() {
        String first = driverFirstName != null ? driverFirstName : "";
        String last = driverLastName != null ? driverLastName : "";
        return (first + " " + last).trim();
    }

    public String getAttendantFullName() {
        String first = attendantFirstName != null ? attendantFirstName : "";
        String last = attendantLastName != null ? attendantLastName : "";
        return (first + " " + last).trim();
    }
}
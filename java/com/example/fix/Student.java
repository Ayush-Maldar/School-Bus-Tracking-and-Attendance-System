package com.example.fix;

import com.google.gson.annotations.SerializedName; // Import SerializedName

public class Student {

    // --- REVERTED: Changed id type back to String ---
    @SerializedName("id") // Match the field name from your API response (e.g., "id" or "student_id")
    private String id; // Changed back to String
    // --- END REVERTED ---

    @SerializedName("name")
    private String name;

    @SerializedName("rollNo")
    private String rollNo;

    @SerializedName("class")
    private String className;

    @SerializedName("busRoute")
    private String busRoute;

    @SerializedName("parentContact")
    private String parentContact;

    @SerializedName("qrCodeData")
    private String qrCodeData;

    @SerializedName("attendanceStatus")
    private String attendanceStatus;

    @SerializedName("bus_id")
    private Integer busId;


    // --- REVERTED: Updated constructor to accept String for id ---
    public Student(String id, String name, String rollNo, String className, String busRoute, String parentContact, String qrCodeData, String attendanceStatus, Integer busId) {
        this.id = id;
        this.name = name;
        this.rollNo = rollNo;
        this.className = className;
        this.busRoute = busRoute;
        this.parentContact = parentContact;
        this.qrCodeData = qrCodeData;
        this.attendanceStatus = attendanceStatus;
        this.busId = busId;
    }
    // --- END REVERTED ---

    // Default constructor
    public Student() {
    }


    // --- REVERTED: Updated getter for id ---
    public String getId() { // Returns String again
        return id;
    }
    // --- END REVERTED ---

    public String getName() {
        return name;
    }

    public String getRollNo() {
        return rollNo;
    }

    public String getClassName() {
        return className;
    }

    public String getBusRoute() {
        return busRoute;
    }

    public String getParentContact() {
        return parentContact;
    }

    public String getQrCodeData() {
        return qrCodeData;
    }

    public String getAttendanceStatus() {
        return attendanceStatus;
    }

    public Integer getBusId() {
        return busId;
    }

    // --- Optional: Add Setters if needed ---
    // public void setId(String id) { this.id = id; }


    // --- REVERTED: Updated toString() method ---
    @Override
    public String toString() {
        return "Student{" +
                "id='" + id + '\'' + // Changed format back for String
                ", name='" + name + '\'' +
                ", rollNo='" + rollNo + '\'' +
                ", className='" + className + '\'' +
                ", busRoute='" + busRoute + '\'' +
                ", parentContact='" + parentContact + '\'' +
                ", attendanceStatus='" + attendanceStatus + '\'' +
                ", busId=" + busId +
                '}';
    }
    // --- END REVERTED ---
}

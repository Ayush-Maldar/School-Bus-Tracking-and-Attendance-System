package com.example.fix;

import com.google.gson.annotations.SerializedName;

public class BusStaff {

    // Fields potentially returned by /api/bus/:busId/staff (used in Parent_activity)
    @SerializedName("id") // Or "staff_id" if server sends that in the array
    private int id;

    @SerializedName("name") // If server sends full name directly in parent endpoint array
    private String name;

    // --- OR --- If server sends first/last name separately in parent endpoint array
    // @SerializedName("first_name")
    // private String firstName;
    // @SerializedName("last_name")
    // private String lastName;
    // --- End OR ---

    @SerializedName("role")
    private String role;

    @SerializedName("phone_number")
    private String phoneNumber;


    // --- Fields returned by /api/admin/bus_staff/all (used in School_authority) ---
    // Add these fields if they are not already covered above, making sure
    // @SerializedName matches the JSON keys from the ADMIN endpoint.

    // staff_id might be the same key as "id" above, or different. Check your admin endpoint JSON.
    // If it's the same key, you only need one field (@SerializedName("id") OR @SerializedName("staff_id"))
    @SerializedName("staff_id") // Assuming admin endpoint uses "staff_id"
    private int staffId; // Use a different variable name if "id" is already used for parent endpoint

    // If admin endpoint sends first/last name separately:
    @SerializedName("first_name")
    private String firstName; // May duplicate field above if parent endpoint also sends this key
    @SerializedName("last_name")
    private String lastName;  // May duplicate field above if parent endpoint also sends this key

    @SerializedName("email") // Assuming admin endpoint returns email
    private String email;

    @SerializedName("user_id") // Assuming admin endpoint returns user_id
    private int userId;

    // *** ADDED Fields for Bus Info (returned by admin endpoint) ***
    @SerializedName("bus_id")
    private Integer busId; // Use Integer to handle potential null if staff isn't assigned

    @SerializedName("bus_plate")
    private String busPlate; // Use String, can be null
    // *** END ADDED Fields ***


    // --- Getters ---
    public int getId() { return id; } // For parent endpoint usage
    public int getStaffId() { return staffId; } // For admin endpoint usage
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getRole() { return role; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getEmail() { return email; }
    public int getUserId() { return userId; }

    // *** ADDED Getters for Bus Info ***
    public Integer getBusId() { return busId; }
    public String getBusPlate() { return busPlate; }
    // *** END ADDED Getters ***

    // Helper to get full name (tries first/last first, then name field)
    public String getName() {
        String first = firstName != null ? firstName : "";
        String last = lastName != null ? lastName : "";
        String fullName = (first + " " + last).trim();

        if (!fullName.isEmpty()) {
            return fullName;
        } else if (name != null && !name.trim().isEmpty()) {
            // Fallback if only 'name' field is available (e.g., from parent endpoint)
            return name.trim();
        } else {
            return "Unknown Name"; // Default if no name info found
        }
    }
}
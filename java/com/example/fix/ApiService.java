package com.example.fix;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
// --- Import the inner class directly ---
import com.example.fix.AttendanceResponse; // <<< ADD THIS IMPORT
// --- Import other needed classes ---
import com.example.fix.School_authority.BusLocationWithId;
public interface ApiService {

    // --- Existing methods ---
    // ... (getUserProfile, updateUserContact, getStudents, getDashboardData, etc.)

    @GET("user/me")
    Call<UserProfile> getUserProfile(@Header("Authorization") String token);

    @PUT("users/update-contact/{userId}")
    Call<ResponseBody> updateUserContact(
            @Header("Authorization") String token,
            @Path("userId") String userId,
            @Body EditProfileRequest body);

    @GET("students")
    Call<List<Student>> getStudents(@Header("Authorization") String token);

    @GET("dashboard")
    Call<DashboardResponse> getDashboardData(@Header("Authorization") String token);

    @GET("bus/{busId}/staff")
    Call<List<BusStaff>> getBusStaff(@Header("Authorization") String token, @Path("busId") int busId);

    @POST("gps_logs")
    Call<ResponseBody> logGpsCoordinates(
            @Header("Authorization") String authorization,
            @Body GpsLogRequest body);

    @GET("bus/{busId}/location")
    Call<BusLocation> getBusLocation(
            @Header("Authorization") String token,
            @Path("busId") int busId);

    @POST("sos")
    Call<ResponseBody> triggerSos(
            @Header("Authorization") String authorization,
            @Body SosRequest body);

    @GET("admin/students/all")
    Call<List<Student>> getAllStudents(@Header("Authorization") String token);

    @GET("admin/buses/all")
    Call<List<ApiService.Bus>> getAllBuses(@Header("Authorization") String token);

    @GET("admin/bus_staff/all")
    Call<List<BusStaff>> getAllBusStaff(@Header("Authorization") String token);

    @POST("scan")
    Call<ScanResponse> sendScanData(@Header("Authorization") String token, @Body ScanRequest request);

    // *** ADDED: Endpoint for School Authority Map ***
    @GET("admin/buses/locations") // Matches the new backend route
    Call<List<BusLocationWithId>> getAllBusLocations(@Header("Authorization") String token);
    // *** END ADDED Endpoint ***
    @GET("bus-incharge/my-bus") // Matches the backend endpoint path
    Call<BusIdResponse> getMyBusId(@Header("Authorization") String token);

    @GET("students/{studentId}/attendance") // Ensure path starts with /api if needed
    Call<AttendanceResponse> getStudentAttendance( // <<< USES IMPORTED INNER CLASS
                                                   @Header("Authorization") String token,
                                                   @Path("studentId") int studentId
    );

    // --- Inner classes for Request/Response bodies (Keep existing) ---
    class SosRequest { /* ... */
        @SerializedName("bus_id") int busId;
        @SerializedName("message") String message;
        @SerializedName("severity") String severity;
        public SosRequest(int busId, String message, String severity) {
            this.busId = busId;
            this.message = message;
            this.severity = severity;
        }
    }
    class GpsLogRequest { /* ... */
        public int bus_id;
        public double latitude;
        public double longitude;
    }
    class BusLocation { /* ... */
        @SerializedName("latitude") public double latitude;
        @SerializedName("longitude") public double longitude;
        @SerializedName("timestamp") public String timestamp;
    }
    class EditProfileRequest { /* ... */
        @SerializedName("email") String email;
        @SerializedName("phone_number") String phoneNumber;
        public EditProfileRequest(String email, String phoneNumber) {
            this.email = email;
            this.phoneNumber = phoneNumber;
        }
    }
    class ScanRequest { /* ... */
        @SerializedName("roll_no") private String roll_no;
        public ScanRequest(String roll_no) { this.roll_no = roll_no; }
    }
    class ScanResponse { /* ... */
        @SerializedName("message") private String message;
        @SerializedName("status") private String status;
        @SerializedName("success") private boolean success;
        @SerializedName("error") private String error;
        public String getMessage() { return message; }
        public String getStatus() { return status; }
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    // Keep Bus model definition
    class Bus { /* ... */
        @SerializedName("bus_id") public int busId;
        @SerializedName("bus_plate") public String busPlate;
        @SerializedName("driver_first_name") public String driverFirstName;
        @SerializedName("driver_last_name") public String driverLastName;
        @SerializedName("attendant_first_name") public String attendantFirstName;
        @SerializedName("attendant_last_name") public String attendantLastName;
        public Bus(int busId, String busPlate, String dfn, String dln, String afn, String aln) { /* constructor */ }
        public String getDriverFullName() { return (driverFirstName != null ? driverFirstName : "") + " " + (driverLastName != null ? driverLastName : ""); }
        public String getAttendantFullName() { return (attendantFirstName != null ? attendantFirstName : "") + " " + (attendantLastName != null ? attendantLastName : ""); }
    }
    class BusIdResponse {
        @SerializedName("busId") // Matches the key returned by the backend
        private Integer busId; // Use Integer to handle potential null responses gracefully

        // Getter needed by Retrofit/Gson
        public Integer getBusId() {
            return busId;
        }
    }


} // End of ApiService interface

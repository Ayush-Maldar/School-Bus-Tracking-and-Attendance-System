package com.example.fix;

// --- Ensure these imports are present ---
import android.content.Intent; // <<< ADDED/ENSURE IMPORT
import android.content.SharedPreferences; // Keep if used elsewhere
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView; // <<< ADDED/ENSURE IMPORT
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

// --- Keep other existing imports ---
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList; // <<< ADDED/ENSURE IMPORT
import java.util.List; // <<< ADDED/ENSURE IMPORT

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Parent_activity extends AppCompatActivity {
    private static final String TAG = "ParentActivity";
    private MapView mapView;
    private Marker busMarker;
    private ListView studentList;
    private ListView busInchargeList;
    private ApiService apiService;
    // --- Ensure this variable exists ---
    private List<Student> studentsData; // To hold the list of students for the listener
    // ---
    private Handler locationHandler = new Handler(Looper.getMainLooper());
    private List<BusStaff> busStaffData;
    private Runnable locationRunnable;
    private static final long UPDATE_INTERVAL_MS = 500; // 15 seconds
    private int busIdToTrack = -1; // Initialize with invalid ID
    private String authToken = ""; // Store the raw token here
    private TokenManager tokenManager; // Use TokenManager

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_parent); // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize TokenManager and ApiService
        tokenManager = new TokenManager(this); // [cite: SchoolWay_app/app/java/com/example/fix/TokenManager.java]
        apiService = Retrofitclient.getClient().create(ApiService.class); // [cite: SchoolWay_app/app/java/com/example/fix/Retrofitclient.java, SchoolWay_app/app/java/com/example/fix/ApiService.java]

        // Initialize UI components
        studentList = findViewById(R.id.students); // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
        busInchargeList = findViewById(R.id.bus_incharge); // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
        ImageButton profile = findViewById(R.id.profile); // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
        mapView = findViewById(R.id.map); // Initialize MapView [cite: SchoolWay_app/app/res/layout/activity_parent.xml]

        busStaffData = new ArrayList<>();

        // Get authentication token
        String token = tokenManager.getToken(); // Use TokenManager [cite: SchoolWay_app/app/java/com/example/fix/TokenManager.java]

        if (token == null || token.isEmpty()) { // Check for null as well
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Parent_activity.this, MainActivity.class); // [cite: SchoolWay_app/app/java/com/example/fix/MainActivity.java]
            startActivity(intent);
            finish();
            return;
        }
        // --- Store the raw token without "Bearer " prefix if needed elsewhere ---
        // --- but add "Bearer " when making API calls ---
        this.authToken = token;
        Log.d(TAG, "Stored auth token in member variable.");

        // Fetch initial data (this will populate studentsData)
        fetchDashboardData(this.authToken); //

        // Set up map
        initializeMap(); //

        // Set up click listeners
        profile.setOnClickListener(view -> {
            Intent intent = new Intent(Parent_activity.this, Info.class); // [cite: SchoolWay_app/app/java/com/example/fix/Info.java]
            startActivity(intent);
        });

        // --- MODIFICATION: Added ItemClickListener for studentList ---
        // Student list item click listener
        studentList.setOnItemClickListener((parent, view, position, id) -> { // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
            if (studentsData != null && position >= 0 && position < studentsData.size()) {
                Student clickedStudent = studentsData.get(position); // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                Log.d(TAG, "Student clicked: " + clickedStudent.getName() + " (ID: " + clickedStudent.getId() + ")");

                // --- Action 1: Start Attendance Details Activity ---
                Intent intent = new Intent(Parent_activity.this, AttendanceDetailsActivity.class);
                intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_ID, clickedStudent.getId()); // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_NAME, clickedStudent.getName()); // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                startActivity(intent);
                // --- End Action 1 ---

                // --- Action 2: Update Bus Tracking/Staff Info (Based on clicked student) ---
                Integer clickedBusIdInteger = clickedStudent.getBusId(); // Use Integer // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                int clickedBusId = (clickedBusIdInteger != null) ? clickedBusIdInteger : -1; // Handle potential null

                Log.d(TAG, "Updating tracking/staff view for Bus ID: " + clickedBusId);
                if (clickedBusId > 0) {
                    // Fetch staff details for the specifically clicked student's bus
                    fetchBusStaffDetails(this.authToken, clickedBusId); // Pass raw token
                    // Stop tracking previous bus (if any) and start tracking new one
                    stopLocationUpdates(); //
                    startLocationUpdates(this.authToken, clickedBusId); // Pass raw token
                } else {
                    Log.w(TAG, "Clicked student has invalid or null bus ID: " + clickedBusId);
                    Toast.makeText(this, "Student not assigned to a valid bus.", Toast.LENGTH_SHORT).show();
                    if (busInchargeList != null) busInchargeList.setAdapter(null); // Clear staff list
                    stopLocationUpdates(); //
                }
                // --- End Action 2 ---

            } else {
                Log.w(TAG,"Invalid position clicked or student data is null/empty.");
                Toast.makeText(Parent_activity.this, "Error selecting student.", Toast.LENGTH_SHORT).show(); // Added feedback
            }
        });

        busInchargeList.setOnItemClickListener((parent, view, position, id) -> {
            if (busStaffData != null && !busStaffData.isEmpty() && position >= 0 && position < busStaffData.size()) {
                // Get the actual BusStaff object that was clicked
                BusStaff selectedStaff = busStaffData.get(position); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]

                // Ensure we have a phone number before proceeding
                String phoneNumber = selectedStaff.getPhoneNumber(); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                if (phoneNumber == null || phoneNumber.trim().isEmpty() || phoneNumber.equalsIgnoreCase("N/A")) {
                    Toast.makeText(Parent_activity.this, "No phone number available for " + selectedStaff.getName(), Toast.LENGTH_SHORT).show();
                    return; // Don't open details if no phone number
                }

                Log.d(TAG, "Bus Staff clicked: " + selectedStaff.getName() + " - Phone: " + phoneNumber);

                // Create Intent to start DriverInfoActivity
                Intent intent = new Intent(Parent_activity.this, DriverInfoActivity.class); // Create DriverInfoActivity.java next

                // Pass necessary data as extras
                intent.putExtra(DriverInfoActivity.EXTRA_DRIVER_NAME, selectedStaff.getName()); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                intent.putExtra(DriverInfoActivity.EXTRA_DRIVER_PHONE, phoneNumber); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                // Pass bus plate if available and needed by DriverInfoActivity
                intent.putExtra(DriverInfoActivity.EXTRA_BUS_PLATE, selectedStaff.getBusPlate() != null ? selectedStaff.getBusPlate() : "N/A"); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]


                startActivity(intent);

            } else {
                Log.w(TAG, "Invalid click on busInchargeList or busStaffData is empty/null.");
                // Optional: Show a toast if the data isn't ready or the click is invalid
                // Toast.makeText(Parent_activity.this, "Error selecting staff.", Toast.LENGTH_SHORT).show();
            }
        });



        // --- END MODIFICATION ---
    }

    // Method to initialize the map
    private void initializeMap() {
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        if (mapView == null) { Log.e(TAG, "MapView is null in initializeMap!"); return; }
        mapView.setTileSource(TileSourceFactory.MAPNIK); // Or use DEFAULT_TILE_SOURCE
//        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        // Set a reasonable initial center and zoom
        GeoPoint startPoint = new GeoPoint(15.593722, 73.814194); // Example: Goa
        mapView.getController().setZoom(16.0); // Slightly zoomed out
        mapView.getController().setCenter(startPoint);

        // Initialize the marker (only create it once)
        if (busMarker == null) {
            busMarker = new Marker(mapView);
            busMarker.setPosition(startPoint); // Start at default location
            busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            busMarker.setTitle("Bus Location");
            mapView.getOverlays().add(busMarker);
        } else {
            busMarker.setPosition(startPoint); // Update position if re-initializing
        }

        mapView.invalidate();
        Log.d(TAG, "Map initialized.");
    }


    private void startLocationUpdates(final String token, final int busId) {
        if (busId <= 0 || token == null || token.isEmpty()) {
            Log.w(TAG, "Cannot start location updates for invalid bus ID or missing token. BusID: " + busId);
            stopLocationUpdates(); // Make sure any previous updates are stopped
            return;
        }
        // If already tracking this bus, don't restart unnecessarily
        if (locationRunnable != null && this.busIdToTrack == busId) {
            Log.d(TAG,"Location updates already running for bus ID: " + busId);
            // Optionally force a fetch immediately if needed:
            // fetchLatestBusLocation(token, busId);
            return;
        }

        stopLocationUpdates(); // Stop any previous updates for a different bus

        this.busIdToTrack = busId;
        Log.d(TAG, "Starting location updates for bus ID: " + busIdToTrack);

        locationRunnable = new Runnable() {
            @Override
            public void run() {
                // Double check token and bus ID validity before fetching
                // Use the member variable authToken directly here
                if (!Parent_activity.this.authToken.isEmpty() && busIdToTrack > 0) {
                    fetchLatestBusLocation(Parent_activity.this.authToken, busIdToTrack); // Pass raw token
                    locationHandler.postDelayed(this, UPDATE_INTERVAL_MS); // Schedule next run
                } else {
                    Log.w(TAG, "Stopping updates in runnable: token or busId invalid. BusID: " + busIdToTrack);
                    stopLocationUpdates(); // Ensure updates stop if state becomes invalid
                }
            }
        };
        locationHandler.post(locationRunnable); // Start the first run
    }

    private void stopLocationUpdates() {
        if (locationRunnable != null) {
            Log.d(TAG, "Stopping location updates for bus ID: " + busIdToTrack);
            locationHandler.removeCallbacks(locationRunnable);
            // Reset tracked bus ID when updates stop
            // busIdToTrack = -1; // Keep tracking conceptually unless explicitly changed
        }
    }

    private void fetchLatestBusLocation(String token, int busId) {
        Log.d(TAG, "Fetching latest location for bus ID: " + busId);
        if (apiService == null) { Log.e(TAG, "ApiService not initialized!"); return; }
        if (token == null || token.isEmpty()) { Log.e(TAG, "Auth token missing for location fetch."); return;}
        if (busId <= 0) { Log.e(TAG, "Invalid busId (" + busId + ") for location fetch."); return;}


        Call<ApiService.BusLocation> call = apiService.getBusLocation("Bearer " + token, busId); // Add "Bearer " prefix [cite: SchoolWay_app/app/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<ApiService.BusLocation>() {
            @Override
            public void onResponse(Call<ApiService.BusLocation> call, Response<ApiService.BusLocation> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.BusLocation location = response.body();
                    Log.d(TAG, "Received location: Lat=" + location.latitude + ", Lon=" + location.longitude);
                    updateMapMarker(location.latitude, location.longitude); //
                } else {
                    // Use the helper method for consistency
                    handleApiError("fetch bus location", response); //
                }
            }

            @Override
            public void onFailure(Call<ApiService.BusLocation> call, Throwable t) {
                // Use the helper method for consistency
                handleApiFailure("fetch bus location", t); //
            }
        });
    }

    private void updateMapMarker(double latitude, double longitude) {
        runOnUiThread(() -> {
            if (mapView != null && busMarker != null) {
                GeoPoint newPosition = new GeoPoint(latitude, longitude);
                busMarker.setPosition(newPosition);
                mapView.getController().animateTo(newPosition); // Center map on new location
                mapView.invalidate();
                Log.d(TAG, "Map marker updated: " + newPosition);
            } else { Log.w(TAG, "Map or Marker null in updateMapMarker"); }
        });
    }

    // --- MODIFIED: Ensure studentsData is assigned ---
    private void fetchDashboardData(String token) {
        Log.d(TAG, "Fetching dashboard (students) with token."); // Removed token value log
        if (apiService == null) { Log.e(TAG, "ApiService null in fetchDashboardData"); return; }
        if (token == null || token.isEmpty()) { Log.e(TAG, "Auth token missing for dashboard fetch."); return;}

        Call<List<Student>> call = apiService.getStudents("Bearer " + token); // Add "Bearer " prefix [cite: SchoolWay_app/app/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<List<Student>>() { // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) { // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                if (response.isSuccessful() && response.body() != null) {
                    // *** Assign to the member variable ***
                    studentsData = response.body();
                    // *** ***

                    Log.d(TAG, "Students received: " + studentsData.size());
                    int busIdForInitialFetch = -1;

                    if (!studentsData.isEmpty()) {
                        Integer initialBusIdInteger = studentsData.get(0).getBusId(); // Use Integer // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                        busIdForInitialFetch = (initialBusIdInteger != null) ? initialBusIdInteger : -1; // Handle null
                        Log.d(TAG, "Using Bus ID from first student for initial display: " + busIdForInitialFetch);

                        StudentAdapter studentAdapter = new StudentAdapter(Parent_activity.this, studentsData); // [cite: SchoolWay_app/app/java/com/example/fix/StudentAdapter.java]
                        studentList.setAdapter(studentAdapter); // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]

                    } else {
                        Log.w(TAG, "No students found for this parent.");
                        Toast.makeText(Parent_activity.this, "No students assigned.", Toast.LENGTH_SHORT).show();
                        // *** Initialize list when empty ***
                        studentsData = new ArrayList<>();
                        if(studentList != null) studentList.setAdapter(null);
                        if(busInchargeList != null) busInchargeList.setAdapter(null);
                    }

                    if (busIdForInitialFetch > 0) {
                        fetchBusStaffDetails(token, busIdForInitialFetch); // Pass raw token
                        startLocationUpdates(token, busIdForInitialFetch); // Pass raw token
                    } else {
                        Log.w(TAG,"No valid initial bus ID found.");
                        if(busInchargeList != null) busInchargeList.setAdapter(null);
                        stopLocationUpdates(); //
                    }

                } else {
                    handleApiError("fetch student data", response); //
                    // *** Initialize list on error ***
                    studentsData = new ArrayList<>();
                    if(studentList != null) studentList.setAdapter(null);
                    if(busInchargeList != null) busInchargeList.setAdapter(null);
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) { // [cite: SchoolWay_app/app/java/com/example/fix/Student.java]
                handleApiFailure("fetch student data", t); //
                // *** Initialize list on failure ***
                studentsData = new ArrayList<>();
                if(studentList != null) studentList.setAdapter(null);
                if(busInchargeList != null) busInchargeList.setAdapter(null);
            }
        });
    }
    // --- END MODIFICATION ---


    private void fetchBusStaffDetails(String token, int busId) {
        Log.d(TAG, "Fetching bus staff details for bus ID: " + busId);
        if (apiService == null) { Log.e(TAG, "ApiService is null in fetchBusStaffDetails!"); Toast.makeText(this, "Network service error", Toast.LENGTH_SHORT).show(); return; }
        if (token == null || token.isEmpty()) { Log.e(TAG, "Auth token missing for staff fetch."); return;}
        if (busId <= 0) { Log.e(TAG, "Invalid busId (" + busId + ") for staff fetch."); return;}


        Call<List<BusStaff>> call = apiService.getBusStaff("Bearer " + token, busId); // Add "Bearer " prefix [cite: SchoolWay_app/app/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<List<BusStaff>>() { // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
            @Override
            public void onResponse(Call<List<BusStaff>> call, Response<List<BusStaff>> response) { // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                if (response.isSuccessful() && response.body() != null) {
                    List<BusStaff> busStaffList = response.body();
                    busStaffData = response.body();
                    Log.d(TAG, "Bus staff received: " + busStaffList.size());

                    List<String> staffDisplayList = new ArrayList<>();
                    if (busStaffList.isEmpty()) {
                        Log.w(TAG,"Server returned empty staff list for bus ID: " + busId);
                        staffDisplayList.add("No staff assigned to this bus.");
                    } else {
                        for (BusStaff staff : busStaffList) {
                            String nameStr = staff.getName() != null ? staff.getName() : "Unknown";
                            String roleStr = staff.getRole() != null ? staff.getRole() : "N/A";
                            String phoneStr = staff.getPhoneNumber() != null ? staff.getPhoneNumber() : "N/A";
                            staffDisplayList.add(nameStr + " (" + roleStr + ") - " + phoneStr); // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                        }
                    }

                    if(busInchargeList != null){ // [cite: SchoolWay_app/app/res/layout/activity_parent.xml]
                        ArrayAdapter<String> busInchargeAdapter = new ArrayAdapter<>(
                                Parent_activity.this,
                                android.R.layout.simple_list_item_1,
                                staffDisplayList
                        );
                        busInchargeList.setAdapter(busInchargeAdapter);
                        Log.d(TAG, "Staff ListView adapter updated.");
                    } else {
                        Log.e(TAG, "busInchargeList ListView is null!");
                    }

                } else {
                    busStaffData.clear();
                    handleApiError("fetch bus staff", response); //
                    if(busInchargeList != null) busInchargeList.setAdapter(null);
                }
            }

            @Override
            public void onFailure(Call<List<BusStaff>> call, Throwable t) { // [cite: SchoolWay_app/app/java/com/example/fix/BusStaff.java]
                busStaffData.clear();
                handleApiFailure("fetch bus staff", t); //
                if(busInchargeList != null) busInchargeList.setAdapter(null);
            }
        });
    }


    // --- Helper methods for API error handling ---
    private <T> void handleApiError(String action, Response<T> response) {
        String errorMsg = "Failed to " + action + ".";
        try {
            errorMsg += " Code: " + response.code();
            if (response.errorBody() != null) { errorMsg += " - " + response.errorBody().string(); }
        } catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
        Log.e(TAG, errorMsg); Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
    }

    private void handleApiFailure(String action, Throwable t) {
        Log.e(TAG, "Network error during " + action, t);
        Toast.makeText(this, "Network error: Could not " + action, Toast.LENGTH_LONG).show();
    }

    // --- Lifecycle Methods ---
    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        Log.d(TAG, "onResume called");
        if (!authToken.isEmpty() && busIdToTrack > 0) {
            startLocationUpdates(authToken, busIdToTrack); // Pass raw token
        } else if (!authToken.isEmpty()) {
            fetchDashboardData(authToken); // Re-fetch if no bus was tracked - Pass raw token
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        Log.d(TAG, "onPause called");
        stopLocationUpdates(); //
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        stopLocationUpdates(); //
        if (mapView != null) mapView.onDetach();
        mapView = null; busMarker = null;
        locationHandler.removeCallbacksAndMessages(null);
    }
} // End of Parent_activity class

package com.example.fix;

// --- Ensure these imports are present ---
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable; // Import for TextWatcher
import android.text.TextWatcher; // Import for TextWatcher
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter; // Keep if used by custom adapters
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar; // <<< ADDED FOR CONSISTENCY (if needed)
import android.widget.TextView;
import android.widget.Toast;

// --- Keep other existing imports ---
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.annotations.SerializedName;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale; // Import Locale for case conversion
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class School_authority extends AppCompatActivity {

    private static final String TAG = "SchoolAuthority";

    // UI Elements
    private ImageButton profileButton;
    private Button searchStudentButton, showAllStudentButton;
    private Button searchBusButton, showAllBusButton;
    private Button searchBusInchargeButton, showAllBusInchargeButton;
    private EditText studentRollnoEditText, busNumberEditText, busInchargeNumberEditText;
    private TextView resultsTitleTextView;
    private ListView resultsListView;
    private MapView mapView;
    private ImageButton recenterButton;
    // private ProgressBar progressBar;

    // Map related variables
    private Map<Integer, Marker> busMarkers = new HashMap<>();
    private Handler locationUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable locationUpdateRunnable;
    private static final long LOCATION_UPDATE_INTERVAL_MS = 5000;
    private boolean isInitialMapLoad = true;

    // API Service & Token
    private ApiService apiService;
    private TokenManager tokenManager;
    private String authToken;

    // --- State variables ---
    private enum ListContentType { NONE, STUDENTS, BUSES, STAFF }
    private ListContentType currentListContent = ListContentType.NONE;
    // --- MODIFIED: Store the full list of onboard students separately ---
    private List<Student> fullOnboardStudentList = new ArrayList<>(); // Holds the complete onboard list
    private List<Student> currentDisplayedStudentList = new ArrayList<>(); // Holds the list currently shown (full or searched)
    private StudentAdapter studentAdapter; // Declare adapter at class level
    // --- END MODIFICATION ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_school_authority);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tokenManager = new TokenManager(this);
        apiService = Retrofitclient.getClient().create(ApiService.class);

        String rawToken = tokenManager.getToken();
        if (rawToken == null || rawToken.isEmpty()) {
            handleAuthError();
            return;
        }
        this.authToken = rawToken;
        Log.d(TAG, "Auth token loaded.");

        findViews();
        // --- Initialize Adapter ---
        // Initialize the adapter once here with an empty list
        studentAdapter = new StudentAdapter(this, new ArrayList<>());
        resultsListView.setAdapter(studentAdapter);
        // --- End Initialize Adapter ---

        setupButtonClickListeners();
        setupResultsListClickListener(); // Keep this for clicking on results
        // --- ADDED: Setup search input listener (optional: real-time search) ---
        // setupSearchInputListener(); // Uncomment if you want search-as-you-type
        // --- END ADDED ---
        initializeMap();
        startBusLocationUpdates();
    }

    private void findViews() {
        profileButton = findViewById(R.id.profile);
        studentRollnoEditText = findViewById(R.id.student_rollno); // Used for search query now
        searchStudentButton = findViewById(R.id.search_student); // Triggers the client-side search
        showAllStudentButton = findViewById(R.id.show_all_student); // Fetches/Refreshes onboard list
        busNumberEditText = findViewById(R.id.bus_number);
        searchBusButton = findViewById(R.id.search_bus);
        showAllBusButton = findViewById(R.id.show_all_bus);
        busInchargeNumberEditText = findViewById(R.id.bus_incharge_number);
        searchBusInchargeButton = findViewById(R.id.search_bus_incharge);
        showAllBusInchargeButton = findViewById(R.id.show_all_bus_incharge);
        resultsTitleTextView = findViewById(R.id.results_title);
        resultsListView = findViewById(R.id.results_list_view);
        mapView = findViewById(R.id.authority_map);
        recenterButton = findViewById(R.id.recenter_button);
        // progressBar = findViewById(R.id.progressBar);

        if (recenterButton != null) {
            recenterButton.setOnClickListener(v -> recenterMap());
        } else {
            Log.e(TAG, "Recenter button not found!");
        }
    }

    private void handleAuthError() {
        Log.e(TAG, "Authentication token not found. Redirecting to login.");
        Toast.makeText(this, "Authentication error. Please log in.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(School_authority.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // --- MODIFIED: Button Click Listeners ---
    private void setupButtonClickListeners() {
        profileButton.setOnClickListener(view -> {
            Intent intent = new Intent(School_authority.this, Info.class);
            startActivity(intent);
        });

        // Search button now triggers client-side filtering of the onboard list
        searchStudentButton.setOnClickListener(view -> performClientSideStudentSearch());

        // Show all still fetches the full list (which is then filtered for onboard)
        showAllStudentButton.setOnClickListener(view -> fetchAllStudents());

        // Other button listeners remain for their original functions
        searchBusButton.setOnClickListener(view -> searchBus());
        showAllBusButton.setOnClickListener(view -> fetchAllBuses());
        searchBusInchargeButton.setOnClickListener(view -> searchBusStaff());
        showAllBusInchargeButton.setOnClickListener(view -> fetchAllBusStaff());
    }
    // --- END MODIFICATION ---

    private void setupResultsListClickListener() {
        resultsListView.setOnItemClickListener((parent, view, position, id) -> {
            // Use currentDisplayedStudentList for clicks, as it reflects what's visible
            if (currentListContent == ListContentType.STUDENTS && currentDisplayedStudentList != null && position >= 0 && position < currentDisplayedStudentList.size()) {
                Student clickedStudent = currentDisplayedStudentList.get(position);
                Log.d(TAG, "Student item clicked: " + clickedStudent.getName() + " (ID: " + clickedStudent.getId() + ")");
                Intent intent = new Intent(School_authority.this, AttendanceDetailsActivity.class);
                intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_ID, clickedStudent.getId());
                intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_NAME, clickedStudent.getName());
                startActivity(intent);
            } else {
                Log.d(TAG, "Item clicked, but list content is not STUDENTS or click is invalid. Type: " + currentListContent);
            }
        });
    }

    // --- Optional: Listener for real-time search as user types ---
    /*
    private void setupSearchInputListener() {
        studentRollnoEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Perform search whenever text changes
                performClientSideStudentSearch();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }
    */
    // --- End Optional Listener ---


    private void hideResults() {
        resultsTitleTextView.setVisibility(View.GONE);
        resultsListView.setVisibility(View.GONE);
        // Don't clear adapter here, just update its data
        // resultsListView.setAdapter(null);
        currentListContent = ListContentType.NONE;
        // Don't clear fullOnboardStudentList here
        currentDisplayedStudentList.clear(); // Clear the displayed list
        if (studentAdapter != null) {
            studentAdapter.updateData(new ArrayList<>()); // Clear adapter data
        }
    }


    // --- Data Fetching Methods ---

    // --- MODIFIED: fetchAllStudents to populate fullOnboardStudentList ---
    private void fetchAllStudents() {
        hideResults(); // Clear previous results first
        Log.d(TAG, "Fetching all students (filtering for onboard)...");
        // showLoading(true);

        if (apiService == null || authToken == null || authToken.isEmpty()) {
            handleAuthError();
            // showLoading(false);
            return;
        }

        apiService.getAllStudents("Bearer " + authToken).enqueue(new Callback<List<Student>>() {
            @Override
            public void onResponse(Call<List<Student>> call, Response<List<Student>> response) {
                // showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<Student> allStudents = response.body();
                    List<Student> onboardStudents = new ArrayList<>();

                    Log.d(TAG, "Received " + allStudents.size() + " students from API.");
                    for (Student s : allStudents) {
                        // Log status received from API
                        Log.d(TAG, "API Student: ID=" + s.getId() + ", Name=" + s.getName() + ", Status=" + s.getAttendanceStatus());
                        // Filter logic
                        if (s.getAttendanceStatus() != null && "Onboard".equalsIgnoreCase(s.getAttendanceStatus())) {
                            onboardStudents.add(s);
                        }
                    }

                    // *** Store the complete onboard list ***
                    fullOnboardStudentList.clear();
                    fullOnboardStudentList.addAll(onboardStudents);
                    Log.d(TAG, "Stored " + fullOnboardStudentList.size() + " onboard students in full list.");

                    // *** Display the full onboard list initially ***
                    displayStudentResults(fullOnboardStudentList);
                    // Clear search field after fetching all
                    studentRollnoEditText.setText("");


                } else {
                    handleApiError("fetch all students", response);
                    fullOnboardStudentList.clear(); // Clear list on error
                    displayStudentResults(new ArrayList<>()); // Show empty state
                }
            }

            @Override
            public void onFailure(Call<List<Student>> call, Throwable t) {
                // showLoading(false);
                handleApiFailure("fetch all students", t);
                fullOnboardStudentList.clear(); // Clear list on error
                displayStudentResults(new ArrayList<>()); // Show empty state
            }
        });
    }
    // --- END MODIFICATION ---


    private void fetchAllBuses() {
        hideResults();
        Log.d(TAG, "Fetching all buses...");
        if (apiService == null || authToken == null || authToken.isEmpty()) { handleAuthError(); return; }
        apiService.getAllBuses("Bearer " + authToken).enqueue(new Callback<List<ApiService.Bus>>() {
            @Override
            public void onResponse(Call<List<ApiService.Bus>> call, Response<List<ApiService.Bus>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    displayBusResults(response.body());
                } else { handleApiError("fetch all buses", response); }
            }
            @Override public void onFailure(Call<List<ApiService.Bus>> call, Throwable t) { handleApiFailure("fetch all buses", t); }
        });
    }
    private void fetchAllBusStaff() {
        hideResults();
        Log.d(TAG, "Fetching all bus staff...");
        if (apiService == null || authToken == null || authToken.isEmpty()) { handleAuthError(); return; }
        apiService.getAllBusStaff("Bearer " + authToken).enqueue(new Callback<List<BusStaff>>() {
            @Override
            public void onResponse(Call<List<BusStaff>> call, Response<List<BusStaff>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    displayBusStaffResults(response.body());
                } else { handleApiError("fetch all bus staff", response); }
            }
            @Override public void onFailure(Call<List<BusStaff>> call, Throwable t) { handleApiFailure("fetch all bus staff", t); }
        });
    }

    // --- Server-Side Search Methods (Keep as placeholders or remove if not needed) ---
    private void searchStudent() {
        // This button is now used for client-side search, so this method might be unused
        // Or repurpose for a server-side search if needed separately
        String rollNo = studentRollnoEditText.getText().toString().trim();
        if (rollNo.isEmpty()) { Toast.makeText(this, "Please enter a roll number for server search", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Server student search not yet implemented", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Server search student called for: " + rollNo);
        hideResults();
        // TODO: Implement API call apiService.searchStudentByRollNo(...)
    }
    private void searchBus() {
        String busPlate = busNumberEditText.getText().toString().trim();
        if (busPlate.isEmpty()) { Toast.makeText(this, "Please enter a bus plate number", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Bus search not yet implemented", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Search bus called for: " + busPlate);
        hideResults();
        // TODO: Implement API call apiService.searchBusByPlate(...)
    }
    private void searchBusStaff() {
        String staffNameOrId = busInchargeNumberEditText.getText().toString().trim();
        if (staffNameOrId.isEmpty()) { Toast.makeText(this, "Please enter staff name or ID", Toast.LENGTH_SHORT).show(); return; }
        Toast.makeText(this, "Bus staff search not yet implemented", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Search bus staff called for: " + staffNameOrId);
        hideResults();
        // TODO: Implement API call apiService.searchBusStaff(...)
    }

    // --- ADDED: Client-Side Search Logic ---
    private void performClientSideStudentSearch() {
        if (currentListContent != ListContentType.STUDENTS) {
            //Toast.makeText(this, "Show onboard students first before searching.", Toast.LENGTH_SHORT).show();
            // Optionally, fetch students if list is not currently shown
            if (fullOnboardStudentList.isEmpty()) {
                fetchAllStudents(); // Fetch if we don't have the list yet
            } else {
                // If we have the list but aren't showing it, just show the full list
                displayStudentResults(fullOnboardStudentList);
            }
            return;
        }

        String query = studentRollnoEditText.getText().toString().trim().toLowerCase(Locale.getDefault());
        Log.d(TAG, "Performing client-side search for query: '" + query + "' on " + fullOnboardStudentList.size() + " onboard students.");

        List<Student> searchResults = new ArrayList<>();

        if (query.isEmpty()) {
            // If query is empty, show the full onboard list
            searchResults.addAll(fullOnboardStudentList);
            Log.d(TAG, "Search query empty, showing all " + searchResults.size() + " onboard students.");
        } else {
            // Filter the full onboard list based on the query (name or roll number)
            for (Student student : fullOnboardStudentList) {
                boolean nameMatches = student.getName() != null && student.getName().toLowerCase(Locale.getDefault()).contains(query);
                boolean rollNoMatches = student.getRollNo() != null && student.getRollNo().toLowerCase(Locale.getDefault()).contains(query);

                if (nameMatches || rollNoMatches) {
                    searchResults.add(student);
                }
            }
            Log.d(TAG, "Search found " + searchResults.size() + " matching students.");
        }

        // Display the search results (or full list if query was empty)
        displayStudentResults(searchResults);

        // Show a message if search yields no results but query wasn't empty
        if (searchResults.isEmpty() && !query.isEmpty()) {
            Toast.makeText(this, "No onboard students match '" + query + "'", Toast.LENGTH_SHORT).show();
            // Keep the title indicating search results, even if empty
            resultsTitleTextView.setText("Onboard Students (0 matching)");
            resultsTitleTextView.setVisibility(View.VISIBLE);
            resultsListView.setVisibility(View.GONE); // Hide list view
        }
    }
    // --- END ADDED ---


    // --- MODIFIED: displayStudentResults to handle updates ---
    private void displayStudentResults(List<Student> studentsToDisplay) {
        // This method now displays whatever list is passed (full onboard or search results)
        currentDisplayedStudentList.clear(); // Clear the currently displayed list
        if (studentsToDisplay != null) {
            currentDisplayedStudentList.addAll(studentsToDisplay); // Add the new list
        }

        // Update the adapter with the new data
        if (studentAdapter != null) {
            studentAdapter.updateData(currentDisplayedStudentList); // Assumes StudentAdapter has an updateData method
        } else {
            // Fallback or initial setup if adapter wasn't initialized
            studentAdapter = new StudentAdapter(this, currentDisplayedStudentList);
            resultsListView.setAdapter(studentAdapter);
        }

        // Update title and visibility based on the displayed list
        if (currentDisplayedStudentList.isEmpty()) {
            // Check if a search was performed (query field is not empty)
            String currentQuery = studentRollnoEditText.getText().toString().trim();
            if (!currentQuery.isEmpty()) {
                // Empty list *because* of search
                resultsTitleTextView.setText("Onboard Students (0 matching)");
                // Toast message handled in performClientSideStudentSearch
            } else {
                // Empty list because no students are onboard initially
                resultsTitleTextView.setText("Onboard Students (0)");
                Toast.makeText(this, "No students are currently onboard.", Toast.LENGTH_SHORT).show();
            }
            resultsTitleTextView.setVisibility(View.VISIBLE);
            resultsListView.setVisibility(View.GONE);
        } else {
            resultsTitleTextView.setText("Onboard Students (" + currentDisplayedStudentList.size() + ")");
            resultsTitleTextView.setVisibility(View.VISIBLE);
            resultsListView.setVisibility(View.VISIBLE);
        }

        // Set the content type flag
        currentListContent = ListContentType.STUDENTS;
        Log.d(TAG, "Displayed " + currentDisplayedStudentList.size() + " students in the list view.");
    }
    // --- END MODIFICATION ---

    // --- displayBusResults and displayBusStaffResults remain unchanged ---
    private void displayBusResults(List<ApiService.Bus> buses) {
        if (buses == null || buses.isEmpty()) { Toast.makeText(this, "No buses found.", Toast.LENGTH_SHORT).show(); hideResults(); return; }
        currentListContent = ListContentType.BUSES;
        currentDisplayedStudentList.clear(); // Clear student list

        resultsTitleTextView.setText("Bus Results (" + buses.size() + ")");
        resultsTitleTextView.setVisibility(View.VISIBLE);

        List<String> busDisplayList = new ArrayList<>();
        for (ApiService.Bus bus : buses) {
            String driverName = (bus.driverFirstName != null ? bus.driverFirstName : "") + " " + (bus.driverLastName != null ? bus.driverLastName : "");
            busDisplayList.add(bus.busPlate + " (Driver: " + driverName.trim() + ")");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, busDisplayList);
        resultsListView.setAdapter(adapter); // Use generic adapter for buses
        resultsListView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Displayed " + buses.size() + " buses.");
    }

    private void displayBusStaffResults(List<BusStaff> staffList) {
        if (staffList == null || staffList.isEmpty()) { Toast.makeText(this, "No bus staff found.", Toast.LENGTH_SHORT).show(); hideResults(); return; }
        currentListContent = ListContentType.STAFF;
        currentDisplayedStudentList.clear(); // Clear student list

        resultsTitleTextView.setText("Bus Staff Results (" + staffList.size() + ")");
        resultsTitleTextView.setVisibility(View.VISIBLE);

        List<String> staffDisplayList = new ArrayList<>();
        for (BusStaff staff : staffList) {
            String busInfo = staff.getBusPlate() != null ? " - Bus " + staff.getBusPlate() : "";
            staffDisplayList.add(staff.getName() + " (" + staff.getRole() + ")" + busInfo);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, staffDisplayList);
        resultsListView.setAdapter(adapter); // Use generic adapter for staff
        resultsListView.setVisibility(View.VISIBLE);
        Log.d(TAG, "Displayed " + staffList.size() + " staff members.");
    }
    // --- END UNCHANGED DISPLAY METHODS ---


    // --- Error Handling Methods (Unchanged) ---
    private <T> void handleApiError(String action, Response<T> response) {
        String errorMsg = "Failed to " + action + ".";
        String responseBody = "";
        try {
            errorMsg += " Code: " + response.code();
            if (response.errorBody() != null) {
                responseBody = response.errorBody().string(); // Read error body once
                errorMsg += " - See logs."; // Don't show raw error body to user
                Log.e(TAG, "API Error Body for " + action + ": " + responseBody); // Log the raw error body
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading error body for " + action, e);
        }
        Log.e(TAG, "API Error: " + errorMsg + (responseBody.isEmpty() ? "" : " Body logged."));
        Toast.makeText(this, "API Error: Failed to " + action, Toast.LENGTH_LONG).show(); // Simplified user message
        hideResults();
    }

    private void handleApiFailure(String action, Throwable t) {
        Log.e(TAG, "Network error during " + action, t);
        Toast.makeText(this, "Network error: Could not " + action, Toast.LENGTH_LONG).show();
        hideResults();
    }

    // --- Map Methods (Unchanged) ---
    private void initializeMap() {
        try {
            Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
            Configuration.getInstance().setUserAgentValue(getPackageName());
        } catch (Exception e) {
            Log.e(TAG, "Error loading osmdroid configuration", e);
        }
        if (mapView == null) {
            Log.e(TAG, "MapView is null during initialization!");
            return;
        }
        mapView.setTileSource(TileSourceFactory.MAPNIK);
//        mapView.setBuiltInZoomControls(true); // Keep commented if not desired
        mapView.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(15.593722, 73.814194); // Goa default
        mapView.getController().setZoom(16.0); // Default zoom
        mapView.getController().setCenter(startPoint);
        mapView.invalidate(); // Redraw map
        Log.d(TAG, "School Authority Map initialized.");
    }
    private void startBusLocationUpdates() {
        Log.d(TAG, "Setting up periodic bus location updates.");
        if (locationUpdateRunnable == null) {
            locationUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    fetchAllBusLocations();
                    // Schedule the next run
                    locationUpdateHandler.postDelayed(this, LOCATION_UPDATE_INTERVAL_MS);
                }
            };
        }
        // Remove any existing callbacks before posting new one
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        locationUpdateHandler.post(locationUpdateRunnable); // Start immediately
    }
    private void stopBusLocationUpdates() {
        Log.d(TAG, "Stopping periodic bus location updates.");
        locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
    }

    private void fetchAllBusLocations() {
        // Log.d(TAG, "Fetching locations for all buses..."); // Reduced verbosity
        if (apiService == null || authToken == null || authToken.isEmpty()) {
            Log.w(TAG, "Skipping location fetch: Auth token or API service missing.");
            return;
        }
        // Add "Bearer " prefix for the API call
        apiService.getAllBusLocations("Bearer " + authToken).enqueue(new Callback<List<BusLocationWithId>>() {
            @Override
            public void onResponse(Call<List<BusLocationWithId>> call, Response<List<BusLocationWithId>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateAllBusMarkers(response.body());
                } else {
                    // Log error but don't necessarily show Toast for background task
                    handleApiError("fetch all bus locations", response);
                }
            }
            @Override
            public void onFailure(Call<List<BusLocationWithId>> call, Throwable t) {
                // Log error but don't necessarily show Toast for background task
                handleApiFailure("fetch all bus locations", t);
            }
        });
    }

    private void updateAllBusMarkers(List<BusLocationWithId> locations) {
        if (mapView == null || locations == null) {
            Log.w(TAG, "Cannot update markers: MapView or locations list is null.");
            return;
        }
        runOnUiThread(() -> {
            // Log.d(TAG, "Updating " + locations.size() + " bus markers."); // Reduced verbosity
            List<Integer> updatedBusIds = new ArrayList<>();
            List<GeoPoint> currentMarkerPoints = new ArrayList<>();

            for (BusLocationWithId loc : locations) {
                // Basic validation for coordinates
                if (loc.getLatitude() < -90 || loc.getLatitude() > 90 || loc.getLongitude() < -180 || loc.getLongitude() > 180) {
                    Log.w(TAG, "Invalid coordinates for bus " + loc.getBusId() + ": " + loc.getLatitude() + "," + loc.getLongitude());
                    continue; // Skip this location
                }

                Marker marker = busMarkers.get(loc.getBusId());
                GeoPoint newPosition = new GeoPoint(loc.getLatitude(), loc.getLongitude());
                updatedBusIds.add(loc.getBusId());

                if (marker == null) {
                    marker = new Marker(mapView);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    // Consider setting a custom bus icon here
                    // marker.setIcon(getResources().getDrawable(R.drawable.ic_bus_marker, getTheme()));
                    mapView.getOverlays().add(marker);
                    busMarkers.put(loc.getBusId(), marker);
                    Log.d(TAG, "Created map marker for bus " + loc.getBusId());
                }
                marker.setPosition(newPosition);
                marker.setTitle("Bus ID: " + loc.getBusId()); // Keep title simple
                // marker.setSnippet("Updated: " + loc.getTimestamp()); // Snippet optional
                currentMarkerPoints.add(newPosition);
            }

            // Remove markers for buses no longer in the update list
            List<Integer> idsToRemove = new ArrayList<>();
            for (Integer existingId : busMarkers.keySet()) {
                if (!updatedBusIds.contains(existingId)) {
                    idsToRemove.add(existingId);
                }
            }
            for (Integer id : idsToRemove) {
                Marker m = busMarkers.remove(id);
                if (m != null) {
                    mapView.getOverlays().remove(m);
                }
                Log.d(TAG, "Removed stale map marker for bus " + id);
            }

            mapView.invalidate(); // Redraw the map with updated markers

            // Auto-zoom/center logic
            if (isInitialMapLoad && !currentMarkerPoints.isEmpty()) {
                Log.d(TAG, "Performing initial map zoom/center on available markers.");
                zoomMapToPoints(currentMarkerPoints, true); // Use helper
                isInitialMapLoad = false; // Only do this once on initial load
            }
        });
    }

    // Helper method to zoom map
    private void zoomMapToPoints(List<GeoPoint> points, boolean animate) {
        if (mapView == null || points == null || points.isEmpty()) return;
        if (points.size() == 1) {
            mapView.getController().setZoom(17.0); // Zoom closer for single point
            if (animate) mapView.getController().animateTo(points.get(0));
            else mapView.getController().setCenter(points.get(0));
        } else {
            BoundingBox bb = BoundingBox.fromGeoPoints(points);
            // Zoom to bounding box with padding
            mapView.zoomToBoundingBox(bb.increaseByScale(1.2f), animate, 100); // 100ms padding for zoom
        }
    }


    private void recenterMap() {
        if (mapView == null) return;
        if (busMarkers != null && !busMarkers.isEmpty()) {
            Log.d(TAG, "Re-centering map on current bus markers.");
            List<GeoPoint> points = new ArrayList<>();
            for (Marker m : busMarkers.values()) {
                if (m != null && m.getPosition() != null) {
                    points.add(m.getPosition());
                }
            }
            if (!points.isEmpty()) {
                zoomMapToPoints(points, true); // Use helper with animation
            } else {
                recenterToDefault(); // Fallback if no valid points
            }
        } else {
            recenterToDefault(); // Recenter to default if no markers exist
        }
    }

    private void recenterToDefault() {
        Log.d(TAG, "Re-centering map to default location (Goa).");
        GeoPoint startPoint = new GeoPoint(15.593722, 73.814194); // Goa
        if (mapView != null) {
            mapView.getController().setZoom(12.0); // Reset zoom level
            mapView.getController().animateTo(startPoint);
        }
    }

    // --- Data Class (Keep as is) ---
    public static class BusLocationWithId {
        @SerializedName("bus_id") private int busId;
        @SerializedName("latitude") private double latitude;
        @SerializedName("longitude") private double longitude;
        @SerializedName("timestamp") private String timestamp;
        public int getBusId() { return busId; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getTimestamp() { return timestamp; }
    }

    // --- Lifecycle Methods (Keep as is) ---
    @Override protected void onResume() {
        super.onResume();
        if(mapView!=null) mapView.onResume();
        startBusLocationUpdates(); // Ensure updates restart when resuming
        Log.d(TAG,"onResume: Map resumed, updates started.");
    }
    @Override protected void onPause() {
        super.onPause();
        if(mapView!=null) mapView.onPause();
        stopBusLocationUpdates(); // Stop updates when pausing
        Log.d(TAG,"onPause: Map paused, updates stopped.");
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        stopBusLocationUpdates(); // Ensure updates are stopped
        if (mapView != null) {
            mapView.onDetach(); // Clean up map resources
        }
        mapView = null; // Help garbage collection
        if (busMarkers != null) {
            busMarkers.clear();
        }
        locationUpdateHandler.removeCallbacksAndMessages(null); // Clean up handler
        Log.d(TAG,"onDestroy: Cleaned up resources.");
    }

    // Optional: Add showLoading method if using ProgressBar
    /*
    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        // Optionally disable/enable buttons while loading
    }
    */

} // End of class

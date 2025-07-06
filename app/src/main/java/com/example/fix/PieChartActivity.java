package com.example.fix; // Use your main app package

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

// MPAndroidChart imports
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

// Java utility imports
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Retrofit imports
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// Your existing classes from com.example.fix
// No need to import AttendanceResponse/Record if ApiService uses them directly
// import com.example.fix.AttendanceResponse;
// import com.example.fix.AttendanceRecord;
// import com.example.fix.ApiService;
// import com.example.fix.RetrofitClient;
// import com.example.fix.TokenManager;


public class PieChartActivity extends AppCompatActivity {

    private static final String TAG = "PieChartActivity";
    private PieChart pieChart;
    private String studentName; // Store student name for title
    private int studentId = -1; // Store student ID for API call

    // Use existing components from com.example.fix
    private ApiService apiService;
    private TokenManager tokenManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pie_chart); // Link to the layout

        pieChart = findViewById(R.id.pieChart);

        // Initialize components from com.example.fix
        tokenManager = new TokenManager(this);
        apiService = Retrofitclient.getClient().create(ApiService.class); // Use existing client

        // Get student ID and name from intent (passed from AttendanceDetailsActivity)
        studentId = getIntent().getIntExtra(AttendanceDetailsActivity.EXTRA_STUDENT_ID, -1); // Use constant from AttendanceDetailsActivity
        studentName = getIntent().getStringExtra(AttendanceDetailsActivity.EXTRA_STUDENT_NAME); // Use constant

        if (studentId != -1) {
            Log.d(TAG, "Received studentId: " + studentId + ", Name: " + studentName);
            // Set Activity title
            setTitle((studentName != null ? studentName : "Student") + " - Attendance Report");
            fetchAttendanceDataForChart(studentId); // Fetch data using studentId
        } else {
            Log.e(TAG, "Student ID not found in Intent.");
            Toast.makeText(this, "Error: Student ID missing.", Toast.LENGTH_LONG).show();
            setupEmptyChart("Error: Student ID missing"); // Show an empty chart state
        }
    }

    // Renamed method to avoid confusion with AttendanceDetailsActivity's method
    private void fetchAttendanceDataForChart(int studentIdToFetch) {
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "Authentication token missing.");
            Toast.makeText(this, "Authentication error.", Toast.LENGTH_SHORT).show();
            setupEmptyChart("Authentication Error");
            return;
        }

        Log.d(TAG, "Fetching attendance data for chart, student ID: " + studentIdToFetch);
        // Use the existing getStudentAttendance endpoint from com.example.fix.ApiService
        Call<AttendanceResponse> call = apiService.getStudentAttendance("Bearer " + token, studentIdToFetch);

        call.enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AttendanceResponse attendanceData = response.body();
                    Log.d(TAG, "Attendance data received for chart.");

                    // Process data - focusing on monthly data for the chart example
                    List<AttendanceRecord> monthlyRecords = attendanceData.getMonthlyAttendance();

                    if (monthlyRecords != null && !monthlyRecords.isEmpty()) {
                        int onboardCount = 0;
                        int offboardCount = 0;
                        for (AttendanceRecord record : monthlyRecords) {
                            if (record.getStatus() != null) {
                                // Map "Onboard"/"Offboard" to Present/Absent for the chart
                                if ("Onboard".equalsIgnoreCase(record.getStatus())) {
                                    onboardCount++;
                                } else if ("Offboard".equalsIgnoreCase(record.getStatus())) {
                                    offboardCount++;
                                }
                                // You could add more categories if needed (e.g., "Absent", "Late")
                            }
                        }
                        Log.d(TAG, "Processed Counts - Onboard: " + onboardCount + ", Offboard: " + offboardCount);
                        setupPieChart(onboardCount, offboardCount); // Setup chart with counts

                    } else {
                        Log.w(TAG, "No monthly attendance records found for student ID: " + studentIdToFetch);
                        Toast.makeText(PieChartActivity.this, "No monthly attendance data available.", Toast.LENGTH_SHORT).show();
                        setupEmptyChart("No Data Available"); // Show empty state
                    }
                } else {
                    String errorMsg = "Failed to load attendance data";
                    try {
                        errorMsg += " (Code: " + response.code() + ")";
                        if (response.errorBody() != null) { errorMsg += " - " + response.errorBody().string(); }
                    } catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
                    Log.e(TAG, "API Response for attendance not successful: " + errorMsg);
                    Toast.makeText(PieChartActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    setupEmptyChart("Error Loading Data"); // Show empty state
                }
            }

            @Override
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                Log.e(TAG, "API Call for attendance failed: ", t);
                Toast.makeText(PieChartActivity.this, "Network Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                setupEmptyChart("Network Error"); // Show empty state
            }
        });
    }

    // Renamed parameters to match the data being counted
    private void setupPieChart(int onboardCount, int offboardCount) {
        if (onboardCount == 0 && offboardCount == 0) {
            Log.w(TAG, "Both onboard and offboard counts are zero.");
            setupEmptyChart("No Onboard/Offboard Data");
            return;
        }

        ArrayList<PieEntry> entries = new ArrayList<>();
        if (onboardCount > 0) {
            entries.add(new PieEntry(onboardCount, "Onboard")); // Label for the slice
        }
        if (offboardCount > 0) {
            entries.add(new PieEntry(offboardCount, "Offboard")); // Label for the slice
        }

        PieDataSet dataSet = new PieDataSet(entries, ""); // Label for the dataset

        // Colors
        ArrayList<Integer> colors = new ArrayList<>();
        if (onboardCount > 0) colors.add(Color.parseColor("#4CAF50")); // Green for Onboard
        if (offboardCount > 0) colors.add(Color.parseColor("#F44336")); // Red for Offboard
        // Add more colors if you have more statuses
        dataSet.setColors(colors);
        // Or use a template: dataSet.setColors(ColorTemplate.MATERIAL_COLORS);

        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f); // Adjust size
        dataSet.setSliceSpace(2f); // Space between slices

        PieData pieData = new PieData(dataSet);
        pieData.setDrawValues(true);
        pieData.setValueFormatter(new PercentFormatter(pieChart)); // Show percentages
        pieData.setValueTextSize(14f);
        pieData.setValueTextColor(Color.WHITE);


        // Configure Pie Chart Appearance
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setExtraOffsets(5, 10, 5, 15); // Adjust padding

        pieChart.setDragDecelerationFrictionCoef(0.95f);

        // Center Hole
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setHoleRadius(58f);

        // Center Text
        pieChart.setDrawCenterText(true);
        // Use studentName if available, otherwise default
        String centerName = (studentName != null && !studentName.isEmpty()) ? studentName : "Student";
        pieChart.setCenterText(centerName + "\nReport");
        pieChart.setCenterTextSize(18f); // Adjust text size
        pieChart.setCenterTextColor(Color.DKGRAY);

        // Entry Labels (Labels next to slices)
        pieChart.setDrawEntryLabels(true); // Show labels like "Onboard", "Offboard"
        pieChart.setEntryLabelColor(Color.BLACK);
        pieChart.setEntryLabelTextSize(12f);


        // Animation
        pieChart.animateY(1000);

        // Legend Configuration
        Legend legend = pieChart.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL); // Horizontal often fits better

        legend.setDrawInside(false);
        legend.setTextSize(20f);
        legend.setXEntrySpace(20f);
        legend.setYEntrySpace(5f);
        legend.setXOffset(10f);
        legend.setYOffset(120f);
        legend.setWordWrapEnabled(true);


        // Set data and refresh
        pieChart.setData(pieData);
        pieChart.invalidate(); // Refresh chart
        Log.d(TAG, "Pie chart setup complete.");
    }

    private void setupEmptyChart(String message) {
        pieChart.clear();
        pieChart.setCenterText(message != null ? message : "No Data Available");
        pieChart.setCenterTextColor(Color.GRAY);
        pieChart.setCenterTextSize(16f);
        // Disable legend/interaction for empty chart
        pieChart.getLegend().setEnabled(false);
        pieChart.setTouchEnabled(false);
        pieChart.invalidate();
        Log.d(TAG, "Displayed empty chart state: " + message);
    }
}

package com.example.fix;

import android.content.Intent; // <<< ENSURE IMPORT
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import com.example.fix.AttendanceResponse;
import com.example.fix.AttendanceRecord;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class AttendanceDetailsActivity extends AppCompatActivity {

    private static final String TAG = "AttendanceDetails";
    public static final String EXTRA_STUDENT_ID = "com.example.fix.STUDENT_ID";
    public static final String EXTRA_STUDENT_NAME = "com.example.fix.STUDENT_NAME";

    private TextView studentNameHeader, weeklyAttendanceText, monthlyAttendanceText;
    private Button generateReportButton;
    private ApiService apiService;
    private TokenManager tokenManager;
    private int studentId = -1;
    private String studentName = "Student";

    // Inner classes removed - using separate files

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_details);

        tokenManager = new TokenManager(this);
        apiService = Retrofitclient.getClient().create(ApiService.class); // [cite: SchoolWay_app/app/java/com/example/fix/Retrofitclient.java]

        studentNameHeader = findViewById(R.id.student_name_header);
        weeklyAttendanceText = findViewById(R.id.weekly_attendance_text);
        monthlyAttendanceText = findViewById(R.id.monthly_attendance_text);
        generateReportButton = findViewById(R.id.generate_report_button);

        Intent intent = getIntent();
        studentId = intent.getIntExtra(EXTRA_STUDENT_ID, -1);
        studentName = intent.getStringExtra(EXTRA_STUDENT_NAME);

        if (studentId == -1) {
            // ... (handle invalid ID) ...
            Log.e(TAG, "Error: Invalid Student ID received.");
            Toast.makeText(this, "Error: Could not load student data.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (studentName != null && !studentName.isEmpty()) {
            studentNameHeader.setText(studentName + " - Attendance");
        } else {
            studentNameHeader.setText("Student Attendance");
        }

        fetchAttendanceData();

        // --- MODIFIED: Set OnClickListener for the button ---
        generateReportButton.setOnClickListener(v -> {
            // Call the method to launch the PieChartActivity
            launchPieChartReport();
        });
        // --- END MODIFICATION ---
    }

    // --- ADD THIS METHOD ---
    private void launchPieChartReport() {
        Log.d(TAG, "Generate Report button clicked. Launching PieChartActivity for student ID: " + studentId);

        // Check if studentId is valid before launching
        if (studentId == -1) {
            Toast.makeText(this, "Cannot generate report: Invalid student ID.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, PieChartActivity.class);
        // Pass the necessary data (studentId and studentName)
        intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_ID, studentId); // Pass ID
        intent.putExtra(AttendanceDetailsActivity.EXTRA_STUDENT_NAME, studentName); // Pass Name
        startActivity(intent);
    }
    // --- END ADDED METHOD ---


    private void fetchAttendanceData() {
        // ... (fetch data logic remains the same) ...
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) { /* handle auth error */ return; }
        if (studentId <= 0) { /* handle invalid id */ return; }

        Log.d(TAG, "Fetching attendance for student ID: " + studentId);
        weeklyAttendanceText.setText("Loading Weekly...");
        monthlyAttendanceText.setText("Loading Monthly...");

        Call<AttendanceResponse> call = apiService.getStudentAttendance("Bearer " + token, studentId); // [cite: SchoolWay_app/app/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<AttendanceResponse>() {
            @Override
            public void onResponse(Call<AttendanceResponse> call, Response<AttendanceResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Attendance data fetched successfully.");
                    displayAttendance(response.body());
                } else {
                    handleApiError("fetch attendance", response);
                }
            }
            @Override
            public void onFailure(Call<AttendanceResponse> call, Throwable t) {
                handleApiFailure("fetch attendance", t);
            }
        });
    }

    private void displayAttendance(AttendanceResponse data) {
        // ... (display logic remains the same) ...
        // --- Weekly ---
        if (data != null && data.getWeeklyAttendance() != null && !data.getWeeklyAttendance().isEmpty()) {
            StringBuilder weeklySb = new StringBuilder();
            for (AttendanceRecord record : data.getWeeklyAttendance()) {
                weeklySb.append(record.getDate()).append(": ").append(record.getStatus()).append("\n");
            }
            weeklyAttendanceText.setText(weeklySb.toString());
        } else {
            weeklyAttendanceText.setText("No weekly data available.");
        }
        // --- Monthly ---
        if (data != null && data.getMonthlyAttendance() != null && !data.getMonthlyAttendance().isEmpty()) {
            StringBuilder monthlySb = new StringBuilder();
            for (AttendanceRecord record : data.getMonthlyAttendance()) {
                monthlySb.append(record.getDate()).append(": ").append(record.getStatus()).append("\n");
            }
            monthlyAttendanceText.setText("Monthly Details:\n" + monthlySb.toString().substring(0, Math.min(monthlySb.length(), 500))+"...");
        } else {
            monthlyAttendanceText.setText("No monthly data available.");
        }
    }

    // --- REMOVED: generateAttendanceReport() logic, replaced by launchPieChartReport() call ---
    // private void generateAttendanceReport() { ... }

    // --- Keep helper methods ---
    private <T> void handleApiError(String action, Response<T> response) {
        // ... (error handling logic remains the same) ...
        String errorMsg = "Failed to " + action;
        try { errorMsg += " (Code: " + response.code() + ")"; if (response.errorBody() != null) { errorMsg += " - " + response.errorBody().string(); }
        } catch (IOException e) { Log.e(TAG, "Error reading error body", e); }
        Log.e(TAG, errorMsg);
        if (weeklyAttendanceText != null) weeklyAttendanceText.setText("Error loading weekly data."); // Check for null
        if (monthlyAttendanceText != null) monthlyAttendanceText.setText("Error loading monthly data."); // Check for null
        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
    }

    private void handleApiFailure(String action, Throwable t) {
        // ... (error handling logic remains the same) ...
        Log.e(TAG, "Network error during " + action, t);
        if (weeklyAttendanceText != null) weeklyAttendanceText.setText("Network error."); // Check for null
        if (monthlyAttendanceText != null) monthlyAttendanceText.setText("Network error."); // Check for null
        Toast.makeText(this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
    }
}

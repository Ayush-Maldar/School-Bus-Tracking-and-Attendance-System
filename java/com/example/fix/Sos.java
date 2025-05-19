package com.example.fix;

// ... other imports ...
import android.content.Intent; // Make sure Intent is imported
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Sos extends AppCompatActivity {

    private static final String TAG = "SosActivity";

    private Button sendSosButton;
    private EditText descriptionEditText;
    private RadioGroup severityRadioGroup;
    private ApiService apiService;
    private TokenManager tokenManager;

    // --- Member variable to store the bus ID received via Intent ---
    private int currentBusId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sos); // [cite: main/res/layout/activity_sos.xml]
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tokenManager = new TokenManager(this); // [cite: uploaded:main/java/com/example/fix/TokenManager.java]
        apiService = Retrofitclient.getClient().create(ApiService.class); // [cite: uploaded:main/java/com/example/fix/Retrofitclient.java, uploaded:main/java/com/example/fix/ApiService.java]

        // --- *** FIX: Retrieve Bus ID from Intent extras *** ---
        Intent intent = getIntent();
        // Use the key "CURRENT_BUS_ID" (must match the key used in Bus_incharge.java)
        // Provide a default value (-1) if the extra is not found.
        currentBusId = intent.getIntExtra("CURRENT_BUS_ID", -1);
        Log.d(TAG, "Received bus ID from Intent: " + currentBusId);

        // Check if a valid ID was received
        if (currentBusId <= 0) {
            Log.e(TAG, "Error: Invalid or missing 'CURRENT_BUS_ID' in Intent extras.");
            Toast.makeText(this, "Error: Could not determine the bus for SOS.", Toast.LENGTH_LONG).show();
            finish(); // Close the activity if the bus ID is invalid/missing
            return; // Stop further execution in onCreate
        }
        // --- *** END FIX *** ---

        sendSosButton = findViewById(R.id.send_sos);
        descriptionEditText = findViewById(R.id.emergency_description);
        severityRadioGroup = findViewById(R.id.severity);

        // --- Input Validation: Check if views were found ---
        if (sendSosButton == null || severityRadioGroup == null) {
            Log.e(TAG, "Required UI elements not found.");
            Toast.makeText(this, "UI Error.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // EditText is optional, so we don't need to fail if it's null

        sendSosButton.setOnClickListener(v -> {
            sendSosAlert(); // Call the method to handle the API call [cite: uploaded:main/java/com/example/fix/Sos.java]
        });
    }

    /**
     * Gathers SOS details from the UI and sends them to the backend API.
     */
    private void sendSosAlert() {
        String token = tokenManager.getToken(); // [cite: uploaded:main/java/com/example/fix/TokenManager.java]
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "Authentication error. Please log in again.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Cannot send SOS: Auth token is missing.");
            return;
        }
        String authToken = "Bearer " + token;

        // --- Use the bus ID obtained from the Intent ---
        int busId = getBusIdForCurrentUser(); // Calls the corrected method below
        // ---

        Log.d(TAG, "Attempting to send SOS for Bus ID: " + busId);

        if (busId <= 0) {
            // This check is technically redundant now because we check in onCreate,
            // but it's good practice to keep it.
            Toast.makeText(this, "Error: Invalid Bus ID.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Cannot send SOS: Invalid busId = " + busId);
            return;
        }

        // ... (rest of the sendSosAlert method remains the same - getting severity, message, making API call) ...
        int selectedSeverityId = severityRadioGroup.getCheckedRadioButtonId();
        String severity = "serious";
        if (selectedSeverityId == R.id.non_serious) {
            severity = "non_serious";
        } else if (selectedSeverityId != R.id.serious) {
            Log.w(TAG, "No severity selected, defaulting to 'serious'.");
        }

        String message = "";
        if (descriptionEditText != null) {
            message = descriptionEditText.getText().toString().trim();
        }
        if (message.isEmpty()) {
            message = "Emergency alert triggered!";
        }

        sendSosButton.setEnabled(false);
        sendSosButton.setText("Sending...");

        ApiService.SosRequest sosRequest = new ApiService.SosRequest(busId, message, severity); // [cite: uploaded:main/java/com/example/fix/ApiService.java]
        Call<ResponseBody> call = apiService.triggerSos(authToken, sosRequest); // [cite: uploaded:main/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                runOnUiThread(() -> {
                    sendSosButton.setEnabled(true);
                    sendSosButton.setText(R.string.send_sos); // Use string resource
                });

                if (response.isSuccessful()) {
                    Log.d(TAG, "SOS alert sent successfully via API.");
                    runOnUiThread(() -> {
                        Toast.makeText(Sos.this, "SOS Alert Sent Successfully!", Toast.LENGTH_LONG).show();
                        finish(); // Close SOS screen after successful send
                    });
                } else {
                    String errorMsg = "Failed to send SOS alert.";
                    int responseCode = response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = "Failed to send SOS: " + responseCode + " - " + response.errorBody().string();
                        } else {
                            errorMsg += " Code: " + responseCode;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body from API response", e);
                        errorMsg += " (Code: " + responseCode + ")";
                    }
                    final String finalErrorMsg = errorMsg;
                    Log.e(TAG, "API error sending SOS: " + finalErrorMsg);
                    runOnUiThread(() -> Toast.makeText(Sos.this, finalErrorMsg, Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "Network error occurred while sending SOS", t);
                runOnUiThread(() -> {
                    sendSosButton.setEnabled(true);
                    sendSosButton.setText(R.string.send_sos); // Use string resource
                    Toast.makeText(Sos.this, "Network Error: Could not send SOS. " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Returns the bus ID that was passed to this activity via Intent extras.
     *
     * @return The bus ID, or -1 if it wasn't received correctly.
     */
    private int getBusIdForCurrentUser() {
        // --- *** FIX: Return the member variable populated from the Intent *** ---
        return this.currentBusId;
        // --- *** END FIX *** ---
    }
}
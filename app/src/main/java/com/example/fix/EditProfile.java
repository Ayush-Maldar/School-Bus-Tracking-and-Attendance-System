package com.example.fix;

import android.content.Intent;
// Removed direct SharedPreferences import
import android.os.Bundle;
import android.util.Log; // Import Log
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
// Using org.json for parsing load response, keep Gson for update request
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
// Ensure Constants and TokenManager are imported
// import com.example.fix.Constants;
// import com.example.fix.TokenManager;

public class EditProfile extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity"; // Added TAG

    private TextInputEditText emailEditText, phoneEditText;
    private Button cancelButton, confirmButton, changePasswordButton;
    private ImageView profileImage;

    // --- Use TokenManager ---
    private TokenManager tokenManager;
    private String authToken;
    private String userIdString; // Store userId as String

    // OkHttpClient can be shared
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile); // Make sure this layout exists [cite: main/res/layout/activity_edit_profile.xml]
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.edit_profile_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize TokenManager
        tokenManager = new TokenManager(this); // [cite: main/java/com/example/fix/TokenManager.java]

        // Initialize UI components
        emailEditText = findViewById(R.id.email_edit);
        phoneEditText = findViewById(R.id.phone_edit);
        cancelButton = findViewById(R.id.cancel_button);
        confirmButton = findViewById(R.id.confirm_button);
        changePasswordButton = findViewById(R.id.change_password_button);
        profileImage = findViewById(R.id.profile_image);

        // --- Get user token and ID from TokenManager ---
        authToken = tokenManager.getToken(); // [cite: main/java/com/example/fix/TokenManager.java]
        userIdString = tokenManager.getUserId(); // Get as String [cite: main/java/com/example/fix/TokenManager.java]

        Log.d(TAG, "Retrieved userId: " + userIdString + ", Token present: " + (authToken != null && !authToken.isEmpty()));

        // --- Check if token and userId are valid before loading ---
        if (authToken == null || authToken.isEmpty() || userIdString == null || userIdString.equals("-1")) {
            Log.e(TAG, "Auth token or User ID is invalid/missing. Cannot load or update profile.");
            Toast.makeText(this, "Login session invalid. Please sign in again.", Toast.LENGTH_LONG).show();
            // Optional: Redirect to login screen
            // Intent intent = new Intent(EditProfile.this, SignInActivity.class); // Adjust activity name
            // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            // startActivity(intent);
            finish(); // Close EditProfile if session is invalid
            return; // Stop further execution in onCreate
        }

        // Load user data if userId is valid
        loadUserProfile(); // [cite: main/java/com/example/fix/EditProfile.java]

        // Set button click listeners
        cancelButton.setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> updateUserProfile()); // [cite: main/java/com/example/fix/EditProfile.java]
        changePasswordButton.setOnClickListener(v -> openChangePasswordActivity()); // [cite: main/java/com/example/fix/EditProfile.java]
    }

    private void openChangePasswordActivity() {
        // Navigate to the change password screen
        Intent intent = new Intent(EditProfile.this, ForgotPasswordActivity.class); // [cite: main/java/com/example/fix/ForgotPasswordActivity.java]
        startActivity(intent);
    }

    // Note: This method still uses the UPDATE_CONTACT_URL for GET.
    // Usually, you'd have a separate GET endpoint like /api/user/me.
    // Ensure your backend route for UPDATE_CONTACT_URL also supports GET,
    // or change this to use the correct GET endpoint (e.g., by adding it to ApiService).
    // Note: This method should fetch from the correct GET endpoint, not the update URL.
    private void loadUserProfile() {
        Log.d(TAG, "Loading profile for userId: " + userIdString); // userIdString should be valid here

        // *** FIX: Use the correct URL for fetching profile data ***
        // String url = Constants.UPDATE_CONTACT_URL + userIdString; // Incorrect URL for GET
        String url = Constants.BASE_URL + "user/me"; // Correct URL for GET '/api/user/me' [cite: main/java/com/example/fix/Constants.java]
        Log.d(TAG, "Load Profile URL: " + url);

        // Ensure authToken is valid
        if (authToken == null || authToken.isEmpty()) {
            Log.e(TAG, "Auth token missing, cannot load profile.");
            Toast.makeText(this, "Authentication error.", Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + authToken)
                .get() // Ensure GET method is used
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load profile network request", e);
                runOnUiThread(() -> Toast.makeText(EditProfile.this,
                        "Failed to load profile: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : null;
                final int responseCode = response.code();
                Log.d(TAG, "Load Profile Response Code: " + responseCode);

                if (response.isSuccessful() && responseBody != null) {
                    try {
                        // *** FIX: Parse the response from /api/user/me ***
                        // The response from /api/user/me is directly the user object
                        JSONObject userObject = new JSONObject(responseBody);

                        // Safely get email and phone
                        final String email = userObject.optString("email", "");
                        final String phone = userObject.optString("phone_number", "");
                        // You could also get first_name, last_name here if needed

                        runOnUiThread(() -> {
                            Log.d(TAG, "Profile loaded successfully. Email: " + email + ", Phone: " + phone);
                            emailEditText.setText(email);
                            phoneEditText.setText(phone);
                        });

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing profile data JSON from /user/me: " + responseBody, e);
                        runOnUiThread(() -> Toast.makeText(EditProfile.this,
                                "Error reading profile data",
                                Toast.LENGTH_SHORT).show());
                    }
                } else {
                    // Handle error (e.g., token expired - 401/403, user not found - 404)
                    String errorMsg = "Failed to load profile (Error: " + responseCode + ")";
                    if (responseCode == 401 || responseCode == 403) {
                        errorMsg = "Session invalid. Please login again.";
                        // Optional: Redirect to login
                    } else if (responseBody != null) {
                        // Try to parse server's error message if it's JSON
                        try {
                            JSONObject errorObject = new JSONObject(responseBody);
                            errorMsg = errorObject.optString("message", errorMsg);
                        } catch (JSONException e) {
                            // Ignore if error body is not JSON
                            Log.w(TAG, "Could not parse error body as JSON: " + responseBody);
                        }
                    }
                    Log.e(TAG, "Failed to load profile. Code: " + responseCode + ", Body: " + responseBody);
                    final String finalErrorMsg = errorMsg;
                    runOnUiThread(() -> Toast.makeText(EditProfile.this, finalErrorMsg, Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void updateUserProfile() {
        // Get values from input fields
        String email = emailEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();

        // Validate input
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.isEmpty()) { // Basic check, add more specific phone validation if needed
            phoneEditText.setError("Phone number is required");
            Toast.makeText(this, "Phone number is required.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button during request
        confirmButton.setEnabled(false);
        confirmButton.setText("Updating...");

        // Create JSON for request using Gson's JsonObject
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("email", email);
        jsonObject.addProperty("phone_number", phone);

        // Create request body
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(jsonObject.toString(), JSON);

        // Construct the correct URL using the valid userIdString
        String url = Constants.UPDATE_CONTACT_URL + userIdString; // [cite: main/java/com/example/fix/Constants.java]
        Log.d(TAG, "Update Profile URL: " + url);
        Log.d(TAG, "Update Payload: " + jsonObject.toString());


        // Create request
        Request request = new Request.Builder()
                .url(url)
                .put(requestBody) // Use PUT for updates
                .addHeader("Authorization", "Bearer " + authToken)
                .build();

        // Execute request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Update profile network request failed", e);
                runOnUiThread(() -> {
                    confirmButton.setEnabled(true);
                    confirmButton.setText("Confirm");
                    Toast.makeText(EditProfile.this,
                            "Update failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseBody = response.body() != null ? response.body().string() : null;
                final boolean isSuccess = response.isSuccessful();
                final int responseCode = response.code();
                Log.d(TAG, "Update Profile Response Code: " + responseCode);

                runOnUiThread(() -> {
                    confirmButton.setEnabled(true);
                    confirmButton.setText("Confirm");

                    if (isSuccess) {
                        Log.d(TAG, "Profile updated successfully.");
                        Toast.makeText(EditProfile.this,
                                "Profile updated successfully",
                                Toast.LENGTH_SHORT).show();

                        // Optional: Update TokenManager if response contains updated info
                        // tokenManager.saveUserDetails(...)

                        finish(); // Close the activity on success
                    } else {
                        Log.e(TAG, "Failed to update profile. Code: " + responseCode + ", Body: " + responseBody);
                        String errorMessage = "Failed to update profile. Please try again."; // Default error
                        if (responseBody != null) {
                            try {
                                JSONObject errorObject = new JSONObject(responseBody);
                                errorMessage = errorObject.optString("message", errorMessage); // Use server message if available
                            } catch (JSONException e) {
                                Log.e(TAG, "Failed to parse error response JSON: " + responseBody, e);
                            }
                        }
                        Toast.makeText(EditProfile.this, errorMessage, Toast.LENGTH_LONG).show(); // Show longer toast for errors
                    }
                });
            }
        });
    }
}
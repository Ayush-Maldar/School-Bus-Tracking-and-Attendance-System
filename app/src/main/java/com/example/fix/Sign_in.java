package com.example.fix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject; // Ensure JSONObject is imported

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
// Make sure Constants class is imported if not already
// import com.example.fix.Constants;

public class Sign_in extends AppCompatActivity {
    private static final String TAG = "SignInActivity";
    private static final String API_URL = Constants.SIGN_IN_URL;

    private EditText emailEditText;
    private EditText passwordEditText;
    private CheckBox showPasswordCheckBox;
    private Button forgotPasswordButton;
    private Button signInButton;

    private OkHttpClient client;
    private String userType;
    // SharedPreferences is now managed via TokenManager
    private TokenManager tokenManager; // Use TokenManager

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        forgotPasswordButton = findViewById(R.id.forgot_password);
        signInButton = findViewById(R.id.sign_in);

        // Initialize HTTP client
        client = new OkHttpClient();

        // Initialize TokenManager
        tokenManager = new TokenManager(this); // Pass context

        // Get user type from intent
        userType = getIntent().getStringExtra("user_type");
        Log.d(TAG, "Sign in requested for user type: " + userType);

        // Optional: Check if already logged in (using TokenManager)
        // This check might need refinement based on desired flow
        // if (tokenManager.hasToken() && userType.equals(tokenManager.getUserRole())) {
        //     Log.d(TAG, "User already logged in as " + userType + ", navigating to dashboard.");
        //     navigateToUserDashboard(userType);
        // } else {
        //     Log.d(TAG, "User not logged in or attempting to log in as different type.");
        // }


        // Set up forgot password button
        forgotPasswordButton.setOnClickListener(v -> {
            Intent intent = new Intent(Sign_in.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Set up sign-in button
        signInButton.setOnClickListener(v -> {
            if (validateInputs()) {
                signIn();
            }
        });
    }

    private boolean validateInputs() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email");
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            return false;
        }
        // Adjust password length validation if needed (e.g., 8)
        if (password.length() < 8) { // Match server validation (min 8)
            passwordEditText.setError("Password must be at least 8 characters");
            return false;
        }
        return true;
    }

    private void signIn() {
        signInButton.setEnabled(false);
        signInButton.setText("Signing In...");

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        Log.d(TAG, "Attempting sign in for email: " + email + ", userType: " + userType);

        try {
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("email", email);
            requestBodyJson.put("password", password);
            if (userType != null) {
                requestBodyJson.put("userType", userType);
            }

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(requestBodyJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        signInButton.setEnabled(true);
                        signInButton.setText("Sign In");
                        Toast.makeText(Sign_in.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Network error during sign in", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body() != null ? response.body().string() : null;
                    final boolean isSuccessful = response.isSuccessful();
                    final int responseCode = response.code();

                    Log.d(TAG, "Server response code: " + responseCode);
                    Log.d(TAG, "Server response body: " + responseBody);

                    runOnUiThread(() -> {
                        signInButton.setEnabled(true);
                        signInButton.setText("Sign In");

                        if (responseBody == null) {
                            Log.e(TAG, "Received null response body from server.");
                            Toast.makeText(Sign_in.this, "Error: Empty server response", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            boolean success = jsonResponse.optBoolean("success", false);
                            String message = jsonResponse.optString("message", "An unknown error occurred.");

                            if (isSuccessful && success) {
                                String token = jsonResponse.optString("token", null);
                                String responseUserType = jsonResponse.optString("userType", null);
                                String userId = jsonResponse.optString("userId", null); // Keep as String

                                // Extract user details from nested 'user' object
                                JSONObject userObject = jsonResponse.optJSONObject("user");
                                String userName = "N/A";
                                String userEmail = "N/A";
                                String userPhoneNumber = "N/A"; // Initialize with default

                                if (userObject != null) {
                                    userName = userObject.optString("name", "N/A");
                                    userEmail = userObject.optString("email", "N/A");
                                    // *** FIX: Correctly extract phone_number from userObject ***
                                    userPhoneNumber = userObject.optString("phone_number", "N/A");
                                }

                                Log.d(TAG, "Login successful. Token: " + (token != null) + ", UserID: " + userId + ", Type: " + responseUserType + ", Name: " + userName + ", Email: " + userEmail + ", Phone: " + userPhoneNumber);

                                if (token == null || token.isEmpty() || responseUserType == null || userId == null) {
                                    Log.e(TAG, "Login success reported, but essential data missing in response!");
                                    Toast.makeText(Sign_in.this, "Login error: Incomplete data received", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                // Use TokenManager to save all details (now includes correct phone number)
                                tokenManager.saveUserDetails(token, userId, userEmail, responseUserType, userName, userPhoneNumber);

                                // Verify save (optional debug step)
                                Log.d(TAG, "Token saved. Verifying... Token from Manager: " + (tokenManager.getToken() != null));
                                Log.d(TAG, "User Name from Manager: " + tokenManager.getUserName());
                                Log.d(TAG, "User Phone from Manager: " + tokenManager.getUserPhoneNumber()); // Verify phone

                                navigateToUserDashboard(responseUserType);

                            } else {
                                Log.w(TAG, "Login failed. Server message: " + message + " (HTTP code: " + responseCode + ")");
                                Toast.makeText(Sign_in.this, message, Toast.LENGTH_SHORT).show();
                            }

                        } catch (JSONException e) {
                            Toast.makeText(Sign_in.this, "Error parsing server response", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "JSON parsing error. Response: " + responseBody, e);
                        }
                    });
                }
            });

        } catch (JSONException e) {
            signInButton.setEnabled(true);
            signInButton.setText("Sign In");
            Toast.makeText(this, "Error creating login request", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "JSON creation error for login request", e);
        }
    }

    private void navigateToUserDashboard(String userType) {
        Intent intent = null;
        Log.d(TAG, "Navigating to dashboard for user type: " + userType);

        switch (userType) {
            case "parent":
                intent = new Intent(Sign_in.this, Parent_activity.class);
                break;
            case "bus_incharge":
                intent = new Intent(Sign_in.this, Bus_incharge.class);
                break;
            case "school_authority":
                intent = new Intent(Sign_in.this, School_authority.class);
                break;
            default:
                Log.e(TAG, "Unknown user type received for navigation: " + userType);
                Toast.makeText(this, "Login successful, but unknown user type: " + userType, Toast.LENGTH_SHORT).show();
                return;
        }

        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Log.e(TAG, "Intent remained null during navigation logic for type: " + userType);
        }
    }
}
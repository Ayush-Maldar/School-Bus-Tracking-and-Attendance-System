package com.example.fix;

import android.content.Intent;
// NEW: Add necessary imports for Retrofit
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
// (Keep existing imports: Bundle, Log, View, etc.)
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Make sure you have UserProfile class created
// import com.example.fix.UserProfile;

public class Info extends AppCompatActivity {
    private static final String TAG = "InfoActivity";
    ImageButton editb;
    ImageButton signOutButton;
    TextView nameTextView, emailTextView, numberTextView;
    ImageView profileImageView;
    TokenManager tokenManager; // Use TokenManager
    // NEW: Add ApiService instance
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_info);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize TokenManager
        tokenManager = new TokenManager(this);

        // NEW: Initialize Retrofit/ApiService
        // Ensure Retrofitclient.java is correctly set up
        apiService = Retrofitclient.getClient().create(ApiService.class); // [cite: main/java/com/example/fix/Retrofitclient.java]

        // Initialize Views
        editb = findViewById(R.id.edit_info_button);
        signOutButton = findViewById(R.id.sign_out_button);
        nameTextView = findViewById(R.id.name);
        emailTextView = findViewById(R.id.email);
        numberTextView = findViewById(R.id.number);
        profileImageView = findViewById(R.id.image);

        // Load User Info - This will now try the API first
        loadUserInfo(); // [cite: main/java/com/example/fix/Info.java]

        // --- Listeners ---
        editb.setOnClickListener(view -> {
            Intent i = new Intent(Info.this, EditProfile.class);
            // Pass User ID for EditProfile API call
            // EditProfile needs to handle retrieving this extra
            // Ensure getUserId() returns the correct type (String or int)
            i.putExtra("USER_ID", tokenManager.getUserId()); // Pass User ID [cite: main/java/com/example/fix/TokenManager.java]
            startActivity(i); // [cite: main/java/com/example/fix/Info.java]
        });

        signOutButton.setOnClickListener(v -> {
            tokenManager.clearToken(); // Clear token using TokenManager [cite: main/java/com/example/fix/TokenManager.java]
            Log.d(TAG, "User signed out, token cleared.");

            Intent intent = new Intent(Info.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // [cite: main/java/com/example/fix/Info.java]
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload user info when returning to the activity, e.g., after editing
        loadUserInfo(); // [cite: main/java/com/example/fix/Info.java]
    }

    private void loadUserInfo() {
        String token = tokenManager.getToken(); // [cite: main/java/com/example/fix/TokenManager.java]

        if (token == null || token.isEmpty()) {
            Log.w(TAG, "No token found. Cannot fetch profile from API.");
            displayInfoFromTokenManager(); // Display whatever is stored locally [cite: main/java/com/example/fix/Info.java]
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure the ApiService instance is available
        if (apiService == null) {
            Log.e(TAG, "ApiService is not initialized!");
            displayInfoFromTokenManager(); // Fallback if ApiService fails to init
            Toast.makeText(this, "Error initializing network service.", Toast.LENGTH_SHORT).show();
            return;
        }

        String authToken = "Bearer " + token; // Add Bearer prefix
        Log.d(TAG, "Fetching user profile with token.");

        // NEW: API Call to /api/user/me
        Call<UserProfile> call = apiService.getUserProfile(authToken); // [cite: main/java/com/example/fix/ApiService.java]
        call.enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Successfully fetched user profile from API.");
                    UserProfile userProfile = response.body(); // [cite: main/java/com/example/fix/UserProfile.java]
                    // NEW: Update UI with fresh data from API
                    updateUI(
                            userProfile.getFullName(), // Use helper method from UserProfile [cite: main/java/com/example/fix/UserProfile.java]
                            userProfile.getEmail(), // [cite: main/java/com/example/fix/UserProfile.java]
                            userProfile.getPhoneNumber() // [cite: main/java/com/example/fix/UserProfile.java]
                    ); // [cite: main/java/com/example/fix/Info.java]
                    // Optional: Update TokenManager with latest data if necessary
                    // tokenManager.saveUserDetails(token, String.valueOf(userProfile.getUserId()), userProfile.getEmail(), userProfile.getRole(), userProfile.getFullName(), userProfile.getPhoneNumber());

                } else {
                    Log.e(TAG, "Failed to fetch profile from API. Code: " + response.code());
                    // Fallback to local data if API fails (e.g., network issue, expired token)
                    displayInfoFromTokenManager(); // [cite: main/java/com/example/fix/Info.java]
                    // Provide more context for the error if possible
                    String errorMsg = "Could not refresh profile. Showing stored info.";
                    if (response.code() == 401 || response.code() == 403) {
                        errorMsg = "Session expired or invalid. Please log in again.";
                        // Consider redirecting to login here
                    }
                    Toast.makeText(Info.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfile> call, Throwable t) {
                Log.e(TAG, "API call failed: " + t.getMessage(), t);
                // Fallback to local data on network failure
                displayInfoFromTokenManager(); // [cite: main/java/com/example/fix/Info.java]
                Toast.makeText(Info.this, "Network error. Showing stored info.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // NEW: Method to display data from TokenManager (Fallback)
    private void displayInfoFromTokenManager() {
        Log.d(TAG, "Displaying info from TokenManager (fallback).");
        updateUI(
                tokenManager.getUserName(), // [cite: main/java/com/example/fix/TokenManager.java]
                tokenManager.getUserEmail(), // [cite: main/java/com/example/fix/TokenManager.java]
                tokenManager.getUserPhoneNumber() // [cite: main/java/com/example/fix/TokenManager.java]
        ); // [cite: main/java/com/example/fix/Info.java]
    }

    // NEW: Helper method to update UI TextViews
    private void updateUI(String name, String email, String phone) {
        // Check if views are actually found in the layout before setting text
        if (nameTextView != null) {
            nameTextView.setText(name != null && !name.trim().isEmpty() ? name.trim() : "User Name"); // Show default if null/empty
        } else {
            Log.w(TAG, "Name TextView (R.id.name) not found in layout.");
        }

        if (emailTextView != null) {
            emailTextView.setText("Email: " + (email != null ? email : "N/A"));
        } else {
            Log.w(TAG, "Email TextView (R.id.email) not found in layout.");
        }

        if (numberTextView != null) {
            numberTextView.setText("Phone: " + (phone != null ? phone : "N/A"));
        } else {
            Log.w(TAG, "Number TextView (R.id.number) not found in layout.");
        }

        // Basic default profile image
        if (profileImageView != null) {
            profileImageView.setImageResource(R.drawable.profile); // Use a placeholder
        } else {
            Log.w(TAG, "Profile ImageView (R.id.image) not found in layout.");
        }
    }
}
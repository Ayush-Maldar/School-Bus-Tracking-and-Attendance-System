package com.example.fix;

// Android & Core Imports
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;

// Activity & UI Imports
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull; // Needed for FCM Task listener
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Firebase Messaging Imports
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

// Networking Imports (OkHttp & JSON)
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
import okhttp3.ResponseBody;

// Your App's Classes
import com.example.fix.TokenManager;
import com.example.fix.Constants; // Assuming Constants.java holds BASE_URL

public class SplashScreen extends AppCompatActivity {

    private static final String TAG = "SplashScreen"; // Updated TAG for clarity
    // URL for the FCM token update endpoint
    private static final String UPDATE_FCM_API_URL = Constants.BASE_URL + "user/fcm-token";

    // OkHttpClient for sending the FCM token
    private OkHttpClient client;

    // Launcher for Notification Permission Request (Android 13+)
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
                    Toast.makeText(this, "Notifications permission denied. You may not receive important updates.", Toast.LENGTH_LONG).show();
                }
                // Proceed with app startup regardless of permission result
                proceedWithAppStartup();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);

        // Initialize OkHttpClient
        client = new OkHttpClient();

        // EdgeToEdge setup
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.intropage), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Play GIF animation
        ImageView imageView = findViewById(R.id.image1);
        if (imageView != null) {
            imageView.setImageResource(R.drawable.bus_animation);
            if (imageView.getDrawable() instanceof Animatable) {
                ((Animatable) imageView.getDrawable()).start();
            }
        } else {
            Log.w(TAG, "ImageView 'image1' not found.");
        }

        // Start the permission check flow -> which then calls proceedWithAppStartup()
        askNotificationPermission();
    }

    /**
     * Checks if POST_NOTIFICATIONS permission is needed and requests it if necessary.
     * Calls proceedWithAppStartup() once the check/request is complete.
     */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission already granted.");
                proceedWithAppStartup();
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Consider showing a dialog explaining why permission is needed before requesting again
                Log.w(TAG, "Rationale should be shown for POST_NOTIFICATIONS (not implemented), requesting directly.");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                // Note: proceedWithAppStartup() will be called by the launcher callback
            } else {
                Log.d(TAG, "Requesting POST_NOTIFICATIONS permission.");
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                // Note: proceedWithAppStartup() will be called by the launcher callback
            }
        } else {
            Log.d(TAG, "POST_NOTIFICATIONS permission not required (API < 33).");
            proceedWithAppStartup(); // Proceed directly on older versions
        }
    }

    /**
     * Continues app startup after permissions are handled.
     * Checks login status, sends FCM token if logged in, and navigates.
     */
    private void proceedWithAppStartup() {
        Log.d(TAG, "Proceeding with app startup...");

        TokenManager tokenManager = new TokenManager(this);
        String savedToken = tokenManager.getToken();
        String savedUserType = tokenManager.getUserRole();

        Log.d(TAG, "Checking saved session: Token exists? " + (savedToken != null && !savedToken.isEmpty()) + ", UserType: " + savedUserType);

        final Intent nextIntent;
        boolean isLoggedIn = savedToken != null && !savedToken.isEmpty() && savedUserType != null && !savedUserType.isEmpty();

        if (isLoggedIn) {
            // --- User is logged in ---
            Log.d(TAG, "Valid session found. Getting/Sending FCM Token and Navigating...");

            // *** Get FCM Token and send it to server ***
            getAndSendFcmToken(savedToken); // Pass the user's auth token

            // Determine dashboard activity
            switch (savedUserType) {
                case "parent": nextIntent = new Intent(this, Parent_activity.class); break;
                case "bus_incharge": nextIntent = new Intent(this, Bus_incharge.class); break;
                case "school_authority": nextIntent = new Intent(this, School_authority.class); break;
                default:
                    Log.e(TAG, "Unknown user type found: " + savedUserType + ". Navigating to MainActivity.");
                    nextIntent = new Intent(this, MainActivity.class);
                    break;
            }
            nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            // --- User is not logged in ---
            Log.d(TAG, "No valid session found. Navigating to MainActivity.");
            nextIntent = new Intent(this, MainActivity.class);
            nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }

        // Navigate after a short delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(nextIntent);
            finish();
        }, 500); // Delay allows permission dialog to close smoothly
    }

    // --- FCM Token Handling Methods (Copied/Adapted from Sign_in.java example) ---

    /**
     * Retrieves the FCM registration token and triggers sending it to the backend server.
     * @param authToken The user's current authentication token (without "Bearer ").
     */
    private void getAndSendFcmToken(String authToken) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> { // Using lambda expression
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return; // Don't proceed if token fetching fails
                    }
                    // Get new FCM registration token
                    String fcmToken = task.getResult();
                    Log.d(TAG, "FCM Registration Token fetched: " + fcmToken);
                    // Send token to your server
                    sendFcmTokenToServer(authToken, fcmToken);
                });
    }

    /**
     * Sends the FCM token to the backend using OkHttp.
     * @param authToken The user's authentication token (without "Bearer ").
     * @param fcmToken The FCM token to send.
     */
    private void sendFcmTokenToServer(String authToken, String fcmToken) {
        if (fcmToken == null || fcmToken.isEmpty()) {
            Log.w(TAG, "FCM token is null or empty, cannot send to server.");
            return;
        }
        // Add Bearer prefix for the Authorization header
        String authHeader = "Bearer " + authToken;

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject jsonObject = new JSONObject();
        try {
            // The backend endpoint expects the token in a field named "fcmToken"
            jsonObject.put("fcmToken", fcmToken);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for FCM token update", e);
            return;
        }
        RequestBody body = RequestBody.create(jsonObject.toString(), JSON);

        // Build the PUT request to the server endpoint
        Request request = new Request.Builder()
                .url(UPDATE_FCM_API_URL) // Use the constant defined above
                .put(body) // Use PUT method
                .addHeader("Authorization", authHeader) // Add authentication header
                .build();

        Log.d(TAG, "Sending FCM token to server: " + UPDATE_FCM_API_URL);
        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Log network errors but typically don't bother the user
                Log.e(TAG, "Failed to send FCM token to server (Network Error): " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                // Use try-with-resources to ensure response body is closed
                try (ResponseBody responseBody = response.body()) {
                    if (response.isSuccessful()) {
                        // Log success
                        Log.d(TAG, "FCM token successfully updated on server.");
                    } else {
                        // Log server-side errors
                        String errorBodyStr = responseBody != null ? responseBody.string() : "Unknown error";
                        Log.e(TAG, "Failed to update FCM token on server. Code: " + response.code() + ", Body: " + errorBodyStr);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading response body for FCM token update", e);
                }
            }
        });
    }
    // --- End FCM Token Handling Methods ---

}

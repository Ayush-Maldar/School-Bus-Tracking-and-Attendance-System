package com.example.fix;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import com.example.fix.Constants;
public class ForgotPasswordActivity extends AppCompatActivity {
    private static final String TAG = "ForgotPasswordActivity";
    private static final String REQUEST_RESET_URL = Constants.REQUEST_RESET_URL;
    private static final String RESET_PASSWORD_URL = Constants.RESET_PASSWORD_URL;

    private EditText emailEditText;
    private EditText newPasswordEditText;
    private EditText confirmPasswordEditText;
    private EditText tokenEditText;
    private Button resetButton;
    private TextView statusTextView;
    private View tokenInputContainer;

    private OkHttpClient client;
    private boolean isTokenRequested = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize views
        emailEditText = findViewById(R.id.email);
        tokenEditText = findViewById(R.id.reset_token);
        newPasswordEditText = findViewById(R.id.new_password);
        confirmPasswordEditText = findViewById(R.id.confirm_password);
        resetButton = findViewById(R.id.reset_button);
        statusTextView = findViewById(R.id.status_text);
        tokenInputContainer = findViewById(R.id.token_container);

        // Initially hide token and password fields
        tokenInputContainer.setVisibility(View.GONE);
        newPasswordEditText.setVisibility(View.GONE);
        confirmPasswordEditText.setVisibility(View.GONE);

        // Initialize HTTP client
        client = new OkHttpClient();

        // Set up reset button
        resetButton.setOnClickListener(v -> {
            if (!isTokenRequested) {
                if (validateEmail()) {
                    requestPasswordReset();
                }
            } else {
                if (validateResetInputs()) {
                    resetPassword();
                }
            }
        });

        // Set up back button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
    }

    private boolean validateEmail() {
        String email = emailEditText.getText().toString().trim();

        // Validate email
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email");
            return false;
        }

        return true;
    }

    private boolean validateResetInputs() {
        String token = tokenEditText.getText().toString().trim();
        String newPassword = newPasswordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Validate token
        if (TextUtils.isEmpty(token)) {
            tokenEditText.setError("Reset token is required");
            return false;
        }

        // Validate password
        if (TextUtils.isEmpty(newPassword)) {
            newPasswordEditText.setError("New password is required");
            return false;
        }

        if (newPassword.length() < 8) {
            newPasswordEditText.setError("Password must be at least 8 characters");
            return false;
        }

        // Validate password confirmation
        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordEditText.setError("Please confirm your password");
            return false;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return false;
        }

        return true;
    }

    private void requestPasswordReset() {
        // Show progress indicator
        resetButton.setEnabled(false);
        resetButton.setText("Sending Request...");
        statusTextView.setVisibility(View.GONE);

        String email = emailEditText.getText().toString().trim();

        try {
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("email", email);

            // Create request
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, requestBody.toString());

            Request request = new Request.Builder()
                    .url(REQUEST_RESET_URL)
                    .post(body)
                    .build();

            // Execute request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resetButton.setEnabled(true);
                        resetButton.setText("Request Reset Token");
                        Toast.makeText(ForgotPasswordActivity.this,
                                "Network error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Network error", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        resetButton.setEnabled(true);
                        resetButton.setText("Reset Password");

                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String message = jsonResponse.getString("message");

                            // Show check email instructions
                            showEmailSentDialog(email);

                            // Update UI to show token input and password fields
                            isTokenRequested = true;
                            emailEditText.setEnabled(false);
                            tokenInputContainer.setVisibility(View.VISIBLE);
                            newPasswordEditText.setVisibility(View.VISIBLE);
                            confirmPasswordEditText.setVisibility(View.VISIBLE);
                            resetButton.setText("Reset Password");

                        } catch (JSONException e) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Error parsing response",
                                    Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "JSON parsing error", e);
                        }
                    });
                }
            });

        } catch (JSONException e) {
            resetButton.setEnabled(true);
            resetButton.setText("Request Reset Token");
            Toast.makeText(this, "Error creating request", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "JSON creation error", e);
        }
    }

    private void showEmailSentDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Check Your Email")
                .setMessage("If an account exists with " + email + ", a reset token has been sent. Please check your email and enter the token below.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void resetPassword() {
        // Show progress indicator
        resetButton.setEnabled(false);
        resetButton.setText("Resetting Password...");
        statusTextView.setVisibility(View.GONE);

        String token = tokenEditText.getText().toString().trim();
        String newPassword = newPasswordEditText.getText().toString().trim();

        try {
            // Create JSON request body
            JSONObject requestBody = new JSONObject();
            requestBody.put("token", token);
            requestBody.put("newPassword", newPassword);

            // Create request
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(JSON, requestBody.toString());

            Request request = new Request.Builder()
                    .url(RESET_PASSWORD_URL)
                    .post(body)
                    .build();

            // Execute request asynchronously
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        resetButton.setEnabled(true);
                        resetButton.setText("Reset Password");
                        Toast.makeText(ForgotPasswordActivity.this,
                                "Network error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Network error", e);
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseBody = response.body().string();

                    runOnUiThread(() -> {
                        resetButton.setEnabled(true);
                        resetButton.setText("Reset Password");

                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String message = jsonResponse.getString("message");

                            if (response.isSuccessful()) {
                                // Show success message
                                statusTextView.setText("Password reset successful! You can now sign in with your new password.");
                                statusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                statusTextView.setVisibility(View.VISIBLE);

                                // Show success dialog and close activity
                                new AlertDialog.Builder(ForgotPasswordActivity.this)
                                        .setTitle("Success")
                                        .setMessage("Password reset successful! You can now sign in with your new password.")
                                        .setPositiveButton("Sign In", (dialog, which) -> finish())
                                        .setCancelable(false)
                                        .show();
                            } else {
                                // Show error
                                statusTextView.setText(message);
                                statusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                statusTextView.setVisibility(View.VISIBLE);
                            }

                        } catch (JSONException e) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Error parsing response",
                                    Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "JSON parsing error", e);
                        }
                    });
                }
            });

        } catch (JSONException e) {
            resetButton.setEnabled(true);
            resetButton.setText("Reset Password");
            Toast.makeText(this, "Error creating request", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "JSON creation error", e);
        }
    }
}
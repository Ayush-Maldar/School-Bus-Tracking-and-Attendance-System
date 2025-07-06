package com.example.fix;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

import com.google.gson.annotations.SerializedName;

import java.util.concurrent.TimeUnit;
import com.example.fix.Constants;
public class Sign_up extends AppCompatActivity {

    private EditText firstNameEditText, lastNameEditText, emailEditText, phoneNumberEditText, passwordEditText, reEnterPasswordEditText;
    private CheckBox showPasswordCheckBox;
    private RadioGroup roleRadioGroup, busStaffTypeRadioGroup;
    private LinearLayout busStaffTypeLayout;
    private Button signUpButton, alreadyHaveAccount;
    private ApiService apiService;

    // Server URL should be moved to a configuration file
    private static final String SERVER_URL = Constants.SIGN_UP_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        // Initialize UI components
        initializeViews();

        // Set up network client and API service
        setupApiService();

        // Set listeners for UI components
        setupListeners();
    }

    private void initializeViews() {
        firstNameEditText = findViewById(R.id.first_name);
        lastNameEditText = findViewById(R.id.last_name);
        emailEditText = findViewById(R.id.email);
        phoneNumberEditText = findViewById(R.id.phone_number);
        passwordEditText = findViewById(R.id.password);
        reEnterPasswordEditText = findViewById(R.id.re_enter_password);
        roleRadioGroup = findViewById(R.id.role);
        busStaffTypeLayout = findViewById(R.id.bus_staff_type_layout);
        busStaffTypeRadioGroup = findViewById(R.id.bus_staff_type);
        signUpButton = findViewById(R.id.sign_up);
        alreadyHaveAccount = findViewById(R.id.already_have_account);
        alreadyHaveAccount.setOnClickListener(v -> {
            Intent intent = new Intent(Sign_up.this, User_type.class);
            startActivity(intent);
        });
    }

    private void setupApiService() {
        // Configure OkHttp with appropriate timeouts and logging
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SERVER_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    private void setupListeners() {
        // Toggle password visibility
        // Show/hide bus staff type selection based on role selection
        roleRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.bus_incharge) {
                busStaffTypeLayout.setVisibility(View.VISIBLE);
            } else {
                busStaffTypeLayout.setVisibility(View.GONE);
            }
        });

        // Handle sign up button click
        signUpButton.setOnClickListener(v -> {
            if (validateInputs()) {
                registerUser();
            }
        });
    }

    private boolean validateInputs() {
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phoneNumber = phoneNumberEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String reEnterPassword = reEnterPasswordEditText.getText().toString();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            showToast("All fields are required");
            return false;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showToast("Invalid email format");
            return false;
        }

        // Add password strength validation to match server requirements
        if (password.length() < 8) {
            showToast("Password must be at least 8 characters long");
            return false;
        }

        if (!password.equals(reEnterPassword)) {
            showToast("Passwords do not match");
            return false;
        }

        if (roleRadioGroup.getCheckedRadioButtonId() == -1) {
            showToast("Please select user role");
            return false;
        }

        // Validate bus staff type if bus_incharge is selected
        if (roleRadioGroup.getCheckedRadioButtonId() == R.id.bus_incharge) {
            if (busStaffTypeRadioGroup.getCheckedRadioButtonId() == -1) {
                showToast("Please select bus staff type (Driver or Attendant)");
                return false;
            }
        }

        return true;
    }

    private void registerUser() {
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phoneNumber = phoneNumberEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        int selectedId = roleRadioGroup.getCheckedRadioButtonId();
        RadioButton radioButton = findViewById(selectedId);
        String roleText = radioButton.getText().toString();

        // Map the displayed text to the server's expected values
        String role = mapRoleToServerValue(roleText);

        if (role == null) {
            showToast("Invalid role selected");
            return;
        }

        // Get bus staff type if applicable
        String busStaffType = null;
        if (role.equals("bus_incharge") && busStaffTypeLayout.getVisibility() == View.VISIBLE) {
            int busStaffTypeId = busStaffTypeRadioGroup.getCheckedRadioButtonId();
            if (busStaffTypeId != -1) {
                RadioButton busStaffTypeRadioButton = findViewById(busStaffTypeId);
                busStaffType = busStaffTypeRadioButton.getText().toString().toLowerCase();
            }
        }

        RegistrationRequest request = new RegistrationRequest(firstName, lastName, email, phoneNumber, password, role, busStaffType);

        apiService.registerUser(request).enqueue(new Callback<RegistrationResponse>() {
            @Override
            public void onResponse(Call<RegistrationResponse> call, Response<RegistrationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    RegistrationResponse result = response.body();
                    showToast(result.getMessage());

                    // Handle successful registration
                    if (result.getUser() != null) {
                        // Get the user ID and role
                        int userId = result.getUser().getUserId();
                        String userRole = result.getUser().getRole();

                        // Navigate to the appropriate activity based on role
                        navigateToRoleActivity(userId, userRole, email);
                    }

                } else {
                    // Parse error response
                    String errorMessage = getErrorMessage(response.errorBody());
                    showToast("Registration failed: " + errorMessage);
                }
            }

            @Override
            public void onFailure(Call<RegistrationResponse> call, Throwable t) {
                showToast("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Navigate to the appropriate activity based on user role
     * @param userId The user's ID
     * @param role The user's role
     * @param email The user's email
     */
    private void navigateToRoleActivity(int userId, String role, String email) {
        Intent intent = null;

        switch (role) {
            case "parent":
                intent = new Intent(Sign_up.this, Parent_activity.class);
                break;
            case "bus_incharge":
                intent = new Intent(Sign_up.this, Bus_incharge.class);
                break;
            case "school_authority":
                intent = new Intent(Sign_up.this, School_authority.class);
                break;
            default:
                showToast("Unknown role: " + role);
                return;
        }

        // Pass user data to the next activity
        if (intent != null) {
            intent.putExtra("USER_ID", userId);
            intent.putExtra("USER_EMAIL", email);
            intent.putExtra("USER_ROLE", role);

            // Start the activity and finish the current one
            startActivity(intent);
            finish();
        }
    }

    /**
     * Maps the UI display text to the server's expected role values
     * @param roleText The text displayed on the RadioButton
     * @return One of the server's expected role values: "parent", "bus_incharge", or "school_authority"
     */
    private String mapRoleToServerValue(String roleText) {
        switch (roleText.toLowerCase()) {
            case "parent":
                return "parent";
            case "bus incharge":
            case "bus in-charge":
            case "bus in charge":
            case "bus":
                return "bus_incharge";
            case "school authority":
            case "school admin":
            case "school":
                return "school_authority";
            default:
                return null;
        }
    }

    private String getErrorMessage(ResponseBody errorBody) {
        try {
            return errorBody != null ? errorBody.string() : "Unknown error";
        } catch (Exception e) {
            return "Error parsing response";
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    public interface ApiService {
        @POST("api/register")
        Call<RegistrationResponse> registerUser(@Body RegistrationRequest request);
    }

    public static class RegistrationRequest {
        @SerializedName("first_name")
        private final String firstName;

        @SerializedName("last_name")
        private final String lastName;

        @SerializedName("email")
        private final String email;

        @SerializedName("phone_number")
        private final String phoneNumber;

        @SerializedName("password")
        private final String password;

        @SerializedName("role")
        private final String role;

        @SerializedName("bus_staff_type")
        private final String busStaffType;

        public RegistrationRequest(String firstName, String lastName, String email, String phoneNumber, String password, String role, String busStaffType) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phoneNumber = phoneNumber;
            this.password = password;
            this.role = role;
            this.busStaffType = busStaffType;
        }
    }

    public static class RegistrationResponse {
        @SerializedName("message")
        private String message;

        @SerializedName("user")
        private UserData user;

        public String getMessage() {
            return message;
        }

        public UserData getUser() {
            return user;
        }

        public static class UserData {
            @SerializedName("user_id")
            private int userId;

            @SerializedName("email")
            private String email;

            @SerializedName("role")
            private String role;

            public int getUserId() {
                return userId;
            }

            public String getEmail() {
                return email;
            }

            public String getRole() {
                return role;
            }
        }
    }
}
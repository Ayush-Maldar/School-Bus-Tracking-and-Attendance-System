package com.example.fix;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log; // Import Log

public class TokenManager {
    private static final String TAG = "TokenManager"; // Add TAG for logging
    private static final String PREF_NAME = "AuthPrefs"; // Consistent Prefs name
    private static final String KEY_AUTH_TOKEN = "token";
    private static final String KEY_USER_ID = "userId"; // Changed key to match Sign_in.java saving
    private static final String KEY_USER_EMAIL = "userEmail"; // Changed key
    private static final String KEY_USER_ROLE = "userType"; // Changed key to match Sign_in.java saving
    private static final String KEY_USER_NAME = "userName"; // New key for name
    private static final String KEY_USER_PHONE = "userPhoneNumber"; // New key for phone

    private SharedPreferences sharedPreferences;
    private Context context;

    public TokenManager(Context context) {
        this.context = context.getApplicationContext(); // Use application context
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Saves token and basic user details. Deprecated in favor of saveUserDetails.
     * @deprecated Use {@link #saveUserDetails(String, String, String, String, String)} instead.
     */
    @Deprecated
    public void saveToken(String token, int userId, String email, String role) {
        // This method might be incomplete as it doesn't save name/phone
        // Keeping it for potential backward compatibility but logging a warning
        Log.w(TAG, "Using deprecated saveToken. Consider using saveUserDetails.");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.putString(KEY_USER_ID, String.valueOf(userId)); // Save userId as String to match Sign_in.java
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_ROLE, role);
        // Missing name and phone saving here
        editor.apply();
    }

    /**
     * Saves comprehensive user details including token, ID, email, role, name, and phone number.
     *
     * @param token       Authentication token.
     * @param userId      User ID (saved as String).
     * @param email       User email.
     * @param role        User role.
     * @param name        User's full name.
     * @param phoneNumber User's phone number.
     */
    public void saveUserDetails(String token, String userId, String email, String role, String name, String phoneNumber) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.putString(KEY_USER_ID, userId); // Save as String
        editor.putString(KEY_USER_EMAIL, email);
        editor.putString(KEY_USER_ROLE, role);
        editor.putString(KEY_USER_NAME, name); // Save name
        editor.putString(KEY_USER_PHONE, phoneNumber); // Save phone number
        editor.apply();
        Log.d(TAG, "Saved User Details: userId=" + userId + ", email=" + email + ", role=" + role + ", name=" + name + ", phone=" + phoneNumber + ", token=" + (token != null ? "present" : "null"));
    }


    public String getToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null);
    }

    // Kept original getSavedToken for compatibility if used elsewhere
    public String getSavedToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, ""); // Return empty string if not found
    }

    public boolean hasToken() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public void clearToken() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Cleared SharedPreferences: " + PREF_NAME);
    }

    // Getters for user data (using updated keys)
    public String getUserId() {
        // Returns String as saved by Sign_in.java
        return sharedPreferences.getString(KEY_USER_ID, "-1"); // Default to "-1" as String
    }

    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, null);
    }

    public String getUserRole() {
        return sharedPreferences.getString(KEY_USER_ROLE, null);
    }

    // New getter for Name
    public String getUserName() {
        return sharedPreferences.getString(KEY_USER_NAME, null);
    }

    // New getter for Phone Number
    public String getUserPhoneNumber() {
        return sharedPreferences.getString(KEY_USER_PHONE, null);
    }
}
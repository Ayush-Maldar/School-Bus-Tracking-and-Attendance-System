package com.example.fix;
// DriverInfoActivity.java

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo; // Needed for resolveActivity check
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View; // Needed for OnClickListener
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List; // Needed for List<Intent>

public class DriverInfoActivity extends AppCompatActivity {

    private static final String TAG = "DriverInfoActivity";

    // Keys for Intent extras (use these when starting this activity)
    public static final String EXTRA_DRIVER_NAME = "DRIVER_NAME";
    public static final String EXTRA_DRIVER_PHONE = "DRIVER_PHONE";
    public static final String EXTRA_BUS_PLATE = "BUS_PLATE"; // Optional: if you want to display it

    private TextView driverNameTextView;
    private TextView busInfoTextView; // Renamed for clarity
    private ImageButton callButton;
    private ImageButton messageButton;

    private String driverName;
    private String driverPhoneNumber;
    private String busPlate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_info); // [cite: SchoolWay_app/app/res/layout/activity_driver_info.xml]

        // Find views
        driverNameTextView = findViewById(R.id.driverName);
        busInfoTextView = findViewById(R.id.email); // Assuming ID R.id.email holds bus info [cite: SchoolWay_app/app/res/layout/activity_driver_info.xml]
        callButton = findViewById(R.id.callButton); // [cite: SchoolWay_app/app/res/layout/activity_driver_info.xml]
        messageButton = findViewById(R.id.messageButton); // [cite: SchoolWay_app/app/res/layout/activity_driver_info.xml]

        // Get data from Intent
        Intent intent = getIntent();
        driverName = intent.getStringExtra(EXTRA_DRIVER_NAME);
        driverPhoneNumber = intent.getStringExtra(EXTRA_DRIVER_PHONE);
        busPlate = intent.getStringExtra(EXTRA_BUS_PLATE);

        // Validate received data
        if (driverName == null || driverPhoneNumber == null) {
            Log.e(TAG, "Error: Driver name or phone number not received via Intent.");
            Toast.makeText(this, "Error displaying driver details.", Toast.LENGTH_SHORT).show();
            finish(); // Close if data is missing
            return;
        }

        // Populate UI
        driverNameTextView.setText(driverName);
        busInfoTextView.setText("Bus Plate: " + (busPlate != null ? busPlate : "N/A")); // Display bus plate if available

        // Set listeners using anonymous inner classes or lambda expressions (if Java 8+ enabled)
        callButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Call button clicked. Attempting to dial number: " + driverPhoneNumber);
                dialDriverNumber();
            }
        });

        messageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageToDriver();
            }
        });
    }

    /**
     * Initiates ACTION_DIAL intent to open the dial pad with the driver's number.
     */
    private void dialDriverNumber() {
        // 1. Check if the phone number string is valid
        if (driverPhoneNumber != null && !driverPhoneNumber.isEmpty()) {
            // 2. Create the Intent with ACTION_DIAL
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);

            // 3. Create the 'tel:' URI. Uri.parse handles basic formatting.
            Uri data = Uri.parse("tel:" + driverPhoneNumber);
            dialIntent.setData(data);

            Log.d(TAG, "Attempting to start ACTION_DIAL with data: " + data.toString()); // Add logging

            // 4. Crucial Check: Verify an app exists to handle this Intent
            PackageManager pm = getPackageManager();
            if (pm != null && dialIntent.resolveActivity(pm) != null) { // Check PM is not null
                Log.d(TAG, "Activity found to handle ACTION_DIAL. Starting activity...");
                try {
                    startActivity(dialIntent); // Start the dialer activity
                } catch (Exception e) {
                    Log.e(TAG, "Exception occurred when trying to start dialer activity", e);
                    Toast.makeText(this, "Could not open dialer. An error occurred.", Toast.LENGTH_LONG).show();
                }
            } else {
                // This error means the PackageManager didn't find any app registered for ACTION_DIAL
                Log.e(TAG, "No activity found on the device to handle ACTION_DIAL for URI: " + data.toString());
                Toast.makeText(this, "Could not open dialer. No suitable app found on this device.", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, "Dial action failed: Driver phone number is null or empty.");
            Toast.makeText(this, "Driver phone number not available.", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * Shows a chooser to send a message via SMS, WhatsApp, or other text messaging apps.
     */
    private void sendMessageToDriver() {
        if (driverPhoneNumber != null && !driverPhoneNumber.isEmpty()) {
            try {
                // 1. Generic SEND intent for text sharing (WhatsApp, etc.)
                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("text/plain");
                // Optional: Pre-fill message
                // sendIntent.putExtra(Intent.EXTRA_TEXT, "Hello " + driverName + ", ");
                // Some apps might pick up the address
                sendIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, driverPhoneNumber);


                // 2. Specific SMS intent
                Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
                smsIntent.setData(Uri.parse("smsto:" + driverPhoneNumber));
                // Optional: Pre-fill SMS body
                // smsIntent.putExtra("sms_body", "Hello " + driverName + ", ");


                // 3. Create the Chooser
                Intent chooserIntent = Intent.createChooser(sendIntent, "Send message via:");

                // 4. Add specific intents (like SMS) to appear as preferred options if available
                List<Intent> initialIntents = new ArrayList<>();
                if (smsIntent.resolveActivity(getPackageManager()) != null) {
                    initialIntents.add(smsIntent);
                }

                // Check if WhatsApp is installed (optional, requires manifest declaration on Android 11+)
                // <queries> <package android:name="com.whatsapp"/> </queries>
                Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
                whatsappIntent.setType("text/plain");
                whatsappIntent.setPackage("com.whatsapp");
                // Ensure the phone number includes the country code for WhatsApp JID
                String whatsappNumber = driverPhoneNumber; // Potentially add country code logic here if needed
                // Basic check: if it doesn't start with +, assume local format (might need refinement)
                // if (!whatsappNumber.startsWith("+")) { /* Add country code */ }

                whatsappIntent.putExtra("jid", whatsappNumber + "@s.whatsapp.net"); // Use JID for WhatsApp number
                whatsappIntent.putExtra(Intent.EXTRA_TEXT, "Hello " + (driverName != null ? driverName : "") + ", "); // Pre-fill message for WhatsApp

                if (whatsappIntent.resolveActivity(getPackageManager()) != null) {
                    initialIntents.add(whatsappIntent);
                    Log.d(TAG, "Adding WhatsApp intent to chooser.");
                } else {
                    Log.w(TAG, "WhatsApp not installed or not queryable.");
                }


                if (!initialIntents.isEmpty()) {
                    // Need to convert List to Array for putExtra
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toArray(new Intent[0]));
                }

                // 5. Start the Chooser
                Log.d(TAG, "Starting message chooser for " + driverPhoneNumber);
                startActivity(chooserIntent);

            } catch (Exception e) {
                Log.e(TAG, "Error creating message intent", e);
                Toast.makeText(this, "Could not open messaging app.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Driver phone number not available.", Toast.LENGTH_SHORT).show();
        }
    }
}
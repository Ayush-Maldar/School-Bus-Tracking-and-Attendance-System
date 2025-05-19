package com.example.fix;

import android.app.Notification; // Import Notification class for DEFAULT flags
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color; // Keep if needed
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect; // Keep
import android.os.Vibrator; // Keep
import android.util.Log;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;


public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "MyFirebaseMsgService";
    private static final String CHANNEL_ID = "SchoolWayChannel";
    private static final String CHANNEL_NAME = "SchoolWay Notifications";
    private static final String CHANNEL_DESC = "Notifications for SchoolWay app";
    private static final String SOS_CHANNEL_ID = "SchoolWaySosChannel";
    private static final String SOS_CHANNEL_NAME = "SOS Alerts";
    private static final String SOS_CHANNEL_DESC = "Urgent SOS notifications from SchoolWay buses.";

    // *** Define the 10-second vibration pattern ***
    // Pattern: Pause 0ms, Vibrate 1s, Pause 0.5s, Vibrate 1s, ... (10 times)
    private static final long[] SOS_VIBRATION_PATTERN = new long[]{
            0, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000,
            500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels(); // Create/Update channels on service creation
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        if (remoteMessage.getNotification() != null) {
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            // Check if the backend explicitly sent the channelId in the payload
            String channelIdFromPayload = remoteMessage.getNotification().getChannelId();
            Log.d(TAG, "Notification Received - Title: " + title + ", Body: " + body + ", Payload Channel ID: " + channelIdFromPayload);

            // Determine channel ID: Prioritize payload, then title, then default
            String channelIdToUse = CHANNEL_ID; // Default channel
            if (SOS_CHANNEL_ID.equals(channelIdFromPayload)) { // Check payload first
                channelIdToUse = SOS_CHANNEL_ID;
                Log.d(TAG, "Using SOS channel based on payload.");
            } else if (title != null && title.toUpperCase().contains("SOS ALERT")) { // Fallback to checking title
                channelIdToUse = SOS_CHANNEL_ID;
                Log.d(TAG, "Using SOS channel based on title content.");
            } else {
                Log.d(TAG, "Using default notification channel.");
            }

            // Display the notification using the determined channel ID
            sendNotification(title, body, channelIdToUse); // Pass the channel ID
        }

        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            // Handle data payload if needed
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Refreshed FCM token: " + token);
        // Server sync logic handled elsewhere (e.g., SplashScreen)
    }

    /**
     * Creates and displays a system notification, ensuring custom vibration for SOS alerts.
     *
     * @param messageTitle The title for the notification.
     * @param messageBody  The main text content for the notification.
     * @param channelId    The ID of the channel to use (determines settings on Android 8+).
     */
    private void sendNotification(String messageTitle, String messageBody, String channelId) {
        Intent intent = new Intent(this, MainActivity.class); // Opens MainActivity on tap
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
                PendingIntent.FLAG_IMMUTABLE);

        // Sound is handled by channel (Oreo+) or defaults (pre-Oreo)
        // Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, channelId) // Use the passed channel ID
                        .setSmallIcon(R.drawable.bus_icon) // !! REPLACE with your actual small icon !!
                        .setContentTitle(messageTitle != null ? messageTitle : getString(R.string.app_name))
                        .setContentText(messageBody)
                        .setAutoCancel(true) // Dismiss notification on tap
                        .setPriority(NotificationCompat.PRIORITY_HIGH) // Keep high priority for alerts
                        .setContentIntent(pendingIntent); // Action on tap

        // *** Set Vibration based on Channel and Android Version ***
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Pre-Oreo: Set vibration directly on the builder
            if (SOS_CHANNEL_ID.equals(channelId)) {
                // Use the custom SOS pattern for pre-Oreo SOS alerts
                notificationBuilder.setVibrate(SOS_VIBRATION_PATTERN);
                // Also ensure sound plays alongside the custom vibration
                notificationBuilder.setDefaults(Notification.DEFAULT_SOUND); // Add default sound
                Log.d(TAG, "Setting custom SOS vibration pattern for pre-Oreo device.");
            } else {
                // For regular notifications on pre-Oreo, use default sound only
                notificationBuilder.setDefaults(Notification.DEFAULT_SOUND);
                Log.d(TAG, "Setting default sound for non-SOS on pre-Oreo device.");
            }
        }
        // For Oreo+, vibration is controlled by the channel settings.
        // The builder automatically uses the channel's vibration pattern.

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // Permission Check for POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.");
                // App needs to request this permission from an Activity context.
                return; // Stop if permission is missing
            }
        }

        // Show the Notification
        int notificationId = (int) System.currentTimeMillis(); // Unique ID for each notification
        notificationManager.notify(notificationId, notificationBuilder.build());
        Log.d(TAG, "Notification displayed with ID: " + notificationId + " on channel: " + channelId);
    }

    /**
     * Creates Notification Channels for Android Oreo (API 26) and above.
     * Sets the custom vibration pattern for the SOS channel.
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager == null) {
                Log.e(TAG, "Failed to get NotificationManager service.");
                return;
            }

            // Create Default Channel (Consider IMPORTANCE_DEFAULT or IMPORTANCE_HIGH based on need)
            NotificationChannel defaultChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT); // Changed to DEFAULT
            defaultChannel.setDescription(CHANNEL_DESC);
            // Optionally disable vibration for default channel if only SOS should vibrate strongly
            // defaultChannel.enableVibration(false);
            // defaultChannel.setVibrationPattern(null);
            notificationManager.createNotificationChannel(defaultChannel);
            Log.d(TAG, "Default notification channel created/updated: " + CHANNEL_ID);

            // Create SOS Channel with Custom Vibration
            NotificationChannel sosChannel = new NotificationChannel(
                    SOS_CHANNEL_ID, SOS_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH); // HIGH is crucial
            sosChannel.setDescription(SOS_CHANNEL_DESC);
            // *** Apply the Custom Vibration Pattern to the SOS Channel ***
            sosChannel.enableVibration(true); // Ensure vibration is enabled
            sosChannel.setVibrationPattern(SOS_VIBRATION_PATTERN); // Set the custom pattern
            // *** End Apply Pattern ***
            // Optional: Add light, configure sound etc.
            sosChannel.setLightColor(Color.RED);
            sosChannel.enableLights(true);
            sosChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()); // Example for specific sound

            notificationManager.createNotificationChannel(sosChannel);
            Log.d(TAG, "SOS notification channel created/updated with custom vibration: " + SOS_CHANNEL_ID);
        }
    }
}
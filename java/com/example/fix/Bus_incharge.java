package com.example.fix;

// --- NECESSARY IMPORTS ---
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint; // Needed for permission suppressions
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import android.widget.ScrollView;


public class Bus_incharge extends AppCompatActivity {
    // Constants
    private static final String TAG = "BusInchargeActivity";
    // --- IMPORTANT: Replace these UUIDs with your actual ESP32 Service/Characteristic UUIDs ---
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("abcd1234-5678-1234-5678-abcdef123456");
    // --- END IMPORTANT ---
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); // Standard CCCD UUID
    private static final String TARGET_DEVICE_ADDRESS = "D0:EF:76:32:48:D2"; // *** REPLACE WITH YOUR ESP32 MAC Address ***
    private static final long SCAN_PERIOD = 30000; // Scan timeout in milliseconds
    private static final long RECONNECT_DELAY_MS = 10000; // 10 seconds delay for reconnect attempt

    // Default Map Values
    private static final double DEFAULT_LATITUDE = 15.593722; // Example: Goa
    private static final double DEFAULT_LONGITUDE = 73.814194;
    private static final int DEFAULT_ZOOM = 16;

    // BLE Variables
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean scanning = false;
    private boolean connecting = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isReconnectScheduled = false;

    // UI Variables
    private TextView coordinatesText, statusText, debugText;
    private Button scanButton;
    private MapView mapView;
    private Marker busMarker;
    private Button scanQrButton;
    private ImageButton profileButton;
    private Button sosButton;
    private ScrollView debugScrollView;
    // API & Auth Variables
    private ApiService apiService;
    private TokenManager tokenManager;

    // Activity Result Launchers
    private final ActivityResultLauncher<ScanOptions> qrCodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    Log.d(TAG, "QR Scan Raw Result: " + result.getContents());
                    String fullScanContent = result.getContents();
                    String extractedRollNo = null;
                    String[] lines = fullScanContent.split("\\r?\\n");
                    for (String line : lines) {
                        if (line.trim().toLowerCase().startsWith("roll no:")) {
                            extractedRollNo = line.substring(line.indexOf(":") + 1).trim();
                            break;
                        }
                    }
                    if (extractedRollNo != null && !extractedRollNo.isEmpty()) {
                        Log.d(TAG, "Extracted Roll No: '" + extractedRollNo + "'");
                        sendScanDataToServer(extractedRollNo);
                    } else {
                        Log.w(TAG, "Could not extract Roll No from QR content: " + fullScanContent);
                        Toast.makeText(this, "Could not read Roll No", Toast.LENGTH_SHORT).show();
                    }
                } else { Log.d(TAG, "QR Scan Cancelled"); }
            });

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean allGranted = true;
                if (permissions.containsValue(false)) { allGranted = false; }
                if (allGranted) {
                    Log.d(TAG, "All required permissions granted after request.");
                    // Try starting scan after permissions are granted
                    handler.postDelayed(this::checkPermissionsAndStartBleScan, 200);
                } else {
                    Toast.makeText(this, "Permissions needed for full functionality.", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Log.d(TAG, "Bluetooth enabled by user.");
                    checkPermissionsAndStartBleScan(); // Attempt scan now BT is enabled
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled.", Toast.LENGTH_LONG).show();
                }
            });

    // --- onCreate ---
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bus_incharge); // Ensure layout has FrameLayout + recenter_button

        findViews(); // Find all UI elements first

        if (!initializeBluetooth()) { return; } // Initialize Bluetooth adapter

        tokenManager = new TokenManager(this);
        apiService = Retrofitclient.getClient().create(ApiService.class);

        setupButtonClickListeners(); // Set up button actions

        initializeMap(); // Set up the map

        checkRequiredPermissions(); // Check permissions needed on startup
    }

    // --- UI Initialization ---
    private void findViews() {
        coordinatesText = findViewById(R.id.coordinates_text);
        statusText = findViewById(R.id.status_text);
        scanButton = findViewById(R.id.scan_button);
        debugText = findViewById(R.id.debug_text);
        if (debugText != null) {
            debugText.setMovementMethod(new ScrollingMovementMethod()); // Make scrollable
        }
        debugScrollView = findViewById(R.id.debug_scroll_view);
        if (debugText != null) {
            debugText.setMovementMethod(new ScrollingMovementMethod());
        }
        mapView = findViewById(R.id.map);
        scanQrButton = findViewById(R.id.scan_qr_button);
        profileButton = findViewById(R.id.profile_button);
        sosButton = findViewById(R.id.sos_button);

        // Find and set up the recenter button listener
        ImageButton recenterButton = findViewById(R.id.recenter_button);
        if (recenterButton != null) {
            recenterButton.setOnClickListener(v -> recenterMap());
        } else {
            Log.e(TAG, "Recenter button (R.id.recenter_button) not found!");
        }
    }

    private void setupButtonClickListeners() {
        // BLE Scan Button
        scanButton.setOnClickListener(v -> {
            if (scanning) {
                stopBleScan();
            } else {
                Log.d(TAG,"Manual scan initiated by button.");
                handler.removeCallbacksAndMessages(null); // Cancel pending scans/reconnects
                isReconnectScheduled = false;
                checkPermissionsAndStartBleScan();
            }
        });

        // QR Scan Button
        if (scanQrButton != null) {
            scanQrButton.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startScanner();
                } else {
                    requestPermissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
                    Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show();
                }
            });
        } else { Log.e(TAG, "scanQrButton not found!"); }

        // Profile Button
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(Bus_incharge.this, Info.class);
                startActivity(intent);
            });
        } else { Log.e(TAG, "profileButton not found!"); }

        // SOS Button
        setupSosButtonListener();
    }

    // --- Initialization and Helper Methods ---
    private boolean initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
        if (bluetoothAdapter == null) { handleBluetoothError("Bluetooth not supported."); return false; }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { handleBluetoothError("BLE not supported."); return false; }
        return true;
    }

    private void handleBluetoothError(String message) {
        Log.e(TAG, "Bluetooth Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void checkRequiredPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        // Bluetooth permissions (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        // Location permission (Needed for BLE scanning before Android 12)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        // Camera permission (for QR scanning)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.CAMERA);

        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsNeeded);
            requestPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
        } else {
            Log.d(TAG, "All required permissions already granted.");
            // Auto-connect attempt moved to onResume
        }
    }

    private void startScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Student QR Code");
        options.setBeepEnabled(true);
        options.setCaptureActivity(CaptureActivityPortrait.class);
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setOrientationLocked(true);
        qrCodeLauncher.launch(options);
    }

    /**
     * Sends the scanned Roll Number to the backend API.
     * Includes checks for token, bus ID, and API service availability.
     */
    private void sendScanDataToServer(String rollNo) {
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "sendScanDataToServer: Auth token missing.");
            Toast.makeText(this, "Auth error. Please log in.", Toast.LENGTH_LONG).show(); return;
        }
        String authHeader = "Bearer " + token;

        int busId = getCurrentBusId();
        if (busId <= 0) {
            Log.e(TAG, "sendScanDataToServer: Invalid Bus ID (" + busId + ").");
            Toast.makeText(this, "Error: Could not determine Bus ID.", Toast.LENGTH_LONG).show(); return;
        }

        if (apiService == null) {
            Log.e(TAG, "sendScanDataToServer: ApiService null.");
            Toast.makeText(this, "Network service error.", Toast.LENGTH_LONG).show(); return;
        }

        ApiService.ScanRequest scanRequest = new ApiService.ScanRequest(rollNo);
        updateDebugText("Sending scan for Roll No: " + rollNo);
        Log.d(TAG, "API Call: POST /api/scan with Roll No: " + rollNo + " for Bus ID: " + busId);

        apiService.sendScanData(authHeader, scanRequest).enqueue(new Callback<ApiService.ScanResponse>() {
            @Override
            public void onResponse(Call<ApiService.ScanResponse> call, Response<ApiService.ScanResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiService.ScanResponse scanResponse = response.body();
                    String message = scanResponse.getMessage() + " - Status: " + scanResponse.getStatus();
                    Toast.makeText(Bus_incharge.this, message, Toast.LENGTH_SHORT).show();
                    updateDebugText("Scan Success: " + message);
                    Log.d(TAG, "Scan successful: " + scanResponse.getMessage());
                } else {
                    String errorMsg = "Scan failed";
                    try {
                        if (response.errorBody() != null) {
                            JSONObject errorJson = new JSONObject(response.errorBody().string());
                            errorMsg = errorJson.optString("message", "Scan failed (Code: " + response.code() + ")");
                        } else { errorMsg += " (Code: " + response.code() + ")"; }
                    } catch (Exception e) { Log.e(TAG, "Error parsing scan error body", e); errorMsg += " (Code: " + response.code() + ")"; }
                    Toast.makeText(Bus_incharge.this, errorMsg, Toast.LENGTH_LONG).show();
                    updateDebugText("Scan Error: " + errorMsg); Log.e(TAG, "Scan API error: " + errorMsg);
                }
            }
            @Override
            public void onFailure(Call<ApiService.ScanResponse> call, Throwable t) {
                String failureMsg = "Network Error during scan: " + t.getMessage();
                Toast.makeText(Bus_incharge.this, failureMsg, Toast.LENGTH_SHORT).show();
                updateDebugText("Scan Network Fail: " + t.getMessage()); Log.e(TAG, "Scan network failure", t);
            }
        });
    }

    private void setupSosButtonListener() {
        if (sosButton != null) {
            int initialBusId = getCurrentBusId();
            sosButton.setEnabled(initialBusId > 0);
            sosButton.setOnClickListener(v -> {
                int currentBusId = getCurrentBusId();
                if (currentBusId <= 0) {
                    Log.e(TAG, "SOS button clicked but currentBusId is invalid: " + currentBusId);
                    Toast.makeText(Bus_incharge.this, "Error: Cannot send SOS. Bus ID unknown.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Bus_incharge.this, Sos.class);
                intent.putExtra("CURRENT_BUS_ID", currentBusId);
                Log.d(TAG, "Starting SOS activity for bus ID: " + currentBusId);
                startActivity(intent);
            });
        } else { Log.e(TAG, "SOS Button not found!"); }
    }

    private int getCurrentBusId() {
        // TODO: Replace placeholder with actual logic (fetch from server, SharedPreferences etc.)
        Log.w(TAG, "getCurrentBusId() is using a HARDCODED value (8). Replace with actual logic!");
        return 8;
    }

    private void initializeMap() {
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());
        if (mapView == null) { Log.e(TAG, "MapView is null!"); return; }
        mapView.setTileSource(TileSourceFactory.MAPNIK);
//        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        GeoPoint startPoint = new GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
        IMapController mapController = mapView.getController();
        mapController.setZoom((double)DEFAULT_ZOOM);
        mapController.setCenter(startPoint);
        if (busMarker == null) {
            busMarker = new Marker(mapView);
            busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            busMarker.setTitle("Bus Location");
            mapView.getOverlays().add(busMarker);
        }
        busMarker.setPosition(startPoint);
        mapView.invalidate();
        Log.d(TAG, "Map initialized.");
    }

    private void updateDebugText(final String message) {
        Log.d(TAG, message); // Keep your logging
        runOnUiThread(() -> {
            // Check if the debug TextView exists
            if (debugText != null) {
                // Append the new message
                debugText.append(message + "\n");

                // --- Auto-scroll logic ---
                // Check if the ScrollView reference exists
                if (debugScrollView != null) {
                    // Post the scroll action to the UI thread's message queue.
                    // This ensures it executes after the TextView has updated its layout.
                    debugScrollView.post(() -> {
                        // Scroll the ScrollView all the way to the bottom
                        debugScrollView.fullScroll(View.FOCUS_DOWN);
                    });
                } else {
                    // Log an error if the ScrollView wasn't found (helps debugging)
                    Log.e(TAG, "debugScrollView is null in updateDebugText. Cannot auto-scroll.");
                }
                // --- End auto-scroll logic ---

                // NOTE: The previous manual scrolling logic using Layout is removed.
                // final Layout layout = debugText.getLayout();
                // if (layout != null) {
                //     int scrollAmount = layout.getLineTop(debugText.getLineCount()) - debugText.getHeight();
                //     debugText.scrollTo(0, Math.max(scrollAmount, 0));
                // }
            } else {
                Log.e(TAG, "debugText is null in updateDebugText.");
            }
        });
    }

    private void sendGpsCoordinatesToServer(double latitude, double longitude) {
        String token = tokenManager.getToken();
        if (token == null || token.isEmpty()) { Log.e(TAG, "No token for GPS send"); return; }
        String authHeader = "Bearer " + token;
        int busId = getCurrentBusId();
        if (busId <= 0) { Log.e(TAG, "Invalid busId for GPS send"); return; }
        if (apiService == null) { Log.e(TAG, "ApiService null for GPS send"); return; }

        ApiService.GpsLogRequest gpsRequest = new ApiService.GpsLogRequest();
        gpsRequest.bus_id = busId;
        gpsRequest.latitude = latitude;
        gpsRequest.longitude = longitude;

        apiService.logGpsCoordinates(authHeader, gpsRequest).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (!response.isSuccessful()) {
                    String errorDetails = "Code: " + response.code(); try { if (response.errorBody() != null) { errorDetails += " Body: " + response.errorBody().string(); } } catch (IOException e) { /* ignore */ }
                    Log.e(TAG, "GPS send failed: " + errorDetails); updateDebugText("GPS send Error: " + response.code());
                } else { Log.d(TAG, "GPS sent successfully for bus: " + busId); }
            }
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) { Log.e(TAG, "GPS send network fail", t); updateDebugText("GPS send Net Fail: " + t.getMessage()); }
        });
    }

    private void processGpsData(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (!CHARACTERISTIC_UUID.equals(characteristic.getUuid())) return;
        final String data = new String(value).trim();
        Log.d(TAG, "Processing GPS data: " + data);

        runOnUiThread(() -> {
            if (coordinatesText != null) coordinatesText.setText(data);
            String[] parts = data.split(",");
            if (parts.length == 2) {
                try {
                    double latitude = Double.parseDouble(parts[0].trim());
                    double longitude = Double.parseDouble(parts[1].trim());
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) { updateDebugText("Invalid GPS range"); return; }

                    sendGpsCoordinatesToServer(latitude, longitude);
                    GeoPoint newPoint = new GeoPoint(latitude, longitude);
                    if (mapView != null) {
                        if (busMarker == null) { busMarker = new Marker(mapView); busMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); busMarker.setTitle("Bus Location"); mapView.getOverlays().add(busMarker); }
                        busMarker.setPosition(newPoint);
                        mapView.getController().animateTo(newPoint);
                        mapView.invalidate();
                        Log.d(TAG, "Map marker updated: " + newPoint);
                    }
                    String timeStamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString();
                    if (statusText != null) statusText.setText(getString(R.string.last_update_format, timeStamp));
                    updateDebugText("GPS Update: " + latitude + ", " + longitude);
                } catch (NumberFormatException e) { updateDebugText("GPS parse error"); Log.e(TAG, "GPS parse error", e);}
            } else { updateDebugText("Invalid GPS format"); Log.w(TAG, "Invalid GPS format: " + data);}
        });
    }

    // --- BLE Methods ---

    private void checkPermissionsAndStartBleScan() {
        Log.d(TAG, "Checking permissions to start BLE scan...");
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

        if (!permissionsNeeded.isEmpty()) {
            Log.w(TAG, "Permissions required for BLE scan: " + permissionsNeeded);
            requestPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
            Toast.makeText(this, "Bluetooth & Location permissions needed.", Toast.LENGTH_LONG).show();
            runOnUiThread(() -> { if (scanButton != null) scanButton.setText("Grant Permissions"); });
        } else {
            Log.d(TAG, "BLE permissions granted, starting scan.");
            startBleScan();
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked in checkPermissionsAndStartBleScan
    private void startBleScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth not enabled. Requesting...");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // Permission check for BLUETOOTH_CONNECT is needed before launching intent on S+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing CONNECT permission for ACTION_REQUEST_ENABLE.");
                Toast.makeText(this, "Bluetooth Connect permission required", Toast.LENGTH_SHORT).show();
                return;
            }
            try { enableBluetoothLauncher.launch(enableBtIntent); } catch (SecurityException e) { Log.e(TAG, "Permission error enabling BT", e); }
            runOnUiThread(() -> { if (scanButton != null) scanButton.setText("Enable BT & Scan"); });
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) { Log.e(TAG, "Failed to get BluetoothLeScanner."); Toast.makeText(this, "BLE Scanner unavailable", Toast.LENGTH_SHORT).show(); return; }

        if (!scanning) {
            connecting = false;
            disconnectGatt(); // Ensure clean state
            updateDebugText("Scanning for: " + TARGET_DEVICE_ADDRESS + "...");
            handler.removeCallbacksAndMessages(null); // Clear previous tasks
            handler.postDelayed(this::timeoutScan, SCAN_PERIOD); // Set scan timeout

            scanning = true;
            runOnUiThread(() -> { if(statusText!=null) statusText.setText("Scanning..."); if(scanButton!=null) scanButton.setText("Stop Scan"); });

            ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(TARGET_DEVICE_ADDRESS).build();
            List<ScanFilter> filters = Collections.singletonList(filter);
            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

            // Permissions checked in checkPermissionsAndStartBleScan
            try { bluetoothLeScanner.startScan(filters, settings, scanCallback); Log.d(TAG, "BLE scan started."); }
            catch (SecurityException e) { Log.e(TAG, "Permission error starting scan (should have been checked)", e); stopBleScan(); }
            catch (Exception e) { Log.e(TAG, "Error starting scan", e); stopBleScan(); }
        } else { Log.d(TAG, "Scan already in progress."); }
    }

    private void timeoutScan() {
        if (scanning) {
            Log.w(TAG, "Scan Timeout: Target device not found.");
            updateDebugText("Scan Timeout.");
            stopBleScan();
            runOnUiThread(() -> {if(statusText!=null) statusText.setText("Device not found");});
            scheduleReconnect(); // Schedule reconnect on scan timeout
        }
    }

    @SuppressLint("MissingPermission") // Permissions checked before calling start/stopScan
    private void stopBleScan() {
        if (scanning) {
            scanning = false;
            handler.removeCallbacks(this::timeoutScan); // Remove specific scan timeout callback

            Log.d(TAG, "Stopping BLE scan.");
            updateDebugText("BLE Scan stopped.");

            runOnUiThread(() -> {
                if(scanButton != null) scanButton.setText("Scan for Device");
                // Only update status text if not currently trying to connect or already connected
                if (!connecting && bluetoothGatt == null) {
                    if(statusText != null) statusText.setText("Scan Stopped");
                }
            });

            if (bluetoothLeScanner != null) {
                // Permissions checked before startScan, assume valid for stopScan
                try { bluetoothLeScanner.stopScan(scanCallback); } catch (Exception e) { Log.e(TAG, "Error stopping scan", e); }
            }
        }
    }

    @SuppressLint("MissingPermission") // Permissions checked before calling connectGatt
    private void connectToDevice(BluetoothDevice device) {
        if (connecting || bluetoothGatt != null) { Log.w(TAG, "Connection attempt skipped: already connecting/connected."); return; }

        connecting = true;
        String deviceName = "Device";
        // Permissions checked before calling connectGatt
        try { deviceName = device.getName() != null ? device.getName() : "Unnamed"; } catch (SecurityException e) { Log.e(TAG, "Permission error getting device name", e); }

        Log.d(TAG, "Attempting connect to: " + deviceName + " (" + device.getAddress() + ")");
        // *** FIX for Lambda Error: Create final variable for use in lambda ***
        final String finalDeviceName = deviceName;
        runOnUiThread(() -> {if(statusText!=null) statusText.setText(getString(R.string.connecting_to_device, finalDeviceName));});
        // *** END FIX ***
        updateDebugText("Connecting BLE: " + finalDeviceName + "...");

        try {
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            if (bluetoothGatt == null) { Log.e(TAG, "connectGatt returned null!"); updateDebugText("BLE Connect Failed (init)"); connecting = false; }
            else { Log.d(TAG, "connectGatt initiated...");}
        } catch (Exception e) { Log.e(TAG, "Error initiating connectGatt", e); updateDebugText("BLE Connect Exception"); connecting = false; }
    }

    @SuppressLint("MissingPermission") // Permissions checked before calling disconnect/close
    private void disconnectGatt() {
        isReconnectScheduled = false; // Stop reconnect attempts on explicit disconnect/destroy
        handler.removeCallbacksAndMessages(null); // Clear all pending handler tasks

        if (bluetoothGatt != null) {
            final BluetoothGatt gattToClose = bluetoothGatt; // Use local final variable
            bluetoothGatt = null; // Nullify member variable immediately
            Log.i(TAG, "Disconnecting and closing GATT connection.");
            // Permission should be checked before calling this method where needed
            try {
                gattToClose.disconnect();
                gattToClose.close(); // Essential to release resources
            } catch (Exception e) { Log.e(TAG, "Error during GATT disconnect/close", e); }
        }
        connecting = false; // Update state
    }

    // --- Scan Callback Definition ---
    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission") // Permissions checked before calling connectToDevice
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device == null || device.getAddress() == null) return;
            String deviceAddress = device.getAddress();

            if (TARGET_DEVICE_ADDRESS.equalsIgnoreCase(deviceAddress)) {
                Log.i(TAG, ">>> Target BLE device found! (" + TARGET_DEVICE_ADDRESS + ")");
                updateDebugText("Target Device Found!");
                stopBleScan();
                if (!connecting && bluetoothGatt == null) { connectToDevice(device); }
                else { Log.w(TAG, "Target found, but already connecting/connected."); }
            }
        }
        @Override public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, "Batch scan results received: " + results.size());
            for (ScanResult result : results) {
                if (result.getDevice() != null && TARGET_DEVICE_ADDRESS.equalsIgnoreCase(result.getDevice().getAddress())) {
                    onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result); break;
                }
            }
        }
        @Override public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "BLE Scan failed: " + errorCode);
            updateDebugText("BLE Scan failed: " + errorCode);
            scanning = false;
            runOnUiThread(() -> { if(scanButton!=null) scanButton.setText("Scan for Device"); if(statusText!=null) statusText.setText("Scan Error: " + errorCode); });
            scheduleReconnect(); // Schedule reconnect on scan failure
        }
    };

    // --- GATT Callback with Auto-Reconnect ---
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission") // Permissions checked at start of callback methods
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            String deviceAddress = "Unknown"; try { deviceAddress = gatt.getDevice().getAddress();} catch (SecurityException e) {Log.e(TAG,"Perm error getAddr", e);}

            connecting = false; // Attempt finished

            // *** FIXED: Declare and check hasConnectPermission locally ***
            boolean hasConnectPermission;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(Bus_incharge.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "GATT Callback: Missing CONNECT permission.");
                    hasConnectPermission = false;
                } else {
                    hasConnectPermission = true;
                }
            } else {
                hasConnectPermission = true;
            }
            // Add legacy BLUETOOTH check if needed for older APIs used within this callback
            // else { if (ActivityCompat.checkSelfPermission(Bus_incharge.this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) { hasConnectPermission = false; } }

            if (!hasConnectPermission) { disconnectGatt(); return; }
            // *** END FIX ***

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server: " + deviceAddress);
                isReconnectScheduled = false; handler.removeCallbacksAndMessages(null); // Cancel reconnect
                updateDebugText("BLE Connected to " + deviceAddress);
                runOnUiThread(() -> {if(statusText!=null) statusText.setText(R.string.connected_to_device);});

                // Delay service discovery slightly
                handler.postDelayed(() -> {
                    // *** FIXED: Use member variable bluetoothGatt inside lambda ***
                    // Check if still connected and permission exists before discovering
                    if (bluetoothGatt != null && hasConnectPermission) { // Re-check permission for safety
                        Log.d(TAG, "Starting service discovery...");
                        if (!bluetoothGatt.discoverServices()) { Log.e(TAG, "discoverServices() failed."); disconnectGatt(); }
                    } else { Log.w(TAG, "GATT null or permission missing before delayed discoverServices");}
                }, 600); // 600ms delay

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server: " + deviceAddress + ". Status: " + status);
                updateDebugText("BLE Disconnected.");
                runOnUiThread(() -> {if(statusText!=null) statusText.setText(R.string.disconnected_from_device);});
                // Schedule reconnect attempt only if not already scheduled
                if (!isReconnectScheduled) { scheduleReconnect(); }
                disconnectGatt(); // Clean up *after* scheduling

            } else if (status != BluetoothGatt.GATT_SUCCESS) { // Handle connection errors during connect attempt
                Log.e(TAG, "GATT Connection Error for " + deviceAddress + ". Status: " + status);
                updateDebugText("BLE Connection Error: " + status);
                runOnUiThread(() -> {if(statusText!=null) statusText.setText("BLE Connection Error: " + status);});
                if (!isReconnectScheduled) { scheduleReconnect(); } // Try reconnect on error
                disconnectGatt();
            }
        }

        @SuppressLint("MissingPermission") // Permissions checked at start of callback methods
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            String deviceAddress = "Unknown"; try { deviceAddress = gatt.getDevice().getAddress();} catch (SecurityException e) {Log.e(TAG,"Perm error getAddr", e);}

            // *** FIXED: Declare and check hasConnectPermission locally ***
            boolean hasConnectPermission = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(Bus_incharge.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG,"Permission missing in onServicesDiscovered");
                    hasConnectPermission = false;
                }
            }
            // Add legacy check if needed
            if (!hasConnectPermission) { disconnectGatt(); return; }
            // *** END FIX ***

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered successfully for " + deviceAddress);
                updateDebugText("Services Discovered.");
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service == null) { Log.e(TAG, "Service " + SERVICE_UUID + " not found."); updateDebugText("Error: Service not found."); disconnectGatt(); return; }

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                if (characteristic == null) { Log.e(TAG, "Characteristic " + CHARACTERISTIC_UUID + " not found."); updateDebugText("Error: Characteristic not found."); disconnectGatt(); return; }

                Log.d(TAG, "Found target characteristic."); updateDebugText("Found Target Characteristic.");
                enableCharacteristicNotification(gatt, characteristic);
                // Read initial value after enabling notify (slight delay)
                handler.postDelayed(() -> readCharacteristicValue(gatt, characteristic), 200);

            } else { Log.e(TAG, "Service discovery failed: " + status); updateDebugText("Error: Service discovery failed."); disconnectGatt(); }
        }

        // --- Other gattCallback methods (ensure @SuppressLint if needed) ---
        @SuppressLint("MissingPermission") private void enableCharacteristicNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (gatt == null || characteristic == null) return;
            int properties = characteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0) { Log.w(TAG, "Characteristic cannot notify."); updateDebugText("Info: Characteristic cannot notify."); readCharacteristicValue(gatt, characteristic); return; }
            if (!gatt.setCharacteristicNotification(characteristic, true)) { Log.e(TAG, "setCharacteristicNotification failed"); updateDebugText("Error enabling BLE notify (local)"); return; }
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor == null) { Log.e(TAG, "CCCD not found"); updateDebugText("Error: CCCD not found"); return; }
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!gatt.writeDescriptor(descriptor)) { Log.e(TAG, "writeDescriptor failed"); updateDebugText("Error writing BLE CCCD"); }
            else { Log.i(TAG, "CCCD write initiated..."); updateDebugText("Enabling BLE notifications..."); }
        }
        @SuppressLint("MissingPermission") private void readCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (gatt == null || characteristic == null) return;
            int properties = characteristic.getProperties();
            if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0) { Log.w(TAG, "Characteristic not readable."); updateDebugText("Info: Characteristic cannot be read."); return; }
            Log.d(TAG, "Attempting to read characteristic: " + characteristic.getUuid());
            if (!gatt.readCharacteristic(characteristic)) { Log.e(TAG, "readCharacteristic failed"); updateDebugText("Error initiating BLE read."); }
        }
        @SuppressLint("MissingPermission") @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            super.onCharacteristicRead(gatt, characteristic, value, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value != null) { processGpsData(characteristic, value); } else { Log.w(TAG, "Char read success but value is null."); }
            } else { Log.e(TAG, "Char read failed: " + status); updateDebugText("Error reading BLE char: " + status); }
        }
        @SuppressLint("MissingPermission") @Override public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            super.onCharacteristicChanged(gatt, characteristic, value);
            if (value != null) { processGpsData(characteristic, value); } else { Log.w(TAG, "Char changed but value is null."); }
        }
        @SuppressLint("MissingPermission") @Override public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            if (descriptor.getUuid().equals(CLIENT_CHARACTERISTIC_CONFIG)) {
                BluetoothGattCharacteristic parentChar = descriptor.getCharacteristic();
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "CCCD written successfully for " + (parentChar!=null ? parentChar.getUuid() : "unknown char"));
                    updateDebugText("BLE Notifications Enabled.");
                    if(parentChar != null) readCharacteristicValue(gatt, parentChar); // Read initial value
                } else {
                    Log.e(TAG, "CCCD write failed: " + status + " for " + (parentChar!=null ? parentChar.getUuid() : "unknown char"));
                    updateDebugText("Error enabling BLE notify (Status: " + status + ")");
                }
            }
        }

    }; // End of gattCallback

    // --- Auto-Reconnect Helper ---
    private void scheduleReconnect() {
        if (isReconnectScheduled) { Log.d(TAG, "Reconnect already scheduled."); return; }
        Log.d(TAG, "Scheduling auto-reconnect in " + (RECONNECT_DELAY_MS / 1000) + "s.");
        updateDebugText("Attempting reconnect in " + (RECONNECT_DELAY_MS / 1000) + "s...");
        isReconnectScheduled = true;
        handler.postDelayed(() -> {
            Log.d(TAG, "Executing scheduled reconnect attempt.");
            isReconnectScheduled = false; // Allow scheduling again if this fails
            // Only try if disconnected, not connecting, and not scanning
            if (bluetoothGatt == null && !connecting && !scanning) {
                checkPermissionsAndStartBleScan();
            } else { Log.w(TAG,"Reconnect scan skipped: already handled."); }
        }, RECONNECT_DELAY_MS);
    }

    // --- Re-center Map Method ---
    private void recenterMap() {
        if (mapView == null) return;
        GeoPoint targetPoint = null;
        if (busMarker != null && busMarker.getPosition() != null) {
            GeoPoint currentPos = busMarker.getPosition();
            // Check if marker has a valid, non-default position before centering on it
            if (currentPos.getLatitude() != DEFAULT_LATITUDE || currentPos.getLongitude() != DEFAULT_LONGITUDE) {
                targetPoint = currentPos; Log.d(TAG, "Re-centering on bus marker.");
            }
        }
        if (targetPoint == null) { // Fallback
            targetPoint = new GeoPoint(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
            Log.d(TAG, "Re-centering on default point.");
            mapView.getController().setZoom((double)DEFAULT_ZOOM); // Reset zoom for default
        }
        mapView.getController().animateTo(targetPoint);
    }

    // --- Lifecycle Methods ---
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        if (mapView != null) mapView.onResume();

        // *** AUTO-CONNECT/SCAN LOGIC ***
        // Check permissions first before attempting scan
        boolean permissionsOk = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsOk = (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        } else {
            permissionsOk = (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            // Add legacy BLUETOOTH/BLUETOOTH_ADMIN checks if needed for older APIs
        }

        // Attempt scan/connect only if permissions are OK and not already handled
        if (permissionsOk && bluetoothAdapter != null && bluetoothGatt == null && !connecting && !scanning && !isReconnectScheduled) {
            Log.d(TAG, "onResume: Attempting automatic BLE scan/connect.");
            handler.postDelayed(this::checkPermissionsAndStartBleScan, 500); // 500ms delay
        } else {
            String reason = !permissionsOk ? "permissions missing" :
                    (bluetoothAdapter == null)?"BT null":
                            (bluetoothGatt != null)?"connected":
                                    (connecting)?"connecting":
                                            (scanning)?"scanning":"reconnect scheduled";
            Log.d(TAG, "onResume: Skipping auto-scan (" + reason + ").");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (mapView != null) mapView.onPause();
        stopBleScan(); // Stop scanning when paused
        // Keep GATT connection and reconnect timer active
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        stopBleScan();
        disconnectGatt(); // Disconnects and cancels reconnect timer
        if (mapView != null) mapView.onDetach();
        mapView = null;
        busMarker = null;
    }

} // End of Class
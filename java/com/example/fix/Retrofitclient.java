package com.example.fix;

// Import necessary classes
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor; // Import the interceptor
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
// Make sure Constants is imported if not already
// import com.example.fix.Constants;
import java.util.concurrent.TimeUnit; // Optional: For timeouts

public class Retrofitclient {

    // Your existing BASE_URL (make sure it points to ngrok if needed)
    private static final String BASE_URL = Constants.BASE_URL;
    private static Retrofit retrofit = null;
    private static OkHttpClient okHttpClient = null; // Keep a reference if needed

    public static Retrofit getClient() {
        // Only build if they haven't been built yet (Singleton pattern)
        if (okHttpClient == null) {
            // --- START: Add HttpLoggingInterceptor ---
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            // Set Level.BODY for max detail (headers, body, etc.)
            // or Level.HEADERS for just headers
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Build OkHttpClient and add the interceptor
            okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor) // Add the logging interceptor
                    // Optional: Add timeouts if needed
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
            // --- END: Add HttpLoggingInterceptor ---
        }

        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient) // Use the custom client with the interceptor
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    // Optional: Method to get just the OkHttpClient if needed elsewhere
    // public static OkHttpClient getOkHttpClient() {
    //     if (okHttpClient == null) {
    //         getClient(); // Ensure client is built
    //     }
    //     return okHttpClient;
    // }
}
// App-level build.gradle (e.g., app/build.gradle)

// Combine plugins block
plugins {
    alias(libs.plugins.android.application) // Assuming you use version catalogs (libs.)
    id("com.google.gms.google-services")    // Apply the Google Services plugin
    // Add other plugins like kotlin-android if needed
}

android {
    namespace = "com.example.fix" // Make sure this matches your actual package
    compileSdk = 35 // Or your target SDK

    defaultConfig {
        applicationId = "com.example.fix" // Make sure this matches
        minSdk = 30 // Or your minimum SDK
        targetSdk = 35 // Or your target SDK
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Ensure these match your project requirements (1_8 is common, 11 is also fine)
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    repositories {
        maven { url = uri("https://jitpack.io") }
    }
    buildFeatures {
        viewBinding = true // Keep if you use view binding
    }
    // Remove the repositories block from here if it exists, it belongs in settings.gradle or project build.gradle
    // repositories {
    //     maven { url = uri("https://jitpack.io") } // <<< INCORRECT LOCATION
    // }
}

dependencies {
    // AndroidX & Material Design (Assuming libs. references are setup in settings.gradle)
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment) // Keep if using Navigation component
    implementation(libs.navigation.ui)       // Keep if using Navigation component
    implementation("androidx.preference:preference-ktx:1.2.1") // Keep if using preferences

    // Firebase - Use BoM for version management
    implementation(platform("com.google.firebase:firebase-bom:33.1.1")) // Use the latest BoM
    implementation("com.google.firebase:firebase-messaging") // No version needed - BoM handles it

    // Networking (Retrofit, OkHttp, Gson) - Use consistent versions
    implementation("com.squareup.retrofit2:retrofit:2.9.0")         // Or latest stable
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")  // Or latest stable
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0") // Use a consistent OkHttp version (e.g., 4.10.0)
    implementation("com.squareup.okhttp3:okhttp:4.10.0")             // Match logging interceptor version
    implementation("com.google.code.gson:gson:2.9.0")               // Or latest stable

    // QR Code Scanner (ZXing)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3") // Use a recent core version (check latest)

    // OsmDroid (Map)
    implementation("org.osmdroid:osmdroid-android:6.1.18") // Use latest stable osmdroid version

    // MPAndroidChart (Keep only ONE entry)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // <<< KEPT THIS ONE

    // JSON library (if needed explicitly, Gson often handles this)
    // implementation("org.json:json:20231013") // Use a recent version if needed
    implementation("com.google.android.material:material:1.12.0")
    // Testing
    testImplementation(libs.junit) // Assuming libs. setup
    androidTestImplementation(libs.ext.junit) // Assuming libs. setup
    androidTestImplementation(libs.espresso.core) // Assuming libs. setup


    // implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // <<< REMOVED DUPLICATE
}

// Import the Firebase Admin SDK
const admin = require('firebase-admin');

// --- !!! IMPORTANT !!! ---
// 1. DOWNLOAD your service account key JSON file from your Firebase Project Settings > Service accounts.
// 2. SAVE the key file securely in your project (e.g., in a 'config' folder, DO NOT commit it to Git).
// 3. REPLACE the placeholder path below with the ACTUAL RELATIVE OR ABSOLUTE PATH to your downloaded key file.

// Use the relative path if the key file is in the SAME directory as firebaseconfig.js
const serviceAccountPath = './school-way-e47f6-firebase-adminsdk-fbsvc-4ffcd851ad.json';

try {
  // Load the service account key
  const serviceAccount = require(serviceAccountPath);

  // Initialize the Firebase Admin SDK
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
    // Optional: If using Firebase Realtime Database features elsewhere, add:
    // databaseURL: "https://your-project-id.firebaseio.com" 
  });

  console.log("[Firebase] Admin SDK initialized successfully using key:", serviceAccountPath);

} catch (error) {
  console.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
  console.error("[Firebase] ERROR initializing Admin SDK!");
  console.error("REASON:", error.message);
  if (error.code === 'MODULE_NOT_FOUND' && error.message.includes(serviceAccountPath)) {
      console.error(`--->>> Could not find the service account key file at: ${serviceAccountPath}`);
      console.error("--->>> Please ensure the path in firebaseconfig.js is correct and the file exists.");
  } else {
      console.error("--->>> There might be an issue with the key file content or other Firebase configuration.");
  }
  console.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
  // Exit the process if Firebase Admin fails to initialize, as dependent features will fail.
  process.exit(1); 
}

// Export the initialized admin object so other files (like scanHandler.js) can use it
module.exports = admin;

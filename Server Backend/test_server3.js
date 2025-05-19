const express = require('express');
 const { Pool } = require('pg');
 const bcrypt = require('bcrypt');
 const jwt = require('jsonwebtoken');
 const cors = require('cors');
 const crypto = require('crypto');
 const rateLimit = require('express-rate-limit');
 const { body, validationResult } = require('express-validator');
 const helmet = require('helmet');
 const nodemailer = require('nodemailer');
 const fs = require('fs');
 const QRCode = require('qrcode');
 const path = require('path');
 require('dotenv').config();

 // --- Require Firebase Admin SDK (ensure path is correct) ---
 const admin = require('./firebaseconfig'); // [cite: SchoolWay_app/server/firebaseconfig.js]
 // --- Require Scan Handler ---
 const { processScan } = require('./scanHandler'); // [cite: SchoolWay_app/server/scanHandler.js]

 const app = express();

 app.set('trust proxy', 1);

 const PORT = process.env.PORT || 3000;
 const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(64).toString('hex');

 // Enhanced security middlewares
 app.use(helmet()); // Adds various HTTP headers for security
 app.use(cors());
 app.use(express.json());
 app.use(express.static(path.join(__dirname, 'public')));
 app.use((req, res, next) => {
   console.log(`[${new Date().toISOString()}] Connection attempt from ${req.ip} - ${req.method} ${req.path}`);
   next();
 });
 // Rate limiting
 const authLimiter = rateLimit({
   windowMs: 15 * 60 * 1000, // 15 minutes
   max: 10000, // Increased slightly for testing/dev if needed
   message: { message: 'Too many authentication attempts. Please try again later.' }
 });

 const apiLimiter = rateLimit({
     windowMs: 15 * 60 * 1000, // 15 minutes
     max: 10000, // Allow more requests for general API usage
     message: { message: 'Too many requests from this IP, please try again after 15 minutes' }
 });

 // Apply general limiter to all API routes
 app.use('/api', apiLimiter); // Apply before specific /api routes

 // Database connection
 const pool = new Pool({
   user: process.env.DB_USER || 'postgres', // [cite: SchoolWay_app/server/database_schema.txt]
   host: process.env.DB_HOST || 'localhost',
   database: process.env.DB_NAME || 'schoolwaydb', // [cite: SchoolWay_app/server/database_schema.txt]
   password: process.env.DB_PASSWORD || '##sv_2338##', // Use environment variable if possible
   port: process.env.DB_PORT || 5432, // [cite: SchoolWay_app/server/database_schema.txt]
 });

 // Email configuration
 const transporter = nodemailer.createTransport({
   service: 'gmail',
   auth: {
     user: process.env.EMAIL_USER, // Ensure these are set in .env
     pass: process.env.EMAIL_PASS, // Ensure these are set in .env
   },
   secure: true, // Use TLS
 });

 // Database initialization
 const createTables = async () => {
   const client = await pool.connect();
   try {
     await client.query('BEGIN');

     // users table (ensure fcm_token column exists)
     await client.query(`
       CREATE TABLE IF NOT EXISTS users (
         user_id SERIAL PRIMARY KEY,
         first_name VARCHAR(50) NOT NULL,
         last_name VARCHAR(50) NOT NULL,
         email VARCHAR(100) UNIQUE NOT NULL,
         phone_number VARCHAR(15) UNIQUE NOT NULL,
         password TEXT NOT NULL,
         role VARCHAR(20) NOT NULL CHECK (role IN ('parent', 'driver', 'attendant', 'school_authority')),
         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
         reset_token TEXT,
         reset_token_expiry TIMESTAMP,
         last_login TIMESTAMP,
         fcm_token TEXT,  -- <<< Ensure this column exists for FCM
         login_attempts INTEGER DEFAULT 0, -- Added from schema.txt
         account_locked BOOLEAN DEFAULT false -- Added from schema.txt
       )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     // parents table
     await client.query(`
       CREATE TABLE IF NOT EXISTS parents (
         parent_id SERIAL PRIMARY KEY,
         user_id INTEGER UNIQUE NOT NULL REFERENCES users(user_id) ON DELETE CASCADE
       )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     // bus_staff table
     await client.query(`
       CREATE TABLE IF NOT EXISTS bus_staff (
         staff_id SERIAL PRIMARY KEY,
         user_id INTEGER UNIQUE NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
         photo BYTEA -- Consider storing path instead of raw bytes if large
       )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

    // routes table (Added based on schema.txt)
     await client.query(`
        CREATE TABLE IF NOT EXISTS routes (
            route_id SERIAL PRIMARY KEY,
            route_name VARCHAR(100) NOT NULL,
            start_location VARCHAR(255) NOT NULL,
            end_location VARCHAR(255) NOT NULL,
            stops JSONB NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
     `); // [cite: SchoolWay_app/server/database_schema.txt]


     // buses table
     await client.query(`
         CREATE TABLE IF NOT EXISTS buses (
             bus_id SERIAL PRIMARY KEY,
             bus_plate VARCHAR(20) UNIQUE NOT NULL,
             driver_id INTEGER REFERENCES bus_staff(staff_id) ON DELETE SET NULL,
             attendant_id INTEGER REFERENCES bus_staff(staff_id) ON DELETE SET NULL,
             route_id INTEGER REFERENCES routes(route_id) ON DELETE SET NULL, -- Added from schema.txt
             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
         )
     `); // [cite: SchoolWay_app/server/database_schema.txt]


     // students table
     await client.query(`
       CREATE TABLE IF NOT EXISTS students (
           student_id SERIAL PRIMARY KEY,
           first_name VARCHAR(50) NOT NULL,
           last_name VARCHAR(50) NOT NULL,
           parent_id INTEGER NOT NULL REFERENCES parents(parent_id) ON DELETE CASCADE,
           bus_id INTEGER REFERENCES buses(bus_id) ON DELETE SET NULL,
           roll_no VARCHAR(50) UNIQUE NOT NULL, -- Added UNIQUE constraint
           class VARCHAR(20), -- Added from schema.txt (removed NOT NULL as it wasn't in original create)
           qr_code_image BYTEA, -- Consider storing path instead
           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
       )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     // gps_logs table
      await client.query(`
         CREATE TABLE IF NOT EXISTS gps_logs (
             log_id SERIAL PRIMARY KEY, -- Renamed from gps_id for clarity
             bus_id INTEGER NOT NULL REFERENCES buses(bus_id) ON DELETE CASCADE,
             latitude DECIMAL(10, 8) NOT NULL, -- Increased precision
             longitude DECIMAL(11, 8) NOT NULL, -- Increased precision
             timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
         )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     // attendance table (Added based on schema.txt)
     await client.query(`
        CREATE TABLE IF NOT EXISTS attendance (
            attendance_id SERIAL PRIMARY KEY,
            student_id INTEGER NOT NULL REFERENCES students(student_id) ON DELETE CASCADE,
            date DATE NOT NULL DEFAULT CURRENT_DATE,
            status VARCHAR(10) NOT NULL CHECK (status IN ('Onboard', 'Offboard')),
            timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     // notifications table (Added based on schema.txt)
     await client.query(`
        CREATE TABLE IF NOT EXISTS notifications (
            notification_id SERIAL PRIMARY KEY,
            parent_id INTEGER NOT NULL REFERENCES parents(parent_id) ON DELETE CASCADE,
            message TEXT NOT NULL,
            timestamp TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            is_read BOOLEAN DEFAULT false -- Optional: track read status
        )
     `); // [cite: SchoolWay_app/server/database_schema.txt]

     await client.query('COMMIT');
     console.log('Database tables checked/created successfully');
   } catch (err) {
     await client.query('ROLLBACK');
     console.error('Error creating/checking database tables:', err);
   } finally {
     client.release();
   }
 };

 // Authentication middleware
 const authenticateToken = (req, res, next) => {
   const authHeader = req.headers['authorization'];
   // console.log(`[${new Date().toISOString()}] authenticateToken: Received Authorization Header:`, authHeader); // Verbose logging

   const token = authHeader && authHeader.startsWith('Bearer ') ? authHeader.split(' ')[1] : null;

   if (!token) {
     console.log(`[${new Date().toISOString()}] authenticateToken: Access denied: Token missing.`);
     return res.status(401).json({ message: 'Access denied. No token provided.' });
   }

   try {
     const verified = jwt.verify(token, JWT_SECRET);
     req.user = verified;
     // console.log(`[${new Date().toISOString()}] authenticateToken: Token verified for userId: ${req.user.userId}, role: ${req.user.role}`); // Verbose logging
     next();
   } catch (err) {
     console.error(`[${new Date().toISOString()}] authenticateToken: Invalid/expired token. Error:`, err.message);
     if (err.name === 'TokenExpiredError') {
         return res.status(401).json({ message: 'Token expired. Please sign in again.', code: 'TOKEN_EXPIRED' });
     }
     return res.status(403).json({ message: 'Invalid token.' });
   }
 };

 // Role-based authorization middleware
 const authorize = (roles = []) => {
   const allowedRoles = Array.isArray(roles) ? roles : [roles];

   return (req, res, next) => {
     if (!req.user || !req.user.role) {
       console.warn(`[${new Date().toISOString()}] authorize: User object or role missing in request.`);
       return res.status(403).json({ message: 'Authorization error.' });
     }

     if (!allowedRoles.includes(req.user.role)) {
       console.log(`[${new Date().toISOString()}] authorize: Forbidden attempt by role '${req.user.role}' for roles '${allowedRoles.join(', ')}' on path ${req.path}`);
       return res.status(403).json({ message: 'Insufficient permissions' });
     }
     next();
   };
 };


 // === DATABASE INITIALIZATION ===
 createTables().catch(err => console.error('Failed to initialize database:', err));


 // === VALIDATION RULES ===
 const registerValidation = [
   body('first_name').notEmpty().trim().escape().withMessage('First name is required'),
   body('last_name').notEmpty().trim().escape().withMessage('Last name is required'),
   body('email').isEmail().normalizeEmail().withMessage('Valid email is required'),
   body('phone_number').isMobilePhone().withMessage('Valid phone number is required'), // Add locale? e.g., 'en-IN'
   body('password').isLength({ min: 8 }).withMessage('Password must be at least 8 characters'),
   body('role').isIn(['parent', 'bus_incharge', 'school_authority']).withMessage('Invalid role'),
   body('bus_staff_type').optional().isIn(['driver', 'attendant']).withMessage('Invalid bus staff type')
 ];

 const signinValidation = [
   body('email').isEmail().normalizeEmail().withMessage('Valid email is required'),
   body('password').notEmpty().withMessage('Password is required')
 ];

 const forgotPasswordValidation = [
   body('email').isEmail().normalizeEmail().withMessage('Valid email is required')
 ];

 const resetPasswordValidation = [
   body('token').notEmpty().withMessage('Reset token is required'),
   body('newPassword').isLength({ min: 8 }).withMessage('Password must be at least 8 characters')
 ];

 const gpsLogValidation = [
     body('bus_id').isInt({ gt: 0 }).withMessage('Valid bus_id is required'),
     body('latitude').isFloat({ min: -90, max: 90 }).withMessage('Valid latitude is required'),
     body('longitude').isFloat({ min: -180, max: 180 }).withMessage('Valid longitude is required'),
 ];

 const updateUserValidation = [
    body('email').isEmail().normalizeEmail().withMessage('Valid email is required'),
    body('phone_number').isMobilePhone().withMessage('Valid phone number is required') // Add locale?
 ];

 const sosValidation = [
    body('bus_id').isInt({ gt: 0 }).withMessage('Valid bus_id is required'),
    body('message').optional().trim().escape(),
    body('severity').optional().isIn(['serious', 'non_serious']).withMessage('Invalid severity')
 ];


 // === AUTHENTICATION ROUTES ===

 // Registration Endpoint
 app.post('/api/register', registerValidation, async (req, res) => {
     const errors = validationResult(req);
     if (!errors.isEmpty()) {
       return res.status(400).json({ message: errors.array()[0].msg });
     }

     const client = await pool.connect();
     try {
      const { first_name, last_name, email, phone_number, password, role, bus_staff_type } = req.body;
       await client.query('BEGIN');

       const userCheck = await client.query('SELECT email, phone_number FROM users WHERE email = $1 OR phone_number = $2', [email, phone_number]);
       if (userCheck.rowCount > 0) {
         await client.query('ROLLBACK');
         const conflictField = userCheck.rows[0].email === email ? 'email' : 'phone number';
         return res.status(409).json({ message: `User already exists with this ${conflictField}` });
       }

       const salt = await bcrypt.genSalt(12);
       const hashedPassword = await bcrypt.hash(password, salt);

       const newUser = await client.query(
         `INSERT INTO users (first_name, last_name, email, phone_number, password, role)
          VALUES ($1, $2, $3, $4, $5, $6) RETURNING user_id, email, role`,
         [first_name, last_name, email, phone_number, hashedPassword, role]
       );

       const userId = newUser.rows[0].user_id;

       if (role === 'parent') {
         await client.query('INSERT INTO parents (user_id) VALUES ($1)', [userId]);
       } else if (role === 'bus_incharge') {
         const staffResult = await client.query('INSERT INTO bus_staff (user_id) VALUES ($1) RETURNING staff_id', [userId]);
         const staffId = staffResult.rows[0].staff_id;

         // Assign staff to a default/test bus if type provided
         if (bus_staff_type) {
           const busIdToAssign = 8; // Hardcoded - Needs better logic for production
           const busExists = await client.query('SELECT bus_id FROM buses WHERE bus_id = $1', [busIdToAssign]);

           if(busExists.rowCount > 0){
               const columnToUpdate = bus_staff_type === 'driver' ? 'driver_id' : 'attendant_id';
               await client.query(`UPDATE buses SET ${columnToUpdate} = $1 WHERE bus_id = $2`, [staffId, busIdToAssign]);
               console.log(`Assigned staff ${staffId} (${bus_staff_type}) to bus ${busIdToAssign}`);
           } else {
               console.warn(`Bus with ID ${busIdToAssign} not found. Cannot assign staff.`);
           }
         }
       } // No specific table for 'school_authority' linked here

       await client.query('COMMIT');

       try {
         await transporter.sendMail({
           from: process.env.EMAIL_USER,
           to: email,
           subject: 'Welcome to SchoolWay',
           text: `Hello ${first_name},\n\nWelcome to SchoolWay! Your account has been created.\n\nThank you!`,
         });
         console.log(`Welcome email sent to ${email}`);
       } catch (emailError) {
         console.error(`Failed to send welcome email to ${email}:`, emailError);
       }

       res.status(201).json({
         message: 'User registered successfully',
         user: {
           user_id: newUser.rows[0].user_id,
           email: newUser.rows[0].email,
           role: newUser.rows[0].role
         }
       });
     } catch (err) {
       await client.query('ROLLBACK');
       console.error('Registration error:', err);
       res.status(500).json({ message: 'Server error during registration' });
     } finally {
       client.release();
     }
 });

 // Sign-in Endpoint
 app.post('/api/signin', authLimiter, signinValidation, async (req, res) => {
     const errors = validationResult(req);
     if (!errors.isEmpty()) {
       return res.status(400).json({ success: false, message: errors.array()[0].msg });
     }

     const client = await pool.connect();
     try {
       const { email, password } = req.body;

       await client.query('BEGIN');

       const userResult = await client.query('SELECT user_id, first_name, last_name, email, phone_number, password, role FROM users WHERE email = $1', [email]);

       if (userResult.rowCount === 0) {
         await client.query('ROLLBACK');


         console.log(`Sign-in attempt failed: User not found for email ${email}`);
         return res.status(401).json({ success: false, message: 'Invalid credentials' });
       }

       const userData = userResult.rows[0];

       const validPassword = await bcrypt.compare(password, userData.password);
       if (!validPassword) {
         await client.query('COMMIT'); // Commit for rate limiting even on password fail
         console.log(`Sign-in attempt failed: Invalid password for email ${email}`);
         return res.status(401).json({ success: false, message: 'Invalid credentials' });
       }

       // Role mismatch check
       if (req.body.userType && userData.role !== req.body.userType) {
           await client.query('COMMIT');
           console.log(`Sign-in attempt failed: Role mismatch for ${email}. Expected ${req.body.userType}, got ${userData.role}`);
           return res.status(401).json({
             success: false,
             message: `Sign in failed. Please use the '${userData.role}' option.`
           });
       }

       await client.query('UPDATE users SET last_login = NOW() WHERE user_id = $1', [userData.user_id]);

       const tokenPayload = {
           userId: userData.user_id,
           role: userData.role,
           email: userData.email,
           name: `${userData.first_name} ${userData.last_name}`
       };
       const token = jwt.sign(tokenPayload, JWT_SECRET, { expiresIn: '7d' });

       await client.query('COMMIT');
       console.log(`Sign-in successful for user ${userData.user_id} (${email})`);

       res.json({
         success: true,
         token,
         userType: userData.role,
         userId: userData.user_id.toString(),
         user: { // Send back user details needed by client
           id: userData.user_id,
           name: `${userData.first_name} ${userData.last_name}`,
           email: userData.email,
           role: userData.role,
           phone_number: userData.phone_number
         }
       });
     } catch (err) {
       await client.query('ROLLBACK');
       console.error('Sign-in error:', err);
       res.status(500).json({ success: false, message: 'Server error during sign-in' });
     } finally {
       client.release();
     }
 });

 // Forgot Password Endpoint
 app.post('/api/forgot-password', authLimiter, forgotPasswordValidation, async (req, res) => {
     const errors = validationResult(req);
     if (!errors.isEmpty()) {
         return res.status(400).json({ message: errors.array()[0].msg });
     }

     const client = await pool.connect();
     try {
         const { email } = req.body;

         await client.query('BEGIN');

         const userResult = await client.query('SELECT user_id, first_name FROM users WHERE email = $1', [email]);

         // Generic success response even if user not found
         if (userResult.rowCount === 0) {
             await client.query('COMMIT');
             console.log(`Forgot password request for non-existent email: ${email}`);
             return res.status(200).json({ message: 'If an account exists, a reset token has been sent.' });
         }

         const userId = userResult.rows[0].user_id;
         const firstName = userResult.rows[0].first_name;

         const resetToken = crypto.randomBytes(32).toString('hex');
         const resetTokenExpiry = new Date(Date.now() + 3600000); // 1 hour

         await client.query(
             'UPDATE users SET reset_token = $1, reset_token_expiry = $2 WHERE user_id = $3',
             [resetToken, resetTokenExpiry, userId]
         );

         // Send email
         try {
             await transporter.sendMail({
                 from: process.env.EMAIL_USER,
                 to: email,
                 subject: 'Password Reset Request - SchoolWay',
                 text: `Hello ${firstName},\n\nYou requested a password reset.\nEnter this token in the app:\n\n${resetToken}\n\nValid for 1 hour.\nIf not you, ignore this email.`,
                 html: `<p>Hello ${firstName},</p><p>Enter this token in the app: <strong>${resetToken}</strong></p><p>Valid for 1 hour.</p>`
             });
             console.log(`Password reset token sent to ${email}`);
         } catch (emailError) {
             console.error(`Failed to send password reset email to ${email}:`, emailError);
             await client.query('ROLLBACK');
             return res.status(500).json({ message: 'Failed to send reset email.' });
         }

         await client.query('COMMIT');

         res.status(200).json({ message: 'If an account exists, a reset token has been sent.' });
     } catch (err) {
         await client.query('ROLLBACK');
         console.error('Forgot password error:', err);
         res.status(500).json({ message: 'Server error requesting password reset' });
     } finally {
         client.release();
     }
 });

 // Reset Password Endpoint
 app.post('/api/reset-password', authLimiter, resetPasswordValidation, async (req, res) => {
     const errors = validationResult(req);
     if (!errors.isEmpty()) {
         return res.status(400).json({ message: errors.array()[0].msg });
     }

     const client = await pool.connect();
     try {
         const { token, newPassword } = req.body;

         console.log('Reset password attempt with token:', token ? token.substring(0, 5) + '...' : 'null');

         await client.query('BEGIN');

         const userResult = await client.query(
             'SELECT user_id, email, first_name FROM users WHERE reset_token = $1 AND reset_token_expiry > NOW()',
             [token]
         );

         if (userResult.rowCount === 0) {
             console.log('Invalid or expired token used for password reset.');
             await client.query('ROLLBACK');
             return res.status(400).json({ message: 'Invalid or expired token.' });
         }

         const userData = userResult.rows[0];
         console.log(`Valid token for user ${userData.user_id}. Resetting password...`);

         const salt = await bcrypt.genSalt(12);
         const hashedPassword = await bcrypt.hash(newPassword, salt);

         await client.query(
             `UPDATE users
              SET password = $1, reset_token = NULL, reset_token_expiry = NULL
              WHERE user_id = $2`,
             [hashedPassword, userData.user_id]
         );

         await client.query('COMMIT');

         // Send confirmation email
         try {
             await transporter.sendMail({
                 from: process.env.EMAIL_USER,
                 to: userData.email,
                 subject: 'Password Reset Successful - SchoolWay',
                 text: `Hello ${userData.first_name},\n\nYour password has been successfully reset.\nIf you did not make this change, contact support.`,
             });
             console.log(`Password reset confirmation email sent to ${userData.email}`);
         } catch (emailError) {
             console.error(`Failed to send password reset confirmation email to ${userData.email}:`, emailError);
         }

         res.status(200).json({ message: 'Password reset successful' });
     } catch (err) {
         await client.query('ROLLBACK');
         console.error('Reset password error:', err);
         res.status(500).json({ message: 'Server error during password reset' });
     } finally {
         client.release();
     }
 });


 // === USER PROFILE ROUTES ===

 // GET Current User's Profile Details
 app.get('/api/user/me', authenticateToken, async (req, res) => {
     const userId = req.user.userId;
     console.log(`Workspaceing profile for authenticated user ID: ${userId}`);

     try {
         const result = await pool.query(
             'SELECT user_id, first_name, last_name, email, phone_number, role, last_login FROM users WHERE user_id = $1',
             [userId]
         );

         if (result.rowCount === 0) {
             console.error(`User ID ${userId} from valid token not found in DB.`);
             return res.status(404).json({ message: 'User not found.' });
         }
         res.json(result.rows[0]);
     } catch (err) {
         console.error(`Error fetching profile for user ${userId}:`, err);
         res.status(500).json({ message: 'Server error fetching profile.' });
     }
 });

 // PUT Update User Contact Info (Email and Phone)
 app.put('/api/users/update-contact/:userId', authenticateToken, updateUserValidation, async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
       return res.status(400).json({ message: errors.array()[0].msg });
    }

    const targetUserId = parseInt(req.params.userId, 10);
    const requestingUserId = req.user.userId;

    console.log(`Update request for userId ${targetUserId} by userId ${requestingUserId}`);

    if (targetUserId !== requestingUserId) {
       console.warn(`Auth Error: User ${requestingUserId} tried to update profile for ${targetUserId}`);
       return res.status(403).json({ message: 'Forbidden: You can only update your own profile.' });
    }

    if (isNaN(targetUserId)) {
        return res.status(400).json({ message: 'Invalid user ID format.' });
    }

    const { email, phone_number } = req.body;
    const client = await pool.connect();

    try {
       await client.query('BEGIN');

       // Check for conflicts with OTHER users
       const conflictCheck = await client.query(
         'SELECT user_id FROM users WHERE (email = $1 OR phone_number = $2) AND user_id != $3',
         [email, phone_number, targetUserId]
       );

       if (conflictCheck.rowCount > 0) {
         await client.query('ROLLBACK');
         return res.status(409).json({ message: 'Email or phone number already in use by another account.' });
       }

       // Perform update
       const updateResult = await client.query(
         `UPDATE users SET email = $1, phone_number = $2 WHERE user_id = $3`,
         [email, phone_number, targetUserId]
       );

       if (updateResult.rowCount === 0) {
         await client.query('ROLLBACK');
         return res.status(404).json({ message: 'User not found for update.' });
       }

       await client.query('COMMIT');
       console.log(`User profile updated for userId: ${targetUserId}`);
       res.status(200).json({ message: 'Profile updated successfully' });

    } catch (err) {
       await client.query('ROLLBACK');
       console.error(`Error updating profile for user ${targetUserId}:`, err);
       if (err.code === '23505') { // Handle potential race condition for unique constraint
           return res.status(409).json({ message: 'Update failed: Email or phone number already exists.' });
       }
       res.status(500).json({ message: 'Server error during profile update.' });
    } finally {
       client.release();
    }
 });

 // PUT Update User FCM Token
 app.put('/api/user/fcm-token', authenticateToken, async (req, res) => {
    const userId = req.user.userId;
    const { fcmToken } = req.body;

    console.log(`[API PUT /fcm-token] Request to update FCM token for userId: ${userId}`);

    if (!fcmToken || typeof fcmToken !== 'string' || fcmToken.trim() === '') {
        console.warn(`[API PUT /fcm-token] Invalid fcmToken for userId: ${userId}`);
        return res.status(400).json({ success: false, message: 'fcmToken is required.' });
    }

    const cleanToken = fcmToken.trim();
    const client = await pool.connect();

    try {
        // Ensure 'fcm_token' column exists in 'users' table!
        const updateResult = await client.query(
            'UPDATE users SET fcm_token = $1 WHERE user_id = $2',
            [cleanToken, userId]
        );

        if (updateResult.rowCount === 0) {
            console.error(`[API PUT /fcm-token] User not found for userId: ${userId}.`);
            return res.status(404).json({ success: false, message: 'User not found.' });
        }

        console.log(`[API PUT /fcm-token] Updated FCM token for userId: ${userId}`);
        res.status(200).json({ success: true, message: 'FCM token updated.' });

    } catch (error) {
        console.error(`[API PUT /fcm-token] Error updating FCM token for userId: ${userId}:`, error);
        res.status(500).json({ success: false, message: 'Server error updating FCM token.' });
    } finally {
        if (client) {
            client.release();
        }
    }
 });


 // === PARENT ROUTES ===

 // GET students for the logged-in parent
 app.get('/api/students', authenticateToken, authorize('parent'), async (req, res) => {
     const userId = req.user.userId;
     console.log(`Workspaceing students for parent user ID: ${userId}`);

     try {
       const parentResult = await pool.query('SELECT parent_id FROM parents WHERE user_id = $1', [userId]);

       if (parentResult.rowCount === 0) {
         console.log(`No parent record found for user ID: ${userId}`);
         return res.status(404).json({ message: 'Parent profile not found.' });
       }

       const parentId = parentResult.rows[0].parent_id;

       const studentResult = await pool.query(
         `SELECT s.student_id, s.first_name, s.last_name, s.parent_id, s.bus_id, b.bus_plate
          FROM students s
          LEFT JOIN buses b ON s.bus_id = b.bus_id
          WHERE s.parent_id = $1`,
         [parentId]
       );

       console.log(`Found ${studentResult.rowCount} students for parent_id ${parentId}`);
       res.json(studentResult.rows);

     } catch (err) {
       console.error(`Error fetching students for parent user ID ${userId}:`, err);
       res.status(500).json({ message: 'Server error fetching students.' });
     }
 });

 // GET dashboard data (simplified, currently same as /api/students)
 app.get('/api/dashboard', authenticateToken, authorize('parent'), async (req, res) => {
     const userId = req.user.userId;
     console.log(`Workspaceing dashboard data for parent user ID: ${userId}`);

     try {
         const parentResult = await pool.query('SELECT parent_id FROM parents WHERE user_id = $1', [userId]);
         if (parentResult.rowCount === 0) {
             return res.status(404).json({ message: 'Parent profile not found.' });
         }
         const parentId = parentResult.rows[0].parent_id;

         const result = await pool.query(
             `SELECT s.student_id, s.first_name, s.last_name, s.parent_id, s.bus_id, b.bus_plate
              FROM students s LEFT JOIN buses b ON s.bus_id = b.bus_id
              WHERE s.parent_id = $1`,
             [parentId]
         );
         res.json({ students: result.rows }); // Match expected client format
     } catch (err) {
         console.error(`Error fetching dashboard data for parent ID ${userId}:`, err);
         res.status(500).json({ message: 'Server error fetching dashboard data.' });
     }
 });


 // === BUS RELATED ROUTES ===

 // GET staff for a specific bus
 app.get('/api/bus/:busId/staff', authenticateToken, async (req, res) => {
     const { busId } = req.params;
     const requestedBusId = parseInt(busId, 10);

     console.log(`Workspaceing staff for bus ID: ${requestedBusId} by user ${req.user.userId}`);

     if (isNaN(requestedBusId)) {
         return res.status(400).json({ message: 'Invalid bus ID format.' });
     }

     // Optional: Add authorization check if needed (e.g., parent can only see staff for their child's bus)

     try {
         const result = await pool.query(`
           SELECT
             b.bus_id,
             d_user.first_name AS driver_first_name, d_user.last_name AS driver_last_name, d_user.phone_number AS driver_phone_number,
             a_user.first_name AS attendant_first_name, a_user.last_name AS attendant_last_name, a_user.phone_number AS attendant_phone_number
           FROM buses b
           LEFT JOIN bus_staff driver_staff ON b.driver_id = driver_staff.staff_id LEFT JOIN users d_user ON driver_staff.user_id = d_user.user_id
           LEFT JOIN bus_staff attendant_staff ON b.attendant_id = attendant_staff.staff_id LEFT JOIN users a_user ON attendant_staff.user_id = a_user.user_id
           WHERE b.bus_id = $1
         `, [requestedBusId]);

         if (result.rows.length > 0) {
             const details = result.rows[0];
             const staffResponse = [];
             if (details.driver_first_name) {
                 staffResponse.push({ name: `${details.driver_first_name} ${details.driver_last_name}`, role: "driver", phone_number: details.driver_phone_number });
             }
             if (details.attendant_first_name) {
                 staffResponse.push({ name: `${details.attendant_first_name} ${details.attendant_last_name}`, role: "attendant", phone_number: details.attendant_phone_number });
             }
             console.log(`Returning ${staffResponse.length} staff for bus ${requestedBusId}`);
             res.json(staffResponse); // Return array as expected by client
         } else {
             console.log(`Bus not found: ${requestedBusId}`);
             res.status(404).json({ message: 'Bus not found' });
         }
     } catch (err) {
         console.error(`Error fetching staff for bus ${requestedBusId}:`, err);
         res.status(500).json({ message: 'Server error fetching bus staff.' });
     }
 });

 // POST GPS logs from bus_incharge
 app.post('/api/gps_logs', authenticateToken, authorize('bus_incharge'), gpsLogValidation, async (req, res) => {
     const errors = validationResult(req);
     if (!errors.isEmpty()) {
       return res.status(400).json({ message: errors.array()[0].msg });
     }

     try {
         const { bus_id, latitude, longitude } = req.body;
         const userId = req.user.userId;

         // console.log(`GPS Log from user ${userId} for bus ${bus_id}: Lat=${latitude}, Lon=${longitude}`); // Verbose log

         await pool.query(
             'INSERT INTO gps_logs (bus_id, latitude, longitude, timestamp) VALUES ($1, $2, $3, NOW())',
             [bus_id, latitude, longitude]
         );
         res.status(201).json({ message: 'GPS log saved' });
     } catch (err) {
         console.error('GPS logging error:', err);
         if (err.code === '23503') { // FK violation
             return res.status(400).json({ message: 'Invalid bus_id.' });
         }
         res.status(500).json({ message: 'Server error logging GPS' });
     }
 });


 app.get(
    "/api/admin/buses/locations",
    authenticateToken,
    authorize("school_authority"),
    async (req, res) => {
        console.log(
            `[${new Date().toISOString()}] School authority ${req.user.userId} requesting all bus locations.`
        );
        try {
            // Query to get the latest timestamped log for each distinct bus_id
            const queryText = `
                SELECT DISTINCT ON (bus_id)
                    bus_id, latitude, longitude, timestamp
                FROM gps_logs
                ORDER BY bus_id, timestamp DESC;
            `;
            const result = await pool.query(queryText);
            console.log(`Found latest location for ${result.rowCount} buses.`);
            res.json(result.rows); // Send the array of location objects
        } catch (err) {
            console.error(
                `[${new Date().toISOString()}] Error fetching all bus locations for authority ${req.user.userId}:`,
                err
            );
            res.status(500).json({ message: "Server error fetching bus locations." });
        }
    }
);

// GET latest location for a specific bus
app.get('/api/bus/:busId/location', authenticateToken, async (req, res) => { // :busId makes it dynamic
    const { busId } = req.params; // Extracts the ID from the URL
    const requestedBusId = parseInt(busId, 10); // Uses the extracted ID

    // Authorization check (e.g., Parent can only see their child's bus)
    // Implement appropriate authorization logic here based on req.user.role
    if (req.user.role === 'parent') {
        try {
            const parentResult = await pool.query('SELECT parent_id FROM parents WHERE user_id = $1', [req.user.userId]);
            if (parentResult.rowCount === 0) return res.status(403).json({ message: 'Parent profile missing.' });
            const parentId = parentResult.rows[0].parent_id;
            const studentBusCheck = await pool.query('SELECT 1 FROM students WHERE parent_id = $1 AND bus_id = $2 LIMIT 1', [parentId, requestedBusId]);
            if (studentBusCheck.rowCount === 0) {
                console.warn(`Parent user ${req.user.userId} (parent_id ${parentId}) attempted to access location for bus ${requestedBusId} they have no children on.`);
                return res.status(403).json({ message: 'You are not authorized to view this bus location.' });
            }
        } catch (authErr) {
            console.error(`Authorization check failed for user ${req.user.userId} accessing bus ${requestedBusId}:`, authErr);
            return res.status(500).json({ message: 'Server error during authorization.' });
        }
    } // Add checks for other roles ('bus_incharge', 'school_authority' might need access too)


    const client = await pool.connect();
    try {
        const queryText = `
          SELECT latitude, longitude, timestamp
          FROM gps_logs
          WHERE bus_id = $1
          ORDER BY timestamp DESC
          LIMIT 1`;
        const result = await client.query(queryText, [requestedBusId]); // Pass the dynamic ID to the query

        if (result.rowCount === 0) {
          // This sends the 404 if NO logs are found for this bus
          return res.status(404).json({ message: 'No location data found for this bus yet.' });
        }
        // Send back the found location
        res.status(200).json(result.rows[0]);
    } catch (err) {
         console.error(`Error fetching location for bus ${requestedBusId}:`, err);
         res.status(500).json({ message: 'Server error fetching location.' });
    } finally {
        client.release();
    }
});

// === SCHOOL AUTHORITY ROUTES ===

// GET All Students
// GET All Students (MODIFIED TO INCLUDE LATEST ATTENDANCE STATUS)
app.get("/api/admin/students/all", authenticateToken, authorize("school_authority"), async (req, res) => {
  console.log(`Authority ${req.user.userId} requesting all students.`);
  try {
      // Modified Query:
      // 1. Use a Common Table Expression (CTE) or Subquery to get the latest attendance status for each student.
      //    Using DISTINCT ON is efficient in PostgreSQL.
      // 2. LEFT JOIN the main student query with the latest status.
      // 3. Select the status, using COALESCE to handle students with no attendance records yet.
      const queryText = `
          WITH LatestAttendance AS (
              SELECT DISTINCT ON (student_id)
                  student_id,
                  status AS latest_status
              FROM attendance
              ORDER BY student_id, timestamp DESC -- Get the latest record per student
          )
          SELECT
              s.student_id,
              s.first_name, -- Renamed for clarity, assuming Student.java expects 'firstName' etc.
              s.last_name,
              s.roll_no,      -- Added roll_no
              s.class,        -- Added class
              s.bus_id,
              b.bus_plate,
              p.parent_id,
              u.first_name AS parent_first_name,
              u.last_name AS parent_last_name,
              u.email AS parent_email,
              u.phone_number AS parent_phone,
              COALESCE(la.latest_status, 'Unknown') AS "attendanceStatus" -- Select status, default to 'Unknown'
                                                                         -- Use "attendanceStatus" to match Java @SerializedName
          FROM students s
          LEFT JOIN buses b ON s.bus_id = b.bus_id
          LEFT JOIN parents p ON s.parent_id = p.parent_id
          LEFT JOIN users u ON p.user_id = u.user_id
          LEFT JOIN LatestAttendance la ON s.student_id = la.student_id -- Join with latest status
          ORDER BY s.last_name, s.first_name;
      `;

      const result = await pool.query(queryText); // Execute the modified query

      console.log(`Found ${result.rowCount} students for authority (with attendance status).`);

      // Map results slightly to match expected field names in Student.java if needed
      // (e.g., if Student.java uses 'name' instead of 'first_name' + 'last_name')
      // This example assumes the SELECT aliases match @SerializedName in Student.java
      const studentsWithStatus = result.rows.map(row => ({
          id: row.student_id, // Assuming Student.java uses 'id'
          name: `${row.first_name} ${row.last_name}`, // Assuming Student.java uses 'name'
          rollNo: row.roll_no, // Assuming Student.java uses 'rollNo'
          className: row.class, // Assuming Student.java uses 'className'
          busRoute: row.bus_plate, // Assuming bus_plate represents the route info needed
          parentContact: row.parent_email, // Or parent_phone depending on Student.java
          // qrCodeData: row.qr_code_data, // Add if needed and selected
          attendanceStatus: row.attendanceStatus // This now comes from the query
          // Add other fields from Student.java if they are selected in the query
      }));


      res.json(studentsWithStatus); // Send the modified list

  } catch (err) {
      console.error(`Error fetching students for authority ${req.user.userId}:`, err);
      res.status(500).json({ message: "Server error fetching students." });
  }
});

// GET All Buses
app.get("/api/admin/buses/all", authenticateToken, authorize("school_authority"), async (req, res) => {
    console.log(`Authority ${req.user.userId} requesting all buses.`);
    try {
      const result = await pool.query(`
        SELECT
          b.bus_id, b.bus_plate,
          d_user.first_name AS driver_first_name, d_user.last_name AS driver_last_name,
          a_user.first_name AS attendant_first_name, a_user.last_name AS attendant_last_name
        FROM buses b
        LEFT JOIN bus_staff driver_staff ON b.driver_id = driver_staff.staff_id LEFT JOIN users d_user ON driver_staff.user_id = d_user.user_id
        LEFT JOIN bus_staff attendant_staff ON b.attendant_id = attendant_staff.staff_id LEFT JOIN users a_user ON attendant_staff.user_id = a_user.user_id
        ORDER BY b.bus_plate
      `);
      console.log(`Found ${result.rowCount} buses for authority.`);
      res.json(result.rows);
    } catch (err) {
      console.error(`Error fetching buses for authority ${req.user.userId}:`, err);
      res.status(500).json({ message: "Server error fetching buses." });
    }
});

// GET All Bus Staff (Corrected Role Logic)
app.get('/api/admin/bus_staff/all', authenticateToken, authorize('school_authority'), async (req, res) => {
    console.log(`Authority ${req.user.userId} requesting all bus staff.`);
    try {
      // Query users specifically with role 'bus_incharge' and join other tables
      const result = await pool.query(`
        SELECT
          u.user_id, u.first_name, u.last_name, u.email, u.phone_number,
          u.role, -- Keep role if needed, will be 'bus_incharge'
          bs.staff_id,
          b.bus_id, b.bus_plate,
          -- Determine if they are driver or attendant for their assigned bus
          CASE
              WHEN b.driver_id = bs.staff_id THEN 'driver'
              WHEN b.attendant_id = bs.staff_id THEN 'attendant'
              ELSE NULL
          END AS bus_staff_type
        FROM users u
        JOIN bus_staff bs ON u.user_id = bs.user_id -- Only bus_incharge users will have bus_staff entries
        LEFT JOIN buses b ON bs.staff_id = b.driver_id OR bs.staff_id = b.attendant_id
        WHERE u.role = 'bus_incharge' -- Filter for the correct role
        ORDER BY u.last_name, u.first_name
      `);
      console.log(`Found ${result.rowCount} bus staff members for authority.`);
      res.json(result.rows);
    } catch (err) {
      console.error(`Error fetching bus staff for authority ${req.user.userId}:`, err);
      res.status(500).json({ message: 'Server error fetching bus staff.' });
    }
});


 // === SCAN ROUTE ===
 app.post('/api/scan', authenticateToken, authorize('bus_incharge'), async (req, res) => {
     const { roll_no } = req.body;
     const cleanRollNo = roll_no ? roll_no.trim() : '';

     if (!cleanRollNo) {
         return res.status(400).json({ success: false, message: "roll_no is required." });
     }

     try {
         // Pass pool and admin to the handler function
         const result = await processScan(pool, admin, cleanRollNo);

         if (result.success) {
             res.status(200).json({
                 success: true,
                 message: result.message || "Scan Successful",
                 status: result.status
             });
         } else {
             const statusCode = result.error === "Student not found" ? 404 : 500;
             res.status(statusCode).json({
                 success: false,
                 message: result.error || "Failed to process scan"
             });
         }

     } catch (handlerError) {
         console.error(`[API /api/scan] Error for roll_no ${cleanRollNo}:`, handlerError);
         res.status(500).json({ success: false, message: "Internal server error." });
     }
 });

 // === SOS ROUTE ===
// === SOS ROUTE (Corrected) ===
app.post('/api/sos', authenticateToken, authorize('bus_incharge'), sosValidation, async (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ message: errors.array()[0].msg });
    }

    const { bus_id, message: sosMessage = 'Emergency alert triggered!', severity = 'serious' } = req.body;
    const triggeringUserId = req.user.userId;

    console.log(`[SOS] Triggered by user ${triggeringUserId} for bus ${bus_id}. Severity: ${severity}`);

    const client = await pool.connect();
    try {
      // 1. Find authorities (with fcm_token)
      const authoritiesResult = await client.query(
        'SELECT user_id, email, first_name, fcm_token FROM users WHERE role = $1', ['school_authority']
      );
      const authorities = authoritiesResult.rows;
      console.log(`[SOS] Found ${authorities.length} authorities.`); // [cite: 1]

      // 2. Find parents on the bus (with fcm_token)
      const parentsResult = await client.query(`
        SELECT DISTINCT u.user_id, u.email, u.first_name, u.fcm_token
        FROM users u JOIN parents p ON u.user_id = p.user_id JOIN students s ON p.parent_id = s.parent_id
        WHERE s.bus_id = $1`, [bus_id]
      ); // [cite: 1]
      const parents = parentsResult.rows;
      console.log(`[SOS] Found ${parents.length} parents for bus ${bus_id}.`); // [cite: 1]

      // 3. Combine recipients (avoid duplicates)
      const recipients = [...authorities];
      const recipientEmails = new Set(authorities.map(a => a.email));
      parents.forEach(parent => {
        if (!recipientEmails.has(parent.email)) {
           recipients.push(parent);
           recipientEmails.add(parent.email);
        }
      });

      if (recipients.length === 0) {
        console.warn(`[SOS] No recipients found for bus ${bus_id}.`);
      }

      // 4. Send Email Notifications
      if (recipients.length > 0) {
          const emailSubject = `[${severity.toUpperCase()}] SOS Alert - Bus ${bus_id} - SchoolWay`;
          const emailPromises = recipients.map(recipient => {
              const emailText = `Hello ${recipient.first_name || 'User'},\n\nSOS alert (${severity}) for Bus ID ${bus_id}.\nMessage: ${sosMessage}\nPlease investigate.\nTriggered by user ID: ${triggeringUserId}`;
              // console.log(`[SOS] Sending email to ${recipient.email}`); // Less verbose log
              return transporter.sendMail({
                  from: process.env.EMAIL_USER,
                  to: recipient.email,
                  subject: emailSubject,
                  text: emailText,
              }).catch(emailError => {
                    console.error(`[SOS] Failed to send email to ${recipient.email}:`, emailError);
                    return null; // Don't fail all if one email fails
              });
          });
          await Promise.all(emailPromises);
          console.log("[SOS] Email notifications attempted."); // [cite: 1]
      }

      // 5. Send Push Notifications
      const fcmTokens = recipients
          .map(r => r.fcm_token)
          .filter(token => token && typeof token === 'string' && token.trim() !== ''); // [cite: 1]

      if (fcmTokens.length > 0) {
          // Construct the multicast message payload
          // Ensure the payload format is suitable for sendEachForMulticast
          const notificationPayload = {
              notification: {
                  title: `[${severity.toUpperCase()}] SOS Alert - Bus ${bus_id}`,
                  body: `Alert from Bus ${bus_id}: ${sosMessage}`
              },
              android: {
                  priority: 'high',
                  notification: { sound: 'default' }
              }
              // Add apns config if needed for iOS
          };

          // *** CORRECTED: Use sendEachForMulticast ***
          const message = {
              tokens: fcmTokens, // Keep the tokens array
              notification: notificationPayload.notification, // Pass notification object
              android: notificationPayload.android          // Pass android config
              // apns: notificationPayload.apns // Pass apns config if you have it
          };


          try {
              console.log(`[SOS] Sending FCM to ${fcmTokens.length} tokens.`); // [cite: 1]
              // *** THE FIX IS HERE: Replace sendMulticast with sendEachForMulticast ***
              const response = await admin.messaging().sendEachForMulticast(message); // [cite: 1]
              // ***********************************************************************
              console.log(`[SOS] FCM results: ${response.successCount} success, ${response.failureCount} failure.`); // [cite: 1]

              // Optional: Handle failures (e.g., remove invalid tokens)
              if (response.failureCount > 0) {
                  response.responses.forEach((resp, idx) => {
                      if (!resp.success) {
                          const failedToken = fcmTokens[idx];
                          console.error(`[SOS] FCM send failed for token ending ...${failedToken.slice(-6)}: ${resp.error.code} - ${resp.error.message}`);
                          // TODO: Add logic here to remove or flag the invalid failedToken in your database
                      }
                  });
              }
          } catch (fcmError) {
               console.error('[SOS] Error sending FCM multicast:', fcmError); // [cite: 1]
           }
      } else {
          console.log(`[SOS] No valid FCM tokens found. Skipping push notification.`); // [cite: 1]
       }

      // Send final success response
      res.status(200).json({ message: `SOS alert processed. Notified ${recipients.length} recipients.` });

    } catch (err) {
      console.error('[SOS] Processing error:', err);
      res.status(500).json({ message: 'Server error processing SOS' });
    } finally {
      if (client) client.release();
    }
 });

 // --- NEW: STUDENT ATTENDANCE ENDPOINT ---
 app.get('/api/students/:studentId/attendance', authenticateToken, async (req, res) => {
     const requestedStudentId = parseInt(req.params.studentId, 10);
     const requestingUserId = req.user.userId;
     const requestingUserRole = req.user.role;

     console.log(`[API GET /attendance] User ${requestingUserId} (${requestingUserRole}) requesting attendance for student ${requestedStudentId}`);

     // Validate studentId
     if (isNaN(requestedStudentId) || requestedStudentId <= 0) {
         return res.status(400).json({ message: 'Invalid student ID format.' });
     }

     const client = await pool.connect();
     try {
         // --- Authorization Check ---
         let isAuthorized = false;
         if (requestingUserRole === 'school_authority') {
             isAuthorized = true; // School authority can see any student
             console.log(`[API GET /attendance] Access granted for school_authority ${requestingUserId}`);
         } else if (requestingUserRole === 'parent') {
             // Check if this parent is linked to the requested student
             const parentCheck = await client.query(
                 'SELECT 1 FROM students s JOIN parents p ON s.parent_id = p.parent_id WHERE s.student_id = $1 AND p.user_id = $2',
                 [requestedStudentId, requestingUserId]
             );
             if (parentCheck.rowCount > 0) {
                 isAuthorized = true;
                 console.log(`[API GET /attendance] Access granted for parent ${requestingUserId}`);
             } else {
                  console.warn(`[API GET /attendance] Forbidden: Parent ${requestingUserId} cannot access student ${requestedStudentId}`);
             }
         } else {
              console.warn(`[API GET /attendance] Forbidden: Role ${requestingUserRole} cannot access attendance.`);
         }

         if (!isAuthorized) {
              return res.status(403).json({ message: 'You are not authorized to view this student\'s attendance.' });
         }
         // --- End Authorization ---

         // --- Fetch Attendance Data ---
         // Weekly: Records from the last 7 days (adjust interval as needed)
         const weeklyQuery = `
             SELECT date::text, status, timestamp -- Cast date to text for consistent JSON
             FROM attendance
             WHERE student_id = $1 AND date >= CURRENT_DATE - INTERVAL '7 days'
             ORDER BY timestamp DESC`;
         const weeklyResult = await client.query(weeklyQuery, [requestedStudentId]);

         // Monthly: Records from the current calendar month
         const monthlyQuery = `
             SELECT date::text, status, timestamp -- Cast date to text
             FROM attendance
             WHERE student_id = $1 AND date_trunc('month', date) = date_trunc('month', CURRENT_DATE)
             ORDER BY timestamp DESC`;
         const monthlyResult = await client.query(monthlyQuery, [requestedStudentId]);

         console.log(`[API GET /attendance] Found ${weeklyResult.rowCount} weekly, ${monthlyResult.rowCount} monthly records for student ${requestedStudentId}`);

         // Return data in the format expected by AttendanceResponse in Android
         res.status(200).json({
             weeklyAttendance: weeklyResult.rows,
             monthlyAttendance: monthlyResult.rows
         });
         // --- End Fetch ---

     } catch (err) {
         console.error(`[API GET /attendance] Error fetching attendance for student ${requestedStudentId}:`, err);
         res.status(500).json({ message: 'Server error fetching attendance data.' });
     } finally {
         if (client) client.release();
     }
 });
 // --- END NEW ENDPOINT ---


 // === BACKGROUND TASK ===
 async function cleanupOldGpsLogs() {
    const cleanupInterval = process.env.GPS_CLEANUP_INTERVAL || '5 minutes';
    // console.log(`[Cleanup] Deleting GPS logs older than ${cleanupInterval}...`); // Less verbose
    const client = await pool.connect();
    try {
        const queryText = `DELETE FROM gps_logs WHERE timestamp < NOW() - INTERVAL '${cleanupInterval}'`;
        const result = await client.query(queryText);
        if (result.rowCount > 0) {
            console.log(`[Cleanup] Deleted ${result.rowCount} old GPS log(s).`);
        }
    } catch (err) {
        console.error(`[Cleanup] Error during GPS log cleanup:`, err);
    } finally {
        client.release();
    }
 }

 const CLEANUP_INTERVAL_MS = process.env.GPS_CLEANUP_SCHEDULE_MS || 60000; // Default 5 mins
 setInterval(cleanupOldGpsLogs, CLEANUP_INTERVAL_MS);
 console.log(`[Scheduler] GPS log cleanup task runs every ${CLEANUP_INTERVAL_MS / 60000} minutes.`);

 // POST Add a New Student
app.post('/api/admin/students/add', authenticateToken, authorize('school_authority'), [
  // Add validation rules similar to your other routes
  body('firstname').notEmpty().trim().escape().withMessage('First name is required'),
  body('lastname').notEmpty().trim().escape().withMessage('Last name is required'),
  body('class').notEmpty().trim().escape().withMessage('Class is required'),
  body('rollno').notEmpty().trim().escape().withMessage('Roll number is required'),
  body('parent_email').isEmail().normalizeEmail().withMessage('Valid parent email is required'),
  body('bus_plate').optional().trim().escape() // Optional field
], async (req, res) => {
  console.log(`[API POST /admin/students/add] Received request from authority ${req.user.userId}`); // Log entry

  const errors = validationResult(req);
  if (!errors.isEmpty()) {
      return res.status(400).json({ message: errors.array()[0].msg });
  }

  const { firstname, lastname, class: studentClass, rollno, parent_email, bus_plate } = req.body;
  const client = await pool.connect();

  try {
      await client.query('BEGIN');
      console.log(`[API POST /admin/students/add] Looking up parent with email: ${parent_email}`);

      // 1. Find Parent ID from email
      const parentUserResult = await client.query('SELECT user_id FROM users WHERE email = $1 AND role = $2', [parent_email, 'parent']);
      if (parentUserResult.rowCount === 0) {
          await client.query('ROLLBACK');
          console.warn(`[API POST /admin/students/add] Parent not found with email: ${parent_email}`);
          return res.status(404).json({ message: `Parent with email ${parent_email} not found.` });
      }
      const parentUserId = parentUserResult.rows[0].user_id;
      const parentResult = await client.query('SELECT parent_id FROM parents WHERE user_id = $1', [parentUserId]);
      if (parentResult.rowCount === 0) {
           await client.query('ROLLBACK'); // Should not happen if user exists, but good practice
           console.error(`[API POST /admin/students/add] Inconsistency: User ${parentUserId} exists but no parent record.`);
           return res.status(500).json({ message: 'Internal server error: Parent record inconsistency.' });
      }
      const parentId = parentResult.rows[0].parent_id;
      console.log(`[API POST /admin/students/add] Found parent_id: ${parentId} for email: ${parent_email}`);

      // 2. Find Bus ID from bus_plate (if provided)
      let busId = null; // Default to null if no plate provided
      if (bus_plate && bus_plate.trim() !== '') {
           console.log(`[API POST /admin/students/add] Looking up bus with plate: ${bus_plate}`);
           const busResult = await client.query('SELECT bus_id FROM buses WHERE bus_plate = $1', [bus_plate]);
           if (busResult.rowCount === 0) {
              await client.query('ROLLBACK');
              console.warn(`[API POST /admin/students/add] Bus not found with plate: ${bus_plate}`);
              return res.status(404).json({ message: `Bus with plate ${bus_plate} not found.` });
           }
           busId = busResult.rows[0].bus_id;
           console.log(`[API POST /admin/students/add] Found bus_id: ${busId} for plate: ${bus_plate}`);
      } else {
          console.log(`[API POST /admin/students/add] No bus plate provided, assigning bus_id as null.`);
      }


      // 3. Insert Student (handle potential duplicate roll_no)
      try {
          console.log(`[API POST /admin/students/add] Attempting to insert student: ${firstname} ${lastname}, Roll: ${rollno}, Class: ${studentClass}, ParentID: ${parentId}, BusID: ${busId}`);
          const insertResult = await client.query(
              `INSERT INTO students (first_name, last_name, parent_id, bus_id, roll_no, class)
               VALUES ($1, $2, $3, $4, $5, $6) RETURNING student_id`,
              [firstname, lastname, parentId, busId, rollno, studentClass]
          );

           const newStudentId = insertResult.rows[0].student_id;
           console.log(`[API POST /admin/students/add] Student inserted successfully with ID: ${newStudentId}`);

          // 4. Commit transaction
          await client.query('COMMIT');

          // 5. Send Success Response (include student ID potentially)
          res.status(201).json({
               message: 'Student added successfully',
               student: { // Send back some details of the added student
                  student_id: newStudentId,
                  first_name: firstname,
                  last_name: lastname,
                  roll_no: rollno,
                  class: studentClass,
                  parent_id: parentId,
                  bus_id: busId
               }
           });

      } catch (insertErr) {
          await client.query('ROLLBACK'); // Rollback on insertion error
          if (insertErr.code === '23505' && insertErr.constraint === 'students_roll_no_key') { // Specific check for unique constraint
              console.warn(`[API POST /admin/students/add] Duplicate roll number: ${rollno}`);
              return res.status(409).json({ message: `Roll number ${rollno} already exists.` });
          }
           console.error(`[API POST /admin/students/add] Error during student insertion:`, insertErr);
           throw insertErr; // Re-throw other insertion errors
      }

  } catch (err) {
      // Ensure rollback happens if not already done
      // Note: A robust implementation might check client transaction state
      // await client.query('ROLLBACK'); // Potentially redundant, but safer depending on error origin
      console.error(`[API POST /admin/students/add] Error processing add student request:`, err);
      res.status(500).json({ message: 'Server error while adding student.' });
  } finally {
      if (client) client.release(); // Ensure client is always released
      console.log(`[API POST /admin/students/add] Request finished.`);
  }
});

// Add these inside your test_server3.js file, typically after other /api/admin/students routes

// GET Single Student Details (for Update Page)
app.get("/api/admin/students/:studentId", authenticateToken, authorize("school_authority"), async (req, res) => {
  const studentId = parseInt(req.params.studentId, 10);
  console.log(`[API GET /admin/students/:id] Authority ${req.user.userId} requesting details for student ${studentId}`);

  if (isNaN(studentId)) {
      return res.status(400).json({ message: "Invalid student ID format." });
  }

  try {
      // Query to get student details, including parent email and bus plate for the form
      const result = await pool.query(`
       SELECT
         s.student_id, s.first_name, s.last_name, s.roll_no, s.class,
         s.bus_id, b.bus_plate,
         s.parent_id, u.email AS parent_email
       FROM students s
       LEFT JOIN buses b ON s.bus_id = b.bus_id
       LEFT JOIN parents p ON s.parent_id = p.parent_id
       LEFT JOIN users u ON p.user_id = u.user_id
       WHERE s.student_id = $1
     `, [studentId]);

      if (result.rowCount === 0) {
          console.log(`[API GET /admin/students/:id] Student not found: ${studentId}`);
          return res.status(404).json({ message: "Student not found." });
      }

      console.log(`[API GET /admin/students/:id] Found details for student ${studentId}`);
      res.json(result.rows[0]); // Send the single student object

  } catch (err) {
      console.error(`[API GET /admin/students/:id] Error fetching student ${studentId}:`, err);
      res.status(500).json({ message: "Server error fetching student details." });
  }
});

// PUT Update Student Details
app.put("/api/admin/students/update/:studentId", authenticateToken, authorize("school_authority"), [
  // Validation rules
  body('firstname').notEmpty().trim().escape().withMessage('First name is required'),
  body('lastname').notEmpty().trim().escape().withMessage('Last name is required'),
  body('class').notEmpty().trim().escape().withMessage('Class is required'),
  body('rollno').notEmpty().trim().escape().withMessage('Roll number is required'),
  body('parent_email').isEmail().normalizeEmail().withMessage('Valid parent email is required'),
  body('bus_plate').optional({ checkFalsy: true }).trim().escape()
], async (req, res) => {
  // Added log to confirm route is hit
  console.log(">>> HIT: PUT /api/admin/students/update/:studentId <<<");
  const studentId = parseInt(req.params.studentId, 10);
  console.log(`[API PUT /admin/students/update/:id] Authority ${req.user.userId} updating student ${studentId}`);

  const errors = validationResult(req);
  if (!errors.isEmpty()) {
      console.warn(`[API PUT /update] Validation errors for student ${studentId}:`, errors.array());
      return res.status(400).json({ message: errors.array()[0].msg });
  }
  if (isNaN(studentId)) {
      console.warn(`[API PUT /update] Invalid student ID format received: ${req.params.studentId}`);
      return res.status(400).json({ message: "Invalid student ID format." });
  }

  const { firstname, lastname, class: studentClass, rollno, parent_email, bus_plate } = req.body;
  const client = await pool.connect();

  try {
      await client.query('BEGIN');

      const studentCheck = await client.query('SELECT 1 FROM students WHERE student_id = $1', [studentId]);
      if (studentCheck.rowCount === 0) {
          await client.query('ROLLBACK');
          console.warn(`[API PUT /update] Student not found: ${studentId}`);
          return res.status(404).json({ message: 'Student to update not found.' });
      }

      const rollNoCheck = await client.query(
          'SELECT student_id FROM students WHERE roll_no = $1 AND student_id != $2',
          [rollno, studentId]
      );
      if (rollNoCheck.rowCount > 0) {
           await client.query('ROLLBACK');
           console.warn(`[API PUT /update] Duplicate roll number ${rollno} attempted for student ${studentId}`);
           return res.status(409).json({ message: `Roll number ${rollno} is already used by another student.` });
      }

      const parentUserResult = await client.query('SELECT user_id FROM users WHERE email = $1 AND role = $2', [parent_email, 'parent']);
      if (parentUserResult.rowCount === 0) {
          await client.query('ROLLBACK');
          console.warn(`[API PUT /update] Parent not found with email: ${parent_email}`);
          return res.status(404).json({ message: `Parent with email ${parent_email} not found.` });
      }
      const parentUserId = parentUserResult.rows[0].user_id;
      const parentResult = await client.query('SELECT parent_id FROM parents WHERE user_id = $1', [parentUserId]);
       if (parentResult.rowCount === 0) {
           await client.query('ROLLBACK');
           console.error(`[API PUT /update] Inconsistency: User ${parentUserId} exists but no parent record.`);
           return res.status(500).json({ message: 'Internal server error: Parent record inconsistency.' });
       }
      const parentId = parentResult.rows[0].parent_id;
      console.log(`[API PUT /update] Found parent_id: ${parentId} for email: ${parent_email}`);

      let busId = null;
      if (bus_plate && bus_plate.trim() !== '') {
          console.log(`[API PUT /update] Looking up bus with plate: ${bus_plate}`);
          const busResult = await client.query('SELECT bus_id FROM buses WHERE bus_plate = $1', [bus_plate]);
          if (busResult.rowCount === 0) {
              await client.query('ROLLBACK');
              console.warn(`[API PUT /update] Bus not found with plate: ${bus_plate}`);
              return res.status(404).json({ message: `Bus with plate ${bus_plate} not found.` });
          }
          busId = busResult.rows[0].bus_id;
          console.log(`[API PUT /update] Found bus_id: ${busId} for plate: ${bus_plate}`);
      } else {
          console.log(`[API PUT /update] Bus plate is empty or null, setting bus_id to null.`);
      }

      const updateQuery = `
          UPDATE students
          SET first_name = $1, last_name = $2, class = $3, roll_no = $4, parent_id = $5, bus_id = $6
          WHERE student_id = $7
      `;
      const updateParams = [firstname, lastname, studentClass, rollno, parentId, busId, studentId];

      console.log(`[API PUT /update] Executing update query for student ${studentId}`);
      await client.query(updateQuery, updateParams);
      console.log(`[API PUT /update] Student ${studentId} updated successfully.`);

      await client.query('COMMIT');
      res.status(200).json({ message: 'Student updated successfully' });

  } catch (err) {
      await client.query('ROLLBACK');
      console.error(`[API PUT /update] Error updating student ${studentId}:`, err); // Log the actual error
       if (err.code === '23505') {
           return res.status(409).json({ message: 'Update failed due to a conflict (e.g., roll number already exists).' });
       }
       // Respond with a generic server error message, but log the specific error
      res.status(500).json({ message: 'Server error while updating student.' });
  } finally {
      if (client) client.release();
      console.log(`[API PUT /update] Request finished for student ${studentId}.`);
  }
});

// DELETE Student
app.delete("/api/admin/students/delete/:studentId", authenticateToken, authorize("school_authority"), async (req, res) => {
  const studentId = parseInt(req.params.studentId, 10);
  console.log(`[API DELETE /admin/students/delete/:id] Authority ${req.user.userId} deleting student ${studentId}`);

  if (isNaN(studentId)) {
      return res.status(400).json({ message: "Invalid student ID format." });
  }

  const client = await pool.connect();
  try {
      await client.query('BEGIN');

      // Check if student exists before deleting
      const checkResult = await client.query('SELECT 1 FROM students WHERE student_id = $1', [studentId]);
      if (checkResult.rowCount === 0) {
          await client.query('ROLLBACK');
          console.log(`[API DELETE] Student not found: ${studentId}`);
          return res.status(404).json({ message: 'Student not found.' });
      }

      // Perform deletion
      await client.query('DELETE FROM students WHERE student_id = $1', [studentId]);
      // Note: Related attendance records might be automatically deleted if ON DELETE CASCADE is set, check your schema.

      await client.query('COMMIT');
      console.log(`[API DELETE] Student ${studentId} deleted successfully.`);
      res.status(200).json({ message: 'Student deleted successfully' }); // Send 200 OK with message
      // Or use res.status(204).send(); // Send 204 No Content

  } catch (err) {
      await client.query('ROLLBACK');
      console.error(`[API DELETE] Error deleting student ${studentId}:`, err);
      // Check for foreign key constraints if deletion fails unexpectedly
      res.status(500).json({ message: 'Server error while deleting student.' });
  } finally {
      if (client) client.release();
  }
});

// Make sure these routes are placed *before* your catch-all 404 handler and generic error handler
 // --- Catch-all 404 Handler (Place AFTER all specific routes) ---
 app.use((req, res, next) => {
    res.status(404).json({ message: `Cannot ${req.method} ${req.path}` });
 });

 // --- Generic Error Handler (Place LAST) ---
 app.use((err, req, res, next) => {
    console.error(`[${new Date().toISOString()}] Unhandled Error:`, err.stack || err); // Log stack trace for better debugging
    // Avoid sending detailed errors to the client in production
    const statusCode = err.status || 500;
    const message = process.env.NODE_ENV === 'production' ? 'Internal Server Error' : err.message;
    res.status(statusCode).json({ message: message });
 });


 // === SERVER START ===
 const server = app.listen(PORT, '0.0.0.0', () => {
   console.log(`HTTP Server running on port ${PORT}`);
 });

 // Optional: Graceful shutdown handling
 process.on('SIGTERM', () => {
     console.log('SIGTERM signal received: closing HTTP server')
     server.close(() => {
         console.log('HTTP server closed')
         pool.end(() => { // Close database connections
             console.log('Database pool closed')
             process.exit(0)
         })
     })
 })

 process.on('SIGINT', () => {
     console.log('SIGINT signal received: closing HTTP server')
     server.close(() => {
         console.log('HTTP server closed')
         pool.end(() => { // Close database connections
             console.log('Database pool closed')
             process.exit(0)
         })
     })
 })




// From test_serv1.js

// GET staff (driver/attendant) for a specific bus
app.get('/api/bus/:busId/staff', authenticateToken, async (req, res) => {
  const { busId } = req.params;
  const requestedBusId = parseInt(busId, 10); // Ensure base 10 parsing

  console.log(`Workspaceing staff details for bus ID: ${requestedBusId} by user ${req.user.userId}`);

  if (isNaN(requestedBusId)) {
      return res.status(400).json({ message: 'Invalid bus ID format.' });
  }

  try {
      // Query joins buses -> bus_staff -> users to get names and phone numbers
      const result = await pool.query(`
        SELECT
          b.bus_id,
          -- Driver details
          driver_staff.staff_id as driver_staff_id, // Include staff ID
          d_user.first_name AS driver_first_name,
          d_user.last_name AS driver_last_name,
          d_user.phone_number AS driver_phone_number, // <<< PHONE NUMBER
          -- Attendant details
          attendant_staff.staff_id as attendant_staff_id, // Include staff ID
          a_user.first_name AS attendant_first_name,
          a_user.last_name AS attendant_last_name,
          a_user.phone_number AS attendant_phone_number // <<< PHONE NUMBER
        FROM buses b
        LEFT JOIN bus_staff driver_staff ON b.driver_id = driver_staff.staff_id
        LEFT JOIN users d_user ON driver_staff.user_id = d_user.user_id
        LEFT JOIN bus_staff attendant_staff ON b.attendant_id = attendant_staff.staff_id
        LEFT JOIN users a_user ON attendant_staff.user_id = a_user.user_id
        WHERE b.bus_id = $1
      `, [requestedBusId]);

      if (result.rows.length > 0) {
          const staffDetails = result.rows[0];
          const busStaffResponse = []; // Array to hold staff objects

          // Create driver object if present
          if (staffDetails.driver_staff_id) {
              busStaffResponse.push({
                  id: staffDetails.driver_staff_id, // Use 'id' or 'staff_id' consistently
                  name: `${staffDetails.driver_first_name || ''} ${staffDetails.driver_last_name || ''}`.trim(),
                  role: "driver",
                  phone_number: staffDetails.driver_phone_number // Include phone
              });
          }
          // Create attendant object if present
          if (staffDetails.attendant_staff_id) {
              busStaffResponse.push({
                  id: staffDetails.attendant_staff_id,
                  name: `${staffDetails.attendant_first_name || ''} ${staffDetails.attendant_last_name || ''}`.trim(),
                  role: "attendant",
                  phone_number: staffDetails.attendant_phone_number // Include phone
              });
          }
          console.log(`Returning ${busStaffResponse.length} staff members for bus ID ${requestedBusId}`);
          // The response is an array of staff objects, matching BusStaff.java
          res.json(busStaffResponse);
      } else {
          console.log(`Bus not found with ID: ${requestedBusId}`);
          res.status(404).json({ message: 'Bus not found' });
      }
  } catch (err) {
      console.error(`Error fetching bus staff for bus ID ${requestedBusId}:`, err);
      res.status(500).json({ message: 'Server error fetching bus staff.' });
  }
});


// GET Route Path Coordinates by Route ID
app.get('/api/routes/:routeId/path', authenticateToken, async (req, res) => {
  const routeId = parseInt(req.params.routeId, 10);
  console.log(`[API GET /routes/:routeId/path] Request for path of routeId: ${routeId} by user ${req.user.userId}`);

  if (isNaN(routeId)) {
      return res.status(400).json({ message: 'Invalid route ID format.' });
  }

  // Optional: Add authorization if needed (e.g., parent can only get path for their child's bus route)
  // Implement authorization check here based on req.user.role and routeId/busId linkage

  try {
      const result = await pool.query(
          'SELECT route_path_coordinates FROM routes WHERE route_id = $1',
          [routeId]
      );

      if (result.rowCount === 0) {
          console.log(`[API GET /routes/:routeId/path] Route not found: ${routeId}`);
          return res.status(404).json({ message: 'Route path not found.' });
      }

      const pathCoordinates = result.rows[0].route_path_coordinates;

      if (!pathCoordinates) {
          console.log(`[API GET /routes/:routeId/path] Route ${routeId} exists but has no path coordinates.`);
          // Return empty array or 404 based on desired behavior
          return res.status(404).json({ message: 'Route path data not available for this route.' });
      }

      // Send the raw JSONB data (which should be the array of coordinates)
      res.status(200).json({ coordinates: pathCoordinates }); // Wrap it in an object for clarity

  } catch (err) {
      console.error(`[API GET /routes/:routeId/path] Error fetching path for route ${routeId}:`, err);
      res.status(500).json({ message: 'Server error fetching route path.' });
  }
  // Note: No need for explicit client.release() if using pool.query() directly
});




// Example modification for the GET /api/students endpoint
app.get('/api/students', authenticateToken, authorize('parent'), async (req, res) => {
  const userId = req.user.userId;
  console.log(`Workspaceing students for parent user ID: ${userId}`); // Keep existing log

  try {
    const parentResult = await pool.query('SELECT parent_id FROM parents WHERE user_id = $1', [userId]);

    if (parentResult.rowCount === 0) {
      console.log(`No parent record found for user ID: ${userId}`);
      return res.status(404).json({ message: 'Parent profile not found.' });
    }
    const parentId = parentResult.rows[0].parent_id;

    // *** MODIFIED QUERY ***
    const studentResult = await pool.query(
      `SELECT
         s.student_id, s.first_name, s.last_name, s.parent_id,
         s.bus_id, b.bus_plate,
         b.route_id -- <<< ADDED route_id selection
       FROM students s
       LEFT JOIN buses b ON s.bus_id = b.bus_id
       -- LEFT JOIN routes r ON b.route_id = r.route_id -- Join already implied by FK if needed, selecting b.route_id is enough
       WHERE s.parent_id = $1`, // Filter by parentId
      [parentId]
    );
    // *** END MODIFIED QUERY ***

    console.log(`Found ${studentResult.rowCount} students for parent_id ${parentId}`);
    res.json(studentResult.rows); // Send the result (now includes route_id)

  } catch (err) {
    console.error(`Error fetching students for parent user ID ${userId}:`, err);
    res.status(500).json({ message: 'Server error fetching students.' });
  }
});




 module.exports = app; // Export for potential testing
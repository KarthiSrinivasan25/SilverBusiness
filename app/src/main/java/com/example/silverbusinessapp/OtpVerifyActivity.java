package com.example.silverbusinessapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class OtpVerifyActivity extends AppCompatActivity {

    // UI Components
    private EditText otpEt;
    private Button verifyBtn;
    private TextView resendOtpText, timerText, titleText, subtitleText;
    private ProgressBar verifyProgress;
    private LinearLayout resendContainer, timerContainer;
    private View backBtn;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    // SharedPreferences
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "LoginSession";

    // Timer
    private Timer resendTimer;
    private int timerSeconds = 120; // 2 minutes

    // Email settings (MOVE TO BACKEND IN PRODUCTION)
    private static final String ALERT_RECEIVER_EMAIL = "karthikeyan25082000@gmail.com"; //sathishthamaraikannan@gmail.com
    private static final String SMTP_EMAIL = "ukkvdon@gmail.com"; //sathishsilver7619@gmail.com
    private static final String SMTP_PASSWORD = "lgxfaqanbscnwtjf";  //xmhamwuhdflrqfry    //tynmmcsnrisntwns

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verify);

        initializeViews();
        setupClickListeners();
        startResendTimer();
        setupOtpInput();
    }

    private void initializeViews() {
        // Initialize all UI components
        otpEt = findViewById(R.id.otpEt);
        verifyBtn = findViewById(R.id.verifyBtn);
        resendOtpText = findViewById(R.id.resendOtpText);
        timerText = findViewById(R.id.timerText);
        verifyProgress = findViewById(R.id.verifyProgress);
        resendContainer = findViewById(R.id.resendContainer);
        timerContainer = findViewById(R.id.timerContainer);
        backBtn = findViewById(R.id.backBtn);
        titleText = findViewById(R.id.titleText);
        subtitleText = findViewById(R.id.subtitleText);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private void setupClickListeners() {
        // Verify OTP button
        verifyBtn.setOnClickListener(v -> verifyOtp());

        // Resend OTP text
        resendOtpText.setOnClickListener(v -> {
            if (resendOtpText.isEnabled()) {
                resendOtp();
            }
        });

        // Back button
        backBtn.setOnClickListener(v -> onBackPressed());

        // OTP text changed listener
        otpEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear error when user starts typing
                if (otpEt.getError() != null) {
                    otpEt.setError(null);
                }

                // Auto-submit when 6 digits entered
                if (s.length() == 6) {
                    verifyOtp();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupOtpInput() {
        // Set input type and max length
        otpEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        otpEt.setMaxLines(1);
    }

    private void verifyOtp() {
        String enteredOtp = otpEt.getText().toString().trim();

        // Input validation
        if (TextUtils.isEmpty(enteredOtp)) {
            showError(otpEt, "Please enter OTP");
            return;
        }

        if (enteredOtp.length() != 6) {
            showError(otpEt, "OTP must be 6 digits");
            return;
        }

        // Get saved OTP data
        String savedOtp = sharedPreferences.getString("otp", "");
        long otpTime = sharedPreferences.getLong("otp_time", 0);
        String email = sharedPreferences.getString("email", "");
        String password = sharedPreferences.getString("password", "");

        // Check if email and password exist
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check OTP expiry (5 minutes)
        if (currentTime - otpTime > 300_000) {
            Toast.makeText(this, "OTP expired. Please request a new one.", Toast.LENGTH_LONG).show();
            startResendTimer(); // Allow resend
            return;
        }

        // Check OTP match
        if (!enteredOtp.equals(savedOtp)) {
            showError(otpEt, "Invalid OTP");
            shakeAnimation();
            return;
        }

        // OTP is correct - proceed with login
        loginUser(email, password);
    }

    private void showError(EditText editText, String message) {
        editText.setError(message);
        editText.requestFocus();
        shakeAnimation();
    }

    private void shakeAnimation() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        otpEt.startAnimation(shake);
    }

    private void resendOtp() {
        // Disable resend button immediately
        resendOtpText.setEnabled(false);
        resendOtpText.setTextColor(ContextCompat.getColor(this, R.color.grey_400));

        // Get user email
        String email = sharedPreferences.getString("email", "");
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Email not found. Please login again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Generate new OTP
        String newOtp = generateNewOtp();

        // Save new OTP and time
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("otp", newOtp);
        editor.putLong("otp_time", System.currentTimeMillis());
        editor.apply();

        // Send new OTP via email
        sendNewOtpEmail(email, newOtp);

        // Show success message
        Toast.makeText(this, "New OTP sent to your email", Toast.LENGTH_SHORT).show();

        // Clear current OTP input
        otpEt.setText("");
        otpEt.requestFocus();

        // Restart timer
        startResendTimer();
    }

    private String generateNewOtp() {
        // Generate random 6-digit OTP
        return String.valueOf((int) ((Math.random() * 900000) + 100000));
    }

    private void sendNewOtpEmail(String email, String otp) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SMTP_EMAIL, SMTP_PASSWORD);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SMTP_EMAIL));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                message.setSubject("Your OTP Verification Code");
                message.setText("Your OTP verification code is: " + otp +
                        "\n\nThis code will expire in 5 minutes." +
                        "\n\nIf you didn't request this code, please ignore this email.");

                Transport.send(message);

            } catch (MessagingException e) {
                runOnUiThread(() ->
                        Toast.makeText(OtpVerifyActivity.this, "Failed to send OTP email", Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }
        }).start();
    }

    private void startResendTimer() {
        // Reset timer
        timerSeconds = 120;

        // Hide resend container, show timer container
        if (resendContainer != null) resendContainer.setVisibility(View.GONE);
        if (timerContainer != null) timerContainer.setVisibility(View.VISIBLE);

        // Cancel existing timer
        if (resendTimer != null) {
            resendTimer.cancel();
        }

        // Create new timer
        resendTimer = new Timer();

        resendTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (timerSeconds > 0) {
                        timerSeconds--;
                        int minutes = timerSeconds / 60;
                        int seconds = timerSeconds % 60;
                        if (timerText != null) {
                            timerText.setText(String.format("%02d:%02d", minutes, seconds));
                        }
                    } else {
                        // Timer finished - show resend option
                        resendTimer.cancel();
                        if (resendContainer != null) resendContainer.setVisibility(View.VISIBLE);
                        if (timerContainer != null) timerContainer.setVisibility(View.GONE);
                        resendOtpText.setEnabled(true);
                        resendOtpText.setTextColor(ContextCompat.getColor(OtpVerifyActivity.this, R.color.colorPrimary));
                    }
                });
            }
        }, 0, 1000);
    }

    private void loginUser(String email, String password) {
        // Show loading state
        setVerificationState(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        handleSuccessfulLogin(email);
                    } else {
                        handleLoginFailure(task.getException());
                    }
                });
    }

    private void setVerificationState(boolean isLoading) {
        verifyBtn.setEnabled(!isLoading);
        verifyBtn.setAlpha(isLoading ? 0.7f : 1.0f);
        verifyProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        verifyBtn.setText(isLoading ? "Verifying..." : "Verify OTP");

        // Disable OTP input during verification
        otpEt.setEnabled(!isLoading);
    }

    private void handleSuccessfulLogin(String email) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Login failed: User not found", Toast.LENGTH_SHORT).show();
            setVerificationState(false);
            return;
        }

        String uid = firebaseUser.getUid();

        // Get user data from Firebase
        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String userName;
                String userEmail = email;

                if (snapshot.exists()) {
                    // User exists in database
                    userName = snapshot.child("name").getValue(String.class);
                    String dbEmail = snapshot.child("email").getValue(String.class);
                    if (dbEmail != null) userEmail = dbEmail;

                    // Fallback for username
                    if (userName == null || userName.isEmpty()) {
                        userName = userEmail.split("@")[0];
                    }

                    // Check for new device login
                    checkNewDeviceLogin(uid, userEmail);

                } else {
                    // New user - use email as username
                    userName = email.split("@")[0];
                }

                // Save session and proceed
                saveUserSession(userEmail, userName);
                goToDashboard(userEmail, userName);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Fallback with basic user info
                saveUserSession(email, email.split("@")[0]);
                goToDashboard(email, email.split("@")[0]);
                Toast.makeText(OtpVerifyActivity.this, "Note: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkNewDeviceLogin(String uid, String userEmail) {
        String currentDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        usersRef.child(uid).child("lastDeviceId").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String lastDeviceId = snapshot.getValue(String.class);

                if (lastDeviceId == null || !lastDeviceId.equals(currentDeviceId)) {
                    // New device detected
                    usersRef.child(uid).child("lastDeviceId").setValue(currentDeviceId);
                    sendLoginAlertEmail(userEmail);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Still update device ID and send alert
                usersRef.child(uid).child("lastDeviceId").setValue(currentDeviceId);
                sendLoginAlertEmail(userEmail);
            }
        });
    }

    private void handleLoginFailure(Exception exception) {
        setVerificationState(false);
        String errorMessage = "Login Failed: ";

        if (exception != null) {
            String message = exception.getMessage();
            if (message != null && message.contains("INVALID_LOGIN_CREDENTIALS")) {
                errorMessage = "Invalid email or password";
            } else if (message != null && message.contains("network error")) {
                errorMessage = "Network error. Please check your connection";
            } else {
                errorMessage += message;
            }
        } else {
            errorMessage += "Unknown error occurred";
        }

        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }

    private void saveUserSession(String email, String name) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("userEmail", email);
        editor.putString("userName", name);
        editor.putLong("loginTime", System.currentTimeMillis());
        editor.apply();
    }

    private void sendLoginAlertEmail(String loggedInEmail) {
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SMTP_EMAIL, SMTP_PASSWORD);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SMTP_EMAIL));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(ALERT_RECEIVER_EMAIL));
                message.setSubject("🔐 New Device Login Alert - " + Build.MANUFACTURER + " " + Build.MODEL);
                message.setText("A new device has logged into your Silver Business App account:\n\n" +
                        "📧 Email: " + loggedInEmail + "\n" +
                        "📱 Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                        "🤖 Android Version: " + Build.VERSION.RELEASE + "\n" +
                        "⏰ Time: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n\n" +
                        "If this wasn't you, please secure your account immediately.");

                Transport.send(message);
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void goToDashboard(String email, String name) {
        new android.os.Handler().postDelayed(() -> {

            // 👉 Go to Password Security Screen instead of Dashboard
            Intent intent = new Intent(OtpVerifyActivity.this, PasswordSecurityActivity.class);
            intent.putExtra("userEmail", email);
            intent.putExtra("userName", name);

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

            overridePendingTransition(R.anim.otp_slide_in_right, R.anim.otp_slide_out_left);

        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up timer to prevent memory leaks
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
package com.example.silverbusinessapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.Properties;
import java.util.Random;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class LoginActivity extends AppCompatActivity {

    private EditText email, password;
    private CardView loginButton;
    private LinearLayout passwordToggle, forgotPasswordLayout;
    private ImageView toggleIcon;
    private SharedPreferences sharedPreferences;

    private static final String PREF_NAME = "LoginSession";
    private static final String ALLOWED_EMAIL = "asdtechcreation@gmail.com"; //sathishthamaraikannan@gmail.com

    // Email settings for sending OTP
    private static final String SMTP_EMAIL = "ukkvdon@gmail.com";  //sathishsilver7619@gmail.com
    private static final String SMTP_PASSWORD = "lgxfaqanbscnwtjf"; // Gmail App Password    xmhamwuhdflrqfry

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Initialize views
        email = findViewById(R.id.login_email);
        password = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        passwordToggle = findViewById(R.id.password_toggle);
        toggleIcon = findViewById(R.id.toggle_icon);
        forgotPasswordLayout = findViewById(R.id.forgot_password_text); // This is a LinearLayout
        TextView signUpText = findViewById(R.id.sign_up_text);

        // Password toggle functionality
        passwordToggle.setOnClickListener(v -> {
            if (password.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                toggleIcon.setImageResource(R.drawable.ic_eye_open_1);
            } else {
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                toggleIcon.setImageResource(R.drawable.ic_eye_closed);
            }
            password.setSelection(password.getText().length());
        });

        // Login button click listener
        loginButton.setOnClickListener(v -> {
            String inputEmail = email.getText().toString().trim();
            String inputPass = password.getText().toString().trim();

            if (!isValidEmail(inputEmail)) {
                email.setError("Enter a valid email");
                email.requestFocus();
                return;
            }

            if (!inputEmail.equals(ALLOWED_EMAIL)) {
                Toast.makeText(this, "This account is not allowed to login", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidPassword(inputPass)) {
                password.setError("Password must be at least 6 characters");
                password.requestFocus();
                return;
            }

            // Generate and send OTP
            String generatedOTP = String.valueOf(new Random().nextInt(900000) + 100000);
            long otpTime = System.currentTimeMillis();

            // Save OTP & time in SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("otp", generatedOTP);
            editor.putLong("otp_time", otpTime);
            editor.putString("email", inputEmail);
            editor.putString("password", inputPass);
            editor.apply();

            // Send OTP email
            sendOtpEmail(inputEmail, generatedOTP);

            // Go to OTP verification activity
            Intent intent = new Intent(LoginActivity.this, OtpVerifyActivity.class);
            startActivity(intent);
        });

        // Forgot password click listener - now on LinearLayout
        forgotPasswordLayout.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Sign up text click listener - UPDATED
        signUpText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
            // Optional: Add transition animation
            // overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    private void sendOtpEmail(String toEmail, String otp) {
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
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject("Your OTP Code");
                message.setText("Your OTP for login is: " + otp + "\nThis code will expire in 5 minutes.");

                Transport.send(message);

                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "OTP sent to your email", Toast.LENGTH_SHORT).show();
                });
            } catch (MessagingException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, "Failed to send OTP", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean isValidEmail(String email) {
        return !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPassword(String pass) {
        return pass.length() >= 6;
    }
}
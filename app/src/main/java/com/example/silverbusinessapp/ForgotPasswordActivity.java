package com.example.silverbusinessapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText forgotEmail;
    private Button resetButton;
    private FirebaseAuth mAuth;
    private ProgressBar progressBar;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        forgotEmail = findViewById(R.id.forgot_email);
        resetButton = findViewById(R.id.reset_password_button);
        progressBar = findViewById(R.id.progress_bar);
        backButton = findViewById(R.id.back_button); // Optional back button if available

        mAuth = FirebaseAuth.getInstance();

        resetButton.setOnClickListener(v -> sendResetLink());

        backButton.setOnClickListener(v -> finish()); // go back to login
    }

    private void sendResetLink() {
        String email = forgotEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            forgotEmail.setError("Email is required");
            forgotEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            forgotEmail.setError("Enter a valid email");
            forgotEmail.requestFocus();
            return;
        }

        progressBar.setVisibility(ProgressBar.VISIBLE);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgotPasswordActivity.this, "Reset link sent to your email", Toast.LENGTH_LONG).show();
                        finish(); // go back to login screen
                    } else {
                        Toast.makeText(ForgotPasswordActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}

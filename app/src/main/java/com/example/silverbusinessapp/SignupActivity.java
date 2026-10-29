package com.example.silverbusinessapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;

public class SignupActivity extends AppCompatActivity {

    EditText name, email, password;
    CardView signupBtn;
    LinearLayout passwordToggle;
    ImageView toggleIcon;

    FirebaseAuth mAuth;
    DatabaseReference usersRef;

    boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        name = findViewById(R.id.signup_name);
        email = findViewById(R.id.signup_email);
        password = findViewById(R.id.signup_password);
        signupBtn = findViewById(R.id.signup_button);
        passwordToggle = findViewById(R.id.password_toggle);
        toggleIcon = findViewById(R.id.toggle_icon);

        mAuth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());
        signupBtn.setOnClickListener(v -> registerUser());

        // Add click listener for login text
        findViewById(R.id.login_text).setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_eye_closed);
            isPasswordVisible = false;
        } else {
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleIcon.setImageResource(R.drawable.ic_eye_open_1);
            isPasswordVisible = true;
        }
        password.setSelection(password.getText().length());
    }

    private void registerUser() {
        String userName = name.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String userPass = password.getText().toString().trim();

        // Input validation
        if (userName.isEmpty()) {
            name.setError("Name is required");
            name.requestFocus();
            return;
        }
        if (userEmail.isEmpty()) {
            email.setError("Email is required");
            email.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            email.setError("Enter a valid email");
            email.requestFocus();
            return;
        }
        if (userPass.isEmpty()) {
            password.setError("Password is required");
            password.requestFocus();
            return;
        }
        if (userPass.length() < 6) {
            password.setError("Password must be at least 6 characters");
            password.requestFocus();
            return;
        }

        // Show loading state
        signupBtn.setEnabled(false);

        // ✅ Check if email already exists in Users node
        usersRef.orderByChild("email").equalTo(userEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            // Email already used
                            signupBtn.setEnabled(true);
                            Toast.makeText(SignupActivity.this, "Signup not allowed. Email already exists.", Toast.LENGTH_LONG).show();
                        } else {
                            // Create new Firebase Auth account
                            mAuth.createUserWithEmailAndPassword(userEmail, userPass)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                            if (firebaseUser != null) {
                                                String uid = firebaseUser.getUid();

                                                HashMap<String, Object> userMap = new HashMap<>();
                                                userMap.put("name", userName);
                                                userMap.put("email", userEmail);
                                                userMap.put("userId", uid);

                                                usersRef.child(uid).setValue(userMap)
                                                        .addOnCompleteListener(saveTask -> {
                                                            signupBtn.setEnabled(true);
                                                            if (saveTask.isSuccessful()) {
                                                                Toast.makeText(SignupActivity.this, "Signup Successful", Toast.LENGTH_SHORT).show();
                                                                startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                                                                finish();
                                                            } else {
                                                                Toast.makeText(SignupActivity.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                                                            }
                                                        });
                                            } else {
                                                signupBtn.setEnabled(true);
                                                Toast.makeText(SignupActivity.this, "User creation failed", Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            signupBtn.setEnabled(true);
                                            Toast.makeText(SignupActivity.this, "Signup Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        signupBtn.setEnabled(true);
                        Toast.makeText(SignupActivity.this, "Error checking email: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
package com.example.silverbusinessapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.*;

public class SetNewPasswordActivity extends AppCompatActivity {

    private EditText newPasswordInput;
    private Button resetPasswordButton;
    private String userEmail;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_new_password);

        newPasswordInput = findViewById(R.id.input_new_password);
        resetPasswordButton = findViewById(R.id.reset_password_btn);

        userEmail = getIntent().getStringExtra("email");
        usersRef = FirebaseDatabase.getInstance().getReference("Users");

        resetPasswordButton.setOnClickListener(v -> {
            String newPassword = newPasswordInput.getText().toString().trim();

            if (TextUtils.isEmpty(newPassword)) {
                newPasswordInput.setError("Password is required");
                newPasswordInput.requestFocus();
                return;
            }

            if (newPassword.length() < 6) {
                newPasswordInput.setError("Password must be at least 6 characters");
                newPasswordInput.requestFocus();
                return;
            }

            // Search user by email in the database
            usersRef.orderByChild("email").equalTo(userEmail)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                for (DataSnapshot userSnap : snapshot.getChildren()) {
                                    userSnap.getRef().child("password").setValue(newPassword)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(SetNewPasswordActivity.this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(SetNewPasswordActivity.this, LoginActivity.class));
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(SetNewPasswordActivity.this, "Failed to update password: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            });
                                }
                            } else {
                                Toast.makeText(SetNewPasswordActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            Toast.makeText(SetNewPasswordActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}

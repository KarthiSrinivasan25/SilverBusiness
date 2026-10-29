package com.example.silverbusinessapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LogoutActivity extends AppCompatActivity {

    private static final String PREF_NAME = "LoginSession";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Optional: You can set a simple layout with a progress message
        // setContentView(R.layout.activity_logout);

        // 🔒 Clear any additional stored user data
        clearAllUserData();

        // Show logout message
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // 🔄 Navigate to SplashActivity after a brief delay
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(LogoutActivity.this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 500); // 0.5 second delay
    }

    private void clearAllUserData() {
        // Clear SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.clear();
        editor.apply();

        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut();

        // Clear any other app-specific data if needed
        // clearCache();
        // clearDatabase();
    }
}
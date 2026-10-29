package com.example.silverbusinessapp;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class VerifyOtpActivity extends AppCompatActivity {

    EditText otp1, otp2, otp3, otp4, otp5, otp6;
    LinearLayout otpContainer;
    String correctOtp = "123456"; // or get from Intent
    String userEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otp);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);
        otpContainer = findViewById(R.id.otp_container);

        correctOtp = getIntent().getStringExtra("otp");
        userEmail = getIntent().getStringExtra("email");

        setupOtpInputs();
    }

    private void setupOtpInputs() {
        EditText[] boxes = {otp1, otp2, otp3, otp4, otp5, otp6};

        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < boxes.length - 1) {
                        boxes[index + 1].requestFocus();
                    }
                    if (allFieldsFilled()) {
                        String enteredOtp = getEnteredOtp();
                        validateOtp(enteredOtp);
                    }
                }
            });
        }
    }

    private boolean allFieldsFilled() {
        return !otp1.getText().toString().isEmpty()
                && !otp2.getText().toString().isEmpty()
                && !otp3.getText().toString().isEmpty()
                && !otp4.getText().toString().isEmpty()
                && !otp5.getText().toString().isEmpty()
                && !otp6.getText().toString().isEmpty();
    }

    private String getEnteredOtp() {
        return otp1.getText().toString() +
                otp2.getText().toString() +
                otp3.getText().toString() +
                otp4.getText().toString() +
                otp5.getText().toString() +
                otp6.getText().toString();
    }

    private void validateOtp(String enteredOtp) {
        if (enteredOtp.equals(correctOtp)) {
            setOtpBoxColor("#10B981"); // green
            Toast.makeText(this, "OTP Verified!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, SetNewPasswordActivity.class);
            intent.putExtra("email", userEmail);
            startActivity(intent);
            finish();
        } else {
            setOtpBoxColor("#EF4444"); // red
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
        }
    }

    private void setOtpBoxColor(String colorHex) {
        for (int i = 0; i < otpContainer.getChildCount(); i++) {
            View child = otpContainer.getChildAt(i);
            if (child instanceof EditText) {
                GradientDrawable drawable = new GradientDrawable();
                drawable.setCornerRadius(8f);
                drawable.setStroke(3, android.graphics.Color.parseColor(colorHex));
                drawable.setColor(android.graphics.Color.WHITE);
                child.setBackground(drawable);
            }
        }
    }
}

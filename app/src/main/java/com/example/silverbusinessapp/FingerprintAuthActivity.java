package com.example.silverbusinessapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;

import java.util.concurrent.Executor;

public class FingerprintAuthActivity extends AppCompatActivity {

    private LottieAnimationView lottieFingerprint;
    private ProgressBar progressIndicator;
    private TextView tvStatus, tvDescription;
    private Button btnCancel;
    private CardView fingerprintContainer;
    private BiometricPrompt biometricPrompt;
    private Executor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fingerprint_auth);

        initViews();
        setupBiometricPrompt();
        setupClickListeners();
        startBiometricAuth();
    }

    private void initViews() {
        lottieFingerprint = findViewById(R.id.lottie_fingerprint);
        progressIndicator = findViewById(R.id.progress_indicator);
        tvStatus = findViewById(R.id.tv_status);
        tvDescription = findViewById(R.id.tv_description);
        btnCancel = findViewById(R.id.btn_cancel);
        fingerprintContainer = findViewById(R.id.fingerprint_container);

        // Hide the Use PIN button since it's not needed
        Button btnUsePin = findViewById(R.id.btn_use_pin);
        btnUsePin.setVisibility(View.GONE);

        executor = ContextCompat.getMainExecutor(this);
    }

    private void setupBiometricPrompt() {
        biometricPrompt = new BiometricPrompt(
                FingerprintAuthActivity.this,
                executor,
                new BiometricPrompt.AuthenticationCallback() {

                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        handleAuthenticationSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        handleAuthenticationError(errorCode, errString.toString());
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        handleAuthenticationFailure();
                    }
                }
        );
    }

    private void setupClickListeners() {
        btnCancel.setOnClickListener(v -> finish());
    }

    private void startBiometricAuth() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Fingerprint Required")
                .setSubtitle("Unlock Recycle Bin")
                .setDescription("Use your fingerprint to access deleted records")
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false)
                .build();

        // Show loading state
        showLoadingState();

        // Delay biometric prompt for better UX
        new Handler().postDelayed(() -> {
            biometricPrompt.authenticate(promptInfo);
        }, 500);
    }

    private void showLoadingState() {
        lottieFingerprint.setAlpha(0.3f);
        progressIndicator.setVisibility(View.VISIBLE);
        tvStatus.setText("Initializing...");
    }

    private void hideLoadingState() {
        lottieFingerprint.setAlpha(1f);
        progressIndicator.setVisibility(View.INVISIBLE);
    }

    private void handleAuthenticationSuccess() {
        hideLoadingState();

        // Show success animation using Lottie fingerprint_success.json
        showLottieSuccessAnimation();

        tvStatus.setText("Authentication Successful!");
        tvDescription.setText("Opening Recycle Bin...");

        // Navigate after success animation
        new Handler().postDelayed(() -> {
            navigateToRecycleBin();
        }, 1500);
    }

    private void showLottieSuccessAnimation() {
        // Configure and show Lottie success animation
        lottieFingerprint.setAnimation(R.raw.fingerprint_success);
        lottieFingerprint.setRepeatCount(0); // Play only once
        lottieFingerprint.playAnimation();
    }

    private void handleAuthenticationError(int errorCode, String errorString) {
        hideLoadingState();

        switch (errorCode) {
            case BiometricPrompt.ERROR_LOCKOUT:
                tvStatus.setText("Too Many Attempts");
                tvDescription.setText("Please try again in 30 seconds");
                lottieFingerprint.setAnimation(R.raw.fingerprint_error);
                break;
            case BiometricPrompt.ERROR_TIMEOUT:
                tvStatus.setText("Authentication Timeout");
                tvDescription.setText("Please try again");
                break;
            case BiometricPrompt.ERROR_USER_CANCELED:
                finish();
                return;
            default:
                tvStatus.setText("Authentication Error");
                tvDescription.setText(errorString);
        }

        lottieFingerprint.playAnimation();
        showRetryOption();
    }

    private void handleAuthenticationFailure() {
        hideLoadingState();
        tvStatus.setText("Authentication Failed");
        tvDescription.setText("Fingerprint not recognized. Please try again.");

        // Shake animation for error feedback
        fingerprintContainer.animate()
                .translationXBy(20f)
                .setDuration(50)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> fingerprintContainer.animate()
                        .translationXBy(-40f)
                        .setDuration(100)
                        .withEndAction(() -> fingerprintContainer.animate()
                                .translationXBy(20f)
                                .setDuration(50)
                                .withEndAction(() -> {
                                    showRetryOption();
                                    lottieFingerprint.setAnimation(R.raw.fingerprint_error);
                                    lottieFingerprint.playAnimation();
                                })))
                .start();
    }

    private void showRetryOption() {
        btnCancel.setText("Cancel");

        // Show retry button instead of Use PIN
        Button btnUsePin = findViewById(R.id.btn_use_pin);
        btnUsePin.setVisibility(View.VISIBLE);
        btnUsePin.setText("Try Again");
        btnUsePin.setOnClickListener(v -> retryAuthentication());
    }

    private void retryAuthentication() {
        // Hide the retry button
        Button btnUsePin = findViewById(R.id.btn_use_pin);
        btnUsePin.setVisibility(View.GONE);

        // Reset to initial state
        resetToInitialState();
        startBiometricAuth();
    }

    private void resetToInitialState() {
        // Reset Lottie animation to fingerprint scanner
        lottieFingerprint.setAnimation(R.raw.fingerprint_scanner);
        lottieFingerprint.setRepeatCount(-1); // Infinite loop
        lottieFingerprint.playAnimation();

        // Reset text
        tvStatus.setText("Touch Sensor");
        tvDescription.setText("Use your fingerprint to access deleted records");

        // Reset cancel button
        btnCancel.setText("Cancel");
    }

    private void navigateToRecycleBin() {
        Intent intent = new Intent(FingerprintAuthActivity.this, RecycleBinActivity.class);
        intent.putExtra("userUid", getIntent().getStringExtra("userUid"));
        startActivity(intent);

        // Smooth transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (biometricPrompt != null) {
            // Cancel authentication when activity is paused
            biometricPrompt.cancelAuthentication();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure Lottie animation is playing when activity resumes
        if (lottieFingerprint != null && lottieFingerprint.getVisibility() == View.VISIBLE) {
            lottieFingerprint.playAnimation();
        }
    }
}
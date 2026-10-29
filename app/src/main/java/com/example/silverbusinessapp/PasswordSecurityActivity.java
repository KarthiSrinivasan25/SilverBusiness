package com.example.silverbusinessapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class PasswordSecurityActivity extends AppCompatActivity {

    private EditText securityPassword;
    private CardView verifyBtn, cardContainer;
    private TextView title, subtitle;
    private ImageView togglePassword, companyLogo;

    private boolean isPasswordVisible = false;

    private static final String PREF_NAME = "LoginSession";
    private static final String DEFAULT_PASSWORD = "07sathish@PST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_security);

        // Initialize Views
        securityPassword = findViewById(R.id.security_password);
        verifyBtn = findViewById(R.id.verify_button);
        cardContainer = findViewById(R.id.card_container);
        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        togglePassword = findViewById(R.id.toggle_password);
        companyLogo = findViewById(R.id.company_logo);

        // Set initial state - HIDE EVERYTHING FIRST
        setInitialVisibility();

        // Set password field type
        securityPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        // Set click listeners
        togglePassword.setOnClickListener(v -> togglePasswordVisibility());
        verifyBtn.setOnClickListener(v -> verifyPassword());

        // Start first animation
        startAnimationSequence();
    }

    // 🔒 Hide all elements initially
    private void setInitialVisibility() {
        companyLogo.setVisibility(View.INVISIBLE);
        title.setVisibility(View.INVISIBLE);
        subtitle.setVisibility(View.INVISIBLE);
        cardContainer.setVisibility(View.INVISIBLE);
    }

    // 🎬 Main Animation Sequence - ONE BY ONE
    private void startAnimationSequence() {
        animateLogo();
    }

    // Step 1: Logo Animation
    private void animateLogo() {
        companyLogo.setVisibility(View.VISIBLE);
        companyLogo.setAlpha(0f);
        companyLogo.setScaleX(0.5f);
        companyLogo.setScaleY(0.5f);

        companyLogo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animateTitle();
                    }
                })
                .start();
    }

    // Step 2: Title Typing Animation
    private void animateTitle() {
        title.setVisibility(View.VISIBLE);
        title.setText("");

        typeText(title, "Welcome Back", 120, new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(() -> animateSubtitle(), 300);
            }
        });
    }

    // Step 3: Subtitle Typing Animation
    private void animateSubtitle() {
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("");

        typeText(subtitle, "PST Silver Ledger", 100, new Runnable() {
            @Override
            public void run() {
                animateCard();
            }
        });
    }

    // Step 4: Card Fade-in Animation
    private void animateCard() {
        cardContainer.setVisibility(View.VISIBLE);
        cardContainer.setAlpha(0f);
        cardContainer.setTranslationY(100f);

        cardContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    // 📝 Typing Animation with Callback
    private void typeText(TextView textView, String text, long delayPerChar, Runnable onComplete) {
        Handler handler = new Handler();
        final int[] index = {0};

        textView.setText("");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (index[0] <= text.length()) {
                    textView.setText(text.substring(0, index[0]));
                    index[0]++;
                    handler.postDelayed(this, delayPerChar);
                } else {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }, delayPerChar);
    }

    // 👁️ Show / Hide Password
    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            securityPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            togglePassword.setImageResource(android.R.drawable.ic_menu_view);
            isPasswordVisible = false;
        } else {
            securityPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            togglePassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            isPasswordVisible = true;
        }

        securityPassword.setSelection(securityPassword.getText().length());
    }

    // 📳 Shake Animation
    private void shakeView(EditText view) {
        view.animate()
                .translationX(20)
                .setDuration(50)
                .withEndAction(() ->
                        view.animate()
                                .translationX(-20)
                                .setDuration(50)
                                .withEndAction(() ->
                                        view.animate()
                                                .translationX(10)
                                                .setDuration(50)
                                                .withEndAction(() ->
                                                        view.animate()
                                                                .translationX(0)
                                                                .setDuration(50)
                                                                .start()
                                                ).start()
                                ).start()
                ).start();
    }

    // 🔐 Verify Password
    private void verifyPassword() {
        String enteredPassword = securityPassword.getText().toString().trim();

        if (enteredPassword.isEmpty()) {
            securityPassword.setError("Enter password");
            shakeView(securityPassword);
            return;
        }

        if (!enteredPassword.equals(DEFAULT_PASSWORD)) {
            securityPassword.setError("Wrong password");
            shakeView(securityPassword);
            return;
        }

        // Save session
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putBoolean("isSecurityVerified", true);
        editor.apply();

        // Button click animation
        verifyBtn.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction(() -> {
                    verifyBtn.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start();
                })
                .start();

        // Go to Dashboard
        Intent intent = new Intent(PasswordSecurityActivity.this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
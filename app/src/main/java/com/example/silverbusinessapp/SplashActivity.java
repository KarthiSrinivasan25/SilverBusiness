package com.example.silverbusinessapp;

import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private ImageView logo;
    private TextView companyName, tagline;
    private LinearLayout authButtons, loadingLayout, termsLayout, mainContent;
    private CheckBox termsCheckbox;
    private Button acceptButton;

    private static final String PREF_NAME = "LoginSession";
    private static final String PREF_APP = "AppPreferences";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_EMAIL = "userEmail";
    private static final String KEY_TERMS_ACCEPTED = "termsAccepted";

    private static final int LOADING_DELAY = 2000;
    private static final int SPLASH_DELAY = 1000;
    private static final int ANIMATION_DELAY = 300;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        initializeViews();
        showLoadingScreen();
    }

    private void initializeViews() {
        logo = findViewById(R.id.logo);
        companyName = findViewById(R.id.companyName);
        tagline = findViewById(R.id.tagline);
        authButtons = findViewById(R.id.authButtons);
        loadingLayout = findViewById(R.id.loadingLayout);
        termsLayout = findViewById(R.id.termsLayout);
        mainContent = findViewById(R.id.mainContent);
        termsCheckbox = findViewById(R.id.termsCheckbox);
        acceptButton = findViewById(R.id.acceptButton);

        // Set initial visibility - only loading screen visible
        loadingLayout.setVisibility(View.VISIBLE);
        termsLayout.setVisibility(View.GONE);
        mainContent.setVisibility(View.GONE);

        // Debug log
        Log.d("SplashActivity", "Views initialized");

        setupClickListeners();
    }

    private void setupClickListeners() {
        findViewById(R.id.loginBtn).setOnClickListener(v -> {
            Log.d("SplashActivity", "Login button clicked");
            navigateToActivity(LoginActivity.class);
        });

        findViewById(R.id.signupBtn).setOnClickListener(v -> {
            Log.d("SplashActivity", "Signup button clicked");
            navigateToActivity(SignupActivity.class);
        });

        termsCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                acceptButton.setEnabled(isChecked);
                acceptButton.setAlpha(isChecked ? 1.0f : 0.5f);
                Log.d("SplashActivity", "Checkbox checked: " + isChecked);
            }
        });

        acceptButton.setOnClickListener(v -> {
            Log.d("SplashActivity", "Accept button clicked");
            if (termsCheckbox.isChecked()) {
                saveTermsAccepted();
                hideTermsAndShowMainUI();
            } else {
                Log.d("SplashActivity", "Checkbox not checked");
            }
        });
    }

    private void showLoadingScreen() {
        Log.d("SplashActivity", "Showing loading screen");
        // Simulate loading process
        new Handler().postDelayed(() -> {
            checkTermsAccepted();
        }, LOADING_DELAY);
    }

    private void checkTermsAccepted() {
        SharedPreferences appPrefs = getSharedPreferences(PREF_APP, Context.MODE_PRIVATE);
        boolean termsAccepted = appPrefs.getBoolean(KEY_TERMS_ACCEPTED, false);

        Log.d("SplashActivity", "Terms accepted: " + termsAccepted);

        if (termsAccepted) {
            hideLoadingAndShowMainUI();
        } else {
            showTermsAndConditions();
        }
    }

    private void showTermsAndConditions() {
        Log.d("SplashActivity", "Showing terms and conditions");
        loadingLayout.setVisibility(View.GONE);
        termsLayout.setVisibility(View.VISIBLE);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        termsLayout.startAnimation(fadeIn);

        // Reset accept button state
        acceptButton.setEnabled(false);
        acceptButton.setAlpha(0.5f);
        termsCheckbox.setChecked(false);
    }

    private void hideTermsAndShowMainUI() {
        Log.d("SplashActivity", "Hiding terms and showing main UI");
        Animation fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        termsLayout.startAnimation(fadeOut);

        new Handler().postDelayed(() -> {
            termsLayout.setVisibility(View.GONE);
            showMainContent();
        }, 300);
    }

    private void hideLoadingAndShowMainUI() {
        Log.d("SplashActivity", "Hiding loading and showing main UI");
        loadingLayout.setVisibility(View.GONE);
        showMainContent();
    }

    private void showMainContent() {
        Log.d("SplashActivity", "Showing main content");
        mainContent.setVisibility(View.VISIBLE);

        // Make sure all main content elements are visible
        logo.setVisibility(View.VISIBLE);
        companyName.setVisibility(View.VISIBLE);
        if (tagline != null) tagline.setVisibility(View.VISIBLE);

        setupMainAnimations();
    }

    private void setupMainAnimations() {
        Log.d("SplashActivity", "Setting up main animations");

        // Set initial states
        logo.setAlpha(0f);
        logo.setScaleX(0.8f);
        logo.setScaleY(0.8f);

        companyName.setAlpha(0f);
        companyName.setTranslationY(50f);

        if (tagline != null) {
            tagline.setAlpha(0f);
            tagline.setTranslationY(30f);
        }

        // Animate logo first
        logo.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator animation) {
                        Log.d("SplashActivity", "Logo animation completed");

                        // Animate company name after logo
                        companyName.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(600)
                                .setStartDelay(ANIMATION_DELAY)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(android.animation.Animator animation) {
                                        Log.d("SplashActivity", "Company name animation completed");

                                        // Animate tagline after company name
                                        if (tagline != null) {
                                            tagline.animate()
                                                    .alpha(1f)
                                                    .translationY(0f)
                                                    .setDuration(400)
                                                    .setStartDelay(ANIMATION_DELAY / 2)
                                                    .setListener(new AnimatorListenerAdapter() {
                                                        @Override
                                                        public void onAnimationEnd(android.animation.Animator animation) {
                                                            Log.d("SplashActivity", "Tagline animation completed");
                                                            checkLoginSession();
                                                        }
                                                    });
                                        } else {
                                            checkLoginSession();
                                        }
                                    }
                                });
                    }
                });
    }
    private void checkLoginSession() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean isLoggedIn = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);

        Log.d("SplashActivity", "User logged in: " + isLoggedIn);

        if (isLoggedIn) {
            String email = sharedPreferences.getString(KEY_EMAIL, null);

            new Handler().postDelayed(() -> {

                // 🔐 ALWAYS OPEN PASSWORD SCREEN FIRST
                Intent intent = new Intent(SplashActivity.this, PasswordSecurityActivity.class);

                if (email != null) {
                    intent.putExtra("userEmail", email);
                }

                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                finish();

            }, SPLASH_DELAY);

        } else {
            // Show login/signup buttons
            new Handler().postDelayed(() -> {
                Log.d("SplashActivity", "Showing auth buttons");
                authButtons.setVisibility(View.VISIBLE);
                Animation buttonsAnim = AnimationUtils.loadAnimation(SplashActivity.this, R.anim.slide_up);
                authButtons.startAnimation(buttonsAnim);
            }, ANIMATION_DELAY);
        }
    }

    private void saveTermsAccepted() {
        SharedPreferences appPrefs = getSharedPreferences(PREF_APP, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = appPrefs.edit();
        editor.putBoolean(KEY_TERMS_ACCEPTED, true);
        editor.apply();
        Log.d("SplashActivity", "Terms accepted saved to preferences");
    }

    private void navigateToActivity(Class<?> activityClass) {
        Intent intent = new Intent(SplashActivity.this, activityClass);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }
}
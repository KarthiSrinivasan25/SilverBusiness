package com.example.silverbusinessapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
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

public class DashboardActivity extends AppCompatActivity {

    private String userUid;

    private static final String PREF_NAME = "LoginSession";

    // ============================================================
    // FIREBASE
    // ============================================================

    private FirebaseUser currentUser;
    private DatabaseReference usersRef;
    private DatabaseReference customersRef;

    // ============================================================
    // DASHBOARD CARDS
    // ============================================================

    private CardView cardAddCustomer;
    private CardView cardViewCustomer;
    private CardView rotatingStatsCard;

    // ============================================================
    // FEATURE CHIPS
    // ============================================================

    private TextView secureChip;
    private TextView accurateChip;
    private TextView fastChip;

    // ============================================================
    // PROFILE
    // ============================================================

    private ImageView profileImage;
    private TextView userNameText;

    // ============================================================
    // ROTATING CARD
    // ============================================================

    private LinearLayout cardFront;
    private LinearLayout cardBack;

    private TextView totalCustomersCount;
    private TextView totalTransactionsCount;

    private boolean isFront = true;

    // ============================================================
    // USER DATA
    // ============================================================

    private String userName = "";
    private String userEmail = "";

    // ============================================================
    // AUTO FLIP
    // ============================================================

    private Handler autoFlipHandler;
    private Runnable autoFlipRunnable;

    private static final long AUTO_FLIP_INTERVAL = 3000;

    // ============================================================
    // WELCOME ANIMATION
    // ============================================================

    private TextView welcomeText;

    private Handler animationHandler;

    private boolean isAnimating = false;

    private String currentWelcomeMessage = "Welcome";

    private LinearLayout letterContainer;

    private boolean isLooping = true;

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private LinearLayout bottomHome;
    private LinearLayout bottomKacha;

    private ImageView homeIcon;
    private ImageView kachaIcon;

    private TextView homeText;
    private TextView kachaText;

    // ============================================================
    // SELECTED DOT
    // ============================================================

    private View homeSelectedDot;
    private View kachaSelectedDot;

    private ObjectAnimator homeDotAnimator;
    private ObjectAnimator kachaDotAnimator;

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);

        // --------------------------------------------------------
        // Firebase Auth Check
        // --------------------------------------------------------

        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {

            Toast.makeText(
                    this,
                    "User not logged in",
                    Toast.LENGTH_SHORT
            ).show();

            Intent intent = new Intent(
                    DashboardActivity.this,
                    LoginActivity.class
            );

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            startActivity(intent);

            finish();

            return;
        }

        userUid = currentUser.getUid();

        // --------------------------------------------------------
        // Firebase References
        // --------------------------------------------------------

        usersRef = FirebaseDatabase
                .getInstance()
                .getReference("Users");

        customersRef = FirebaseDatabase
                .getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers");

        // --------------------------------------------------------
        // Initialize Views
        // --------------------------------------------------------

        initializeViews();

        // --------------------------------------------------------
        // Handlers
        // --------------------------------------------------------

        animationHandler = new Handler();
        autoFlipHandler = new Handler();

        // --------------------------------------------------------
        // Dashboard Animations
        // --------------------------------------------------------

        startContinuousWelcomeAnimation();

        animateProfileImage();

        setupProfileImageTouchAnimation();

        // --------------------------------------------------------
        // User Data
        // --------------------------------------------------------

        loadUserData();

        // --------------------------------------------------------
        // Feature Chips
        // --------------------------------------------------------

        setupFeatureChips();

        // --------------------------------------------------------
        // Click Listeners
        // --------------------------------------------------------

        setupClickListeners();

        // --------------------------------------------------------
        // Statistics
        // --------------------------------------------------------

        loadStatsData();

        // --------------------------------------------------------
        // Auto Flip
        // --------------------------------------------------------

        startAutoFlip();

        // --------------------------------------------------------
        // Bottom Navigation
        // --------------------------------------------------------

        setupBottomNavigation();

        // Dashboard is selected initially
        setHomeSelected();
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private void initializeViews() {

        // --------------------------------------------------------
        // Cards
        // --------------------------------------------------------

        cardAddCustomer =
                findViewById(R.id.card_add_customer);

        cardViewCustomer =
                findViewById(R.id.card_view_customer);

        rotatingStatsCard =
                findViewById(R.id.rotating_stats_card);

        // --------------------------------------------------------
        // Rotating Statistics Card
        // --------------------------------------------------------

        cardFront =
                findViewById(R.id.card_front);

        cardBack =
                findViewById(R.id.card_back);

        totalCustomersCount =
                findViewById(R.id.total_customers_count);

        totalTransactionsCount =
                findViewById(R.id.total_transactions_count);

        // --------------------------------------------------------
        // Profile
        // --------------------------------------------------------

        profileImage =
                findViewById(R.id.profile_image);

        welcomeText =
                findViewById(R.id.welcomeText);

        userNameText =
                findViewById(R.id.userNameText);

        // --------------------------------------------------------
        // Feature Chips
        // --------------------------------------------------------

        secureChip =
                findViewById(R.id.secure_chip);

        accurateChip =
                findViewById(R.id.accurate_chip);

        fastChip =
                findViewById(R.id.fast_chip);

        // --------------------------------------------------------
        // Bottom Navigation
        // --------------------------------------------------------

        bottomHome =
                findViewById(R.id.bottom_home);

        bottomKacha =
                findViewById(R.id.bottom_kacha);

        homeIcon =
                findViewById(R.id.home_icon);

        kachaIcon =
                findViewById(R.id.kacha_icon);

        homeText =
                findViewById(R.id.home_text);

        kachaText =
                findViewById(R.id.kacha_text);

        // --------------------------------------------------------
        // Selected Dots
        // --------------------------------------------------------

        homeSelectedDot =
                findViewById(R.id.home_selected_dot);

        kachaSelectedDot =
                findViewById(R.id.kacha_selected_dot);

        // Hide both initially
        if (homeSelectedDot != null) {
            homeSelectedDot.setVisibility(View.GONE);
        }

        if (kachaSelectedDot != null) {
            kachaSelectedDot.setVisibility(View.GONE);
        }
    }

    // ============================================================
    // WELCOME ANIMATION
    // ============================================================

    private void startContinuousWelcomeAnimation() {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        isLooping = true;

        currentWelcomeMessage = "Welcome";

        startLetterFlyDownAnimation();
    }

    private void startLetterFlyDownAnimation() {

        if (welcomeText == null ||
                isFinishing() ||
                isDestroyed() ||
                !isLooping) {

            return;
        }

        isAnimating = true;

        welcomeText.setText("");

        welcomeText.setVisibility(View.VISIBLE);

        if (letterContainer == null) {

            letterContainer = new LinearLayout(this);

            letterContainer.setOrientation(
                    LinearLayout.HORIZONTAL
            );

            letterContainer.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );

            View parent = (View) welcomeText.getParent();

            if (parent instanceof LinearLayout) {

                LinearLayout parentLayout =
                        (LinearLayout) parent;

                int index =
                        parentLayout.indexOfChild(
                                welcomeText
                        );

                parentLayout.addView(
                        letterContainer,
                        index
                );
            }
        }

        letterContainer.removeAllViews();

        final String message =
                currentWelcomeMessage;

        final TextView[] letterViews =
                new TextView[message.length()];

        for (int i = 0; i < message.length(); i++) {

            char c = message.charAt(i);

            final TextView letterView =
                    new TextView(this);

            letterView.setText(
                    String.valueOf(c)
            );

            letterView.setTextSize(10);

            letterView.setTextColor(
                    Color.parseColor("#666666")
            );

            letterView.setTypeface(
                    welcomeText.getTypeface()
            );

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );

            if (c == ' ') {

                params.width = 20;

                letterView.setText(" ");
            }

            params.setMargins(
                    2,
                    0,
                    2,
                    0
            );

            letterView.setLayoutParams(params);

            letterView.setTranslationY(-150f);

            letterView.setAlpha(0f);

            letterContainer.addView(letterView);

            letterViews[i] = letterView;
        }

        for (int i = 0; i < letterViews.length; i++) {

            final TextView letterView =
                    letterViews[i];

            animationHandler.postDelayed(
                    () -> {

                        if (letterView != null &&
                                !isFinishing() &&
                                !isDestroyed() &&
                                isLooping) {

                            letterView.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(600)
                                    .setInterpolator(
                                            new BounceInterpolator()
                                    )
                                    .start();

                            ObjectAnimator colorAnim =
                                    ObjectAnimator.ofArgb(
                                            letterView,
                                            "textColor",
                                            Color.parseColor("#FF5722"),
                                            Color.parseColor("#4CAF50"),
                                            Color.parseColor("#666666")
                                    );

                            colorAnim.setDuration(800);

                            colorAnim.start();
                        }

                    },
                    i * 150
            );
        }

        animationHandler.postDelayed(
                () -> {

                    if (isLooping &&
                            !isFinishing() &&
                            !isDestroyed()) {

                        isAnimating = false;

                        animationHandler.postDelayed(
                                () -> {

                                    if (isLooping &&
                                            !isFinishing() &&
                                            !isDestroyed()) {

                                        startLetterFlyDownAnimation();
                                    }

                                },
                                1000
                        );
                    }

                },
                (letterViews.length * 150) + 1000
        );
    }

    private void updateWelcomeMessageAndRestart(
            String newMessage
    ) {

        if (isFinishing() || isDestroyed()) {
            return;
        }

        currentWelcomeMessage = newMessage;

        if (isAnimating) {

            isAnimating = false;

            if (animationHandler != null) {

                animationHandler.removeCallbacksAndMessages(
                        null
                );
            }

            animationHandler.postDelayed(
                    () -> {

                        if (isLooping &&
                                !isFinishing() &&
                                !isDestroyed()) {

                            startLetterFlyDownAnimation();
                        }

                    },
                    500
            );

        } else {

            startLetterFlyDownAnimation();
        }
    }

    // ============================================================
    // PROFILE ANIMATION
    // ============================================================

    private void animateProfileImage() {

        if (profileImage == null ||
                isFinishing() ||
                isDestroyed()) {

            return;
        }

        try {

            ObjectAnimator pulseX =
                    ObjectAnimator.ofFloat(
                            profileImage,
                            "scaleX",
                            1f,
                            1.15f,
                            1f
                    );

            ObjectAnimator pulseY =
                    ObjectAnimator.ofFloat(
                            profileImage,
                            "scaleY",
                            1f,
                            1.15f,
                            1f
                    );

            pulseX.setDuration(2000);

            pulseY.setDuration(2000);

            pulseX.setRepeatCount(
                    ValueAnimator.INFINITE
            );

            pulseY.setRepeatCount(
                    ValueAnimator.INFINITE
            );

            pulseX.setRepeatMode(
                    ValueAnimator.REVERSE
            );

            pulseY.setRepeatMode(
                    ValueAnimator.REVERSE
            );

            AnimatorSet pulseSet =
                    new AnimatorSet();

            pulseSet.playTogether(
                    pulseX,
                    pulseY
            );

            pulseSet.start();

            ObjectAnimator rotate =
                    ObjectAnimator.ofFloat(
                            profileImage,
                            "rotation",
                            0f,
                            360f
                    );

            rotate.setDuration(5000);

            rotate.setRepeatCount(
                    ValueAnimator.INFINITE
            );

            rotate.setRepeatMode(
                    ValueAnimator.RESTART
            );

            rotate.start();

        } catch (Exception e) {

            Log.e(
                    "Dashboard",
                    "Profile animation error: "
                            + e.getMessage()
            );
        }
    }

    // ============================================================
    // PROFILE TOUCH
    // ============================================================

    private void setupProfileImageTouchAnimation() {

        if (profileImage == null) {
            return;
        }

        profileImage.setOnTouchListener(
                (v, event) -> {

                    if (isFinishing() ||
                            isDestroyed()) {

                        return false;
                    }

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:

                            v.animate()
                                    .scaleX(0.85f)
                                    .scaleY(0.85f)
                                    .setDuration(100)
                                    .start();

                            break;

                        case MotionEvent.ACTION_UP:

                        case MotionEvent.ACTION_CANCEL:

                            v.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
                                    .withEndAction(() -> {

                                        if (!isFinishing() &&
                                                v != null) {

                                            v.animate()
                                                    .scaleX(1.1f)
                                                    .scaleY(1.1f)
                                                    .setDuration(80)
                                                    .withEndAction(() -> {

                                                        if (!isFinishing() &&
                                                                v != null) {

                                                            v.animate()
                                                                    .scaleX(1f)
                                                                    .scaleY(1f)
                                                                    .setDuration(80)
                                                                    .start();
                                                        }

                                                    })
                                                    .start();
                                        }

                                    })
                                    .start();

                            break;
                    }

                    return false;
                }
        );
    }

    // ============================================================
    // USER DATA
    // ============================================================

    private void loadUserData() {

        Intent intent = getIntent();

        userName =
                intent.getStringExtra("userName");

        userEmail =
                intent.getStringExtra("userEmail");

        if (userName == null ||
                userName.isEmpty()) {

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREF_NAME,
                            MODE_PRIVATE
                    );

            userName =
                    preferences.getString(
                            "userName",
                            ""
                    );

            userEmail =
                    preferences.getString(
                            "userEmail",
                            ""
                    );
        }

        updateUserDisplay();

        updateWelcomeTextBasedOnUser();

        if (userName == null ||
                userName.isEmpty()) {

            fetchUserDataFromFirebase();
        }
    }

    private void updateUserDisplay() {

        if (userNameText == null) {
            return;
        }

        if (userName != null &&
                !userName.isEmpty() &&
                !userName.equals("User")) {

            userNameText.setText(userName);

        } else {

            userNameText.setText(
                    currentUser != null &&
                            currentUser.getEmail() != null
                            ? currentUser
                            .getEmail()
                            .split("@")[0]
                            : "Guest"
            );
        }
    }

    private void updateWelcomeTextBasedOnUser() {

        String welcomeMessage =
                "Welcome";

        if (!currentWelcomeMessage.equals(
                welcomeMessage
        )) {

            updateWelcomeMessageAndRestart(
                    welcomeMessage
            );
        }
    }

    private void fetchUserDataFromFirebase() {

        usersRef.child(userUid)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {

                            @Override
                            public void onDataChange(
                                    @NonNull DataSnapshot snapshot
                            ) {

                                if (snapshot.exists()) {

                                    String firebaseName =
                                            snapshot
                                                    .child("name")
                                                    .getValue(
                                                            String.class
                                                    );

                                    String firebaseEmail =
                                            snapshot
                                                    .child("email")
                                                    .getValue(
                                                            String.class
                                                    );

                                    if (firebaseName != null &&
                                            !firebaseName.isEmpty()) {

                                        userName =
                                                firebaseName;

                                    } else {

                                        userName =
                                                currentUser
                                                        .getEmail() != null
                                                        ? currentUser
                                                        .getEmail()
                                                        .split("@")[0]
                                                        : "User";
                                    }

                                    if (firebaseEmail != null &&
                                            !firebaseEmail.isEmpty()) {

                                        userEmail =
                                                firebaseEmail;

                                    } else {

                                        userEmail =
                                                currentUser
                                                        .getEmail() != null
                                                        ? currentUser
                                                        .getEmail()
                                                        : "";
                                    }

                                    updateUserDisplay();

                                    updateWelcomeTextBasedOnUser();

                                    saveUserDataToPreferences();

                                } else {

                                    setDefaultUserData();
                                }
                            }

                            @Override
                            public void onCancelled(
                                    @NonNull DatabaseError error
                            ) {

                                Toast.makeText(
                                        DashboardActivity.this,
                                        "Failed to load user data",
                                        Toast.LENGTH_SHORT
                                ).show();

                                setDefaultUserData();
                            }
                        }
                );
    }

    private void setDefaultUserData() {

        if (currentUser != null) {

            userEmail =
                    currentUser.getEmail() != null
                            ? currentUser.getEmail()
                            : "";

            userName =
                    currentUser.getDisplayName() != null
                            ? currentUser.getDisplayName()
                            : (
                            userEmail.isEmpty()
                                    ? "User"
                                    : userEmail.split("@")[0]
                    );

        } else {

            userName = "User";

            userEmail = "";
        }

        updateUserDisplay();

        updateWelcomeTextBasedOnUser();

        saveUserDataToPreferences();
    }

    private void saveUserDataToPreferences() {

        SharedPreferences.Editor editor =
                getSharedPreferences(
                        PREF_NAME,
                        MODE_PRIVATE
                ).edit();

        editor.putString(
                "userName",
                userName
        );

        editor.putString(
                "userEmail",
                userEmail
        );

        editor.apply();
    }

    // ============================================================
    // CLICK LISTENERS
    // ============================================================

    private void setupClickListeners() {

        if (profileImage != null) {

            profileImage.setOnClickListener(
                    v -> showProfileDialog()
            );
        }

        if (cardAddCustomer != null) {

            cardAddCustomer.setOnClickListener(
                    v -> {

                        Intent intent =
                                new Intent(
                                        DashboardActivity.this,
                                        AddCustomerActivity.class
                                );

                        intent.putExtra(
                                "userUid",
                                userUid
                        );

                        startActivity(intent);
                    }
            );
        }

        if (cardViewCustomer != null) {

            cardViewCustomer.setOnClickListener(
                    v -> {

                        Intent intent =
                                new Intent(
                                        DashboardActivity.this,
                                        ViewCustomerActivity.class
                                );

                        intent.putExtra(
                                "userUid",
                                userUid
                        );

                        startActivity(intent);
                    }
            );
        }

        if (rotatingStatsCard != null) {

            rotatingStatsCard.setOnClickListener(
                    v -> flipCard()
            );

            rotatingStatsCard.setOnTouchListener(
                    (v, event) -> {

                        if (event.getAction() ==
                                MotionEvent.ACTION_DOWN) {

                            stopAutoFlip();

                        } else if (
                                event.getAction() ==
                                        MotionEvent.ACTION_UP ||
                                        event.getAction() ==
                                                MotionEvent.ACTION_CANCEL
                        ) {

                            if (autoFlipHandler != null) {

                                autoFlipHandler.postDelayed(
                                        this::restartAutoFlip,
                                        2000
                                );
                            }
                        }

                        return false;
                    }
            );
        }
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private void setupBottomNavigation() {

        // HOME
        if (bottomHome != null) {

            bottomHome.setOnClickListener(
                    v -> {

                        setHomeSelected();

                        // Already in Dashboard
                    }
            );
        }

        // KACHA
        if (bottomKacha != null) {

            bottomKacha.setOnClickListener(
                    v -> {

                        setKachaSelected();

                        Intent intent =
                                new Intent(
                                        DashboardActivity.this,
                                        KachaActivity.class
                                );

                        intent.putExtra(
                                "userUid",
                                userUid
                        );

                        intent.putExtra(
                                "userName",
                                userName
                        );

                        intent.putExtra(
                                "userEmail",
                                userEmail
                        );

                        startActivity(intent);
                    }
            );
        }
    }

    // ============================================================
    // HOME SELECTED
    // ============================================================

    private void setHomeSelected() {

        stopKachaDotAnimation();

        if (homeSelectedDot != null) {

            homeSelectedDot.setVisibility(
                    View.VISIBLE
            );

            startHomeDotAnimation();
        }

        if (kachaSelectedDot != null) {

            kachaSelectedDot.setVisibility(
                    View.GONE
            );
        }
    }

    // ============================================================
    // KACHA SELECTED
    // ============================================================

    private void setKachaSelected() {

        stopHomeDotAnimation();

        if (homeSelectedDot != null) {

            homeSelectedDot.setVisibility(
                    View.GONE
            );
        }

        if (kachaSelectedDot != null) {

            kachaSelectedDot.setVisibility(
                    View.VISIBLE
            );

            startKachaDotAnimation();
        }
    }

    // ============================================================
    // HOME DOT BLINK
    // ============================================================

    private void startHomeDotAnimation() {

        if (homeSelectedDot == null) {
            return;
        }

        stopHomeDotAnimation();

        homeDotAnimator =
                ObjectAnimator.ofFloat(
                        homeSelectedDot,
                        "alpha",
                        1f,
                        0.15f,
                        1f
                );

        homeDotAnimator.setDuration(900);

        homeDotAnimator.setRepeatCount(
                ValueAnimator.INFINITE
        );

        homeDotAnimator.setRepeatMode(
                ValueAnimator.RESTART
        );

        homeDotAnimator.start();
    }

    // ============================================================
    // KACHA DOT BLINK
    // ============================================================

    private void startKachaDotAnimation() {

        if (kachaSelectedDot == null) {
            return;
        }

        stopKachaDotAnimation();

        kachaDotAnimator =
                ObjectAnimator.ofFloat(
                        kachaSelectedDot,
                        "alpha",
                        1f,
                        0.15f,
                        1f
                );

        kachaDotAnimator.setDuration(900);

        kachaDotAnimator.setRepeatCount(
                ValueAnimator.INFINITE
        );

        kachaDotAnimator.setRepeatMode(
                ValueAnimator.RESTART
        );

        kachaDotAnimator.start();
    }

    // ============================================================
    // STOP HOME DOT
    // ============================================================

    private void stopHomeDotAnimation() {

        if (homeDotAnimator != null) {

            homeDotAnimator.cancel();

            homeDotAnimator = null;
        }

        if (homeSelectedDot != null) {

            homeSelectedDot.animate().cancel();

            homeSelectedDot.setAlpha(1f);
        }
    }

    // ============================================================
    // STOP KACHA DOT
    // ============================================================

    private void stopKachaDotAnimation() {

        if (kachaDotAnimator != null) {

            kachaDotAnimator.cancel();

            kachaDotAnimator = null;
        }

        if (kachaSelectedDot != null) {

            kachaSelectedDot.animate().cancel();

            kachaSelectedDot.setAlpha(1f);
        }
    }

    // ============================================================
    // STATISTICS
    // ============================================================

    private void loadStatsData() {

        if (customersRef == null) {
            return;
        }

        customersRef.addListenerForSingleValueEvent(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(
                            @NonNull DataSnapshot snapshot
                    ) {

                        try {

                            final long customerCount;

                            final long totalTransactionCount;

                            if (snapshot.exists()) {

                                customerCount =
                                        snapshot.getChildrenCount();

                                long transactionCount = 0;

                                for (
                                        DataSnapshot customerSnapshot :
                                        snapshot.getChildren()
                                ) {

                                    if (
                                            customerSnapshot.hasChild(
                                                    "transactions"
                                            )
                                    ) {

                                        DataSnapshot transactionsSnapshot =
                                                customerSnapshot.child(
                                                        "transactions"
                                                );

                                        transactionCount +=
                                                transactionsSnapshot
                                                        .getChildrenCount();
                                    }
                                }

                                totalTransactionCount =
                                        transactionCount;

                            } else {

                                customerCount = 0;

                                totalTransactionCount = 0;
                            }

                            runOnUiThread(
                                    () -> {

                                        if (isFinishing() ||
                                                isDestroyed()) {

                                            return;
                                        }

                                        if (totalCustomersCount != null) {

                                            totalCustomersCount.setText(
                                                    String.valueOf(
                                                            customerCount
                                                    )
                                            );
                                        }

                                        if (totalTransactionsCount != null) {

                                            totalTransactionsCount.setText(
                                                    String.valueOf(
                                                            totalTransactionCount
                                                    )
                                            );
                                        }
                                    }
                            );

                        } catch (Exception e) {

                            Log.e(
                                    "Dashboard",
                                    "Stats error: "
                                            + e.getMessage()
                            );
                        }
                    }

                    @Override
                    public void onCancelled(
                            @NonNull DatabaseError error
                    ) {

                        Log.e(
                                "Dashboard",
                                "Stats cancelled: "
                                        + error.getMessage()
                        );
                    }
                }
        );
    }

    // ============================================================
    // FLIP CARD
    // ============================================================

    private void flipCard() {

        if (rotatingStatsCard == null ||
                !rotatingStatsCard.isClickable()) {

            return;
        }

        rotatingStatsCard.setClickable(false);

        if (isFront) {

            ObjectAnimator flipOut =
                    ObjectAnimator.ofFloat(
                            cardFront,
                            "rotationY",
                            0f,
                            90f
                    );

            flipOut.setDuration(300);

            ObjectAnimator flipIn =
                    ObjectAnimator.ofFloat(
                            cardBack,
                            "rotationY",
                            -90f,
                            0f
                    );

            flipIn.setDuration(300);

            flipOut.addListener(
                    new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationEnd(
                                Animator animation
                        ) {

                            if (cardFront != null &&
                                    cardBack != null) {

                                cardFront.setVisibility(
                                        View.GONE
                                );

                                cardBack.setVisibility(
                                        View.VISIBLE
                                );

                                flipIn.start();

                                flipIn.addListener(
                                        new AnimatorListenerAdapter() {

                                            @Override
                                            public void onAnimationEnd(
                                                    Animator animation
                                            ) {

                                                if (rotatingStatsCard != null) {

                                                    rotatingStatsCard
                                                            .setClickable(true);
                                                }
                                            }
                                        }
                                );
                            }
                        }
                    }
            );

            flipOut.start();

        } else {

            ObjectAnimator flipOut =
                    ObjectAnimator.ofFloat(
                            cardBack,
                            "rotationY",
                            0f,
                            90f
                    );

            flipOut.setDuration(300);

            ObjectAnimator flipIn =
                    ObjectAnimator.ofFloat(
                            cardFront,
                            "rotationY",
                            -90f,
                            0f
                    );

            flipIn.setDuration(300);

            flipOut.addListener(
                    new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationEnd(
                                Animator animation
                        ) {

                            if (cardBack != null &&
                                    cardFront != null) {

                                cardBack.setVisibility(
                                        View.GONE
                                );

                                cardFront.setVisibility(
                                        View.VISIBLE
                                );

                                flipIn.start();

                                flipIn.addListener(
                                        new AnimatorListenerAdapter() {

                                            @Override
                                            public void onAnimationEnd(
                                                    Animator animation
                                            ) {

                                                if (rotatingStatsCard != null) {

                                                    rotatingStatsCard
                                                            .setClickable(true);
                                                }
                                            }
                                        }
                                );
                            }
                        }
                    }
            );

            flipOut.start();
        }

        isFront = !isFront;

        restartAutoFlip();
    }

    // ============================================================
    // AUTO FLIP
    // ============================================================

    private void startAutoFlip() {

        if (autoFlipHandler == null) {
            return;
        }

        stopAutoFlip();

        autoFlipRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        if (!isFinishing() &&
                                !isDestroyed()) {

                            flipCard();

                            if (autoFlipHandler != null) {

                                autoFlipHandler.postDelayed(
                                        this,
                                        AUTO_FLIP_INTERVAL
                                );
                            }
                        }
                    }
                };

        autoFlipHandler.postDelayed(
                autoFlipRunnable,
                AUTO_FLIP_INTERVAL
        );
    }

    private void stopAutoFlip() {

        if (autoFlipHandler != null &&
                autoFlipRunnable != null) {

            autoFlipHandler.removeCallbacks(
                    autoFlipRunnable
            );
        }
    }

    private void restartAutoFlip() {

        stopAutoFlip();

        startAutoFlip();
    }

    // ============================================================
    // PROFILE DIALOG
    // ============================================================

    private void showProfileDialog() {

        Dialog profileDialog =
                new Dialog(this);

        profileDialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        profileDialog.setContentView(
                R.layout.dialog_profile
        );

        if (profileDialog.getWindow() != null) {

            profileDialog.getWindow()
                    .setBackgroundDrawable(
                            new ColorDrawable(
                                    Color.TRANSPARENT
                            )
                    );
        }

        profileDialog.setCancelable(true);

        TextView userNameTextView =
                profileDialog.findViewById(
                        R.id.dialog_user_name
                );

        TextView userEmailTextView =
                profileDialog.findViewById(
                        R.id.dialog_user_email
                );

        CardView recycleBinCard =
                profileDialog.findViewById(
                        R.id.card_recycle_bin
                );

        CardView appInfoCard =
                profileDialog.findViewById(
                        R.id.card_app_info
                );

        CardView logoutCard =
                profileDialog.findViewById(
                        R.id.card_logout
                );

        ImageView closeButton =
                profileDialog.findViewById(
                        R.id.close_button
                );

        if (userNameTextView != null) {

            userNameTextView.setText(
                    userName != null &&
                            !userName.isEmpty()
                            ? userName
                            : "User"
            );
        }

        if (userEmailTextView != null) {

            userEmailTextView.setText(
                    userEmail != null
                            ? userEmail
                            : ""
            );
        }

        if (recycleBinCard != null) {

            recycleBinCard.setOnClickListener(
                    v -> {

                        Intent intent =
                                new Intent(
                                        DashboardActivity.this,
                                        FingerprintAuthActivity.class
                                );

                        intent.putExtra(
                                "userUid",
                                userUid
                        );

                        startActivity(intent);

                        profileDialog.dismiss();
                    }
            );
        }

        if (appInfoCard != null) {

            appInfoCard.setOnClickListener(
                    v -> {

                        profileDialog.dismiss();

                        showAppInfoDialog();
                    }
            );
        }

        if (closeButton != null) {

            closeButton.setOnClickListener(
                    v -> profileDialog.dismiss()
            );
        }

        if (logoutCard != null) {

            logoutCard.setOnClickListener(
                    v -> {

                        profileDialog.dismiss();

                        performLogout();
                    }
            );
        }

        profileDialog.show();
    }

    // ============================================================
    // APP INFO
    // ============================================================

    private void showAppInfoDialog() {

        Dialog appInfoDialog =
                new Dialog(this);

        appInfoDialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        appInfoDialog.setContentView(
                R.layout.dialog_app_info
        );

        if (appInfoDialog.getWindow() != null) {

            appInfoDialog.getWindow()
                    .setBackgroundDrawable(
                            new ColorDrawable(
                                    Color.TRANSPARENT
                            )
                    );
        }

        appInfoDialog.setCancelable(true);

        Window window =
                appInfoDialog.getWindow();

        if (window != null) {

            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams();

            params.copyFrom(
                    window.getAttributes()
            );

            params.width =
                    WindowManager.LayoutParams.MATCH_PARENT;

            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;

            params.gravity =
                    Gravity.CENTER;

            window.setAttributes(params);
        }

        TextView appVersion =
                appInfoDialog.findViewById(
                        R.id.app_version
                );

        Button closeButton =
                appInfoDialog.findViewById(
                        R.id.btn_close
                );

        Button shareButton =
                appInfoDialog.findViewById(
                        R.id.btn_share
                );

        ImageButton btnExpandDescription =
                appInfoDialog.findViewById(
                        R.id.btn_expand_description
                );

        TextView tvDescription =
                appInfoDialog.findViewById(
                        R.id.tv_description
                );

        try {

            String versionName =
                    getPackageManager()
                            .getPackageInfo(
                                    getPackageName(),
                                    0
                            )
                            .versionName;

            appVersion.setText(versionName);

        } catch (Exception e) {

            appVersion.setText("2.0.0");
        }

        if (btnExpandDescription != null &&
                tvDescription != null) {

            setupExpandCollapseDescription(
                    btnExpandDescription,
                    tvDescription
            );
        }

        if (closeButton != null) {

            closeButton.setOnClickListener(
                    v -> appInfoDialog.dismiss()
            );
        }

        if (shareButton != null) {

            shareButton.setOnClickListener(
                    v -> shareApp()
            );
        }

        appInfoDialog.show();
    }

    private void setupExpandCollapseDescription(
            ImageButton expandButton,
            TextView descriptionText
    ) {

        descriptionText.setMaxLines(3);

        expandButton.setImageResource(
                R.drawable.ic_expand_more
        );

        expandButton.setOnClickListener(
                v -> {

                    if (descriptionText.getMaxLines() == 3) {

                        descriptionText.setMaxLines(
                                Integer.MAX_VALUE
                        );

                        expandButton.setImageResource(
                                R.drawable.ic_expand_less
                        );

                    } else {

                        descriptionText.setMaxLines(3);

                        expandButton.setImageResource(
                                R.drawable.ic_expand_more
                        );
                    }
                }
        );
    }

    // ============================================================
    // SHARE
    // ============================================================

    private void shareApp() {

        try {

            Intent shareIntent =
                    new Intent(
                            Intent.ACTION_SEND
                    );

            shareIntent.setType(
                    "text/plain"
            );

            shareIntent.putExtra(
                    Intent.EXTRA_SUBJECT,
                    "PST Silver Ledger"
            );

            shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "Check out this amazing PST Silver Ledger!\n\n"
            );

            startActivity(
                    Intent.createChooser(
                            shareIntent,
                            "Share via"
                    )
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Error sharing app",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ============================================================
    // LOGOUT
    // ============================================================

    private void performLogout() {

        showLoggingOutDialog();
    }

    private void showLoggingOutDialog() {

        Dialog logoutDialog =
                new Dialog(this);

        logoutDialog.requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        logoutDialog.setContentView(
                R.layout.dialog_logging_out
        );

        if (logoutDialog.getWindow() != null) {

            logoutDialog.getWindow()
                    .setBackgroundDrawable(
                            new ColorDrawable(
                                    Color.TRANSPARENT
                            )
                    );
        }

        logoutDialog.setCancelable(false);

        logoutDialog.show();

        new Handler().postDelayed(
                () -> {

                    FirebaseAuth.getInstance()
                            .signOut();

                    SharedPreferences.Editor editor =
                            getSharedPreferences(
                                    PREF_NAME,
                                    MODE_PRIVATE
                            ).edit();

                    editor.clear();

                    editor.apply();

                    logoutDialog.dismiss();

                    Intent intent =
                            new Intent(
                                    DashboardActivity.this,
                                    SplashActivity.class
                            );

                    intent.setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );

                    startActivity(intent);

                    finish();

                },
                2000
        );
    }

    // ============================================================
    // FEATURE CHIPS
    // ============================================================

    private void setupFeatureChips() {

        setGreenTickText(
                secureChip,
                "Secure"
        );

        setGreenTickText(
                accurateChip,
                "Accurate"
        );

        setGreenTickText(
                fastChip,
                "Fast"
        );
    }

    private void setGreenTickText(
            TextView textView,
            String text
    ) {

        if (textView == null) {
            return;
        }

        SpannableString spannable =
                new SpannableString(
                        "✓ " + text
                );

        spannable.setSpan(
                new ForegroundColorSpan(
                        Color.parseColor("#4CAF50")
                ),
                0,
                1,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        textView.setText(spannable);
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    protected void onResume() {

        super.onResume();

        loadUserData();

        loadStatsData();

        restartAutoFlip();

        if (!isAnimating &&
                isLooping) {

            startLetterFlyDownAnimation();
        }

        // Dashboard always selected when resumed
        setHomeSelected();
    }

    @Override
    protected void onPause() {

        super.onPause();

        stopAutoFlip();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        isLooping = false;

        isAnimating = false;

        stopAutoFlip();

        stopHomeDotAnimation();

        stopKachaDotAnimation();

        if (animationHandler != null) {

            animationHandler.removeCallbacksAndMessages(
                    null
            );

            animationHandler = null;
        }

        if (autoFlipHandler != null) {

            autoFlipHandler.removeCallbacksAndMessages(
                    null
            );

            autoFlipHandler = null;
        }

        if (letterContainer != null) {

            letterContainer.removeAllViews();

            letterContainer = null;
        }

        welcomeText = null;

        userNameText = null;

        profileImage = null;

        rotatingStatsCard = null;

        homeSelectedDot = null;

        kachaSelectedDot = null;
    }
}
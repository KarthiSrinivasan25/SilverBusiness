package com.example.silverbusinessapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddKachaActivity extends AppCompatActivity {

    // =========================================================
    // FIREBASE
    // =========================================================

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private String userId;

    // =========================================================
    // BASIC INFORMATION
    // =========================================================

    private EditText etDate;
    private EditText etParty;

    // =========================================================
    // BUTTONS
    // =========================================================

    private TextView btnBack;
    private TextView btnTopSave;
    private TextView btnAddRow;
    private TextView btnRemoveAll;
    private TextView btnCancel;
    private TextView btnSave;

    // =========================================================
    // TITLES
    // =========================================================

    private TextView tvTitle;
    private TextView tvRowCount;

    // =========================================================
    // KACHA TABLE
    // =========================================================

    private LinearLayout rowsContainer;

    // =========================================================
    // GRAND TOTAL
    // =========================================================

    private TextView tvTotalWeight;
    private TextView tvTotalTouch;
    private TextView tvTotalPure;

    // =========================================================
    // COLOR TOTAL ROWS
    // =========================================================

    private LinearLayout redTotalRow;
    private LinearLayout blueTotalRow;
    private LinearLayout greenTotalRow;
    private LinearLayout orangeTotalRow;
    private LinearLayout multiColorTotalRow;

    // =========================================================
    // RED TOTAL
    // =========================================================

    private TextView tvRedWeight;
    private TextView tvRedTouch;
    private TextView tvRedPure;

    // =========================================================
    // BLUE TOTAL
    // =========================================================

    private TextView tvBlueWeight;
    private TextView tvBlueTouch;
    private TextView tvBluePure;

    // =========================================================
    // GREEN TOTAL
    // =========================================================

    private TextView tvGreenWeight;
    private TextView tvGreenTouch;
    private TextView tvGreenPure;

    // =========================================================
    // ORANGE TOTAL
    // =========================================================

    private TextView tvOrangeWeight;
    private TextView tvOrangeTouch;
    private TextView tvOrangePure;

    // =========================================================
    // MULTI-COLOR TOTAL
    // =========================================================

    private TextView tvMultiColorWeight;
    private TextView tvMultiColorTouch;
    private TextView tvMultiColorPure;

    // =========================================================
    // GOLD DATA
    // =========================================================

    private EditText etNayamKacha;
    private EditText etNayamTouch;
    private TextView tvNayamPure;

    private EditText etMattamKacha;
    private EditText etMattamTouch;
    private TextView tvMattamPure;

    private EditText etRequiredTouch;

    // =========================================================
    // REFERENCE VIEWS
    // =========================================================

    private TextView tvRefNayamKacha;
    private TextView tvRefNayamTouch;
    private TextView tvRefNayamPure;
    private TextView tvRefMattamKacha;
    private TextView tvRefMattamTouch;
    private TextView tvRefMattamPure;
    private TextView tvRefTotalKacha;
    private TextView tvRefTotalTouch;
    private TextView tvRefTotalPure;

    // =========================================================
    // REFERENCE CONTAINERS (HIDDEN BY DEFAULT)
    // =========================================================

    private LinearLayout goldReferenceContainer;

    // =========================================================
    // KACHA EXCHANGE DETAILS
    // =========================================================

    private EditText etExchangeWeight;
    private EditText etExchangeTouch;
    private TextView tvExchangePure;
    private EditText etExchangeRequiredTouch;
    private TextView tvExchangeKacha;
    private EditText etExchangeTouchInput;
    private TextView tvExchangePureOutput;

    // =========================================================
    // KACHA EXCHANGE REFERENCE VIEWS
    // =========================================================

    private TextView tvRefWeightAns;
    private TextView tvRefTouchValue;
    private TextView tvRefPureValue;
    private TextView tvRefExchangeKacha;
    private TextView tvRefExchangeTouch;
    private TextView tvRefExchangePure;
    private TextView tvRefTotalWeight;
    private TextView tvRefTotalTouchValue;
    private TextView tvRefTotalPureValue;

    // =========================================================
    // EXCHANGE REFERENCE CONTAINER (HIDDEN BY DEFAULT)
    // =========================================================

    private LinearLayout exchangeReferenceContainer;

    // =========================================================
    // WEIGHT & TOUCH DETAILS
    // =========================================================

    private LinearLayout wtRowsContainer;
    private TextView tvWtRowCount;
    private TextView btnWtRemoveAll;
    private TextView btnAddWtRow;

    // Grand Total
    private TextView tvWtTotalWeight;
    private TextView tvWtTotalTouch;
    private TextView tvWtTotalPure;

    // Color Total Rows
    private LinearLayout wtRedTotalRow;
    private LinearLayout wtBlueTotalRow;
    private LinearLayout wtBothColorsTotalRow;

    // Red Total
    private TextView tvWtRedWeight;
    private TextView tvWtRedTouch;
    private TextView tvWtRedPure;

    // Blue Total
    private TextView tvWtBlueWeight;
    private TextView tvWtBlueTouch;
    private TextView tvWtBluePure;

    // Both Colors Total
    private TextView tvWtBothColorsWeight;
    private TextView tvWtBothColorsTouch;
    private TextView tvWtBothColorsPure;

    // =========================================================
    // ROW LISTS
    // =========================================================

    private final List<KachaRow> rows = new ArrayList<>();
    private final List<WTRow> wtRows = new ArrayList<>();

    // =========================================================
    // CALENDAR
    // =========================================================

    private final Calendar calendar = Calendar.getInstance();

    // =========================================================
    // EDIT MODE
    // =========================================================

    private boolean editMode = false;
    private String editKey = null; // Firebase key for editing
    private int editPosition = -1;

    // =========================================================
    // GOLD CALCULATION LOCK
    // =========================================================

    private boolean updatingGoldFields = false;

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_kacha);

        // Initialize Firebase
        initializeFirebase();

        initializeViews();
        setupDate();
        setupButtons();
        setupGoldCalculation();
        checkEditMode();

        if (!editMode) {
            addRow();
            addWTRow();
        }
    }

    // =========================================================
    // INITIALIZE FIREBASE
    // =========================================================

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userId = currentUser.getUid();
        databaseReference = FirebaseDatabase.getInstance()
                .getReference()
                .child("Users")
                .child(userId)
                .child("kacha");
    }

    // =========================================================
    // INITIALIZE VIEWS
    // =========================================================

    private void initializeViews() {
        // -----------------------------------------------------
        // BASIC
        // -----------------------------------------------------
        etDate = findViewById(R.id.et_date);
        etParty = findViewById(R.id.et_party);

        // -----------------------------------------------------
        // BUTTONS
        // -----------------------------------------------------
        btnBack = findViewById(R.id.btn_back);
        btnTopSave = findViewById(R.id.btn_top_save);
        btnAddRow = findViewById(R.id.btn_add_row);
        btnRemoveAll = findViewById(R.id.btn_remove_all);
        btnCancel = findViewById(R.id.btn_cancel);
        btnSave = findViewById(R.id.btn_save);

        // -----------------------------------------------------
        // TITLES
        // -----------------------------------------------------
        tvTitle = findViewById(R.id.tv_title);
        tvRowCount = findViewById(R.id.tv_row_count);

        // -----------------------------------------------------
        // ROW CONTAINER
        // -----------------------------------------------------
        rowsContainer = findViewById(R.id.rows_container);

        // -----------------------------------------------------
        // GRAND TOTAL
        // -----------------------------------------------------
        tvTotalWeight = findViewById(R.id.tv_total_weight);
        tvTotalTouch = findViewById(R.id.tv_total_touch);
        tvTotalPure = findViewById(R.id.tv_total_pure);

        // -----------------------------------------------------
        // COLOR TOTAL ROWS
        // -----------------------------------------------------
        redTotalRow = findViewById(R.id.red_total_row);
        blueTotalRow = findViewById(R.id.blue_total_row);
        greenTotalRow = findViewById(R.id.green_total_row);
        orangeTotalRow = findViewById(R.id.orange_total_row);
        multiColorTotalRow = findViewById(R.id.multi_color_total_row);

        // -----------------------------------------------------
        // RED
        // -----------------------------------------------------
        tvRedWeight = findViewById(R.id.tv_red_weight);
        tvRedTouch = findViewById(R.id.tv_red_touch);
        tvRedPure = findViewById(R.id.tv_red_pure);

        // -----------------------------------------------------
        // BLUE
        // -----------------------------------------------------
        tvBlueWeight = findViewById(R.id.tv_blue_weight);
        tvBlueTouch = findViewById(R.id.tv_blue_touch);
        tvBluePure = findViewById(R.id.tv_blue_pure);

        // -----------------------------------------------------
        // GREEN
        // -----------------------------------------------------
        tvGreenWeight = findViewById(R.id.tv_green_weight);
        tvGreenTouch = findViewById(R.id.tv_green_touch);
        tvGreenPure = findViewById(R.id.tv_green_pure);

        // -----------------------------------------------------
        // ORANGE
        // -----------------------------------------------------
        tvOrangeWeight = findViewById(R.id.tv_orange_weight);
        tvOrangeTouch = findViewById(R.id.tv_orange_touch);
        tvOrangePure = findViewById(R.id.tv_orange_pure);

        // -----------------------------------------------------
        // MULTI-COLOR
        // -----------------------------------------------------
        tvMultiColorWeight = findViewById(R.id.tv_multi_color_weight);
        tvMultiColorTouch = findViewById(R.id.tv_multi_color_touch);
        tvMultiColorPure = findViewById(R.id.tv_multi_color_pure);

        // =====================================================
        // GOLD DATA
        // =====================================================
        etNayamKacha = findViewById(R.id.et_nayam_kacha);
        etNayamTouch = findViewById(R.id.et_nayam_touch);
        tvNayamPure = findViewById(R.id.tv_nayam_pure);

        etMattamKacha = findViewById(R.id.et_mattam_kacha);
        etMattamTouch = findViewById(R.id.et_mattam_touch);
        tvMattamPure = findViewById(R.id.tv_mattam_pure);

        etRequiredTouch = findViewById(R.id.et_required_touch);

        // =====================================================
        // REFERENCE VIEWS
        // =====================================================
        tvRefNayamKacha = findViewById(R.id.tv_ref_nayam_kacha);
        tvRefNayamTouch = findViewById(R.id.tv_ref_nayam_touch);
        tvRefNayamPure = findViewById(R.id.tv_ref_nayam_pure);
        tvRefMattamKacha = findViewById(R.id.tv_ref_mattam_kacha);
        tvRefMattamTouch = findViewById(R.id.tv_ref_mattam_touch);
        tvRefMattamPure = findViewById(R.id.tv_ref_mattam_pure);
        tvRefTotalKacha = findViewById(R.id.tv_ref_total_kacha);
        tvRefTotalTouch = findViewById(R.id.tv_ref_total_touch);
        tvRefTotalPure = findViewById(R.id.tv_ref_total_pure);

        // =====================================================
        // REFERENCE CONTAINERS
        // =====================================================
        goldReferenceContainer = findViewById(R.id.gold_reference_container);
        if (goldReferenceContainer != null) {
            goldReferenceContainer.setVisibility(View.GONE);
        }

        // =====================================================
        // KACHA EXCHANGE DETAILS
        // =====================================================
        etExchangeWeight = findViewById(R.id.et_exchange_weight);
        etExchangeTouch = findViewById(R.id.et_exchange_touch);
        tvExchangePure = findViewById(R.id.tv_exchange_pure);
        etExchangeRequiredTouch = findViewById(R.id.et_exchange_required_touch);
        tvExchangeKacha = findViewById(R.id.tv_exchange_kacha);
        etExchangeTouchInput = findViewById(R.id.et_exchange_touch_input);
        tvExchangePureOutput = findViewById(R.id.tv_exchange_pure_output);

        // =====================================================
        // KACHA EXCHANGE REFERENCE VIEWS
        // =====================================================
        tvRefWeightAns = findViewById(R.id.tv_ref_weight_ans);
        tvRefTouchValue = findViewById(R.id.tv_ref_touch_value);
        tvRefPureValue = findViewById(R.id.tv_ref_pure_value);
        tvRefExchangeKacha = findViewById(R.id.tv_ref_exchange_kacha);
        tvRefExchangeTouch = findViewById(R.id.tv_ref_exchange_touch);
        tvRefExchangePure = findViewById(R.id.tv_ref_exchange_pure);
        tvRefTotalWeight = findViewById(R.id.tv_ref_total_weight);
        tvRefTotalTouchValue = findViewById(R.id.tv_ref_total_touch_value);
        tvRefTotalPureValue = findViewById(R.id.tv_ref_total_pure_value);

        // =====================================================
        // EXCHANGE REFERENCE CONTAINER
        // =====================================================
        exchangeReferenceContainer = findViewById(R.id.exchange_reference_container);
        if (exchangeReferenceContainer != null) {
            exchangeReferenceContainer.setVisibility(View.GONE);
        }

        // =====================================================
        // WEIGHT & TOUCH DETAILS
        // =====================================================
        wtRowsContainer = findViewById(R.id.wt_rows_container);
        tvWtRowCount = findViewById(R.id.tv_wt_row_count);
        btnWtRemoveAll = findViewById(R.id.btn_wt_remove_all);
        btnAddWtRow = findViewById(R.id.btn_add_wt_row);

        // Grand Total
        tvWtTotalWeight = findViewById(R.id.tv_wt_total_weight);
        tvWtTotalTouch = findViewById(R.id.tv_wt_total_touch);
        tvWtTotalPure = findViewById(R.id.tv_wt_total_pure);

        // Color Total Rows
        wtRedTotalRow = findViewById(R.id.wt_red_total_row);
        wtBlueTotalRow = findViewById(R.id.wt_blue_total_row);
        wtBothColorsTotalRow = findViewById(R.id.wt_both_colors_total_row);

        // Red
        tvWtRedWeight = findViewById(R.id.tv_wt_red_weight);
        tvWtRedTouch = findViewById(R.id.tv_wt_red_touch);
        tvWtRedPure = findViewById(R.id.tv_wt_red_pure);

        // Blue
        tvWtBlueWeight = findViewById(R.id.tv_wt_blue_weight);
        tvWtBlueTouch = findViewById(R.id.tv_wt_blue_touch);
        tvWtBluePure = findViewById(R.id.tv_wt_blue_pure);

        // Both Colors
        tvWtBothColorsWeight = findViewById(R.id.tv_wt_both_colors_weight);
        tvWtBothColorsTouch = findViewById(R.id.tv_wt_both_colors_touch);
        tvWtBothColorsPure = findViewById(R.id.tv_wt_both_colors_pure);
    }

    // =========================================================
    // DATE
    // =========================================================

    private void setupDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

        if (TextUtils.isEmpty(etDate.getText().toString())) {
            etDate.setText(sdf.format(calendar.getTime()));
        }

        etDate.setOnClickListener(v -> showDatePicker());
    }

    // =========================================================
    // DATE PICKER
    // =========================================================

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    calendar.set(year, month, day);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
                    etDate.setText(sdf.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void setupButtons() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }

        if (btnAddRow != null) {
            btnAddRow.setOnClickListener(v -> addRow());
        }

        if (btnRemoveAll != null) {
            btnRemoveAll.setOnClickListener(v -> removeAllRows());
        }

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveKacha());
        }

        if (btnTopSave != null) {
            btnTopSave.setOnClickListener(v -> saveKacha());
        }

        // Weight & Touch buttons
        if (btnAddWtRow != null) {
            btnAddWtRow.setOnClickListener(v -> addWTRow());
        }

        if (btnWtRemoveAll != null) {
            btnWtRemoveAll.setOnClickListener(v -> removeAllWTRows());
        }
    }

    // =========================================================
    // GOLD CALCULATION SETUP
    // =========================================================

    private void setupGoldCalculation() {
        TextWatcher goldWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!updatingGoldFields) {
                    calculateGoldData();
                    calculateExchangeData();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        etNayamKacha.addTextChangedListener(goldWatcher);
        etNayamTouch.addTextChangedListener(goldWatcher);
        etMattamKacha.addTextChangedListener(goldWatcher);
        etMattamTouch.addTextChangedListener(goldWatcher);
        etRequiredTouch.addTextChangedListener(goldWatcher);

        // Add exchange watchers - ALL exchange input fields
        etExchangeWeight.addTextChangedListener(goldWatcher);
        etExchangeTouch.addTextChangedListener(goldWatcher);
        etExchangeRequiredTouch.addTextChangedListener(goldWatcher);
        etExchangeTouchInput.addTextChangedListener(goldWatcher);
    }

    // =========================================================
    // GOLD CALCULATION
    // =========================================================

    private void calculateGoldData() {
        if (updatingGoldFields) {
            return;
        }

        double nayamKacha = parse(etNayamKacha.getText().toString());
        double nayamTouch = parse(etNayamTouch.getText().toString());
        double mattamKacha = parse(etMattamKacha.getText().toString());
        double mattamTouch = parse(etMattamTouch.getText().toString());
        double requiredTouch = parse(etRequiredTouch.getText().toString());

        boolean hasNayamKacha = !TextUtils.isEmpty(etNayamKacha.getText().toString().trim());
        boolean hasNayamTouch = !TextUtils.isEmpty(etNayamTouch.getText().toString().trim());
        boolean hasMattamKacha = !TextUtils.isEmpty(etMattamKacha.getText().toString().trim());
        boolean hasMattamTouch = !TextUtils.isEmpty(etMattamTouch.getText().toString().trim());
        boolean hasRequiredTouch = !TextUtils.isEmpty(etRequiredTouch.getText().toString().trim());

        // =====================================================
        // CALCULATE NAYAM PURE
        // =====================================================
        if (hasNayamKacha && hasNayamTouch) {
            double nayamPure = (nayamKacha * nayamTouch) / 100.0;
            tvNayamPure.setText(format(nayamPure));
        } else {
            tvNayamPure.setText("0.000");
        }

        // =====================================================
        // CALCULATE MATTAM PURE
        // =====================================================
        if (hasMattamKacha && hasMattamTouch) {
            double mattamPure = (mattamKacha * mattamTouch) / 100.0;
            tvMattamPure.setText(format(mattamPure));
        } else {
            tvMattamPure.setText("0.000");
        }

        if (!hasRequiredTouch) {
            updateReference();
            return;
        }

        // =====================================================
        // CASE 1: Calculate Mattam Kacha
        // =====================================================
        if (hasNayamKacha && hasNayamTouch && hasMattamTouch && !hasMattamKacha) {
            double denominator = mattamTouch - requiredTouch;
            if (denominator != 0) {
                double calculatedMattamKacha = ((requiredTouch - nayamTouch) * nayamKacha) / denominator;
                if (calculatedMattamKacha > 0) {
                    setGoldValue(etMattamKacha, calculatedMattamKacha);
                }
            }
            updateReference();
            return;
        }

        // =====================================================
        // CASE 2: Calculate Nayam Kacha
        // =====================================================
        if (hasMattamKacha && hasMattamTouch && hasNayamTouch && !hasNayamKacha) {
            double denominator = nayamTouch - requiredTouch;
            if (denominator != 0) {
                double calculatedNayamKacha = ((requiredTouch - mattamTouch) * mattamKacha) / denominator;
                if (calculatedNayamKacha > 0) {
                    setGoldValue(etNayamKacha, calculatedNayamKacha);
                }
            }
            updateReference();
            return;
        }

        updateReference();
    }

    // =========================================================
    // CALCULATE EXCHANGE DATA WITH REFERENCE
    // =========================================================

    private void calculateExchangeData() {
        // Get Exchange Weight (user input)
        double exchangeWeight = parse(etExchangeWeight.getText().toString());

        // Get Exchange Touch (user input)
        double exchangeTouch = parse(etExchangeTouch.getText().toString());

        // Get Required Touch (user input)
        double requiredTouch = parse(etExchangeRequiredTouch.getText().toString());

        // =====================================================
        // CALCULATE EXCHANGE PURE (Output)
        // Pure = Weight × Touch / 100
        // =====================================================
        double exchangePure = 0;
        if (exchangeWeight != 0 && exchangeTouch != 0) {
            exchangePure = (exchangeWeight * exchangeTouch) / 100.0;
            tvExchangePure.setText(format(exchangePure));
        } else {
            tvExchangePure.setText("0.000");
        }

        // =====================================================
        // CALCULATE EXCHANGE KACHA (Output)
        // Formula:
        // Exchange Kacha = [(Weight × Required Touch) - (100 × Pure)]
        //                  × 100 / [Required Touch × (100 - Touch)]
        // =====================================================

        double exchangeKacha = 0;
        if (exchangeWeight != 0 && requiredTouch != 0 && exchangeTouch != 0) {
            double numerator = (exchangeWeight * requiredTouch) - (100 * exchangePure);
            double denominator = requiredTouch * (100 - exchangeTouch);

            if (denominator != 0) {
                exchangeKacha = (numerator * 100) / denominator;

                // Only show positive values
                if (exchangeKacha > 0) {
                    tvExchangeKacha.setText(format(exchangeKacha));
                } else {
                    tvExchangeKacha.setText("0.000");
                    exchangeKacha = 0;
                }
            } else {
                tvExchangeKacha.setText("0.000");
            }
        } else {
            tvExchangeKacha.setText("0.000");
        }

        // =====================================================
        // CALCULATE EXCHANGE PURE (Output)
        // Exchange Pure = Exchange Kacha × Exchange Touch / 100
        // =====================================================

        double exchangeTouchInput = parse(etExchangeTouchInput.getText().toString());
        double exchangePureOutput = 0;

        if (exchangeKacha != 0 && exchangeTouchInput != 0) {
            exchangePureOutput = (exchangeKacha * exchangeTouchInput) / 100.0;
            tvExchangePureOutput.setText(format(exchangePureOutput));
        } else {
            tvExchangePureOutput.setText("0.000");
        }

        // =====================================================
        // CALCULATE REFERENCE VALUES (CORRECTED)
        // =====================================================

        // 1. Weight Ans = Weight - Exchange Kacha
        double weightAns = exchangeWeight - exchangeKacha;

        // 2. Touch Value = Exchange Touch (same as input)
        double touchValue = exchangeTouch;

        // 3. Pure Value = Weight Ans × Touch Value / 100
        double pureValue = 0;
        if (weightAns != 0 && touchValue != 0) {
            pureValue = (weightAns * touchValue) / 100.0;
        }

        // 4. Exchange Kacha (already calculated)
        // 5. Exchange Touch Input (already from input)
        // 6. Exchange Pure Output (already calculated)

        // 7. Total Weight = Weight Ans + Exchange Kacha
        double totalWeight = weightAns + exchangeKacha;

        // 8. Total Touch Value = (Total Weight × Total Pure) / 100
        // Note: Total Pure = Pure Value + Exchange Pure Output
        double totalPure = pureValue + exchangePureOutput;
        double totalTouchValue = 0;
        if (totalWeight != 0 && totalPure != 0) {
            totalTouchValue = (totalPure / totalWeight) * 100;
        }

        // =====================================================
        // UPDATE REFERENCE VIEWS
        // =====================================================

        // Row 1: Weight Ans, Touch Value, Pure Value
        tvRefWeightAns.setText(format(weightAns));
        tvRefTouchValue.setText(format(touchValue));
        tvRefPureValue.setText(format(pureValue));

        // Row 2: Exchange Kacha, Exchange Touch, Exchange Pure
        tvRefExchangeKacha.setText(format(exchangeKacha));
        tvRefExchangeTouch.setText(format(exchangeTouchInput));
        tvRefExchangePure.setText(format(exchangePureOutput));

        // Row 3: Total Weight, Total Touch Value, Total Pure
        tvRefTotalWeight.setText(format(totalWeight));
        tvRefTotalTouchValue.setText(format(totalTouchValue));
        tvRefTotalPureValue.setText(format(totalPure));

        // =====================================================
        // SHOW/HIDE EXCHANGE REFERENCE BASED ON DATA PRESENCE
        // =====================================================
        boolean hasExchangeData = !TextUtils.isEmpty(etExchangeWeight.getText().toString().trim()) ||
                !TextUtils.isEmpty(etExchangeTouch.getText().toString().trim()) ||
                !TextUtils.isEmpty(etExchangeRequiredTouch.getText().toString().trim()) ||
                !TextUtils.isEmpty(etExchangeTouchInput.getText().toString().trim());

        if (exchangeReferenceContainer != null) {
            exchangeReferenceContainer.setVisibility(hasExchangeData ? View.VISIBLE : View.GONE);
        }
    }

    // =========================================================
    // UPDATE REFERENCE TABLE (Gold Data)
    // =========================================================

    private void updateReference() {
        double nayamKacha = parse(etNayamKacha.getText().toString());
        double nayamTouch = parse(etNayamTouch.getText().toString());
        double nayamPure = parse(tvNayamPure.getText().toString());

        double mattamKacha = parse(etMattamKacha.getText().toString());
        double mattamTouch = parse(etMattamTouch.getText().toString());
        double mattamPure = parse(tvMattamPure.getText().toString());

        // Set Nayam values
        tvRefNayamKacha.setText(format(nayamKacha));
        tvRefNayamTouch.setText(format(nayamTouch));
        tvRefNayamPure.setText(format(nayamPure));

        // Set Mattam values
        tvRefMattamKacha.setText(format(mattamKacha));
        tvRefMattamTouch.setText(format(mattamTouch));
        tvRefMattamPure.setText(format(mattamPure));

        // Calculate totals
        double totalKacha = nayamKacha + mattamKacha;
        double totalPure = nayamPure + mattamPure;
        double totalTouch = 0;
        if (totalKacha != 0) {
            totalTouch = (totalPure / totalKacha) * 100;
        }

        // Set Total values
        tvRefTotalKacha.setText(format(totalKacha));
        tvRefTotalTouch.setText(format(totalTouch));
        tvRefTotalPure.setText(format(totalPure));

        // =====================================================
        // SHOW/HIDE GOLD REFERENCE BASED ON DATA PRESENCE
        // =====================================================
        boolean hasGoldData = !TextUtils.isEmpty(etNayamKacha.getText().toString().trim()) ||
                !TextUtils.isEmpty(etNayamTouch.getText().toString().trim()) ||
                !TextUtils.isEmpty(etMattamKacha.getText().toString().trim()) ||
                !TextUtils.isEmpty(etMattamTouch.getText().toString().trim());

        if (goldReferenceContainer != null) {
            goldReferenceContainer.setVisibility(hasGoldData ? View.VISIBLE : View.GONE);
        }
    }

    // =========================================================
    // SET CALCULATED GOLD VALUE
    // =========================================================

    private void setGoldValue(EditText editText, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
            return;
        }

        updatingGoldFields = true;
        editText.setText(format(value));
        editText.setSelection(editText.getText().length());
        updatingGoldFields = false;

        // Update pure values after setting output
        updateGoldPure();
    }

    // =========================================================
    // UPDATE GOLD PURE
    // =========================================================

    private void updateGoldPure() {
        double nayamKacha = parse(etNayamKacha.getText().toString());
        double nayamTouch = parse(etNayamTouch.getText().toString());
        double mattamKacha = parse(etMattamKacha.getText().toString());
        double mattamTouch = parse(etMattamTouch.getText().toString());

        if (nayamKacha != 0 && nayamTouch != 0) {
            tvNayamPure.setText(format((nayamKacha * nayamTouch) / 100.0));
        }

        if (mattamKacha != 0 && mattamTouch != 0) {
            tvMattamPure.setText(format((mattamKacha * mattamTouch) / 100.0));
        }

        updateReference();
    }

    // =========================================================
    // ADD KACHA ROW
    // =========================================================

    private KachaRow addRow() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.item_kacha_row, rowsContainer, false);

        KachaRow row = new KachaRow(view);
        rows.add(row);
        rowsContainer.addView(view);

        setupRow(row);
        refreshRowNumbers();
        updateTotals();

        return row;
    }

    // =========================================================
    // SETUP KACHA ROW
    // =========================================================

    private void setupRow(KachaRow row) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculatePure(row);
                updateTotals();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        row.etWeight.addTextChangedListener(watcher);
        row.etTouch.addTextChangedListener(watcher);

        row.btnDelete.setOnClickListener(v -> deleteRow(row));

        setupColorListeners(row);
    }

    // =========================================================
    // COLOR LISTENERS (Allow Multi-Select)
    // =========================================================

    private void setupColorListeners(KachaRow row) {
        row.cbColor1.setOnCheckedChangeListener((buttonView, checked) -> {
            updateTotals();
        });

        row.cbColor2.setOnCheckedChangeListener((buttonView, checked) -> {
            updateTotals();
        });

        row.cbColor3.setOnCheckedChangeListener((buttonView, checked) -> {
            updateTotals();
        });

        row.cbColor4.setOnCheckedChangeListener((buttonView, checked) -> {
            updateTotals();
        });
    }

    // =========================================================
    // DELETE ROW
    // =========================================================

    private void deleteRow(KachaRow row) {
        rows.remove(row);
        rowsContainer.removeView(row.root);
        refreshRowNumbers();
        updateTotals();
    }

    // =========================================================
    // REMOVE ALL
    // =========================================================

    private void removeAllRows() {
        rows.clear();
        rowsContainer.removeAllViews();
        refreshRowNumbers();
        updateTotals();

        Toast.makeText(this, "All rows removed", Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // CALCULATE ROW PURE
    // =========================================================

    private void calculatePure(KachaRow row) {
        double weight = parse(row.etWeight.getText().toString());
        double touch = parse(row.etTouch.getText().toString());
        double pure = (weight * touch) / 100.0;
        row.tvPure.setText(format(pure));
    }

    // =========================================================
    // UPDATE TOTALS (Combine ALL colored rows for Multi-Color)
    // =========================================================

    private void updateTotals() {
        double totalWeight = 0;
        double totalPure = 0;

        double redWeight = 0;
        double redPure = 0;

        double blueWeight = 0;
        double bluePure = 0;

        double greenWeight = 0;
        double greenPure = 0;

        double orangeWeight = 0;
        double orangePure = 0;

        // These will store ALL rows that have at least one color selected
        double multiColorWeight = 0;
        double multiColorPure = 0;

        boolean hasRed = false;
        boolean hasBlue = false;
        boolean hasGreen = false;
        boolean hasOrange = false;
        boolean hasMultiColor = false;

        for (KachaRow row : rows) {
            double weight = parse(row.etWeight.getText().toString());
            double touch = parse(row.etTouch.getText().toString());
            double pure = (weight * touch) / 100.0;

            totalWeight += weight;
            totalPure += pure;

            // Count how many colors are selected for this row
            int colorCount = 0;
            boolean hasAnyColor = false;

            if (row.cbColor1.isChecked()) {
                colorCount++;
                hasAnyColor = true;
            }
            if (row.cbColor2.isChecked()) {
                colorCount++;
                hasAnyColor = true;
            }
            if (row.cbColor3.isChecked()) {
                colorCount++;
                hasAnyColor = true;
            }
            if (row.cbColor4.isChecked()) {
                colorCount++;
                hasAnyColor = true;
            }

            // -------------------------------------------------
            // Check if this row has multiple colors
            // -------------------------------------------------
            if (colorCount >= 2) {
                hasMultiColor = true;
            }

            // -------------------------------------------------
            // Track individual colors
            // -------------------------------------------------
            if (row.cbColor1.isChecked()) {
                hasRed = true;
                redWeight += weight;
                redPure += pure;
            }

            if (row.cbColor2.isChecked()) {
                hasBlue = true;
                blueWeight += weight;
                bluePure += pure;
            }

            if (row.cbColor3.isChecked()) {
                hasGreen = true;
                greenWeight += weight;
                greenPure += pure;
            }

            if (row.cbColor4.isChecked()) {
                hasOrange = true;
                orangeWeight += weight;
                orangePure += pure;
            }

            // -------------------------------------------------
            // If this row has at least one color, add to multi-color total
            // (This will be used when ANY multi-color row exists)
            // -------------------------------------------------
            if (hasAnyColor) {
                multiColorWeight += weight;
                multiColorPure += pure;
            }
        }

        // =====================================================
        // ROW COUNT
        // =====================================================
        if (tvRowCount != null) {
            tvRowCount.setText(rows.size() + (rows.size() == 1 ? " Entry" : " Entries"));
        }

        // =====================================================
        // GRAND TOTAL (Always show these - ALL rows including unselected)
        // =====================================================
        tvTotalWeight.setText(format(totalWeight));
        tvTotalPure.setText(format(totalPure));

        if (totalWeight != 0) {
            double totalTouch = (totalPure / totalWeight) * 100;
            tvTotalTouch.setText(format(totalTouch));
        } else {
            tvTotalTouch.setText("0.000");
        }

        // =====================================================
        // DETERMINE WHAT TO SHOW
        // =====================================================

        // If ANY row has multi-color, show Multi-Color total
        // AND hide individual color totals
        if (hasMultiColor) {
            // Show Multi-Color total (ALL rows with at least one color)
            multiColorTotalRow.setVisibility(View.VISIBLE);
            tvMultiColorWeight.setText(format(multiColorWeight));
            tvMultiColorPure.setText(format(multiColorPure));

            if (multiColorWeight != 0) {
                double multiColorTouch = (multiColorPure / multiColorWeight) * 100;
                tvMultiColorTouch.setText(format(multiColorTouch));
            } else {
                tvMultiColorTouch.setText("0.000");
            }

            // Hide all individual color totals
            redTotalRow.setVisibility(View.GONE);
            blueTotalRow.setVisibility(View.GONE);
            greenTotalRow.setVisibility(View.GONE);
            orangeTotalRow.setVisibility(View.GONE);
        }
        // If NO multi-color rows, show individual color totals
        else {
            // Hide Multi-Color total
            multiColorTotalRow.setVisibility(View.GONE);

            // Show individual color totals
            redTotalRow.setVisibility(hasRed ? View.VISIBLE : View.GONE);
            tvRedWeight.setText(format(redWeight));
            tvRedPure.setText(format(redPure));

            if (redWeight != 0) {
                double redTouch = (redPure / redWeight) * 100;
                tvRedTouch.setText(format(redTouch));
            } else {
                tvRedTouch.setText("0.000");
            }

            blueTotalRow.setVisibility(hasBlue ? View.VISIBLE : View.GONE);
            tvBlueWeight.setText(format(blueWeight));
            tvBluePure.setText(format(bluePure));

            if (blueWeight != 0) {
                double blueTouch = (bluePure / blueWeight) * 100;
                tvBlueTouch.setText(format(blueTouch));
            } else {
                tvBlueTouch.setText("0.000");
            }

            greenTotalRow.setVisibility(hasGreen ? View.VISIBLE : View.GONE);
            tvGreenWeight.setText(format(greenWeight));
            tvGreenPure.setText(format(greenPure));

            if (greenWeight != 0) {
                double greenTouch = (greenPure / greenWeight) * 100;
                tvGreenTouch.setText(format(greenTouch));
            } else {
                tvGreenTouch.setText("0.000");
            }

            orangeTotalRow.setVisibility(hasOrange ? View.VISIBLE : View.GONE);
            tvOrangeWeight.setText(format(orangeWeight));
            tvOrangePure.setText(format(orangePure));

            if (orangeWeight != 0) {
                double orangeTouch = (orangePure / orangeWeight) * 100;
                tvOrangeTouch.setText(format(orangeTouch));
            } else {
                tvOrangeTouch.setText("0.000");
            }
        }

        // =====================================================
        // UPDATE EXCHANGE DATA
        // =====================================================
        calculateExchangeData();

        // =====================================================
        // UPDATE WEIGHT & TOUCH TOTALS
        // =====================================================
        updateWTTotals();
    }

    // =========================================================
    // REFRESH ROW NUMBERS
    // =========================================================

    private void refreshRowNumbers() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).tvRowNumber.setText(String.format(Locale.US, "%02d", i + 1));
        }
    }

    // =========================================================
    // WEIGHT & TOUCH DETAILS METHODS
    // =========================================================

    // =========================================================
    // ADD WEIGHT & TOUCH ROW
    // =========================================================

    private WTRow addWTRow() {
        View view = LayoutInflater.from(this)
                .inflate(R.layout.item_wt_row, wtRowsContainer, false);

        WTRow row = new WTRow(view);
        wtRows.add(row);
        wtRowsContainer.addView(view);

        setupWTRow(row);
        refreshWTRowNumbers();
        updateWTTotals();

        return row;
    }

    // =========================================================
    // SETUP WEIGHT & TOUCH ROW
    // =========================================================

    private void setupWTRow(WTRow row) {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                calculateWTPure(row);
                updateWTTotals();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        row.etWeight.addTextChangedListener(watcher);
        row.etTouch.addTextChangedListener(watcher);

        row.btnDelete.setOnClickListener(v -> deleteWTRow(row));

        setupWTColorListeners(row);
    }

    // =========================================================
    // COLOR LISTENERS
    // =========================================================

    private void setupWTColorListeners(WTRow row) {
        row.cbColor1.setOnCheckedChangeListener((buttonView, checked) -> updateWTTotals());
        row.cbColor2.setOnCheckedChangeListener((buttonView, checked) -> updateWTTotals());
    }

    // =========================================================
    // DELETE WEIGHT & TOUCH ROW
    // =========================================================

    private void deleteWTRow(WTRow row) {
        wtRows.remove(row);
        wtRowsContainer.removeView(row.root);
        refreshWTRowNumbers();
        updateWTTotals();
    }

    // =========================================================
    // REMOVE ALL WEIGHT & TOUCH ROWS
    // =========================================================

    private void removeAllWTRows() {
        wtRows.clear();
        wtRowsContainer.removeAllViews();
        refreshWTRowNumbers();
        updateWTTotals();
        Toast.makeText(this, "All weight & touch rows removed", Toast.LENGTH_SHORT).show();
    }

    // =========================================================
    // CALCULATE WEIGHT & TOUCH ROW PURE
    // =========================================================

    private void calculateWTPure(WTRow row) {
        double weight = parse(row.etWeight.getText().toString());
        double touch = parse(row.etTouch.getText().toString());
        double pure = (weight * touch) / 100.0;
        row.tvPure.setText(format(pure));
    }

    // =========================================================
    // UPDATE WEIGHT & TOUCH TOTALS
    // =========================================================

    private void updateWTTotals() {
        double totalWeight = 0;
        double totalPure = 0;

        double redWeight = 0;
        double redPure = 0;

        double blueWeight = 0;
        double bluePure = 0;

        // Multi-Color = ALL rows that have at least one color selected
        double multiColorWeight = 0;
        double multiColorPure = 0;

        boolean hasRed = false;
        boolean hasBlue = false;
        boolean hasMultiColor = false;  // True if ANY row has BOTH colors selected

        for (WTRow row : wtRows) {
            double weight = parse(row.etWeight.getText().toString());
            double touch = parse(row.etTouch.getText().toString());
            double pure = (weight * touch) / 100.0;

            totalWeight += weight;
            totalPure += pure;

            boolean isRed = row.cbColor1.isChecked();
            boolean isBlue = row.cbColor2.isChecked();
            boolean hasAnyColor = isRed || isBlue;

            // =====================================================
            // CHECK IF BOTH COLORS ARE SELECTED IN THIS ROW
            // =====================================================
            if (isRed && isBlue) {
                hasMultiColor = true;  // This triggers Multi-Color display
            }

            // =====================================================
            // TRACK INDIVIDUAL COLORS
            // =====================================================
            if (isRed) {
                hasRed = true;
                redWeight += weight;
                redPure += pure;
            }

            if (isBlue) {
                hasBlue = true;
                blueWeight += weight;
                bluePure += pure;
            }

            // =====================================================
            // MULTI-COLOR = ALL rows with at least one color
            // =====================================================
            if (hasAnyColor) {
                multiColorWeight += weight;
                multiColorPure += pure;
            }
        }

        // =====================================================
        // ROW COUNT
        // =====================================================
        if (tvWtRowCount != null) {
            tvWtRowCount.setText(wtRows.size() + (wtRows.size() == 1 ? " Entry" : " Entries"));
        }

        // =====================================================
        // GRAND TOTAL
        // =====================================================
        tvWtTotalWeight.setText(format(totalWeight));
        tvWtTotalPure.setText(format(totalPure));

        if (totalWeight != 0) {
            double totalTouch = (totalPure / totalWeight) * 100;
            tvWtTotalTouch.setText(format(totalTouch));
        } else {
            tvWtTotalTouch.setText("0.000");
        }

        // =====================================================
        // DISPLAY LOGIC
        // =====================================================

        // CASE 1: ANY row has BOTH colors selected → Show MULTI-COLOR total
        if (hasMultiColor) {
            // Show MULTI-COLOR total (ALL rows with at least one color)
            wtBothColorsTotalRow.setVisibility(View.VISIBLE);
            tvWtBothColorsWeight.setText(format(multiColorWeight));
            tvWtBothColorsPure.setText(format(multiColorPure));

            if (multiColorWeight != 0) {
                double multiColorTouch = (multiColorPure / multiColorWeight) * 100;
                tvWtBothColorsTouch.setText(format(multiColorTouch));
            } else {
                tvWtBothColorsTouch.setText("0.000");
            }

            // Hide individual color totals
            wtRedTotalRow.setVisibility(View.GONE);
            wtBlueTotalRow.setVisibility(View.GONE);
        }
        // CASE 2: NO row has BOTH colors → Show individual totals
        else {
            // Hide MULTI-COLOR total
            wtBothColorsTotalRow.setVisibility(View.GONE);

            // Show RED total if any row has Red selected
            wtRedTotalRow.setVisibility(hasRed ? View.VISIBLE : View.GONE);
            tvWtRedWeight.setText(format(redWeight));
            tvWtRedPure.setText(format(redPure));

            if (redWeight != 0) {
                double redTouch = (redPure / redWeight) * 100;
                tvWtRedTouch.setText(format(redTouch));
            } else {
                tvWtRedTouch.setText("0.000");
            }

            // Show BLUE total if any row has Blue selected
            wtBlueTotalRow.setVisibility(hasBlue ? View.VISIBLE : View.GONE);
            tvWtBlueWeight.setText(format(blueWeight));
            tvWtBluePure.setText(format(bluePure));

            if (blueWeight != 0) {
                double blueTouch = (bluePure / blueWeight) * 100;
                tvWtBlueTouch.setText(format(blueTouch));
            } else {
                tvWtBlueTouch.setText("0.000");
            }
        }
    }

    // =========================================================
    // REFRESH WEIGHT & TOUCH ROW NUMBERS
    // =========================================================

    private void refreshWTRowNumbers() {
        for (int i = 0; i < wtRows.size(); i++) {
            wtRows.get(i).tvRowNumber.setText(String.format(Locale.US, "%02d", i + 1));
        }
    }

    // =========================================================
    // EDIT MODE
    // =========================================================

    private void checkEditMode() {
        Intent intent = getIntent();

        editMode = intent.getBooleanExtra("edit_mode", false);
        editKey = intent.getStringExtra("edit_key");
        editPosition = intent.getIntExtra("position", -1);

        if (!editMode) {
            return;
        }

        // -----------------------------------------------------
        // TITLE
        // -----------------------------------------------------
        tvTitle.setText("Edit Kacha");
        btnTopSave.setText("UPDATE");
        btnSave.setText("UPDATE KACHA");

        // -----------------------------------------------------
        // DATE
        // -----------------------------------------------------
        String date = intent.getStringExtra("date");
        if (date != null) {
            etDate.setText(date);
        }

        // -----------------------------------------------------
        // PARTY
        // -----------------------------------------------------
        String party = intent.getStringExtra("party");
        if (party != null) {
            etParty.setText(party);
        }

        // =====================================================
        // GOLD DATA - EDIT
        // =====================================================
        String nayamKacha = intent.getStringExtra("nayam_kacha");
        String nayamTouch = intent.getStringExtra("nayam_touch");
        String nayamPure = intent.getStringExtra("nayam_pure");
        String mattamKacha = intent.getStringExtra("mattam_kacha");
        String mattamTouch = intent.getStringExtra("mattam_touch");
        String mattamPure = intent.getStringExtra("mattam_pure");
        String requiredTouch = intent.getStringExtra("required_touch");

        if (nayamKacha != null) {
            etNayamKacha.setText(nayamKacha);
        }
        if (nayamTouch != null) {
            etNayamTouch.setText(nayamTouch);
        }
        if (mattamKacha != null) {
            etMattamKacha.setText(mattamKacha);
        }
        if (mattamTouch != null) {
            etMattamTouch.setText(mattamTouch);
        }
        if (requiredTouch != null) {
            etRequiredTouch.setText(requiredTouch);
        }
        if (nayamPure != null) {
            tvNayamPure.setText(nayamPure);
        }
        if (mattamPure != null) {
            tvMattamPure.setText(mattamPure);
        }

        // =====================================================
        // EXCHANGE DATA - EDIT
        // =====================================================
        String exchangeWeight = intent.getStringExtra("exchange_weight");
        String exchangeTouch = intent.getStringExtra("exchange_touch");
        String exchangePure = intent.getStringExtra("exchange_pure");
        String exchangeRequiredTouch = intent.getStringExtra("exchange_required_touch");
        String exchangeKacha = intent.getStringExtra("exchange_kacha");
        String exchangeTouchInput = intent.getStringExtra("exchange_touch_input");
        String exchangePureOutput = intent.getStringExtra("exchange_pure_output");

        // Exchange Reference values - for editing
        String refWeightAns = intent.getStringExtra("ref_weight_ans");
        String refTouchValue = intent.getStringExtra("ref_touch_value");
        String refPureValue = intent.getStringExtra("ref_pure_value");
        String refExchangeKacha = intent.getStringExtra("ref_exchange_kacha");
        String refExchangeTouch = intent.getStringExtra("ref_exchange_touch");
        String refExchangePure = intent.getStringExtra("ref_exchange_pure");
        String refTotalWeight = intent.getStringExtra("ref_total_weight");
        String refTotalTouchValue = intent.getStringExtra("ref_total_touch_value");
        String refTotalPureValue = intent.getStringExtra("ref_total_pure_value");

        if (exchangeWeight != null) {
            etExchangeWeight.setText(exchangeWeight);
        }
        if (exchangeTouch != null) {
            etExchangeTouch.setText(exchangeTouch);
        }
        if (exchangePure != null) {
            tvExchangePure.setText(exchangePure);
        }
        if (exchangeRequiredTouch != null) {
            etExchangeRequiredTouch.setText(exchangeRequiredTouch);
        }
        if (exchangeKacha != null) {
            tvExchangeKacha.setText(exchangeKacha);
        }
        if (exchangeTouchInput != null) {
            etExchangeTouchInput.setText(exchangeTouchInput);
        }
        if (exchangePureOutput != null) {
            tvExchangePureOutput.setText(exchangePureOutput);
        }

        // Restore reference values
        if (refWeightAns != null) tvRefWeightAns.setText(refWeightAns);
        if (refTouchValue != null) tvRefTouchValue.setText(refTouchValue);
        if (refPureValue != null) tvRefPureValue.setText(refPureValue);
        if (refExchangeKacha != null) tvRefExchangeKacha.setText(refExchangeKacha);
        if (refExchangeTouch != null) tvRefExchangeTouch.setText(refExchangeTouch);
        if (refExchangePure != null) tvRefExchangePure.setText(refExchangePure);
        if (refTotalWeight != null) tvRefTotalWeight.setText(refTotalWeight);
        if (refTotalTouchValue != null) tvRefTotalTouchValue.setText(refTotalTouchValue);
        if (refTotalPureValue != null) tvRefTotalPureValue.setText(refTotalPureValue);

        // =====================================================
        // SHOW REFERENCES IF DATA EXISTS
        // =====================================================
        boolean hasExchangeData = !TextUtils.isEmpty(exchangeWeight) ||
                !TextUtils.isEmpty(exchangeTouch) ||
                !TextUtils.isEmpty(exchangeRequiredTouch) ||
                !TextUtils.isEmpty(exchangeTouchInput);

        if (exchangeReferenceContainer != null) {
            exchangeReferenceContainer.setVisibility(hasExchangeData ? View.VISIBLE : View.GONE);
        }

        boolean hasGoldData = !TextUtils.isEmpty(nayamKacha) ||
                !TextUtils.isEmpty(nayamTouch) ||
                !TextUtils.isEmpty(mattamKacha) ||
                !TextUtils.isEmpty(mattamTouch);

        if (goldReferenceContainer != null) {
            goldReferenceContainer.setVisibility(hasGoldData ? View.VISIBLE : View.GONE);
        }

        // =====================================================
        // KACHA ROWS - EDIT
        // =====================================================
        ArrayList<String> weights = intent.getStringArrayListExtra("weights");
        ArrayList<String> touches = intent.getStringArrayListExtra("touches");
        ArrayList<String> formNos = intent.getStringArrayListExtra("form_nos");
        ArrayList<String> refineries = intent.getStringArrayListExtra("refineries");
        ArrayList<String> colors1 = intent.getStringArrayListExtra("color1");
        ArrayList<String> colors2 = intent.getStringArrayListExtra("color2");
        ArrayList<String> colors3 = intent.getStringArrayListExtra("color3");
        ArrayList<String> colors4 = intent.getStringArrayListExtra("color4");

        // =====================================================
        // WEIGHT & TOUCH DATA - EDIT
        // =====================================================
        ArrayList<String> wtWeights = intent.getStringArrayListExtra("wt_weights");
        ArrayList<String> wtTouches = intent.getStringArrayListExtra("wt_touches");
        ArrayList<String> wtPures = intent.getStringArrayListExtra("wt_pures");
        ArrayList<String> wtColor1 = intent.getStringArrayListExtra("wt_color1");
        ArrayList<String> wtColor2 = intent.getStringArrayListExtra("wt_color2");

        // =====================================================
        // LOAD WEIGHT & TOUCH ROWS
        // =====================================================
        if (wtWeights != null && !wtWeights.isEmpty()) {
            for (int i = 0; i < wtWeights.size(); i++) {
                WTRow row = addWTRow();
                row.etWeight.setText(safeGet(wtWeights, i));
                row.etTouch.setText(safeGet(wtTouches, i));
                row.cbColor1.setChecked(getBoolean(wtColor1, i));
                row.cbColor2.setChecked(getBoolean(wtColor2, i));
                calculateWTPure(row);
            }
            updateWTTotals();
        }

        // =====================================================
        // LOAD KACHA ROWS
        // =====================================================
        if (weights == null || weights.isEmpty()) {
            addRow();
            calculateGoldData();
            calculateExchangeData();
            return;
        }

        for (int i = 0; i < weights.size(); i++) {
            KachaRow row = addRow();

            row.etWeight.setText(safeGet(weights, i));
            row.etTouch.setText(safeGet(touches, i));
            row.etFormNo.setText(safeGet(formNos, i));
            row.etRefinery.setText(safeGet(refineries, i));

            row.cbColor1.setChecked(getBoolean(colors1, i));
            row.cbColor2.setChecked(getBoolean(colors2, i));
            row.cbColor3.setChecked(getBoolean(colors3, i));
            row.cbColor4.setChecked(getBoolean(colors4, i));

            calculatePure(row);
        }

        updateTotals();
        calculateGoldData();
        calculateExchangeData();
    }

    // =========================================================
    // SAVE TO FIREBASE
    // =========================================================

    private void saveKacha() {
        // =====================================================
        // BASIC INFORMATION
        // =====================================================
        String date = etDate.getText().toString().trim();
        String party = etParty.getText().toString().trim();

        // ONLY PARTY NAME IS REQUIRED
        if (TextUtils.isEmpty(party)) {
            etParty.setError("Party name is required");
            etParty.requestFocus();
            return;
        }

        // Date will use current date if empty
        if (TextUtils.isEmpty(date)) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
            date = sdf.format(calendar.getTime());
            etDate.setText(date);
        }

        // =====================================================
        // GOLD DATA
        // =====================================================
        String nayamKacha = etNayamKacha.getText().toString().trim();
        String nayamTouch = etNayamTouch.getText().toString().trim();
        String nayamPure = tvNayamPure.getText().toString().trim();
        String mattamKacha = etMattamKacha.getText().toString().trim();
        String mattamTouch = etMattamTouch.getText().toString().trim();
        String mattamPure = tvMattamPure.getText().toString().trim();
        String requiredTouch = etRequiredTouch.getText().toString().trim();

        // =====================================================
        // EXCHANGE DATA
        // =====================================================
        String exchangeWeight = etExchangeWeight.getText().toString().trim();
        String exchangeTouch = etExchangeTouch.getText().toString().trim();
        String exchangePure = tvExchangePure.getText().toString().trim();
        String exchangeRequiredTouch = etExchangeRequiredTouch.getText().toString().trim();
        String exchangeKacha = tvExchangeKacha.getText().toString().trim();
        String exchangeTouchInput = etExchangeTouchInput.getText().toString().trim();
        String exchangePureOutput = tvExchangePureOutput.getText().toString().trim();

        // Exchange Reference values
        String refWeightAns = tvRefWeightAns.getText().toString().trim();
        String refTouchValue = tvRefTouchValue.getText().toString().trim();
        String refPureValue = tvRefPureValue.getText().toString().trim();
        String refExchangeKacha = tvRefExchangeKacha.getText().toString().trim();
        String refExchangeTouch = tvRefExchangeTouch.getText().toString().trim();
        String refExchangePure = tvRefExchangePure.getText().toString().trim();
        String refTotalWeight = tvRefTotalWeight.getText().toString().trim();
        String refTotalTouchValue = tvRefTotalTouchValue.getText().toString().trim();
        String refTotalPureValue = tvRefTotalPureValue.getText().toString().trim();

        // =====================================================
        // ARRAYS
        // =====================================================
        ArrayList<String> weights = new ArrayList<>();
        ArrayList<String> touches = new ArrayList<>();
        ArrayList<String> pures = new ArrayList<>();
        ArrayList<String> formNos = new ArrayList<>();
        ArrayList<String> refineries = new ArrayList<>();
        ArrayList<String> color1 = new ArrayList<>();
        ArrayList<String> color2 = new ArrayList<>();
        ArrayList<String> color3 = new ArrayList<>();
        ArrayList<String> color4 = new ArrayList<>();

        // =====================================================
        // LOOP ROWS (All fields optional)
        // =====================================================
        for (KachaRow row : rows) {
            String weight = row.etWeight.getText().toString().trim();
            String touch = row.etTouch.getText().toString().trim();

            // Add data even if empty
            weights.add(weight);
            touches.add(touch);

            double weightValue = parse(weight);
            double touchValue = parse(touch);
            double pure = (weightValue * touchValue) / 100.0;
            pures.add(format(pure));

            formNos.add(row.etFormNo.getText().toString().trim());
            refineries.add(row.etRefinery.getText().toString().trim());
            color1.add(String.valueOf(row.cbColor1.isChecked()));
            color2.add(String.valueOf(row.cbColor2.isChecked()));
            color3.add(String.valueOf(row.cbColor3.isChecked()));
            color4.add(String.valueOf(row.cbColor4.isChecked()));
        }

        // =====================================================
        // WEIGHT & TOUCH DATA
        // =====================================================
        ArrayList<String> wtWeights = new ArrayList<>();
        ArrayList<String> wtTouches = new ArrayList<>();
        ArrayList<String> wtPures = new ArrayList<>();
        ArrayList<String> wtColor1 = new ArrayList<>();
        ArrayList<String> wtColor2 = new ArrayList<>();

        for (WTRow row : wtRows) {
            String weight = row.etWeight.getText().toString().trim();
            String touch = row.etTouch.getText().toString().trim();

            wtWeights.add(weight);
            wtTouches.add(touch);
            wtPures.add(row.tvPure.getText().toString().trim());
            wtColor1.add(String.valueOf(row.cbColor1.isChecked()));
            wtColor2.add(String.valueOf(row.cbColor2.isChecked()));
        }

        // =====================================================
        // CREATE DATA MAP
        // =====================================================
        Map<String, Object> kachaData = new HashMap<>();
        kachaData.put("date", date);
        kachaData.put("party", party);
        kachaData.put("timestamp", System.currentTimeMillis());

        // KACHA ROWS
        kachaData.put("weights", weights);
        kachaData.put("touches", touches);
        kachaData.put("pures", pures);
        kachaData.put("form_nos", formNos);
        kachaData.put("refineries", refineries);
        kachaData.put("color1", color1);
        kachaData.put("color2", color2);
        kachaData.put("color3", color3);
        kachaData.put("color4", color4);

        // GRAND TOTAL
        kachaData.put("total_weight", tvTotalWeight.getText().toString());
        kachaData.put("total_touch", tvTotalTouch.getText().toString());
        kachaData.put("total_pure", tvTotalPure.getText().toString());

        // GOLD DATA
        kachaData.put("nayam_kacha", nayamKacha);
        kachaData.put("nayam_touch", nayamTouch);
        kachaData.put("nayam_pure", nayamPure);
        kachaData.put("mattam_kacha", mattamKacha);
        kachaData.put("mattam_touch", mattamTouch);
        kachaData.put("mattam_pure", mattamPure);
        kachaData.put("required_touch", requiredTouch);

        // EXCHANGE DATA
        kachaData.put("exchange_weight", exchangeWeight);
        kachaData.put("exchange_touch", exchangeTouch);
        kachaData.put("exchange_pure", exchangePure);
        kachaData.put("exchange_required_touch", exchangeRequiredTouch);
        kachaData.put("exchange_kacha", exchangeKacha);
        kachaData.put("exchange_touch_input", exchangeTouchInput);
        kachaData.put("exchange_pure_output", exchangePureOutput);

        // EXCHANGE REFERENCE DATA
        kachaData.put("ref_weight_ans", refWeightAns);
        kachaData.put("ref_touch_value", refTouchValue);
        kachaData.put("ref_pure_value", refPureValue);
        kachaData.put("ref_exchange_kacha", refExchangeKacha);
        kachaData.put("ref_exchange_touch", refExchangeTouch);
        kachaData.put("ref_exchange_pure", refExchangePure);
        kachaData.put("ref_total_weight", refTotalWeight);
        kachaData.put("ref_total_touch_value", refTotalTouchValue);
        kachaData.put("ref_total_pure_value", refTotalPureValue);

        // WEIGHT & TOUCH DATA
        kachaData.put("wt_weights", wtWeights);
        kachaData.put("wt_touches", wtTouches);
        kachaData.put("wt_pures", wtPures);
        kachaData.put("wt_color1", wtColor1);
        kachaData.put("wt_color2", wtColor2);

        // =====================================================
        // SAVE TO FIREBASE
        // =====================================================

        Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).show();

        DatabaseReference kachaRef;

        if (editMode && !TextUtils.isEmpty(editKey)) {
            // UPDATE existing record
            kachaRef = databaseReference.child(editKey);
            kachaRef.updateChildren(kachaData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddKachaActivity.this,
                                "Kacha updated successfully",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddKachaActivity.this,
                                "Failed to update: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        } else {
            // CREATE new record
            kachaRef = databaseReference.push();
            kachaData.put("key", kachaRef.getKey());
            kachaRef.setValue(kachaData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(AddKachaActivity.this,
                                "Kacha saved successfully",
                                Toast.LENGTH_SHORT).show();

                        Intent result = new Intent();
                        result.putExtra("saved", true);
                        result.putExtra("key", kachaRef.getKey());
                        setResult(RESULT_OK, result);

                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(AddKachaActivity.this,
                                "Failed to save: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // =========================================================
    // PARSE
    // =========================================================

    private double parse(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    // =========================================================
    // FORMAT
    // =========================================================

    private String format(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    // =========================================================
    // SAFE GET
    // =========================================================

    private String safeGet(ArrayList<String> list, int position) {
        if (list == null || position < 0 || position >= list.size()) {
            return "";
        }
        String value = list.get(position);
        return value == null ? "" : value;
    }

    // =========================================================
    // GET BOOLEAN
    // =========================================================

    private boolean getBoolean(ArrayList<String> list, int position) {
        return Boolean.parseBoolean(safeGet(list, position));
    }

    // =========================================================
    // KACHA ROW MODEL
    // =========================================================

    private static class KachaRow {
        View root;
        TextView tvRowNumber;
        TextView tvPure;
        TextView btnDelete;

        CheckBox cbColor1;
        CheckBox cbColor2;
        CheckBox cbColor3;
        CheckBox cbColor4;

        EditText etWeight;
        EditText etTouch;
        EditText etFormNo;
        EditText etRefinery;

        KachaRow(View root) {
            this.root = root;

            // -------------------------------------------------
            // TEXT VIEWS
            // -------------------------------------------------
            tvRowNumber = root.findViewById(R.id.tv_row_number);
            tvPure = root.findViewById(R.id.tv_row_pure);
            btnDelete = root.findViewById(R.id.btn_delete_row);

            // -------------------------------------------------
            // COLORS
            // -------------------------------------------------
            cbColor1 = root.findViewById(R.id.cb_color_1);
            cbColor2 = root.findViewById(R.id.cb_color_2);
            cbColor3 = root.findViewById(R.id.cb_color_3);
            cbColor4 = root.findViewById(R.id.cb_color_4);

            // -------------------------------------------------
            // INPUTS
            // -------------------------------------------------
            etWeight = root.findViewById(R.id.et_row_weight);
            etTouch = root.findViewById(R.id.et_row_touch);
            etFormNo = root.findViewById(R.id.et_row_form_no);
            etRefinery = root.findViewById(R.id.et_row_refinery);
        }
    }

    // =========================================================
    // WEIGHT & TOUCH ROW MODEL
    // =========================================================

    private static class WTRow {
        View root;
        TextView tvRowNumber;
        TextView tvPure;
        TextView btnDelete;

        CheckBox cbColor1;  // Red
        CheckBox cbColor2;  // Blue

        EditText etWeight;
        EditText etTouch;

        WTRow(View root) {
            this.root = root;

            // Text Views
            tvRowNumber = root.findViewById(R.id.tv_wt_row_number);
            tvPure = root.findViewById(R.id.wt_tv_pure);
            btnDelete = root.findViewById(R.id.wt_btn_delete_row);

            // Colors
            cbColor1 = root.findViewById(R.id.wt_cb_color_1);
            cbColor2 = root.findViewById(R.id.wt_cb_color_2);

            // Inputs
            etWeight = root.findViewById(R.id.wt_et_weight);
            etTouch = root.findViewById(R.id.wt_et_touch);
        }
    }
}
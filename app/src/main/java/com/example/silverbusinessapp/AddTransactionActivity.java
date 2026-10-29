package com.example.silverbusinessapp;

import static java.lang.Double.parseDouble;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddTransactionActivity extends AppCompatActivity {

    private EditText etTransactionName, etNote, etDate, etParticulars;
    private EditText etDebitWeight, etDebitTouch, etDebitWastage;
    private EditText etCreditWeight, etCreditTouch, etCreditWastage;
    private TextView tvDebitPure, tvCreditPure, tvTotalDebitWeight, tvTotalCreditWeight, tvRemaining;
    private TextView tvClosedLabel;
    private Spinner spinnerStatus;
    private LinearLayout containerDynamicRows, layoutCashDetails;
    private CheckBox showCashDetails;
    private String userEmail, customerId, customerName, userUid;
    private DatabaseReference transactionRef;
    private double debitPure = 0, creditPure = 0;

    private boolean shouldShowClosedLabel = false;

    private LinearLayout closedLabelDefaultParent;
    private int closedLabelDefaultIndex;
    private LinearLayout cashRemoveButtonContainer;

    private EditText etCashDateDebit, etDebitCashAmount, etCashNameCredit, etCreditCashAmount, etDebitRateCutting, etCreditRateCutting;
    private TextView tvDebitCashPure, tvCreditCashPure;
    private Button btnRemoveCashRow;

    private LinearLayout cashTotalsContainer;
    private TextView tvCashTotalDP, tvCashTotalCP, tvCashResult;

    private LinearLayout dynamicCashRowsContainer;
    private List<View> dynamicCashRows = new ArrayList<>();
    private List<View> dynamicKachaRows = new ArrayList<>();

    private static final int INPUT_HEIGHT_DP = 42;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        userEmail = getIntent().getStringExtra("userEmail");
        customerId = getIntent().getStringExtra("customerId");
        customerName = getIntent().getStringExtra("customerName");
        userUid = getIntent().getStringExtra("userUid");

        if (customerName == null) {
            customerName = "Customer";
        }

        initializeViews();
        setupFirebase();
        setupListeners();
    }

    private double safeParseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private void updateClosedLabelPlacement() {
        if (tvClosedLabel == null) return;

        boolean cashChecked = showCashDetails != null && showCashDetails.isChecked();

        ViewParent currentParent = tvClosedLabel.getParent();
        if (currentParent instanceof LinearLayout) {
            ((LinearLayout) currentParent).removeView(tvClosedLabel);
        }

        if (cashChecked) {
            if (cashRemoveButtonContainer != null) {
                cashRemoveButtonContainer.addView(tvClosedLabel);
            }
        } else {
            if (closedLabelDefaultParent != null) {
                int index = Math.min(closedLabelDefaultIndex, closedLabelDefaultParent.getChildCount());
                closedLabelDefaultParent.addView(tvClosedLabel, index);
            }
        }

        tvClosedLabel.setVisibility(shouldShowClosedLabel ? View.VISIBLE : View.GONE);
    }

    private void refreshClosedLabelVisibility() {
        String selectedStatus = (spinnerStatus != null && spinnerStatus.getSelectedItem() != null)
                ? spinnerStatus.getSelectedItem().toString() : "";
        shouldShowClosedLabel = "Close".equalsIgnoreCase(selectedStatus);
        updateClosedLabelPlacement();
    }

    private void initializeViews() {
        etTransactionName = findViewById(R.id.et_transaction_name);
        etNote = findViewById(R.id.et_note);
        etDate = findViewById(R.id.et_date);
        etParticulars = findViewById(R.id.et_particulars);
        etDebitWeight = findViewById(R.id.et_debit_weight);
        etDebitTouch = findViewById(R.id.et_debit_touch);
        etDebitWastage = findViewById(R.id.et_debit_wastage);
        etCreditWeight = findViewById(R.id.et_credit_weight);
        etCreditTouch = findViewById(R.id.et_credit_touch);
        etCreditWastage = findViewById(R.id.et_credit_wastage);
        tvDebitPure = findViewById(R.id.tv_debit_pure);
        tvCreditPure = findViewById(R.id.tv_credit_pure);
        tvTotalDebitWeight = findViewById(R.id.tv_total_debit_weight);
        tvTotalCreditWeight = findViewById(R.id.tv_total_credit_weight);
        tvRemaining = findViewById(R.id.tv_remaining);
        showCashDetails = findViewById(R.id.show_cash_details);
        layoutCashDetails = findViewById(R.id.layoutCashDetails);

        etCashDateDebit = findViewById(R.id.et_cash_date_debit);
        etDebitCashAmount = findViewById(R.id.et_debit_cash_amount);
        etCashNameCredit = findViewById(R.id.et_cash_name_credit);
        etCreditCashAmount = findViewById(R.id.et_credit_cash_amount);
        tvDebitCashPure = findViewById(R.id.tv_debit_cash_pure);
        tvCreditCashPure = findViewById(R.id.tv_credit_cash_pure);
        btnRemoveCashRow = findViewById(R.id.btnRemoveCashRow);
        etDebitRateCutting = findViewById(R.id.et_debit_rate_cutting);
        etCreditRateCutting = findViewById(R.id.et_credit_rate_cutting);

        styleEditText(etCashDateDebit);
        styleEditText(etDebitCashAmount);
        styleEditText(etCashNameCredit);
        styleEditText(etCreditCashAmount);
        styleEditText(etDebitRateCutting);
        styleEditText(etCreditRateCutting);

        // Dynamic Weight, Cash and Kacha rows share one container so the user-selected
        // order is preserved visually. Static cash fields remain in the legacy cash card.
        createCashTotalsSection();

        if (btnRemoveCashRow.getParent() != null) {
            ((LinearLayout) btnRemoveCashRow.getParent()).removeView(btnRemoveCashRow);
        }

        LinearLayout buttonContainer = new LinearLayout(this);
        buttonContainer.setOrientation(LinearLayout.VERTICAL);
        buttonContainer.setPadding(0, 16, 0, 16);
        LinearLayout.LayoutParams buttonContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonContainerParams.setMargins(0, 8, 0, 8);
        buttonContainer.setLayoutParams(buttonContainerParams);
        buttonContainer.addView(btnRemoveCashRow);
        layoutCashDetails.addView(buttonContainer);

        cashRemoveButtonContainer = buttonContainer;

        spinnerStatus = findViewById(R.id.spinner_status);
        containerDynamicRows = findViewById(R.id.container_dynamic_rows);
        dynamicCashRowsContainer = containerDynamicRows;

        tvClosedLabel = new TextView(this);
        tvClosedLabel.setText("Closed");
        tvClosedLabel.setTextColor(Color.parseColor("#DC2626"));
        tvClosedLabel.setTypeface(null, Typeface.BOLD);
        tvClosedLabel.setTextSize(15);
        tvClosedLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams closedLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closedLabelParams.setMargins(0, 12, 0, 0);
        tvClosedLabel.setLayoutParams(closedLabelParams);
        tvClosedLabel.setVisibility(View.GONE);

        ViewParent remainingParent = tvRemaining.getParent();
        if (remainingParent instanceof LinearLayout) {
            LinearLayout parentLayout = (LinearLayout) remainingParent;
            int remainingIndex = parentLayout.indexOfChild(tvRemaining);
            parentLayout.addView(tvClosedLabel, remainingIndex + 1);
            closedLabelDefaultParent = parentLayout;
            closedLabelDefaultIndex = remainingIndex + 1;
        }

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshClosedLabelVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        showCashDetails.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                for (View cashRow : dynamicCashRows) {
                    cashRow.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                }
                if (isChecked) {
                    layoutCashDetails.setVisibility(View.VISIBLE);
                    tvTotalDebitWeight.setVisibility(View.GONE);
                    tvTotalCreditWeight.setVisibility(View.GONE);
                    tvRemaining.setVisibility(View.GONE);
                    if (cashTotalsContainer != null) {
                        cashTotalsContainer.setVisibility(View.VISIBLE);
                        calculateCombinedTotals();
                    }
                } else {
                    layoutCashDetails.setVisibility(View.GONE);
                    tvTotalDebitWeight.setVisibility(View.VISIBLE);
                    tvTotalCreditWeight.setVisibility(View.VISIBLE);
                    tvRemaining.setVisibility(View.VISIBLE);
                    if (cashTotalsContainer != null) {
                        cashTotalsContainer.setVisibility(View.GONE);
                    }
                }
                updateClosedLabelPlacement();
            }
        });

        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Open", "Close"});
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);
    }

    private void styleEditText(EditText editText) {
        if (editText != null) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) editText.getLayoutParams();
            if (params == null) {
                params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(INPUT_HEIGHT_DP)
                );
            } else {
                params.height = dpToPx(INPUT_HEIGHT_DP);
                params.width = LinearLayout.LayoutParams.MATCH_PARENT;
            }
            params.setMargins(0, 10, 0, 0);
            editText.setLayoutParams(params);
            editText.setTextSize(13);
            editText.setTextColor(Color.parseColor("#000000"));
            editText.setHintTextColor(Color.parseColor("#9E9E9E"));
            editText.setBackgroundResource(R.drawable.edit_text_bg);
            editText.setPadding(20, 8, 10, 8);
            editText.setSingleLine(true);
        }
    }

    private void calculateCombinedTotals() {
        double transactionDebitPure = safeParseDouble(tvDebitPure.getText().toString().replaceAll("[^0-9.-]", ""));
        double transactionCreditPure = safeParseDouble(tvCreditPure.getText().toString().replaceAll("[^0-9.-]", ""));

        double dynamicTransactionDebitPure = 0;
        double dynamicTransactionCreditPure = 0;

        for (int i = 0; i < containerDynamicRows.getChildCount(); i++) {
            View wrapperView = containerDynamicRows.getChildAt(i);
            if (dynamicCashRows.contains(wrapperView) || dynamicKachaRows.contains(wrapperView)) continue;
            if (!(wrapperView instanceof LinearLayout)) continue;

            LinearLayout wrapper = (LinearLayout) wrapperView;
            if (wrapper.getChildCount() == 0) continue;

            View rowView = wrapper.getChildAt(0);
            if (!(rowView instanceof LinearLayout)) continue;

            LinearLayout row = (LinearLayout) rowView;
            if (row.getChildCount() < 2) continue;

            LinearLayout left = (LinearLayout) row.getChildAt(0);
            TextView tvDynamicDebitPure = (TextView) left.getChildAt(9);
            double dynamicDebitPure = safeParseDouble(tvDynamicDebitPure.getText().toString().replaceAll("[^0-9.-]", ""));
            dynamicTransactionDebitPure += dynamicDebitPure;

            LinearLayout right = (LinearLayout) row.getChildAt(1);
            TextView tvDynamicCreditPure = (TextView) right.getChildAt(9);
            double dynamicCreditPure = safeParseDouble(tvDynamicCreditPure.getText().toString().replaceAll("[^0-9.-]", ""));
            dynamicTransactionCreditPure += dynamicCreditPure;
        }

        double totalTransactionDebitPure = transactionDebitPure + dynamicTransactionDebitPure;
        double totalTransactionCreditPure = transactionCreditPure + dynamicTransactionCreditPure;

        double cashDebitPureTotal = 0;
        double cashCreditPureTotal = 0;

        // Static cash row calculations - using 1000
        double staticDebit = safeParseDouble(etDebitCashAmount.getText().toString());
        double staticDebitRate = safeParseDouble(etDebitRateCutting.getText().toString());
        double staticCredit = safeParseDouble(etCreditCashAmount.getText().toString());
        double staticCreditRate = safeParseDouble(etCreditRateCutting.getText().toString());

        double staticPureDebitValue = (staticDebitRate != 0) ? (staticDebit / staticDebitRate) * 1000.0 : 0;
        double staticPureCreditValue = (staticCreditRate != 0) ? (staticCredit / staticCreditRate) * 1000.0 : 0;

        tvDebitCashPure.setText(String.format(Locale.getDefault(), "%.2f", staticPureDebitValue));
        tvCreditCashPure.setText(String.format(Locale.getDefault(), "%.2f", staticPureCreditValue));

        cashDebitPureTotal += staticPureDebitValue;
        cashCreditPureTotal += staticPureCreditValue;

        for (View view : dynamicCashRows) {
            if (view.getTag() != null) {
                Object[] tags = (Object[]) view.getTag();
                TextView tvPureDebit = (TextView) tags[6];
                TextView tvPureCredit = (TextView) tags[7];

                double pureDebit = safeParseDouble(tvPureDebit.getText().toString());
                double pureCredit = safeParseDouble(tvPureCredit.getText().toString());

                cashDebitPureTotal += pureDebit;
                cashCreditPureTotal += pureCredit;
            }
        }

        double totalDebitPure = totalTransactionDebitPure + cashDebitPureTotal;
        double totalCreditPure = totalTransactionCreditPure + cashCreditPureTotal;
        double difference = totalDebitPure - totalCreditPure;

        if (tvCashTotalDP != null) {
            tvCashTotalDP.setText("Total DP: " + String.format("%.2f", totalDebitPure));
            tvCashTotalDP.setTextColor(Color.parseColor("#059669"));
        }

        if (tvCashTotalCP != null) {
            tvCashTotalCP.setText("Total CP: " + String.format("%.2f", totalCreditPure));
            tvCashTotalCP.setTextColor(Color.parseColor("#DC2626"));
        }

        if (tvCashResult != null) {
            if (difference > 0) {
                tvCashResult.setText("Balance Pure: " + String.format("%.2f", difference));
                tvCashResult.setTextColor(Color.parseColor("#00ab41"));
                tvCashResult.setGravity(Gravity.START);
            } else if (difference < 0) {
                tvCashResult.setText("Ex Pure: " + String.format("%.2f", Math.abs(difference)));
                tvCashResult.setTextColor(Color.parseColor("#FF0000"));
                tvCashResult.setGravity(Gravity.START);
            } else {
                tvCashResult.setText("Balance: 0.00");
                tvCashResult.setTextColor(Color.parseColor("#000000"));
                tvCashResult.setGravity(Gravity.START);
            }
        }
    }

    private void createCashTotalsSection() {
        cashTotalsContainer = new LinearLayout(this);
        cashTotalsContainer.setOrientation(LinearLayout.VERTICAL);
        cashTotalsContainer.setPadding(16, 16, 16, 16);
        cashTotalsContainer.setBackgroundColor(Color.parseColor("#F8FAFC"));

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 12, 0, 0);
        cashTotalsContainer.setLayoutParams(containerParams);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        dividerParams.setMargins(0, 0, 0, 12);
        divider.setLayoutParams(dividerParams);
        cashTotalsContainer.addView(divider);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setWeightSum(2);

        tvCashTotalDP = new TextView(this);
        tvCashTotalDP.setText("Total DP: 0.00");
        tvCashTotalDP.setTextColor(Color.parseColor("#059669"));
        tvCashTotalDP.setTextSize(13);
        tvCashTotalDP.setTypeface(null, Typeface.BOLD);
        tvCashTotalDP.setGravity(Gravity.START);
        tvCashTotalDP.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        tvCashTotalCP = new TextView(this);
        tvCashTotalCP.setText("Total CP: 0.00");
        tvCashTotalCP.setTextColor(Color.parseColor("#DC2626"));
        tvCashTotalCP.setTextSize(13);
        tvCashTotalCP.setTypeface(null, Typeface.BOLD);
        tvCashTotalCP.setGravity(Gravity.END);
        tvCashTotalCP.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row1.addView(tvCashTotalDP);
        row1.addView(tvCashTotalCP);
        cashTotalsContainer.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, 8, 0, 0);

        tvCashResult = new TextView(this);
        tvCashResult.setText("Balance: 0.00");
        tvCashResult.setTextColor(Color.parseColor("#000000"));
        tvCashResult.setTextSize(13);
        tvCashResult.setTypeface(null, Typeface.BOLD);
        tvCashResult.setGravity(Gravity.START);
        tvCashResult.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        row2.addView(tvCashResult);
        cashTotalsContainer.addView(row2);

        cashTotalsContainer.setVisibility(View.GONE);
        layoutCashDetails.addView(cashTotalsContainer);
    }

    private void setupFirebase() {
        if (userUid == null) {
            userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        transactionRef = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(userUid)
                .child("customers")
                .child(customerId)
                .child("transactions")
                .push();
    }

    private void setupListeners() {
        Runnable updateCalculations = new Runnable() {
            @Override
            public void run() {
                calculateAndUpdateValues();
                calculateTotalsForAllRows();
                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
            }
        };

        TextWatcher transactionWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateCalculations.run();
            }
        };

        etDebitWeight.addTextChangedListener(transactionWatcher);
        etDebitTouch.addTextChangedListener(transactionWatcher);
        etDebitWastage.addTextChangedListener(transactionWatcher);
        etCreditWeight.addTextChangedListener(transactionWatcher);
        etCreditTouch.addTextChangedListener(transactionWatcher);
        etCreditWastage.addTextChangedListener(transactionWatcher);

        View.OnFocusChangeListener calcListener = (v, hasFocus) -> {
            if (!hasFocus) {
                updateCalculations.run();
            }
        };

        etDebitWeight.setOnFocusChangeListener(calcListener);
        etDebitTouch.setOnFocusChangeListener(calcListener);
        etDebitWastage.setOnFocusChangeListener(calcListener);
        etCreditWeight.setOnFocusChangeListener(calcListener);
        etCreditTouch.setOnFocusChangeListener(calcListener);
        etCreditWastage.setOnFocusChangeListener(calcListener);

        TextWatcher cashWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
            }
        };

        etDebitCashAmount.addTextChangedListener(cashWatcher);
        etCreditCashAmount.addTextChangedListener(cashWatcher);
        etDebitRateCutting.addTextChangedListener(cashWatcher);
        etCreditRateCutting.addTextChangedListener(cashWatcher);

        Button fabAddRow = findViewById(R.id.fab_add_row);
        fabAddRow.setText("+");
        fabAddRow.setOnClickListener(view -> showAddRowMenu());

        Button btnSave = findViewById(R.id.btn_save_transaction);
        btnSave.setOnClickListener(v -> saveTransaction());

        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> openDatePicker(etDate));

        etCashDateDebit.setFocusable(false);
        etCashDateDebit.setClickable(true);
        etCashDateDebit.setOnClickListener(v -> openDatePicker(etCashDateDebit));

        btnRemoveCashRow.setOnClickListener(v -> {
            if (!dynamicCashRows.isEmpty()) {
                View lastRow = dynamicCashRows.remove(dynamicCashRows.size() - 1);
                dynamicCashRowsContainer.removeView(lastRow);
                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
                Toast.makeText(this, "Last cash row removed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No dynamic cash rows to remove", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddRowMenu() {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(16);
        menu.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Add Row");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#4F46E5"));
        title.setPadding(0, 0, 0, dpToPx(8));
        menu.addView(title);

        CheckBox cashEnable = new CheckBox(this);
        cashEnable.setText("Enable Cash Rows");
        cashEnable.setTextSize(14);
        cashEnable.setChecked(showCashDetails.isChecked());
        menu.addView(cashEnable);

        TextView weightOption = createMenuOption("＋  Weight Row");
        TextView cashOption = createMenuOption("＋  Cash Row");
        TextView kachaOption = createMenuOption("＋  Kacha Calculation");
        menu.addView(weightOption);
        menu.addView(cashOption);
        menu.addView(kachaOption);
        cashOption.setEnabled(cashEnable.isChecked());
        cashOption.setAlpha(cashEnable.isChecked() ? 1f : 0.4f);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(menu).create();
        cashEnable.setOnCheckedChangeListener((button, checked) -> {
            showCashDetails.setChecked(checked);
            cashOption.setEnabled(checked);
            cashOption.setAlpha(checked ? 1f : 0.4f);
        });
        weightOption.setOnClickListener(v -> { addDynamicTransactionRow(); dialog.dismiss(); });
        cashOption.setOnClickListener(v -> {
            if (!showCashDetails.isChecked()) {
                Toast.makeText(this, "Enable Cash Rows first", Toast.LENGTH_SHORT).show();
                return;
            }
            addDynamicCashRow(); dialog.dismiss();
        });
        kachaOption.setOnClickListener(v -> { addDynamicKachaRow(); dialog.dismiss(); });
        dialog.show();
    }

    private TextView createMenuOption(String text) {
        TextView option = new TextView(this);
        option.setText(text);
        option.setTextSize(15);
        option.setTextColor(Color.parseColor("#1F2937"));
        option.setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14));
        option.setBackgroundResource(android.R.drawable.list_selector_background);
        return option;
    }

    private void addDynamicKachaRow() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        card.setBackgroundColor(Color.WHITE);
        card.setElevation(dpToPx(2));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cp);

        TextView heading = new TextView(this);
        heading.setText("KACHA CALCULATION");
        heading.setTextColor(Color.parseColor("#6D28D9"));
        heading.setTextSize(14);
        heading.setTypeface(null, Typeface.BOLD);
        card.addView(heading);

        EditText date = createEditText("Date (dd-mm-yyyy)", INPUT_HEIGHT_DP);
        date.setFocusable(false); date.setClickable(true);
        date.setOnClickListener(v -> openDatePicker(date));
        EditText name = createEditText("Name / Particulars", INPUT_HEIGHT_DP);
        card.addView(createLabel("Date", Color.DKGRAY)); card.addView(date);
        card.addView(createLabel("Name / Particulars", Color.DKGRAY)); card.addView(name);

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout debit = new LinearLayout(this); debit.setOrientation(LinearLayout.VERTICAL);
        LinearLayout credit = new LinearLayout(this); credit.setOrientation(LinearLayout.VERTICAL);
        debit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        credit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        debit.setPadding(0, 0, dpToPx(6), 0); credit.setPadding(dpToPx(6), 0, 0, 0);
        String[] labels = {"Kacha Pure", "Rate Cutting", "Cash", "Final Kacha Pure"};
        EditText[] debitFields = new EditText[4];
        EditText[] creditFields = new EditText[4];
        for (int i = 0; i < 4; i++) {
            debit.addView(createLabel("Debit " + labels[i], Color.parseColor("#059669")));
            debitFields[i] = createNumberEditText("0.00", INPUT_HEIGHT_DP);
            debit.addView(debitFields[i]);
            credit.addView(createLabel("Credit " + labels[i], Color.parseColor("#DC2626")));
            creditFields[i] = createNumberEditText("0.00", INPUT_HEIGHT_DP);
            credit.addView(creditFields[i]);
        }
        columns.addView(debit); columns.addView(credit); card.addView(columns);
        card.setTag(new Object[]{date, name, debitFields[0], creditFields[0], debitFields[1], creditFields[1], debitFields[2], creditFields[2], debitFields[3], creditFields[3]});
        setupSwipeGesture(card, true);
        containerDynamicRows.addView(card);
        dynamicKachaRows.add(card);
    }

    private void addDynamicTransactionRow() {
        LinearLayout rowWrapper = new LinearLayout(this);
        rowWrapper.setOrientation(LinearLayout.VERTICAL);
        rowWrapper.setBackgroundColor(Color.WHITE);
        rowWrapper.setElevation(2f);

        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, 0, 0, 12);
        rowWrapper.setLayoutParams(wrapperParams);
        rowWrapper.setPadding(0, 16, 0, 16);
        rowWrapper.setTag("WEIGHT_ROW");

        setupSwipeGesture(rowWrapper, true);
        rowWrapper.setClickable(true);
        rowWrapper.setFocusable(true);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
        );
        dividerParams.setMargins(0, 30, 0, 12);
        divider.setLayoutParams(dividerParams);

        LinearLayout parentRow = new LinearLayout(this);
        parentRow.setOrientation(LinearLayout.HORIZONTAL);
        parentRow.setWeightSum(2);
        parentRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        leftCol.setPadding(0, 0, 15, 0);

        TextView debitHeader = createSectionHeader("DEBIT", Color.parseColor("#059669"), Color.parseColor("#F0FFF4"));
        leftCol.addView(debitHeader);

        leftCol.addView(createLabel("Date", Color.parseColor("#4A5568")));
        EditText etDate = createEditText("dd-mm-yyyy", INPUT_HEIGHT_DP);
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> openDatePicker(etDate));
        leftCol.addView(etDate);

        leftCol.addView(createLabel("Debit Weight", Color.parseColor("#059669")));
        EditText etDebitWeight = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        leftCol.addView(etDebitWeight);

        leftCol.addView(createLabel("Touch", Color.parseColor("#059669")));
        EditText etDebitTouch = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        leftCol.addView(etDebitTouch);

        leftCol.addView(createLabel("Wastage", Color.parseColor("#059669")));
        EditText etDebitWastage = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        leftCol.addView(etDebitWastage);

        TextView tvDebitPure = createPureTextView("Debit Pure: 0.00", Color.parseColor("#059669"), Color.parseColor("#F0FFF4"));
        leftCol.addView(tvDebitPure);

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rightCol.setPadding(15, 0, 0, 0);

        TextView creditHeader = createSectionHeader("CREDIT", Color.parseColor("#DC2626"), Color.parseColor("#FEF2F2"));
        rightCol.addView(creditHeader);

        rightCol.addView(createLabel("Particulars", Color.parseColor("#4A5568")));
        EditText etParticulars = createEditText("Enter particulars", INPUT_HEIGHT_DP);
        rightCol.addView(etParticulars);

        rightCol.addView(createLabel("Credit Weight", Color.parseColor("#DC2626")));
        EditText etCreditWeight = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        rightCol.addView(etCreditWeight);

        rightCol.addView(createLabel("Touch", Color.parseColor("#DC2626")));
        EditText etCreditTouch = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        rightCol.addView(etCreditTouch);

        rightCol.addView(createLabel("Wastage", Color.parseColor("#DC2626")));
        EditText etCreditWastage = createNumberEditText("0.00", INPUT_HEIGHT_DP);
        rightCol.addView(etCreditWastage);

        TextView tvCreditPure = createPureTextView("Credit Pure: 0.00", Color.parseColor("#DC2626"), Color.parseColor("#FEF2F2"));
        rightCol.addView(tvCreditPure);

        parentRow.addView(leftCol);
        parentRow.addView(rightCol);
        rowWrapper.addView(parentRow);
        rowWrapper.addView(divider);

        containerDynamicRows.addView(rowWrapper);

        setupCalculationListeners(etDebitWeight, etDebitTouch, etDebitWastage, etCreditWeight,
                etCreditTouch, etCreditWastage, tvDebitPure, tvCreditPure);

        if (showCashDetails.isChecked()) {
            calculateCombinedTotals();
        }
    }

    private void addDynamicCashRow() {
        LinearLayout rowContainer = new LinearLayout(this);
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        rowContainer.setBackgroundColor(Color.WHITE);
        rowContainer.setElevation(2f);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.setMargins(0, 0, 0, 12);
        rowContainer.setLayoutParams(containerParams);
        rowContainer.setPadding(0, 16, 0, 16);

        setupSwipeGesture(rowContainer, false);

        LinearLayout parentRow = new LinearLayout(this);
        parentRow.setOrientation(LinearLayout.HORIZONTAL);
        parentRow.setWeightSum(2);
        parentRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        leftCol.setPadding(0, 0, 15, 0);

        TextView debitHeader = new TextView(this);
        debitHeader.setText("DEBIT CASH");
        debitHeader.setTextColor(Color.parseColor("#059669"));
        debitHeader.setTypeface(null, Typeface.BOLD);
        debitHeader.setGravity(Gravity.CENTER);
        debitHeader.setBackgroundColor(Color.parseColor("#F0FFF4"));
        debitHeader.setPadding(8, 12, 8, 12);
        leftCol.addView(debitHeader);

        TextView dateLabel = new TextView(this);
        dateLabel.setText("Date");
        dateLabel.setTextColor(Color.parseColor("#4A5568"));
        dateLabel.setTextSize(12);
        dateLabel.setTypeface(null, Typeface.BOLD);
        dateLabel.setPadding(0, 16, 0, 4);
        leftCol.addView(dateLabel);

        EditText etDate = new EditText(this);
        etDate.setHint("dd-mm-yyyy");
        etDate.setInputType(InputType.TYPE_CLASS_DATETIME);
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> openDatePicker(etDate));
        styleEditText(etDate);
        leftCol.addView(etDate);

        TextView debitCashLabel = new TextView(this);
        debitCashLabel.setText("Debit Cash");
        debitCashLabel.setTextColor(Color.parseColor("#059669"));
        debitCashLabel.setTextSize(12);
        debitCashLabel.setTypeface(null, Typeface.BOLD);
        debitCashLabel.setPadding(0, 16, 0, 4);
        leftCol.addView(debitCashLabel);

        EditText etDebitCash = new EditText(this);
        etDebitCash.setHint("0.00");
        etDebitCash.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleEditText(etDebitCash);
        leftCol.addView(etDebitCash);

        TextView debitRateLabel = new TextView(this);
        debitRateLabel.setText("Rate Cutting");
        debitRateLabel.setTextColor(Color.parseColor("#059669"));
        debitRateLabel.setTextSize(12);
        debitRateLabel.setTypeface(null, Typeface.BOLD);
        debitRateLabel.setPadding(0, 16, 0, 4);
        leftCol.addView(debitRateLabel);

        EditText etDebitRate = new EditText(this);
        etDebitRate.setHint("0.00");
        etDebitRate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleEditText(etDebitRate);
        leftCol.addView(etDebitRate);

        TextView debitPureLabel = new TextView(this);
        debitPureLabel.setText("Pure");
        debitPureLabel.setTextColor(Color.parseColor("#059669"));
        debitPureLabel.setTextSize(12);
        debitPureLabel.setTypeface(null, Typeface.BOLD);
        debitPureLabel.setPadding(0, 16, 0, 4);
        leftCol.addView(debitPureLabel);

        TextView tvPureDebit = new TextView(this);
        tvPureDebit.setText("0.00");
        tvPureDebit.setBackgroundColor(Color.parseColor("#F0FFF4"));
        tvPureDebit.setGravity(Gravity.CENTER);
        tvPureDebit.setPadding(10, 12, 10, 12);
        tvPureDebit.setTextSize(13);
        tvPureDebit.setTypeface(null, Typeface.BOLD);
        tvPureDebit.setTextColor(Color.parseColor("#059669"));
        LinearLayout.LayoutParams pureParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(INPUT_HEIGHT_DP)
        );
        pureParams.setMargins(0, 10, 0, 0);
        tvPureDebit.setLayoutParams(pureParams);
        leftCol.addView(tvPureDebit);

        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        rightCol.setPadding(15, 0, 0, 0);

        TextView creditHeader = new TextView(this);
        creditHeader.setText("CREDIT CASH");
        creditHeader.setTextColor(Color.parseColor("#DC2626"));
        creditHeader.setTextSize(13);
        creditHeader.setTypeface(null, Typeface.BOLD);
        creditHeader.setGravity(Gravity.CENTER);
        creditHeader.setBackgroundColor(Color.parseColor("#FEF2F2"));
        creditHeader.setPadding(8, 12, 8, 12);
        rightCol.addView(creditHeader);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("Name");
        nameLabel.setTextColor(Color.parseColor("#4A5568"));
        nameLabel.setTextSize(12);
        nameLabel.setTypeface(null, Typeface.BOLD);
        nameLabel.setPadding(0, 16, 0, 4);
        rightCol.addView(nameLabel);

        EditText etName = new EditText(this);
        etName.setHint("Enter name");
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        styleEditText(etName);
        rightCol.addView(etName);

        TextView creditCashLabel = new TextView(this);
        creditCashLabel.setText("Credit Cash");
        creditCashLabel.setTextColor(Color.parseColor("#DC2626"));
        creditCashLabel.setTextSize(12);
        creditCashLabel.setTypeface(null, Typeface.BOLD);
        creditCashLabel.setPadding(0, 16, 0, 4);
        rightCol.addView(creditCashLabel);

        EditText etCreditCash = new EditText(this);
        etCreditCash.setHint("0.00");
        etCreditCash.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleEditText(etCreditCash);
        rightCol.addView(etCreditCash);

        TextView creditRateLabel = new TextView(this);
        creditRateLabel.setText("Rate Cutting");
        creditRateLabel.setTextColor(Color.parseColor("#DC2626"));
        creditRateLabel.setTextSize(12);
        creditRateLabel.setTypeface(null, Typeface.BOLD);
        creditRateLabel.setPadding(0, 16, 0, 4);
        rightCol.addView(creditRateLabel);

        EditText etCreditRate = new EditText(this);
        etCreditRate.setHint("0.00");
        etCreditRate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        styleEditText(etCreditRate);
        rightCol.addView(etCreditRate);

        TextView creditPureLabel = new TextView(this);
        creditPureLabel.setText("Pure");
        creditPureLabel.setTextColor(Color.parseColor("#DC2626"));
        creditPureLabel.setTextSize(12);
        creditPureLabel.setTypeface(null, Typeface.BOLD);
        creditPureLabel.setPadding(0, 16, 0, 4);
        rightCol.addView(creditPureLabel);

        TextView tvPureCredit = new TextView(this);
        tvPureCredit.setText("0.00");
        tvPureCredit.setBackgroundColor(Color.parseColor("#FEF2F2"));
        tvPureCredit.setGravity(Gravity.CENTER);
        tvPureCredit.setPadding(10, 12, 10, 12);
        tvPureCredit.setTextSize(13);
        tvPureCredit.setTypeface(null, Typeface.BOLD);
        tvPureCredit.setTextColor(Color.parseColor("#DC2626"));
        LinearLayout.LayoutParams pureCreditParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(INPUT_HEIGHT_DP)
        );
        pureCreditParams.setMargins(0, 10, 0, 0);
        tvPureCredit.setLayoutParams(pureCreditParams);
        rightCol.addView(tvPureCredit);

        parentRow.addView(leftCol);
        parentRow.addView(rightCol);
        rowContainer.addView(parentRow);

        rowContainer.setVisibility(showCashDetails.isChecked() ? View.VISIBLE : View.GONE);
        dynamicCashRowsContainer.addView(rowContainer);
        dynamicCashRows.add(rowContainer);

        rowContainer.setTag(new Object[]{etDate, etName, etDebitCash, etCreditCash, etDebitRate, etCreditRate, tvPureDebit, tvPureCredit});

        // Using 1000 for cash pure calculation
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                double debitAmount = safeParseDouble(etDebitCash.getText().toString());
                double debitRate = safeParseDouble(etDebitRate.getText().toString());
                double creditAmount = safeParseDouble(etCreditCash.getText().toString());
                double creditRate = safeParseDouble(etCreditRate.getText().toString());

                double pureDebit = (debitRate != 0) ? (debitAmount / debitRate) * 1000.0 : 0;
                double pureCredit = (creditRate != 0) ? (creditAmount / creditRate) * 1000.0 : 0;

                tvPureDebit.setText(String.format(Locale.getDefault(), "%.2f", pureDebit));
                tvPureCredit.setText(String.format(Locale.getDefault(), "%.2f", pureCredit));

                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
            }
        };

        etDebitCash.addTextChangedListener(watcher);
        etDebitRate.addTextChangedListener(watcher);
        etCreditCash.addTextChangedListener(watcher);
        etCreditRate.addTextChangedListener(watcher);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private EditText createEditText(String hint, int heightDp) {
        EditText editText = new EditText(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
        );
        params.setMargins(0, 10, 0, 0);
        editText.setLayoutParams(params);
        editText.setHint(hint);
        editText.setTextSize(13);
        editText.setTextColor(Color.parseColor("#000000"));
        editText.setHintTextColor(Color.parseColor("#9E9E9E"));
        editText.setBackgroundResource(R.drawable.edit_text_bg);
        editText.setPadding(20, 8, 10, 8);
        editText.setSingleLine(true);
        return editText;
    }

    private EditText createNumberEditText(String hint, int heightDp) {
        EditText editText = createEditText(hint, heightDp);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return editText;
    }

    private TextView createPureTextView(String text, int textColor, int bgColor) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(textColor);
        textView.setBackgroundColor(bgColor);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(10, 16, 10, 16);
        textView.setTextSize(13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 20, 0, 0);
        textView.setLayoutParams(params);
        return textView;
    }

    private TextView createLabel(String text, int color) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(12);
        label.setTextColor(color);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 16, 0, 0);
        label.setLayoutParams(params);
        return label;
    }

    private TextView createSectionHeader(String title, int textColor, int bgColor) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(textColor);
        header.setBackgroundColor(bgColor);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 12, 0, 12);
        header.setTextSize(14);
        return header;
    }

    private void setupCalculationListeners(EditText etDebitWeight, EditText etDebitTouch, EditText etDebitWastage,
                                           EditText etCreditWeight, EditText etCreditTouch, EditText etCreditWastage,
                                           TextView tvDebitPure, TextView tvCreditPure) {

        Runnable update = new Runnable() {
            @Override
            public void run() {
                try {
                    double dWeight = safeParseDouble(etDebitWeight.getText().toString());
                    double dTouch = safeParseDouble(etDebitTouch.getText().toString());
                    double dWastage = safeParseDouble(etDebitWastage.getText().toString());
                    double cWeight = safeParseDouble(etCreditWeight.getText().toString());
                    double cTouch = safeParseDouble(etCreditTouch.getText().toString());
                    double cWastage = safeParseDouble(etCreditWastage.getText().toString());

                    double dPure = (dWeight * (dTouch + dWastage)) / 100.0;
                    double cPure = (cWeight * (cTouch + cWastage)) / 100.0;

                    tvDebitPure.setText("Debit Pure: " + String.format("%.2f", dPure));
                    tvCreditPure.setText("Credit Pure: " + String.format("%.2f", cPure));

                    calculateTotalsForAllRows();

                    if (showCashDetails.isChecked()) {
                        calculateCombinedTotals();
                    }
                } catch (Exception e) {
                    Log.e("AddTransaction", "Error calculating dynamic row values: " + e.getMessage());
                }
            }
        };

        View.OnFocusChangeListener calcListener = (view, hasFocus) -> {
            if (!hasFocus) {
                update.run();
            }
        };

        etDebitWeight.setOnFocusChangeListener(calcListener);
        etDebitTouch.setOnFocusChangeListener(calcListener);
        etDebitWastage.setOnFocusChangeListener(calcListener);
        etCreditWeight.setOnFocusChangeListener(calcListener);
        etCreditTouch.setOnFocusChangeListener(calcListener);
        etCreditWastage.setOnFocusChangeListener(calcListener);
    }

    private void setupSwipeGesture(View view, boolean isTransactionRow) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0;
            private float startY = 0;
            private final int SWIPE_THRESHOLD = 100;
            private boolean isSwiping = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        isSwiping = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (!isSwiping) {
                            float deltaX = Math.abs(event.getX() - startX);
                            float deltaY = Math.abs(event.getY() - startY);

                            if (deltaX > SWIPE_THRESHOLD && deltaX > deltaY * 2) {
                                isSwiping = true;
                            }
                        } else {
                            float currentX = event.getX();
                            float deltaX = currentX - startX;
                            if (deltaX > 0) {
                                v.setTranslationX(Math.min(deltaX, v.getWidth() * 0.3f));
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().translationX(0).setDuration(150).start();

                        if (isSwiping) {
                            float endX = event.getX();
                            float deltaX = endX - startX;

                            if (deltaX > SWIPE_THRESHOLD) {
                                showDeleteConfirmation(v, isTransactionRow);
                                return true;
                            }
                        }
                        isSwiping = false;
                        break;
                }
                return false;
            }
        });
    }

    private void showDeleteConfirmation(View viewToDelete, boolean isTransactionRow) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Row");
        builder.setMessage("Are you sure you want to delete this row?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            removeRow(viewToDelete, isTransactionRow);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            resetRowPosition(viewToDelete);
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY);
    }

    private void removeRow(View viewToDelete, boolean isTransactionRow) {
        if (isTransactionRow) {
            animateRowRemoval(viewToDelete, () -> {
                containerDynamicRows.removeView(viewToDelete);
                dynamicKachaRows.remove(viewToDelete);
                calculateTotalsForAllRows();
                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
                Toast.makeText(AddTransactionActivity.this, "Transaction row deleted", Toast.LENGTH_SHORT).show();
            });
        } else {
            animateRowRemoval(viewToDelete, () -> {
                dynamicCashRowsContainer.removeView(viewToDelete);
                dynamicCashRows.remove(viewToDelete);
                if (showCashDetails.isChecked()) {
                    calculateCombinedTotals();
                }
                Toast.makeText(AddTransactionActivity.this, "Cash row deleted", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void animateRowRemoval(View view, Runnable onComplete) {
        view.animate()
                .translationX(view.getWidth())
                .alpha(0.0f)
                .setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onComplete.run();
                    }
                })
                .start();
    }

    private void resetRowPosition(View view) {
        view.animate()
                .translationX(0)
                .setDuration(200)
                .start();
    }

    // ===================== CARRY-FORWARD TRANSACTION CREATION =====================
    // Creates a new "Temporary" / Open transaction that carries forward the
    // leftover balance of a transaction that is being saved as CLOSED.
    // This now runs immediately at save-time (status == "Close"), NOT when the
    // record is later moved into ClosedTransactionsActivity.
    private void createCarryForwardTransaction(boolean isBalancePure, double balanceAmount) {
        if (balanceAmount <= 0) return;

        DatabaseReference transactionsParentRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers")
                .child(customerId)
                .child("transactions");

        Map<String, Object> newTxn = new HashMap<>();
        newTxn.put("transactionName", "Temporary");

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        String todayDate = sdf.format(new Date());
        newTxn.put("date", todayDate);
        newTxn.put("status", "Open");

        if (isBalancePure) {
            // If Debit > Credit -> balance goes to Debit
            newTxn.put("staticDebitWeight", balanceAmount);
            newTxn.put("staticDebitTouch", 100.0);
            newTxn.put("staticDebitWastage", 0.0);
            newTxn.put("staticDebitPure", balanceAmount);
            newTxn.put("totalDebitPure", balanceAmount);
        } else {
            // If Credit > Debit -> balance goes to Credit
            newTxn.put("staticCreditWeight", balanceAmount);
            newTxn.put("staticCreditTouch", 100.0);
            newTxn.put("staticCreditWastage", 0.0);
            newTxn.put("staticCreditPure", balanceAmount);
            newTxn.put("totalCreditPure", balanceAmount);
        }

        newTxn.put("fromClosedTransaction", true);

        transactionsParentRef.push().setValue(newTxn)
                .addOnSuccessListener(aVoid -> {
                    Log.d("CarryForward", "New Temporary transaction created with balance: " + balanceAmount);
                    Toast.makeText(this, "Carry-forward transaction created with balance: " + balanceAmount, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e ->
                        Log.e("CarryForward", "Failed to create carry-forward transaction: " + e.getMessage()));
    }

    private void saveTransaction() {
        String transactionName = etTransactionName.getText().toString().trim();
        String note = etNote.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String status = spinnerStatus.getSelectedItem().toString();
        String particulars = etParticulars.getText().toString().trim();
        double totalDebit = 0, totalCredit = 0;

        Map<String, Object> transactionData = new HashMap<>();

        boolean showCashDetailsChecked = showCashDetails.isChecked();
        transactionData.put("showCashDetails", showCashDetailsChecked);

        if (!transactionName.isEmpty()) transactionData.put("transactionName", transactionName);
        if (!note.isEmpty()) transactionData.put("note", note);
        if (!date.isEmpty()) transactionData.put("date", date);
        transactionData.put("status", status);
        if (!particulars.isEmpty()) transactionData.put("particulars", particulars);

        double staticDebitWeight = safeParseDouble(etDebitWeight.getText().toString());
        double staticDebitTouch = safeParseDouble(etDebitTouch.getText().toString());
        double staticDebitWastage = safeParseDouble(etDebitWastage.getText().toString());
        double staticCreditWeight = safeParseDouble(etCreditWeight.getText().toString());
        double staticCreditTouch = safeParseDouble(etCreditTouch.getText().toString());
        double staticCreditWastage = safeParseDouble(etCreditWastage.getText().toString());

        double staticDebitPercent = staticDebitTouch + staticDebitWastage;
        double staticCreditPercent = staticCreditTouch + staticCreditWastage;

        double staticDebitPure = (staticDebitWeight * staticDebitPercent) / 100.0;
        double staticCreditPure = (staticCreditWeight * staticCreditPercent) / 100.0;

        if (staticDebitWeight != 0) transactionData.put("staticDebitWeight", staticDebitWeight);
        if (staticDebitTouch != 0) transactionData.put("staticDebitTouch", staticDebitTouch);
        if (staticDebitWastage != 0) transactionData.put("staticDebitWastage", staticDebitWastage);
        if (staticDebitPure != 0) transactionData.put("staticDebitPure", staticDebitPure);
        if (staticCreditWeight != 0) transactionData.put("staticCreditWeight", staticCreditWeight);
        if (staticCreditTouch != 0) transactionData.put("staticCreditTouch", staticCreditTouch);
        if (staticCreditWastage != 0) transactionData.put("staticCreditWastage", staticCreditWastage);
        if (staticCreditPure != 0) transactionData.put("staticCreditPure", staticCreditPure);

        Map<String, Object> dynamicRows = new HashMap<>();
        int weightRowIndex = 0;
        for (int i = 0; i < containerDynamicRows.getChildCount(); i++) {
            View childView = containerDynamicRows.getChildAt(i);
            if (dynamicCashRows.contains(childView) || dynamicKachaRows.contains(childView)) continue;
            if (!(childView instanceof LinearLayout)) continue;
            LinearLayout wrapper = (LinearLayout) childView;
            LinearLayout parentRow = (LinearLayout) wrapper.getChildAt(0);
            LinearLayout left = (LinearLayout) parentRow.getChildAt(0);
            LinearLayout right = (LinearLayout) parentRow.getChildAt(1);

            String dDate = ((EditText) left.getChildAt(2)).getText().toString().trim();
            double dWeight = safeParseDouble(((EditText) left.getChildAt(4)).getText().toString());
            double dTouch = safeParseDouble(((EditText) left.getChildAt(6)).getText().toString());
            double dWastage = safeParseDouble(((EditText) left.getChildAt(8)).getText().toString());
            double dPercent = dTouch + dWastage;
            double dPure = (dWeight * dPercent) / 100.0;

            String particularsRow = ((EditText) right.getChildAt(2)).getText().toString().trim();
            double cWeight = safeParseDouble(((EditText) right.getChildAt(4)).getText().toString());
            double cTouch = safeParseDouble(((EditText) right.getChildAt(6)).getText().toString());
            double cWastage = safeParseDouble(((EditText) right.getChildAt(8)).getText().toString());
            double cPercent = cTouch + cWastage;
            double cPure = (cWeight * cPercent) / 100.0;

            totalDebit += dPure;
            totalCredit += cPure;

            Map<String, Object> rowData = new HashMap<>();
            if (!dDate.isEmpty()) rowData.put("date", dDate);
            if (dWeight != 0) rowData.put("debitWeight", dWeight);
            if (dTouch != 0) rowData.put("debitTouch", dTouch);
            if (dWastage != 0) rowData.put("debitWastage", dWastage);
            if (dPure != 0) rowData.put("debitPure", dPure);
            if (!particularsRow.isEmpty()) rowData.put("particulars", particularsRow);
            if (cWeight != 0) rowData.put("creditWeight", cWeight);
            if (cTouch != 0) rowData.put("creditTouch", cTouch);
            if (cWastage != 0) rowData.put("creditWastage", cWastage);
            if (cPure != 0) rowData.put("creditPure", cPure);

            dynamicRows.put("row" + (++weightRowIndex), rowData);
        }

        if (!dynamicRows.isEmpty()) transactionData.put("dynamicRows", dynamicRows);

        // Kacha calculation rows are stored separately until its business formula is provided.
        Map<String, Object> kachaCalculations = new HashMap<>();
        int kachaIndex = 0;
        for (View kachaView : dynamicKachaRows) {
            Object tag = kachaView.getTag();
            if (!(tag instanceof Object[])) continue;
            Object[] f = (Object[]) tag;
            Map<String, Object> row = new HashMap<>();
            String[] keys = {"date", "name", "debitKachaPure", "creditKachaPure", "debitRateCutting", "creditRateCutting", "debitCash", "creditCash", "debitFinalPure", "creditFinalPure"};
            for (int k = 0; k < f.length; k++) {
                if (f[k] instanceof EditText) {
                    String value = ((EditText) f[k]).getText().toString().trim();
                    if (!value.isEmpty()) {
                        if (k == 0 || k == 1) row.put(keys[k], value);
                        else row.put(keys[k], safeParseDouble(value));
                    }
                }
            }
            if (!row.isEmpty()) kachaCalculations.put("row" + (++kachaIndex), row);
        }
        if (!kachaCalculations.isEmpty()) transactionData.put("kachaCalculations", kachaCalculations);

        double totalDebitPure = totalDebit + staticDebitPure;
        double totalCreditPure = totalCredit + staticCreditPure;

        if (totalDebitPure != 0) transactionData.put("totalDebitPure", totalDebitPure);
        if (totalCreditPure != 0) transactionData.put("totalCreditPure", totalCreditPure);

        Map<String, Object> cashDetails = new HashMap<>();

        String cashName = etCashNameCredit.getText().toString().trim();
        if (!cashName.isEmpty()) {
            cashDetails.put("name", cashName);
        }

        // Static cash - using 1000
        Map<String, Object> staticCash = new HashMap<>();
        double staticCashDebit = safeParseDouble(etDebitCashAmount.getText().toString());
        double staticCashCredit = safeParseDouble(etCreditCashAmount.getText().toString());
        double staticCashDebitRate = safeParseDouble(etDebitRateCutting.getText().toString());
        double staticCashCreditRate = safeParseDouble(etCreditRateCutting.getText().toString());

        double staticCashPureDebit = (staticCashDebitRate != 0) ? (staticCashDebit / staticCashDebitRate) * 1000.0 : 0;
        double staticCashPureCredit = (staticCashCreditRate != 0) ? (staticCashCredit / staticCashCreditRate) * 1000.0 : 0;

        String cashDate = etCashDateDebit.getText().toString().trim();

        if (staticCashDebit != 0) staticCash.put("debit", staticCashDebit);
        if (staticCashCredit != 0) staticCash.put("credit", staticCashCredit);
        if (staticCashDebitRate != 0) staticCash.put("debitRateCutting", staticCashDebitRate);
        if (staticCashCreditRate != 0) staticCash.put("creditRateCutting", staticCashCreditRate);
        if (staticCashPureDebit != 0) staticCash.put("pureDebit", staticCashPureDebit);
        if (staticCashPureCredit != 0) staticCash.put("pureCredit", staticCashPureCredit);
        if (!cashDate.isEmpty()) staticCash.put("date", cashDate);
        if (!staticCash.isEmpty()) cashDetails.put("static", staticCash);

        // Dynamic cash rows - using 1000
        Map<String, Object> dynamicCash = new HashMap<>();
        double totalDynamicCashDebitPure = 0.0;
        double totalDynamicCashCreditPure = 0.0;

        for (int i = 0; i < dynamicCashRows.size(); i++) {
            View rowContainer = dynamicCashRows.get(i);
            if (rowContainer.getTag() != null) {
                Object[] tags = (Object[]) rowContainer.getTag();
                EditText etDate = (EditText) tags[0];
                EditText etName = (EditText) tags[1];
                EditText etDebitCash = (EditText) tags[2];
                EditText etCreditCash = (EditText) tags[3];
                EditText etDebitRate = (EditText) tags[4];
                EditText etCreditRate = (EditText) tags[5];
                TextView tvPureDebit = (TextView) tags[6];
                TextView tvPureCredit = (TextView) tags[7];

                String rowDate = etDate.getText().toString().trim();
                String rowName = etName.getText().toString().trim();
                double rowDebitCash = safeParseDouble(etDebitCash.getText().toString());
                double rowCreditCash = safeParseDouble(etCreditCash.getText().toString());
                double rowDebitRate = safeParseDouble(etDebitRate.getText().toString());
                double rowCreditRate = safeParseDouble(etCreditRate.getText().toString());
                double rowPureDebit = (rowDebitRate != 0) ? (rowDebitCash / rowDebitRate) * 1000.0 : 0;
                double rowPureCredit = (rowCreditRate != 0) ? (rowCreditCash / rowCreditRate) * 1000.0 : 0;

                totalDynamicCashDebitPure += rowPureDebit;
                totalDynamicCashCreditPure += rowPureCredit;

                Map<String, Object> rowData = new HashMap<>();
                if (!rowDate.isEmpty()) rowData.put("date", rowDate);
                if (!rowName.isEmpty()) rowData.put("name", rowName);
                if (rowDebitCash != 0) rowData.put("debitCash", rowDebitCash);
                if (rowCreditCash != 0) rowData.put("creditCash", rowCreditCash);
                if (rowDebitRate != 0) rowData.put("debitRate", rowDebitRate);
                if (rowCreditRate != 0) rowData.put("creditRate", rowCreditRate);
                if (rowPureDebit != 0) rowData.put("pureDebit", rowPureDebit);
                if (rowPureCredit != 0) rowData.put("pureCredit", rowPureCredit);

                if (!rowData.isEmpty()) dynamicCash.put("row" + (i + 1), rowData);
            }
        }

        if (!dynamicCash.isEmpty()) cashDetails.put("dynamic", dynamicCash);

        if (!cashDetails.isEmpty()) transactionData.put("cashDetails", cashDetails);

        // ===================== FINAL COMBINED TOTALS (used for carry-forward) =====================
        double combinedDebitPure = totalDebitPure + staticCashPureDebit + totalDynamicCashDebitPure;
        double combinedCreditPure = totalCreditPure + staticCashPureCredit + totalDynamicCashCreditPure;
        double finalDifference = showCashDetailsChecked
                ? (combinedDebitPure - combinedCreditPure)
                : (totalDebitPure - totalCreditPure);

        if (!transactionData.isEmpty()) {
            transactionRef.setValue(transactionData).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Transaction saved successfully!", Toast.LENGTH_SHORT).show();

                    // ===================== CARRY-FORWARD ON SAVE (status == Close) =====================
                    // As soon as a transaction is saved with status "Close", immediately
                    // create the "Temporary" carry-forward transaction with the leftover
                    // balance - no need to visit ClosedTransactionsActivity for this anymore.
                    if ("Close".equalsIgnoreCase(status) && Math.abs(finalDifference) > 0) {
                        createCarryForwardTransaction(finalDifference > 0, Math.abs(finalDifference));
                    }

                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Failed to save transaction.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "No data to save.", Toast.LENGTH_SHORT).show();
        }
    }

    private void calculateAndUpdateValues() {
        double dWeight = safeParseDouble(etDebitWeight.getText().toString());
        double dTouch = safeParseDouble(etDebitTouch.getText().toString());
        double cWeight = safeParseDouble(etCreditWeight.getText().toString());
        double cTouch = safeParseDouble(etCreditTouch.getText().toString());
        double dWastage = safeParseDouble(etDebitWastage.getText().toString());
        double cWastage = safeParseDouble(etCreditWastage.getText().toString());
        double dPercent = dTouch + dWastage;
        double cPercent = cTouch + cWastage;

        debitPure = (dWeight * dPercent) / 100.0;
        creditPure = (cWeight * cPercent) / 100.0;

        double remaining = debitPure - creditPure;

        tvDebitPure.setText("Debit Pure: " + String.format("%.2f", debitPure));
        tvCreditPure.setText("Credit Pure: " + String.format("%.2f", creditPure));

        if (remaining > 0) {
            tvRemaining.setText("Balance Pure: " + String.format("%.2f", Math.abs(remaining)));
            tvRemaining.setTextColor(Color.parseColor("#00ab41"));
        } else if (remaining < 0) {
            tvRemaining.setText("Ex Pure: " + String.format("%.2f", Math.abs(remaining)));
            tvRemaining.setTextColor(Color.parseColor("#FF0000"));
        } else {
            tvRemaining.setText("Remaining: 0.00");
            tvRemaining.setTextColor(Color.parseColor("#000000"));
        }
    }

    private void calculateTotalsForAllRows() {
        double totalDebitWeight = safeParseDouble(etDebitWeight.getText().toString());
        double totalDebitTouch = safeParseDouble(etDebitTouch.getText().toString());
        double totalDebitWastage = safeParseDouble(etDebitWastage.getText().toString());
        double totalDebitFinalTouch = totalDebitTouch + totalDebitWastage;
        double totalDebitPure = (totalDebitWeight * totalDebitFinalTouch) / 100.0;

        double totalCreditWeight = safeParseDouble(etCreditWeight.getText().toString());
        double totalCreditTouch = safeParseDouble(etCreditTouch.getText().toString());
        double totalCreditWastage = safeParseDouble(etCreditWastage.getText().toString());
        double totalCreditFinalTouch = totalCreditTouch + totalCreditWastage;
        double totalCreditPure = (totalCreditWeight * totalCreditFinalTouch) / 100.0;

        for (int i = 0; i < containerDynamicRows.getChildCount(); i++) {
            View wrapperView = containerDynamicRows.getChildAt(i);
            if (dynamicCashRows.contains(wrapperView) || dynamicKachaRows.contains(wrapperView)) continue;
            if (!(wrapperView instanceof LinearLayout)) continue;

            LinearLayout wrapper = (LinearLayout) wrapperView;
            if (wrapper.getChildCount() == 0) continue;

            View rowView = wrapper.getChildAt(0);
            if (!(rowView instanceof LinearLayout)) continue;

            LinearLayout row = (LinearLayout) rowView;
            if (row.getChildCount() < 2) continue;

            LinearLayout left = (LinearLayout) row.getChildAt(0);
            LinearLayout right = (LinearLayout) row.getChildAt(1);

            double dWeight = safeParseDouble(((EditText) left.getChildAt(4)).getText().toString());
            double dTouch = safeParseDouble(((EditText) left.getChildAt(6)).getText().toString());
            double dWastage = safeParseDouble(((EditText) left.getChildAt(8)).getText().toString());
            double dFinalTouch = dTouch + dWastage;
            double dPure = (dWeight * dFinalTouch) / 100.0;
            totalDebitWeight += dWeight;
            totalDebitPure += dPure;

            double cWeight = safeParseDouble(((EditText) right.getChildAt(4)).getText().toString());
            double cTouch = safeParseDouble(((EditText) right.getChildAt(6)).getText().toString());
            double cWastage = safeParseDouble(((EditText) right.getChildAt(8)).getText().toString());
            double cFinalTouch = cTouch + cWastage;
            double cPure = (cWeight * cFinalTouch) / 100.0;
            totalCreditWeight += cWeight;
            totalCreditPure += cPure;
        }

        double remaining = totalDebitPure - totalCreditPure;

        tvTotalDebitWeight.setText("Total DP: " + String.format("%.2f", totalDebitPure));
        tvTotalCreditWeight.setText("Total CP: " + String.format("%.2f", totalCreditPure));

        if (remaining > 0) {
            tvRemaining.setText("Balance Pure: " + String.format("%.2f", Math.abs(remaining)));
            tvRemaining.setTextColor(Color.parseColor("#00ab41"));
        } else if (remaining < 0) {
            tvRemaining.setText("Ex Pure: " + String.format("%.2f", Math.abs(remaining)));
            tvRemaining.setTextColor(Color.parseColor("#FF0000"));
        } else {
            tvRemaining.setText("Remaining: 0.00");
            tvRemaining.setTextColor(Color.parseColor("#000000"));
        }
    }

    private void openDatePicker(EditText targetEditText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String formattedDate = String.format("%02d-%02d-%04d", dayOfMonth, month + 1, year);
                    targetEditText.setText(formattedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }
}
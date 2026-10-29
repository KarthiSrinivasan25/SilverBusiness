package com.example.silverbusinessapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClosedTransactionsActivity extends AppCompatActivity {

    // Firebase variables
    private String userUid;
    private String customerId;

    // Reference to closed_transactions node
    private DatabaseReference closedTransactionRef;

    // UI Components
    private LinearLayout transactionContainer;
    private TextView tvStatusClosed, tvPageInfo;
    private ImageButton btnBack;
    private TextView tvCustomerName;

    // Pagination variables
    private List<DataSnapshot> closedTransactions = new ArrayList<>();
    private int currentPage = 0;
    private final int PAGE_SIZE = 6;

    // Selection variables
    private List<String> selectedTransactionIds = new ArrayList<>();
    private boolean isSelectAll = false;

    // Session pagination flags
    private String lastEditedTransactionId = null;
    private boolean shouldRestorePage = false;

    // PDF variables
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int MIN_DOWNLOAD_DURATION = 2500;
    private android.app.AlertDialog downloadDialog;
    private byte[] generatedPdfBytes;

    // Pagination components
    private ImageButton btnFirstPage, btnLastPage, btnNextPage, btnPreviousPage;
    private Button btnGoToPage;
    private EditText etGoToPage;
    private TextView tvTotalPages;
    private TextView tvCurrentPageNumber;
    private TextView tvSelectedCount;
    private TextView tvSelectedCountTrans;

    // Button for Select All
    private Button btnSelectAll;

    // Finalize Button - ImageView (as per XML)
    private ImageView btnFinalize;

    // FIX: Add reference to the parent LinearLayout container
    private LinearLayout finalizeContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_closed_transactions);

        // Get intent data
        userUid = getIntent().getStringExtra("userUid");
        customerId = getIntent().getStringExtra("customerId");
        String customerName = getIntent().getStringExtra("customerName");

        if (userUid == null || customerId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase reference to closed_transactions
        closedTransactionRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("closed_transactions")
                .child(customerId);

        initializeViews();
        setupClickListeners();
        setupSwipeGestures();

        // Set customer name
        if (customerName != null && tvCustomerName != null) {
            tvCustomerName.setText(customerName);
        } else {
            fetchCustomerName();
        }

        loadClosedTransactions();
    }

    // ===================== FETCH CUSTOMER NAME =====================
    private void fetchCustomerName() {
        DatabaseReference customerRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers")
                .child(customerId);

        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && tvCustomerName != null) {
                        tvCustomerName.setText(name);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("CustomerName", "Error fetching customer name: " + error.getMessage());
            }
        });
    }

    private void initializeViews() {
        // Initialize main views
        transactionContainer = findViewById(R.id.transaction_container);
        tvStatusClosed = findViewById(R.id.tv_status_closed);
        tvPageInfo = findViewById(R.id.tv_page_info);
        btnBack = findViewById(R.id.btn_back);
        tvCustomerName = findViewById(R.id.tv_customer_name);

        // Initialize pagination views
        btnFirstPage = findViewById(R.id.btn_first_page);
        btnPreviousPage = findViewById(R.id.btn_previous_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnLastPage = findViewById(R.id.btn_last_page);
        btnGoToPage = findViewById(R.id.btn_go_to_page);
        etGoToPage = findViewById(R.id.et_go_to_page);
        tvTotalPages = findViewById(R.id.tv_total_pages);
        tvCurrentPageNumber = findViewById(R.id.tv_current_page_number);

        // Initialize Finalize button (ImageView), Select All button, and Selected Count
        btnFinalize = findViewById(R.id.btn_finalize_closed);
        btnSelectAll = findViewById(R.id.btn_select_all_closed);
        tvSelectedCount = findViewById(R.id.tv_selected_count_closed);
        tvSelectedCountTrans = findViewById(R.id.tv_selected_count_closed_trans);

        // FIX: Initialize the parent LinearLayout container
        finalizeContainer = findViewById(R.id.finalize_container);

        // Set initial text
        tvPageInfo.setText("0/0");
        tvTotalPages.setText("/ 0");
        if (tvCurrentPageNumber != null) {
            tvCurrentPageNumber.setText("0");
        }
        if (tvSelectedCount != null) {
            tvSelectedCount.setText("0");
        }
        if (tvSelectedCountTrans != null) {
            tvSelectedCountTrans.setText("0");
        }

        updateSelectAllButton();
        updateFinalizeButton();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Pagination click listeners
        btnFirstPage.setOnClickListener(v -> goToFirstPage());
        btnPreviousPage.setOnClickListener(v -> previousPage());
        btnNextPage.setOnClickListener(v -> nextPage());
        btnLastPage.setOnClickListener(v -> goToLastPage());
        btnGoToPage.setOnClickListener(v -> goToSpecificPage());

        // Enter key listener for EditText
        etGoToPage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                goToSpecificPage();
                return true;
            }
            return false;
        });

        // FIX: Set click listener on the parent LinearLayout container
        if (finalizeContainer != null) {
            finalizeContainer.setOnClickListener(v -> finalizeClosedTransactions());
        }

        // Also keep the click listener on the ImageView for backward compatibility
        if (btnFinalize != null) {
            btnFinalize.setOnClickListener(v -> finalizeClosedTransactions());
        }

        // Select All button click listener
        btnSelectAll.setOnClickListener(v -> toggleSelectAll());
    }

    // ===================== FINALIZE CLOSED TRANSACTIONS =====================
    private void finalizeClosedTransactions() {
        if (selectedTransactionIds.isEmpty()) {
            Toast.makeText(this, "Select at least one transaction to finalize", Toast.LENGTH_SHORT).show();
            return;
        }

        // Navigate to TotalCalculationActivity with selected transaction IDs
        Intent intent = new Intent(ClosedTransactionsActivity.this, TotalCalculationActivity.class);
        intent.putExtra("userUid", userUid);
        intent.putExtra("customerId", customerId);
        intent.putStringArrayListExtra("selectedTransactionIds", new ArrayList<>(selectedTransactionIds));
        intent.putExtra("isFromClosedTransactions", true);
        startActivity(intent);
    }

    // ===================== SELECT ALL TOGGLE =====================
    private void toggleSelectAll() {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, closedTransactions.size());

        if (closedTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isSelectAll) {
            for (int i = start; i < end; i++) {
                String transactionId = closedTransactions.get(i).getKey();
                selectedTransactionIds.remove(transactionId);
            }
            isSelectAll = false;
        } else {
            for (int i = start; i < end; i++) {
                String transactionId = closedTransactions.get(i).getKey();
                if (!selectedTransactionIds.contains(transactionId)) {
                    selectedTransactionIds.add(transactionId);
                }
            }
            isSelectAll = true;
        }

        updateSelectAllButton();
        updateFinalizeButton();
        displayCurrentPage();
    }

    // ===================== UPDATE SELECT ALL BUTTON =====================
    private void updateSelectAllButton() {
        if (btnSelectAll != null) {
            if (isSelectAll) {
                btnSelectAll.setText("Deselect All");
                btnSelectAll.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                btnSelectAll.setText("Select All");
                btnSelectAll.setBackgroundColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        }
    }

    // ===================== UPDATE FINALIZE BUTTON =====================
    private void updateFinalizeButton() {
        boolean hasSelection = !selectedTransactionIds.isEmpty();

        // Update the Finalize container opacity
        if (finalizeContainer != null) {
            finalizeContainer.setAlpha(hasSelection ? 1.0f : 0.5f);
        }

        if (btnFinalize != null) {
            btnFinalize.setAlpha(hasSelection ? 1.0f : 0.5f);
            btnFinalize.setEnabled(hasSelection);
            btnFinalize.setColorFilter(Color.parseColor("#FFEB3B"));
        }

        // Update the count on the FINAL button card
        if (tvSelectedCount != null) {
            tvSelectedCount.setText(String.valueOf(selectedTransactionIds.size()));
        }

        // Update the count on the "Selected:" row
        if (tvSelectedCountTrans != null) {
            tvSelectedCountTrans.setText(String.valueOf(selectedTransactionIds.size()));
        }
    }

    // ===================== LOAD CLOSED TRANSACTIONS =====================
    private void loadClosedTransactions() {
        closedTransactionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                closedTransactions.clear();

                for (DataSnapshot txnSnapshot : snapshot.getChildren()) {
                    closedTransactions.add(txnSnapshot);
                }

                updateStats();

                if (shouldRestorePage) {
                    int maxPage = getTotalPages() - 1;
                    if (currentPage > maxPage) {
                        currentPage = Math.max(0, maxPage);
                    }
                    shouldRestorePage = false;
                } else {
                    currentPage = 0;
                }

                selectedTransactionIds.clear();
                isSelectAll = false;
                updateSelectAllButton();
                updateFinalizeButton();

                displayCurrentPage();

                if (lastEditedTransactionId != null) {
                    highlightTransaction(lastEditedTransactionId);
                    lastEditedTransactionId = null;
                }

                updatePaginationControls();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ClosedTransactionsActivity.this,
                        "Failed to load transactions: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStats() {
        int closedCount = closedTransactions.size();
        tvStatusClosed.setText(String.valueOf(closedCount));
    }

    private void displayCurrentPage() {
        transactionContainer.removeAllViews();

        if (closedTransactions.isEmpty()) {
            showEmptyState();
            return;
        }

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, closedTransactions.size());

        for (int i = start; i < end; i += 2) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(2f);

            if (i < end) {
                addCardToRow(rowLayout, closedTransactions.get(i), 0);
            }

            if (i + 1 < end) {
                addCardToRow(rowLayout, closedTransactions.get(i + 1), 1);
            }

            transactionContainer.addView(rowLayout);
        }

        updatePaginationInfo();
        updateNavigationButtonsState();
    }

    // ===================== ADD CARD WITH FULL DATA =====================
    private void addCardToRow(LinearLayout rowLayout, DataSnapshot txnSnapshot, int column) {
        try {
            String transactionId = txnSnapshot.getKey();
            String transactionName = txnSnapshot.child("transactionName").getValue(String.class);
            String status = txnSnapshot.child("status").getValue(String.class);

            if (transactionName == null) transactionName = "Unnamed Transaction";
            if (status == null) status = "CLOSE";

            final View cardView = getLayoutInflater().inflate(R.layout.item_transaction, null);
            CardView cardViewContainer = cardView.findViewById(R.id.card_view_container);

            TextView tvTransactionName = cardView.findViewById(R.id.tv_transaction_name);
            TextView tvStatus = cardView.findViewById(R.id.tv_status);
            ImageButton btnEdit = cardView.findViewById(R.id.btn_edit);
            ImageButton btnDelete = cardView.findViewById(R.id.btn_delete);
            CheckBox checkboxSelect = cardView.findViewById(R.id.checkbox_select);
            ImageButton btnExportPdf = cardView.findViewById(R.id.btn_export_pdf);

            tvTransactionName.setText(transactionName);
            tvStatus.setText(status);
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

            checkboxSelect.setVisibility(View.VISIBLE);

            boolean isSelected = selectedTransactionIds.contains(transactionId);
            checkboxSelect.setChecked(isSelected);
            updateCardColor(cardViewContainer, isSelected);

            checkboxSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (!selectedTransactionIds.contains(transactionId)) {
                        selectedTransactionIds.add(transactionId);
                    }
                } else {
                    selectedTransactionIds.remove(transactionId);
                    if (isSelectAll) {
                        isSelectAll = false;
                        updateSelectAllButton();
                    }
                }
                updateCardColor(cardViewContainer, isChecked);
                updateFinalizeButton();
            });

            // ========== EDIT BUTTON - PASS COMPLETE DATA ==========
            btnEdit.setEnabled(true);
            btnEdit.setAlpha(1.0f);
            btnEdit.setOnClickListener(v -> {
                // Get ALL transaction data from snapshot
                HashMap<String, Object> transactionData = getFullTransactionData(txnSnapshot);

                // IMPORTANT: Pass the transaction ID
                String txnId = txnSnapshot.getKey();

                // Open UpdateTransactionActivity with ALL data
                editClosedTransaction(txnId, transactionData);
            });

            btnDelete.setOnClickListener(v -> deleteTransaction(transactionId));
            btnExportPdf.setOnClickListener(v -> handlePdfExport(txnSnapshot));

            cardView.setOnClickListener(v -> {
                checkboxSelect.setChecked(!checkboxSelect.isChecked());
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            params.setMargins(8, 8, 8, 8);

            cardView.setLayoutParams(params);
            rowLayout.addView(cardView);

        } catch (Exception e) {
            Log.e("TransactionDebug", "Error adding card: " + e.getMessage());
        }
    }

    // ===================== GET FULL TRANSACTION DATA =====================
    private HashMap<String, Object> getFullTransactionData(DataSnapshot snapshot) {
        HashMap<String, Object> transactionData = new HashMap<>();

        try {
            // Basic fields
            transactionData.put("transactionName", snapshot.child("transactionName").getValue(String.class));
            transactionData.put("status", snapshot.child("status").getValue(String.class));
            transactionData.put("date", snapshot.child("date").getValue(String.class));
            transactionData.put("particulars", snapshot.child("particulars").getValue(String.class));
            transactionData.put("note", snapshot.child("note").getValue(String.class));

            // Static fields
            transactionData.put("staticDebitWeight", getDoubleValue(snapshot, "staticDebitWeight"));
            transactionData.put("staticDebitTouch", getDoubleValue(snapshot, "staticDebitTouch"));
            transactionData.put("staticDebitWastage", getDoubleValue(snapshot, "staticDebitWastage"));
            transactionData.put("staticDebitPure", getDoubleValue(snapshot, "staticDebitPure"));
            transactionData.put("staticCreditWeight", getDoubleValue(snapshot, "staticCreditWeight"));
            transactionData.put("staticCreditTouch", getDoubleValue(snapshot, "staticCreditTouch"));
            transactionData.put("staticCreditWastage", getDoubleValue(snapshot, "staticCreditWastage"));
            transactionData.put("staticCreditPure", getDoubleValue(snapshot, "staticCreditPure"));

            // Dynamic rows - CRITICAL: Need to preserve row structure
            if (snapshot.child("dynamicRows").exists()) {
                HashMap<String, Object> dynamicRowsMap = new HashMap<>();
                for (DataSnapshot row : snapshot.child("dynamicRows").getChildren()) {
                    HashMap<String, Object> rowData = new HashMap<>();
                    rowData.put("date", row.child("date").getValue(String.class));
                    rowData.put("particulars", row.child("particulars").getValue(String.class));
                    rowData.put("debitWeight", getDoubleValue(row, "debitWeight"));
                    rowData.put("debitTouch", getDoubleValue(row, "debitTouch"));
                    rowData.put("debitWastage", getDoubleValue(row, "debitWastage"));
                    rowData.put("debitPure", getDoubleValue(row, "debitPure"));
                    rowData.put("creditWeight", getDoubleValue(row, "creditWeight"));
                    rowData.put("creditTouch", getDoubleValue(row, "creditTouch"));
                    rowData.put("creditWastage", getDoubleValue(row, "creditWastage"));
                    rowData.put("creditPure", getDoubleValue(row, "creditPure"));
                    dynamicRowsMap.put(row.getKey(), rowData);
                }
                transactionData.put("dynamicRows", dynamicRowsMap);
            }

            // ========== FIX: showCashDetails is stored at ROOT level in Firebase ==========
            Boolean showCashDetailsValue = snapshot.child("showCashDetails").getValue(Boolean.class);
            if (showCashDetailsValue == null) {
                showCashDetailsValue = false;
            }

            // ========== Cash Details ==========
            if (snapshot.child("cashDetails").exists()) {
                HashMap<String, Object> cashDetails = new HashMap<>();
                DataSnapshot cashSnap = snapshot.child("cashDetails");

                cashDetails.put("showCashDetails", showCashDetailsValue);
                cashDetails.put("name", cashSnap.child("name").getValue(String.class));

                if (cashSnap.child("static").exists()) {
                    HashMap<String, Object> staticCash = new HashMap<>();
                    DataSnapshot staticSnap = cashSnap.child("static");
                    staticCash.put("date", staticSnap.child("date").getValue(String.class));
                    staticCash.put("debit", getDoubleValue(staticSnap, "debit"));
                    staticCash.put("credit", getDoubleValue(staticSnap, "credit"));
                    staticCash.put("debitRateCutting", getDoubleValue(staticSnap, "debitRateCutting"));
                    staticCash.put("creditRateCutting", getDoubleValue(staticSnap, "creditRateCutting"));
                    staticCash.put("pureDebit", getDoubleValue(staticSnap, "pureDebit"));
                    staticCash.put("pureCredit", getDoubleValue(staticSnap, "pureCredit"));
                    cashDetails.put("static", staticCash);
                }

                if (cashSnap.child("dynamic").exists()) {
                    HashMap<String, Object> dynamicCashMap = new HashMap<>();
                    for (DataSnapshot cashRow : cashSnap.child("dynamic").getChildren()) {
                        HashMap<String, Object> cashRowData = new HashMap<>();
                        cashRowData.put("date", cashRow.child("date").getValue(String.class));
                        cashRowData.put("name", cashRow.child("name").getValue(String.class));
                        cashRowData.put("debitCash", getDoubleValue(cashRow, "debitCash"));
                        cashRowData.put("creditCash", getDoubleValue(cashRow, "creditCash"));
                        cashRowData.put("debitRate", getDoubleValue(cashRow, "debitRate"));
                        cashRowData.put("creditRate", getDoubleValue(cashRow, "creditRate"));
                        cashRowData.put("pureDebit", getDoubleValue(cashRow, "pureDebit"));
                        cashRowData.put("pureCredit", getDoubleValue(cashRow, "pureCredit"));
                        dynamicCashMap.put(cashRow.getKey(), cashRowData);
                    }
                    cashDetails.put("dynamic", dynamicCashMap);
                }

                transactionData.put("cashDetails", cashDetails);
            } else {
                HashMap<String, Object> defaultCashDetails = new HashMap<>();
                defaultCashDetails.put("showCashDetails", showCashDetailsValue);
                transactionData.put("cashDetails", defaultCashDetails);
            }

            // Totals
            transactionData.put("totalDebitPure", getDoubleValue(snapshot, "totalDebitPure"));
            transactionData.put("totalCreditPure", getDoubleValue(snapshot, "totalCreditPure"));
            transactionData.put("cashRemaining", getDoubleValue(snapshot, "cashRemaining"));
            transactionData.put("cashRemainingLabel", snapshot.child("cashRemainingLabel").getValue(String.class));

            // ========== Carry-forward guard flag (preserve across edits) ==========
            Boolean carryForwardCreated = snapshot.child("carryForwardCreated").getValue(Boolean.class);
            transactionData.put("carryForwardCreated", carryForwardCreated != null && carryForwardCreated);

            // IMPORTANT: Flag to show this is from closed transactions
            transactionData.put("isFromClosedTransactions", true);

            transactionData.put("createdAt", snapshot.child("createdAt").getValue(Long.class));
            transactionData.put("updatedAt", snapshot.child("updatedAt").getValue(Long.class));

        } catch (Exception e) {
            Log.e("GetData", "Error extracting transaction data: " + e.getMessage());
        }

        return transactionData;
    }

    // ===================== EDIT WITH COMPLETE DATA =====================
    private void editClosedTransaction(String transactionId, HashMap<String, Object> transactionData) {
        new AlertDialog.Builder(this)
                .setTitle("Edit Closed Transaction")
                .setMessage("This transaction is already CLOSED. You need to change the status to OPEN to edit it. Do you want to continue?")
                .setPositiveButton("Yes, Continue", (dialog, which) -> {
                    lastEditedTransactionId = transactionId;
                    shouldRestorePage = true;

                    Intent editIntent = new Intent(ClosedTransactionsActivity.this, UpdateTransactionActivity.class);
                    editIntent.putExtra("userUid", userUid);
                    editIntent.putExtra("customerId", customerId);
                    editIntent.putExtra("transactionId", transactionId);

                    // CRITICAL: Pass the entire transaction data
                    editIntent.putExtra("transactionData", transactionData);

                    // Pass individual fields for backward compatibility
                    if (transactionData.containsKey("transactionName")) {
                        editIntent.putExtra("transactionName", (String) transactionData.get("transactionName"));
                    }
                    if (transactionData.containsKey("date")) {
                        editIntent.putExtra("date", (String) transactionData.get("date"));
                    }
                    if (transactionData.containsKey("status")) {
                        editIntent.putExtra("status", (String) transactionData.get("status"));
                    }
                    if (transactionData.containsKey("particulars")) {
                        editIntent.putExtra("particulars", (String) transactionData.get("particulars"));
                    }
                    if (transactionData.containsKey("note")) {
                        editIntent.putExtra("note", (String) transactionData.get("note"));
                    }

                    // Pass all numeric values
                    if (transactionData.containsKey("staticDebitWeight")) {
                        editIntent.putExtra("staticDebitWeight", getDoubleFromObject(transactionData.get("staticDebitWeight")));
                    }
                    if (transactionData.containsKey("staticDebitTouch")) {
                        editIntent.putExtra("staticDebitTouch", getDoubleFromObject(transactionData.get("staticDebitTouch")));
                    }
                    if (transactionData.containsKey("staticDebitWastage")) {
                        editIntent.putExtra("staticDebitWastage", getDoubleFromObject(transactionData.get("staticDebitWastage")));
                    }
                    if (transactionData.containsKey("staticCreditWeight")) {
                        editIntent.putExtra("staticCreditWeight", getDoubleFromObject(transactionData.get("staticCreditWeight")));
                    }
                    if (transactionData.containsKey("staticCreditTouch")) {
                        editIntent.putExtra("staticCreditTouch", getDoubleFromObject(transactionData.get("staticCreditTouch")));
                    }
                    if (transactionData.containsKey("staticCreditWastage")) {
                        editIntent.putExtra("staticCreditWastage", getDoubleFromObject(transactionData.get("staticCreditWastage")));
                    }

                    // ========== FIXED: Pass showCashDetails ==========
                    if (transactionData.containsKey("cashDetails")) {
                        HashMap<String, Object> cashDetails = (HashMap<String, Object>) transactionData.get("cashDetails");
                        if (cashDetails != null && cashDetails.containsKey("showCashDetails")) {
                            Boolean showCash = (Boolean) cashDetails.get("showCashDetails");
                            editIntent.putExtra("showCashDetails", showCash != null && showCash);
                        } else {
                            editIntent.putExtra("showCashDetails", false);
                        }
                    } else {
                        editIntent.putExtra("showCashDetails", false);
                    }

                    // Pass Total values
                    if (transactionData.containsKey("totalDebitPure")) {
                        editIntent.putExtra("totalDebitPure", getDoubleFromObject(transactionData.get("totalDebitPure")));
                    }
                    if (transactionData.containsKey("totalCreditPure")) {
                        editIntent.putExtra("totalCreditPure", getDoubleFromObject(transactionData.get("totalCreditPure")));
                    }

                    // IMPORTANT: Flag to tell UpdateTransactionActivity this is from closed transactions
                    editIntent.putExtra("isFromClosedTransactions", true);
                    editIntent.putExtra("isEditingClosedTransaction", true);

                    startActivityForResult(editIntent, 1);
                })
                .setNegativeButton("No", null)
                .show();
    }

    // ===================== HELPER METHODS =====================
    private Double getDoubleFromObject(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Long) return ((Long) value).doubleValue();
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private Double getDoubleValue(DataSnapshot snapshot, String key) {
        try {
            if (snapshot.child(key).exists()) {
                Object value = snapshot.child(key).getValue();
                if (value instanceof Double) {
                    return (Double) value;
                } else if (value instanceof Long) {
                    return ((Long) value).doubleValue();
                } else if (value instanceof Integer) {
                    return ((Integer) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("GetDouble", "Error getting double value for key: " + key, e);
        }
        return 0.0;
    }

    private void updateCardColor(CardView cardView, boolean isSelected) {
        if (isSelected) {
            cardView.setCardBackgroundColor(getResources().getColor(R.color.sandal_color));
        } else {
            cardView.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        }
    }

    private void highlightTransaction(String transactionId) {
        handler.postDelayed(() -> {
            for (int i = 0; i < transactionContainer.getChildCount(); i++) {
                LinearLayout rowLayout = (LinearLayout) transactionContainer.getChildAt(i);
                for (int j = 0; j < rowLayout.getChildCount(); j++) {
                    View card = rowLayout.getChildAt(j);
                    CardView cardView = card.findViewById(R.id.card_view_container);
                    if (cardView != null) {
                        cardView.setCardBackgroundColor(getResources().getColor(R.color.sandal_color));
                        handler.postDelayed(() -> {
                            CheckBox checkbox = card.findViewById(R.id.checkbox_select);
                            boolean isSelected = checkbox != null && checkbox.isChecked();
                            cardView.setCardBackgroundColor(isSelected ?
                                    getResources().getColor(R.color.sandal_color) :
                                    getResources().getColor(android.R.color.white));
                        }, 500);
                    }
                }
            }
        }, 300);
    }

    private void deleteTransaction(String transactionId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to permanently delete this closed transaction?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    closedTransactionRef.child(transactionId).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(ClosedTransactionsActivity.this,
                                        "Transaction deleted permanently", Toast.LENGTH_SHORT).show();
                                selectedTransactionIds.remove(transactionId);
                                updateFinalizeButton();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(ClosedTransactionsActivity.this,
                                            "Failed to delete", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }

    // ===================== PDF EXPORT METHODS =====================
    private void handlePdfExport(DataSnapshot txnSnapshot) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Generating PDF...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            try {
                final String transactionId = txnSnapshot.getKey();
                final String transactionName = txnSnapshot.child("transactionName").getValue(String.class);
                final String finalTransactionName = transactionName != null ? transactionName : "Unnamed Transaction";
                String status = txnSnapshot.child("status").getValue(String.class);
                String date = txnSnapshot.child("date").getValue(String.class);
                String particulars = txnSnapshot.child("particulars").getValue(String.class);

                final String finalStatus = status != null ? status : "CLOSE";
                final String finalDate = date != null ? date : new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
                final String finalParticulars = particulars != null ? particulars : "";

                final int entryNumber = getTransactionEntryNumber(transactionId);

                DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference()
                        .child("Users")
                        .child(userUid)
                        .child("customers")
                        .child(customerId);

                customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot customerSnapshot) {
                        String customerName = customerSnapshot.exists()
                                ? customerSnapshot.child("name").getValue(String.class)
                                : "Customer";
                        String customerPhone = customerSnapshot.exists() && customerSnapshot.child("phone").exists()
                                ? customerSnapshot.child("phone").getValue(String.class)
                                : "N/A";
                        String customerAddress = customerSnapshot.exists() && customerSnapshot.child("address").exists()
                                ? customerSnapshot.child("address").getValue(String.class)
                                : "N/A";

                        new Thread(() -> {
                            byte[] pdfBytes = generateFastPDF(
                                    txnSnapshot,
                                    finalTransactionName,
                                    finalStatus,
                                    customerName,
                                    customerPhone,
                                    customerAddress,
                                    finalDate,
                                    finalParticulars,
                                    entryNumber
                            );

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                if (pdfBytes != null) {
                                    generatedPdfBytes = pdfBytes;
                                    showPdfOptionsDialog(customerName, finalTransactionName);
                                } else {
                                    Toast.makeText(ClosedTransactionsActivity.this,
                                            "Failed to generate PDF", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(ClosedTransactionsActivity.this,
                                    "Error fetching customer data", Toast.LENGTH_SHORT).show();
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ClosedTransactionsActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private byte[] generateFastPDF(DataSnapshot txnSnapshot, String transactionName,
                                   String status, String customerName, String customerPhone,
                                   String customerAddress, String date, String particulars,
                                   int entryNumber) {
        try {
            final List<HashMap<String, Object>> transactionRows = new ArrayList<>();
            final List<HashMap<String, Object>> cashRows = new ArrayList<>();
            final HashMap<String, Object> totalsData = new HashMap<>();

            extractTransactionData(txnSnapshot, transactionRows, cashRows, totalsData, date);

            File tempFile = File.createTempFile("preview_", ".pdf", getCacheDir());
            PdfExportUtil pdfExportUtil = new PdfExportUtil(this);

            final boolean[] exportCompleted = {false};
            final String[] exportError = {null};

            pdfExportUtil.exportTransactionToPdf(
                    txnSnapshot.getKey(),
                    transactionName,
                    status,
                    customerName,
                    customerPhone,
                    customerAddress,
                    date,
                    transactionRows,
                    cashRows,
                    totalsData,
                    tempFile,
                    new PdfExportUtil.OnPdfExportListener() {
                        @Override
                        public void onPdfExportSuccess(String filePath) {
                            exportCompleted[0] = true;
                        }

                        @Override
                        public void onPdfExportFailed(String error) {
                            exportError[0] = error;
                            exportCompleted[0] = true;
                        }
                    },
                    false,
                    entryNumber
            );

            int waitCount = 0;
            while (!exportCompleted[0] && waitCount < 30) {
                Thread.sleep(100);
                waitCount++;
            }

            if (exportError[0] != null) {
                Log.e("PDFExport", "Export failed: " + exportError[0]);
                return null;
            }

            if (!exportCompleted[0]) {
                Log.e("PDFExport", "Export timeout");
                return null;
            }

            return readFileToBytes(tempFile);

        } catch (Exception e) {
            Log.e("PDFExport", "Error generating PDF: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void extractTransactionData(DataSnapshot txnSnapshot,
                                        List<HashMap<String, Object>> transactionRows,
                                        List<HashMap<String, Object>> cashRows,
                                        HashMap<String, Object> totalsData, String date) {
        try {
            if (txnSnapshot.child("staticDebitPure").exists()) {
                HashMap<String, Object> staticRow = new HashMap<>();
                staticRow.put("date", date);

                String staticParticular = txnSnapshot.child("particulars").getValue(String.class);
                staticRow.put("particular", staticParticular != null ? staticParticular : "Static");

                staticRow.put("debitWeight", safeParseDouble(txnSnapshot.child("staticDebitWeight").getValue()));
                staticRow.put("debitTouch", safeParseDouble(txnSnapshot.child("staticDebitTouch").getValue()));
                staticRow.put("debitWastage", safeParseDouble(txnSnapshot.child("staticDebitWastage").getValue()));
                staticRow.put("debitPure", safeParseDouble(txnSnapshot.child("staticDebitPure").getValue()));
                staticRow.put("creditWeight", safeParseDouble(txnSnapshot.child("staticCreditWeight").getValue()));
                staticRow.put("creditTouch", safeParseDouble(txnSnapshot.child("staticCreditTouch").getValue()));
                staticRow.put("creditWastage", safeParseDouble(txnSnapshot.child("staticCreditWastage").getValue()));
                staticRow.put("creditPure", safeParseDouble(txnSnapshot.child("staticCreditPure").getValue()));
                transactionRows.add(staticRow);
            }

            DataSnapshot dynamicRows = txnSnapshot.child("dynamicRows");
            if (dynamicRows.exists()) {
                for (DataSnapshot row : dynamicRows.getChildren()) {
                    HashMap<String, Object> rowData = new HashMap<>();
                    rowData.put("date", row.child("date").getValue(String.class));

                    String particular = row.child("particulars").getValue(String.class);
                    rowData.put("particular", particular != null ? particular : "");

                    rowData.put("debitWeight", safeParseDouble(row.child("debitWeight").getValue()));
                    rowData.put("debitTouch", safeParseDouble(row.child("debitTouch").getValue()));
                    rowData.put("debitWastage", safeParseDouble(row.child("debitWastage").getValue()));
                    rowData.put("debitPure", safeParseDouble(row.child("debitPure").getValue()));
                    rowData.put("creditWeight", safeParseDouble(row.child("creditWeight").getValue()));
                    rowData.put("creditTouch", safeParseDouble(row.child("creditTouch").getValue()));
                    rowData.put("creditWastage", safeParseDouble(row.child("creditWastage").getValue()));
                    rowData.put("creditPure", safeParseDouble(row.child("creditPure").getValue()));
                    transactionRows.add(rowData);
                }
            }

            Boolean showCashDetails = txnSnapshot.child("showCashDetails").getValue(Boolean.class);
            if (showCashDetails == null) {
                showCashDetails = false;
            }

            if (showCashDetails) {
                DataSnapshot cashDetails = txnSnapshot.child("cashDetails");
                if (cashDetails.exists()) {
                    String cashName = cashDetails.child("name").getValue(String.class);

                    DataSnapshot staticCash = cashDetails.child("static");
                    if (staticCash.exists()) {
                        HashMap<String, Object> staticCashRow = new HashMap<>();
                        staticCashRow.put("date", staticCash.child("date").getValue(String.class));
                        staticCashRow.put("name", cashName != null && !cashName.isEmpty() ? cashName : "Cash");
                        staticCashRow.put("debitCash", getDoubleValue(staticCash, "debit"));
                        staticCashRow.put("creditCash", getDoubleValue(staticCash, "credit"));
                        staticCashRow.put("pure", getDoubleValue(staticCash, "pure"));
                        cashRows.add(staticCashRow);
                    }

                    DataSnapshot dynamicCash = cashDetails.child("dynamic");
                    if (dynamicCash.exists()) {
                        for (DataSnapshot cashRow : dynamicCash.getChildren()) {
                            HashMap<String, Object> cashRowData = new HashMap<>();
                            cashRowData.put("date", cashRow.child("date").getValue(String.class));
                            String dynamicCashName = cashRow.child("name").getValue(String.class);
                            cashRowData.put("name", dynamicCashName != null ? dynamicCashName : "");
                            cashRowData.put("debitCash", getDoubleValue(cashRow, "debit"));
                            cashRowData.put("creditCash", getDoubleValue(cashRow, "credit"));
                            cashRowData.put("pure", getDoubleValue(cashRow, "pure"));
                            cashRows.add(cashRowData);
                        }
                    }
                }
            }

            totalsData.put("totalDebitPure", safeParseDouble(txnSnapshot.child("totalDebitPure").getValue()));
            totalsData.put("totalCreditPure", safeParseDouble(txnSnapshot.child("totalCreditPure").getValue()));
            totalsData.put("cashRemaining", safeParseDouble(txnSnapshot.child("cashRemaining").getValue()));
            totalsData.put("cashRemainingLabel", txnSnapshot.child("cashRemainingLabel").getValue(String.class));

        } catch (Exception e) {
            Log.e("PDFData", "Error extracting transaction data: " + e.getMessage());
        }
    }

    private byte[] readFileToBytes(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            fis.close();
            return bos.toByteArray();
        } catch (Exception e) {
            Log.e("FileRead", "Error reading file: " + e.getMessage());
            return null;
        }
    }

    private double safeParseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Long) {
                return ((Long) value).doubleValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).doubleValue();
            } else {
                return Double.parseDouble(value.toString());
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void showEmptyState() {
        TextView emptyText = new TextView(this);
        emptyText.setText("No closed transactions found");
        emptyText.setTextColor(getResources().getColor(android.R.color.white));
        emptyText.setTextSize(16f);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(1.5f, 1.5f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(16, 16, 16, 16);

        emptyText.setLayoutParams(params);
        transactionContainer.addView(emptyText);
    }

    // ===================== PDF DIALOG METHODS =====================
    private void showPdfOptionsDialog(String customerName, String transactionName) {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.pdf_options_dialog, null);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            View btnViewPdf = dialogView.findViewById(R.id.btn_view_pdf);
            View btnSavePdf = dialogView.findViewById(R.id.btn_save_pdf);
            Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
            ImageView closeBtn = dialogView.findViewById(R.id.btn_close_dialog);

            btnViewPdf.setOnClickListener(v -> {
                dialog.dismiss();
                showPdfViewChoiceDialog(customerName, transactionName);
            });

            btnSavePdf.setOnClickListener(v -> {
                dialog.dismiss();
                if (generatedPdfBytes != null) {
                    savePdfToDownloads(generatedPdfBytes, customerName, transactionName);
                } else {
                    Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
                }
            });

            btnCancel.setOnClickListener(v -> dialog.dismiss());
            if (closeBtn != null) {
                closeBtn.setOnClickListener(v -> dialog.dismiss());
            }

        } catch (Exception e) {
            e.printStackTrace();
            showPdfViewChoiceDialog(customerName, transactionName);
        }
    }

    // ===================== PDF VIEW CHOICE DIALOG =====================
    private void showPdfViewChoiceDialog(String customerName, String transactionName) {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            View dialogView = LayoutInflater.from(this).inflate(
                    R.layout.dialog_pdf_view_choice,
                    null
            );

            // Find views from XML
            CardView btnViewThisApp = dialogView.findViewById(R.id.btn_view_this_app);
            CardView btnViewOtherApp = dialogView.findViewById(R.id.btn_view_other_app);
            Button btnCancelChoice = dialogView.findViewById(R.id.btn_cancel_choice);
            Button btnAlwaysUse = dialogView.findViewById(R.id.btn_always_use);

            // Build and show dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogView);

            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            dialog.show();

            // Set click listeners
            btnViewThisApp.setOnClickListener(v -> {
                dialog.dismiss();
                viewPdfInThisApp(customerName, transactionName);
            });

            btnViewOtherApp.setOnClickListener(v -> {
                dialog.dismiss();
                viewPdfInOtherApp(customerName, transactionName);
            });

            btnCancelChoice.setOnClickListener(v -> dialog.dismiss());

            btnAlwaysUse.setOnClickListener(v -> {
                dialog.dismiss();
                // Default to in-app viewer
                viewPdfInThisApp(customerName, transactionName);
                Toast.makeText(this, "Opening in-app viewer", Toast.LENGTH_SHORT).show();
            });

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: if XML dialog fails, open in-app viewer directly
            viewPdfInThisApp(customerName, transactionName);
        }
    }

    // ===================== VIEW PDF IN THIS APP (PdfViewerActivity) =====================
    private void viewPdfInThisApp(String customerName, String transactionName) {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = "inapp_view_" + System.currentTimeMillis() + ".pdf";
            File tempFile = new File(getCacheDir(), fileName);

            try (OutputStream out = new FileOutputStream(tempFile)) {
                out.write(generatedPdfBytes);
                out.flush();
            }

            String displayTitle = (transactionName != null && !transactionName.isEmpty())
                    ? transactionName
                    : "PDF Preview";

            Intent intent = new Intent(ClosedTransactionsActivity.this, PdfViewerActivity.class);
            intent.putExtra("pdfFilePath", tempFile.getAbsolutePath());
            intent.putExtra("pdfTitle", displayTitle + " - " + customerName);
            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening PDF in app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ===================== VIEW PDF IN OTHER APP (External Viewer) =====================
    private void viewPdfInOtherApp(String customerName, String transactionName) {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String fileName = "preview_" + System.currentTimeMillis() + ".pdf";
            File tempFile = new File(getCacheDir(), fileName);

            try (OutputStream out = new FileOutputStream(tempFile)) {
                out.write(generatedPdfBytes);
                out.flush();
            }

            Uri pdfUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    tempFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                showNoPdfViewerDialog(customerName, transactionName);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error viewing PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNoPdfViewerDialog(String customerName, String transactionName) {
        runOnUiThread(() -> {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("No PDF Viewer Found");
                builder.setMessage("You don't have a PDF viewer app installed. Please install a PDF viewer app (like Adobe Acrobat, Google PDF Viewer, or any other PDF reader) to view the PDF.");

                builder.setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                });

                builder.setNegativeButton("Download PDF Instead", (dialog, which) -> {
                    dialog.dismiss();
                    if (generatedPdfBytes != null) {
                        savePdfToDownloads(generatedPdfBytes, customerName, transactionName);
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
            }
        });
    }

    // ===================== SAVE PDF TO DOWNLOADS =====================
    // FIX: filename is now just "<customer_name>.pdf" (removed "_Closed" suffix)
    // saved under Downloads/PST Silver Ledger/<customer_name>.pdf
    private void savePdfToDownloads(byte[] pdfData, String customerName, String transactionName) {
        try {
            showDownloadingDialog();

            final long startTime = System.currentTimeMillis();

            new Thread(() -> {
                boolean success = false;
                String fileName = "";
                String errorMessage = "";

                try {
                    Thread.sleep(300);

                    // FIX: filename is now "<customer_name>.pdf" only
                    fileName = customerName.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";

                    String relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + "PST Silver Ledger";

                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
                    values.put(MediaStore.Downloads.IS_PENDING, 1);

                    ContentResolver resolver = getContentResolver();
                    Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    Uri fileUri = resolver.insert(collection, values);

                    if (fileUri != null) {
                        try (OutputStream out = resolver.openOutputStream(fileUri)) {
                            out.write(pdfData);
                        }

                        values.clear();
                        values.put(MediaStore.Downloads.IS_PENDING, 0);
                        resolver.update(fileUri, values, null, null);
                        success = true;

                    } else {
                        errorMessage = "Failed to create file";
                        success = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    errorMessage = e.getMessage();
                    success = false;
                }

                final boolean finalSuccess = success;
                final String finalFileName = fileName;
                final String finalErrorMessage = errorMessage;

                final long elapsedTime = System.currentTimeMillis() - startTime;
                final long remainingTime = Math.max(0, MIN_DOWNLOAD_DURATION - elapsedTime);

                handler.postDelayed(() -> {
                    dismissDownloadDialog();
                    showDownloadResultDialog(finalSuccess, finalFileName, finalErrorMessage);
                }, remainingTime);

            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            dismissDownloadDialog();
            showDownloadResultDialog(false, "", e.getMessage());
        }
    }

    private void dismissDownloadDialog() {
        runOnUiThread(() -> {
            if (downloadDialog != null && downloadDialog.isShowing()) {
                try {
                    downloadDialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showDownloadingDialog() {
        runOnUiThread(() -> {
            try {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_downloading, null);

                LottieAnimationView lottieAnimationView = dialogView.findViewById(R.id.lottie_animation);
                TextView tvStatus = dialogView.findViewById(R.id.tv_status);
                TextView tvMessage = dialogView.findViewById(R.id.tv_message);

                try {
                    lottieAnimationView.setAnimation(R.raw.download_animation);
                } catch (Exception e) {
                    try {
                        lottieAnimationView.setAnimation("downloading.json");
                    } catch (Exception ex) {
                        lottieAnimationView.setAnimation("loading.json");
                    }
                }

                lottieAnimationView.playAnimation();
                lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);

                tvStatus.setText("Downloading PDF...");
                tvMessage.setText("Please wait");

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(dialogView);
                builder.setCancelable(false);

                downloadDialog = builder.create();

                if (downloadDialog.getWindow() != null) {
                    downloadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                downloadDialog.show();
                lottieAnimationView.resumeAnimation();

            } catch (Exception e) {
                e.printStackTrace();
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Downloading PDF...");
                progressDialog.setCancelable(false);
                progressDialog.show();
                downloadDialog = progressDialog;
            }
        });
    }

    private void showDownloadResultDialog(boolean success, String fileName, String errorMessage) {
        runOnUiThread(() -> {
            try {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_downloading, null);

                LottieAnimationView lottieAnimationView = dialogView.findViewById(R.id.lottie_animation);
                TextView tvStatus = dialogView.findViewById(R.id.tv_status);
                TextView tvMessage = dialogView.findViewById(R.id.tv_message);

                if (success) {
                    try {
                        lottieAnimationView.setAnimation(R.raw.success_animation);
                    } catch (Exception e) {
                        try {
                            lottieAnimationView.setAnimation("success.json");
                        } catch (Exception ex) {
                            lottieAnimationView.setAnimation("checkmark.json");
                        }
                    }
                    tvStatus.setText("Download Complete!");
                    tvMessage.setText("Saved to: PST Silver Ledger/\n" + fileName);
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    try {
                        lottieAnimationView.setAnimation(R.raw.error_animation);
                    } catch (Exception e) {
                        try {
                            lottieAnimationView.setAnimation("error.json");
                        } catch (Exception ex) {
                            lottieAnimationView.setAnimation("warning.json");
                        }
                    }
                    tvStatus.setText("Download Failed");
                    tvMessage.setText(errorMessage != null && !errorMessage.isEmpty() ?
                            errorMessage : "Please try again");
                    tvStatus.setTextColor(Color.parseColor("#F44336"));
                }

                lottieAnimationView.playAnimation();
                lottieAnimationView.setRepeatCount(0);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setView(dialogView);

                final AlertDialog resultDialog = builder.create();

                if (resultDialog.getWindow() != null) {
                    resultDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                resultDialog.show();

                handler.postDelayed(() -> {
                    if (resultDialog != null && resultDialog.isShowing()) {
                        try {
                            resultDialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, success ? 2500 : 3000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private int getTransactionEntryNumber(String transactionId) {
        final int[] entryNumber = {0};

        DatabaseReference allTransactionsRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers")
                .child(customerId)
                .child("transactions");

        try {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            allTransactionsRef.orderByChild("createdAt").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<String> orderedTransactionIds = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        orderedTransactionIds.add(child.getKey());
                    }

                    for (int i = 0; i < orderedTransactionIds.size(); i++) {
                        if (orderedTransactionIds.get(i).equals(transactionId)) {
                            entryNumber[0] = i + 1;
                            break;
                        }
                    }
                    latch.countDown();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    latch.countDown();
                }
            });

            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            Log.e("EntryNumber", "Error getting entry number: " + e.getMessage());
        }

        return entryNumber[0] > 0 ? entryNumber[0] : 1;
    }

    // ===================== PAGINATION METHODS =====================
    private void nextPage() {
        if ((currentPage + 1) * PAGE_SIZE < closedTransactions.size()) {
            currentPage++;
            displayCurrentPage();
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            displayCurrentPage();
        }
    }

    private void goToFirstPage() {
        if (currentPage > 0) {
            currentPage = 0;
            displayCurrentPage();
        }
    }

    private void goToLastPage() {
        int lastPage = getTotalPages() - 1;
        if (currentPage < lastPage) {
            currentPage = lastPage;
            displayCurrentPage();
        }
    }

    private void goToSpecificPage() {
        String pageStr = etGoToPage.getText().toString().trim();
        if (pageStr.isEmpty()) {
            Toast.makeText(this, "Please enter a page number", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int requestedPage = Integer.parseInt(pageStr);
            int totalPages = getTotalPages();

            int targetPage = requestedPage - 1;

            if (targetPage >= 0 && targetPage < totalPages) {
                currentPage = targetPage;
                displayCurrentPage();
                etGoToPage.setText("");

                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(etGoToPage.getWindowToken(), 0);
            } else {
                Toast.makeText(this, "Please enter a valid page number (1-" + totalPages + ")",
                        Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid page number", Toast.LENGTH_SHORT).show();
        }
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) closedTransactions.size() / PAGE_SIZE);
    }

    private void updatePaginationControls() {
        if (closedTransactions.isEmpty()) {
            btnFirstPage.setEnabled(false);
            btnPreviousPage.setEnabled(false);
            btnNextPage.setEnabled(false);
            btnLastPage.setEnabled(false);
            btnGoToPage.setEnabled(false);
            etGoToPage.setEnabled(false);
            setButtonsAlpha(0.3f);
            if (tvCurrentPageNumber != null) tvCurrentPageNumber.setText("0");
        } else {
            btnFirstPage.setEnabled(true);
            btnPreviousPage.setEnabled(true);
            btnNextPage.setEnabled(true);
            btnLastPage.setEnabled(true);
            btnGoToPage.setEnabled(true);
            etGoToPage.setEnabled(true);
            updateNavigationButtonsState();
        }
    }

    private void setButtonsAlpha(float alpha) {
        btnFirstPage.setAlpha(alpha);
        btnPreviousPage.setAlpha(alpha);
        btnNextPage.setAlpha(alpha);
        btnLastPage.setAlpha(alpha);
    }

    private void updateNavigationButtonsState() {
        int totalPages = getTotalPages();

        btnFirstPage.setEnabled(currentPage > 0);
        btnPreviousPage.setEnabled(currentPage > 0);
        btnNextPage.setEnabled(currentPage < totalPages - 1);
        btnLastPage.setEnabled(currentPage < totalPages - 1);

        btnFirstPage.setAlpha(currentPage > 0 ? 1.0f : 0.3f);
        btnPreviousPage.setAlpha(currentPage > 0 ? 1.0f : 0.3f);
        btnNextPage.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.3f);
        btnLastPage.setAlpha(currentPage < totalPages - 1 ? 1.0f : 0.3f);
    }

    private void updatePaginationInfo() {
        int totalPages = getTotalPages();
        int currentPageDisplay = currentPage + 1;

        tvPageInfo.setText(currentPageDisplay + "/" + totalPages);
        tvTotalPages.setText("/ " + totalPages);

        if (tvCurrentPageNumber != null) {
            tvCurrentPageNumber.setText(String.valueOf(currentPageDisplay));
        }
    }

    private void setupSwipeGestures() {
        transactionContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private static final int MIN_DISTANCE = 100;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        return true;
                    case MotionEvent.ACTION_UP:
                        float endX = event.getX();
                        float deltaX = endX - startX;

                        if (Math.abs(deltaX) > MIN_DISTANCE) {
                            if (deltaX > 0) {
                                previousPage();
                            } else {
                                nextPage();
                            }
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }

    // ===================== ACTIVITY RESULT =====================
    // FIX: When UpdateTransactionActivity reports that it moved the transaction back
    // to OPEN (see UpdateTransactionActivity.moveTransactionBackToOpenTransactions),
    // this activity now finishes itself too, so the user lands directly back on
    // EditTransactionActivity instead of staying on this (now stale) closed list.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                boolean movedBackToOpen = data != null && data.getBooleanExtra("movedBackToOpen", false);
                if (movedBackToOpen) {
                    Toast.makeText(this, "Transaction moved back to OPEN", Toast.LENGTH_SHORT).show();
                    // Go straight back to EditTransactionActivity (which is already
                    // underneath this one in the back stack and auto-refreshes via
                    // its live Firebase listener).
                    finish();
                    return;
                }
                shouldRestorePage = true;
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show();
            } else {
                shouldRestorePage = true;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}
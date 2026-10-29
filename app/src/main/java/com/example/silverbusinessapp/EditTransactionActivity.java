package com.example.silverbusinessapp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
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

import org.xmlpull.v1.XmlPullParser;

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

public class EditTransactionActivity extends AppCompatActivity {

    // Firebase variables
    String userUid, customerId;
    DatabaseReference transactionRef;

    // UI Components
    LinearLayout transactionContainer;
    TextView tvStatusOpen, tvStatusClose, tvTotalTransactions, tvPageInfo, tvSelectedCount;
    ImageButton btnAdd;
    ImageButton btnclose_dialog; // Close Dialog ImageButton with close_icon

    // Pagination variables
    List<DataSnapshot> allTransactions = new ArrayList<>();
    int currentPage = 0;
    final int PAGE_SIZE = 6;

    // Selection variables
    List<String> selectedTransactionIds = new ArrayList<>();
    boolean isSelectAll = false;

    // Session pagination flags
    private String lastEditedTransactionId = null;
    private boolean shouldRestorePage = false;

    // PDF variables
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int MIN_DOWNLOAD_DURATION = 2500;
    private android.app.AlertDialog downloadDialog;
    private byte[] generatedPdfBytes;

    // New pagination components
    private ImageButton btnFirstPage, btnLastPage, btnNextPage, btnPreviousPage;
    private Button btnGoToPage;
    private EditText etGoToPage;
    private TextView tvTotalPages;
    private TextView tvCurrentPageNumber;

    // CLOSED status card - to open ClosedTransactionsActivity
    private LinearLayout statusCloseCard;

    // Select All Button
    private Button btnSelectAll;

    // Constants for selection modes
    private static final int SELECT_ALL = 0;
    private static final int SELECT_OPEN_ONLY = 1;
    private static final int SELECT_CLOSED_ONLY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);

        // Get intent data
        userUid = getIntent().getStringExtra("userUid");
        customerId = getIntent().getStringExtra("customerId");

        if (userUid == null || customerId == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize Firebase reference
        transactionRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers")
                .child(customerId)
                .child("transactions");

        initializeViews();
        setupClickListeners();
        setupSwipeGestures();

        // Start from page 0
        currentPage = 0;
        loadTransactions();
    }

    private void initializeViews() {
        // Initialize main views
        transactionContainer = findViewById(R.id.transaction_container);
        tvStatusOpen = findViewById(R.id.tv_status_open);
        tvStatusClose = findViewById(R.id.tv_status_close);
        tvTotalTransactions = findViewById(R.id.tv_total_transactions);
        tvPageInfo = findViewById(R.id.tv_page_info);
        tvSelectedCount = findViewById(R.id.tv_selected_count);

        btnAdd = findViewById(R.id.btn_add_transaction);
        btnclose_dialog = findViewById(R.id.btnclose_dialog); // ImageButton with close_icon

        // Initialize Select All Button
        btnSelectAll = findViewById(R.id.btn_select_all);

        // CLOSED status card - clicking this opens ClosedTransactionsActivity
        statusCloseCard = findViewById(R.id.status_close_card);

        // Initialize pagination views
        btnFirstPage = findViewById(R.id.btn_first_page);
        btnPreviousPage = findViewById(R.id.btn_previous_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnLastPage = findViewById(R.id.btn_last_page);
        btnGoToPage = findViewById(R.id.btn_go_to_page);
        etGoToPage = findViewById(R.id.et_go_to_page);
        tvTotalPages = findViewById(R.id.tv_total_pages);

        // Initialize new simple pagination views
        tvCurrentPageNumber = findViewById(R.id.tv_current_page_number);

        // Set initial text
        tvPageInfo.setText("0/0");
        tvSelectedCount.setText("0");
        tvTotalPages.setText("/ 0");

        if (tvCurrentPageNumber != null) {
            tvCurrentPageNumber.setText("0");
        }

        // Initially disable close button
        if (btnclose_dialog != null) {
            btnclose_dialog.setEnabled(false);
            btnclose_dialog.setAlpha(0.5f);
        }

        // Set initial Select All button text
        if (btnSelectAll != null) {
            btnSelectAll.setText("Select All");
        }
    }

    private void setupClickListeners() {
        btnAdd.setOnClickListener(v -> openAddTransaction());

        // CLOSE DIALOG IMAGEBUTTON - Moves selected CLOSED records to ClosedTransactionsActivity
        if (btnclose_dialog != null) {
            btnclose_dialog.setOnClickListener(v -> moveSelectedToClosedActivity());
        }

        // CLOSED status card click - Opens ClosedTransactionsActivity (VIEW ONLY)
        if (statusCloseCard != null) {
            statusCloseCard.setOnClickListener(v -> openClosedTransactions());
        }

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

        // Select All Button Click Listener - Shows dialog with options
        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(v -> showSelectAllOptionsDialog());
        }
    }

    // ===================== SAFE PARSE DOUBLE METHOD =====================
    private double safeParseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof Long) {
                return ((Long) value).doubleValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).doubleValue();
            } else if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) return 0.0;
                return Double.parseDouble(strValue);
            } else {
                return Double.parseDouble(value.toString());
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ===================== SELECT ALL OPTIONS DIALOG =====================
    private void showSelectAllOptionsDialog() {
        if (allTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to select", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current page transactions
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());

        // Check if current page has any transactions
        if (start >= allTransactions.size()) {
            Toast.makeText(this, "No transactions on current page", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build the options dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Transactions");

        // Create options array
        String[] options = {"Select All (Both Open & Closed)", "Select Only Open", "Select Only Closed"};

        // Count open and closed on current page for better UX
        int openCount = 0;
        int closedCount = 0;
        for (int i = start; i < end; i++) {
            DataSnapshot transaction = allTransactions.get(i);
            String status = transaction.child("status").getValue(String.class);
            if ("Close".equalsIgnoreCase(status)) {
                closedCount++;
            } else {
                openCount++;
            }
        }

        // Update options with counts
        options[0] = "Select All (" + (openCount + closedCount) + " transactions)";
        options[1] = "Select Only Open (" + openCount + " transactions)";
        options[2] = "Select Only Closed (" + closedCount + " transactions)";

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case SELECT_ALL:
                    selectAllTransactions();
                    break;
                case SELECT_OPEN_ONLY:
                    selectTransactionsByStatus("Open");
                    break;
                case SELECT_CLOSED_ONLY:
                    selectTransactionsByStatus("Close");
                    break;
            }
        });

        // Add cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // ===================== SELECT ALL TRANSACTIONS (BOTH OPEN & CLOSED) =====================
    private void selectAllTransactions() {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());

        // Clear existing selection first
        selectedTransactionIds.clear();

        // Select all transactions on current page
        for (int i = start; i < end; i++) {
            DataSnapshot transaction = allTransactions.get(i);
            String transactionId = transaction.getKey();
            if (transactionId != null) {
                selectedTransactionIds.add(transactionId);
            }
        }

        isSelectAll = true;
        btnSelectAll.setText("Deselect All");

        Toast.makeText(this, "All " + selectedTransactionIds.size() + " transactions selected", Toast.LENGTH_SHORT).show();

        // Update UI
        updateCloseButton();
        updateFinalizeButton();
        displayCurrentPage(); // Refresh to show checkbox states
    }

    // ===================== SELECT TRANSACTIONS BY STATUS =====================
    private void selectTransactionsByStatus(String status) {
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());

        // Clear existing selection first
        selectedTransactionIds.clear();

        int selectedCount = 0;

        // Select only transactions with matching status on current page
        for (int i = start; i < end; i++) {
            DataSnapshot transaction = allTransactions.get(i);
            String transactionStatus = transaction.child("status").getValue(String.class);
            String transactionId = transaction.getKey();

            if (transactionId != null && status.equalsIgnoreCase(transactionStatus)) {
                selectedTransactionIds.add(transactionId);
                selectedCount++;
            }
        }

        // Update button state based on selection
        int totalOnPage = end - start;
        if (selectedCount == totalOnPage) {
            // All transactions on page have the same status and are selected
            isSelectAll = true;
            btnSelectAll.setText("Deselect All");
        } else {
            isSelectAll = false;
            btnSelectAll.setText("Select All");
        }

        String statusDisplay = status.equalsIgnoreCase("Open") ? "OPEN" : "CLOSED";
        Toast.makeText(this, selectedCount + " " + statusDisplay + " transaction(s) selected", Toast.LENGTH_SHORT).show();

        // Update UI
        updateCloseButton();
        updateFinalizeButton();
        displayCurrentPage(); // Refresh to show checkbox states
    }

    // ===================== TOGGLE SELECT ALL (Original functionality - kept for compatibility) =====================
    private void toggleSelectAll() {
        if (allTransactions.isEmpty()) {
            Toast.makeText(this, "No transactions to select", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get current page transactions
        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());
        List<DataSnapshot> currentPageTransactions = new ArrayList<>();
        for (int i = start; i < end; i++) {
            currentPageTransactions.add(allTransactions.get(i));
        }

        if (isSelectAll) {
            // DESELECT ALL - Remove all current page transactions from selection
            for (DataSnapshot transaction : currentPageTransactions) {
                String transactionId = transaction.getKey();
                selectedTransactionIds.remove(transactionId);
            }
            isSelectAll = false;
            btnSelectAll.setText("Select All");
            Toast.makeText(this, "All transactions deselected", Toast.LENGTH_SHORT).show();
        } else {
            // SELECT ALL - Add all current page transactions to selection
            for (DataSnapshot transaction : currentPageTransactions) {
                String transactionId = transaction.getKey();
                if (!selectedTransactionIds.contains(transactionId)) {
                    selectedTransactionIds.add(transactionId);
                }
            }
            isSelectAll = true;
            btnSelectAll.setText("Deselect All");
            Toast.makeText(this, "All transactions selected", Toast.LENGTH_SHORT).show();
        }

        // Update UI
        updateCloseButton();
        updateFinalizeButton();
        displayCurrentPage(); // Refresh to show checkbox states
    }

    // ===================== MOVE SELECTED CLOSED RECORDS TO CLOSED TRANSACTIONS ACTIVITY =====================
    private void moveSelectedToClosedActivity() {
        // Step 1: Check if any transaction is selected
        if (selectedTransactionIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one CLOSED transaction", Toast.LENGTH_SHORT).show();
            return;
        }

        // Step 2: Filter only CLOSED transactions from selected ones
        List<String> closedTransactionIds = new ArrayList<>();
        List<String> openTransactionIds = new ArrayList<>();

        for (String transactionId : selectedTransactionIds) {
            boolean isClosed = false;
            for (DataSnapshot transaction : allTransactions) {
                if (transaction.getKey().equals(transactionId)) {
                    String status = transaction.child("status").getValue(String.class);
                    if ("Close".equalsIgnoreCase(status)) {
                        isClosed = true;
                        closedTransactionIds.add(transactionId);
                    } else {
                        openTransactionIds.add(transactionId);
                    }
                    break;
                }
            }
        }

        // Step 3: If no CLOSED records selected
        if (closedTransactionIds.isEmpty() && !openTransactionIds.isEmpty()) {
            Toast.makeText(this, "Please select CLOSED transactions only", Toast.LENGTH_LONG).show();
            return;
        }

        // Step 4: Show confirmation dialog
        String message = "Are you sure you want to move " + closedTransactionIds.size() +
                " CLOSED transaction(s) to Closed Transactions?";

        new AlertDialog.Builder(this)
                .setTitle("Move to Closed Transactions")
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Step 5: Move only CLOSED transactions
                    for (String transactionId : closedTransactionIds) {
                        moveTransactionToClosedActivity(transactionId);
                    }

                    // Clear selection after moving
                    selectedTransactionIds.clear();
                    isSelectAll = false;
                    if (btnSelectAll != null) {
                        btnSelectAll.setText("Select All");
                    }
                    updateCloseButton();
                    updateFinalizeButton();
                    displayCurrentPage();

                    Toast.makeText(this, closedTransactionIds.size() + " transaction(s) moved to Closed Transactions", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Step 6: User clicked No - Do nothing, keep records
                    dialog.dismiss();
                    Toast.makeText(this, "Move cancelled", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ===================== MOVE CLOSED TRANSACTION TO CLOSED TRANSACTIONS ACTIVITY =====================
    // NOTE: Carry-forward ("Temporary" transaction) creation has been REMOVED from here.
    // It now happens immediately in AddTransactionActivity / UpdateTransactionActivity
    // at the moment status is saved/updated as "Close". This method now ONLY moves
    // the record from /transactions to /closed_transactions - it does NOT create
    // any carry-forward transaction anymore.
    private void moveTransactionToClosedActivity(String transactionId) {
        // IMPORTANT: This method ONLY runs when user clicks "Yes" in the dialog
        // Get the transaction data first
        transactionRef.child(transactionId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {

                    // ============================================================
                    // MOVE TO CLOSED TRANSACTIONS (carry-forward creation removed -
                    // it is now handled at save/update time, not here)
                    // ============================================================
                    DatabaseReference closedRef = FirebaseDatabase.getInstance()
                            .getReference("Users")
                            .child(userUid)
                            .child("closed_transactions")
                            .child(customerId)
                            .child(transactionId);

                    HashMap<String, Object> txnData = (HashMap<String, Object>) snapshot.getValue();
                    if (txnData == null) txnData = new HashMap<>();
                    txnData.put("customerId", customerId);
                    txnData.put("movedToClosedDate", new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(new Date()));

                    closedRef.setValue(txnData)
                            .addOnSuccessListener(aVoid -> {
                                // Remove from original location
                                transactionRef.child(transactionId).removeValue()
                                        .addOnSuccessListener(aVoid2 -> {
                                            Log.d("MoveToClosed", "Transaction " + transactionId + " moved to Closed Transactions");
                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e("MoveToClosed", "Failed to remove: " + e.getMessage());
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e("MoveToClosed", "Failed to move to Closed Transactions: " + e.getMessage());
                            });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("MoveToClosed", "Error: " + error.getMessage());
            }
        });
    }

    private void updateCloseButton() {
        if (btnclose_dialog != null) {
            if (selectedTransactionIds.isEmpty()) {
                btnclose_dialog.setEnabled(false);
                btnclose_dialog.setAlpha(0.5f);
            } else {
                // Check if any selected transaction is CLOSED
                boolean hasClosed = false;
                for (String id : selectedTransactionIds) {
                    for (DataSnapshot transaction : allTransactions) {
                        if (transaction.getKey().equals(id)) {
                            String status = transaction.child("status").getValue(String.class);
                            if ("Close".equalsIgnoreCase(status)) {
                                hasClosed = true;
                                break;
                            }
                        }
                    }
                    if (hasClosed) break;
                }

                if (hasClosed) {
                    btnclose_dialog.setEnabled(true);
                    btnclose_dialog.setAlpha(1.0f);
                } else {
                    btnclose_dialog.setEnabled(false);
                    btnclose_dialog.setAlpha(0.5f);
                }
            }
        }
    }

    // ===================== OPEN CLOSED TRANSACTIONS ACTIVITY (VIEW ONLY) =====================
    private void openClosedTransactions() {
        // This just OPENS the activity - NO AUTO-MOVE happens here
        Intent intent = new Intent(EditTransactionActivity.this, ClosedTransactionsActivity.class);
        intent.putExtra("userUid", userUid);
        intent.putExtra("customerId", customerId);
        startActivity(intent);
    }

    private void openAddTransaction() {
        DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child(userUid)
                .child("customers")
                .child(customerId);

        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                String customerName = dataSnapshot.exists() ?
                        dataSnapshot.child("name").getValue(String.class) : "Customer";

                Intent intent = new Intent(EditTransactionActivity.this, AddTransactionActivity.class);
                intent.putExtra("userUid", userUid);
                intent.putExtra("customerId", customerId);
                intent.putExtra("customerName", customerName);
                startActivityForResult(intent, 1);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Intent intent = new Intent(EditTransactionActivity.this, AddTransactionActivity.class);
                intent.putExtra("userUid", userUid);
                intent.putExtra("customerId", customerId);
                intent.putExtra("customerName", "Customer");
                startActivityForResult(intent, 1);
            }
        });
    }

    private void nextPage() {
        if ((currentPage + 1) * PAGE_SIZE < allTransactions.size()) {
            currentPage++;
            displayCurrentPage();
            // Reset Select All state when changing pages
            if (isSelectAll) {
                isSelectAll = false;
                if (btnSelectAll != null) {
                    btnSelectAll.setText("Select All");
                }
            }
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            displayCurrentPage();
            // Reset Select All state when changing pages
            if (isSelectAll) {
                isSelectAll = false;
                if (btnSelectAll != null) {
                    btnSelectAll.setText("Select All");
                }
            }
        }
    }

    // ===================== LOAD TRANSACTIONS - SHOWS ALL RECORDS (NO AUTO-MOVE) =====================
    private void loadTransactions() {
        transactionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTransactions.clear();

                // Update stats with ALL transactions
                updateStatsWithAllTransactions(snapshot);

                // CRITICAL: SHOW ALL TRANSACTIONS - Both OPEN and CLOSED
                // NO FILTER - NO AUTO-MOVE
                // CLOSED records STAY VISIBLE here
                for (DataSnapshot txnSnapshot : snapshot.getChildren()) {
                    allTransactions.add(txnSnapshot);
                }

                // Handle page restoration
                if (shouldRestorePage) {
                    int maxPage = getTotalPages() - 1;
                    if (currentPage > maxPage) {
                        currentPage = Math.max(0, maxPage);
                    }
                    shouldRestorePage = false;
                } else {
                    currentPage = 0;
                }

                displayCurrentPage();

                // Highlight edited transaction
                if (lastEditedTransactionId != null) {
                    highlightTransaction(lastEditedTransactionId);
                    lastEditedTransactionId = null;
                }

                // Clear selection state
                selectedTransactionIds.clear();
                isSelectAll = false;
                if (btnSelectAll != null) {
                    btnSelectAll.setText("Select All");
                }
                updateCloseButton();
                updateFinalizeButton();

                // Update pagination controls
                updatePaginationControls();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditTransactionActivity.this,
                        "Failed to load transactions: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatsWithAllTransactions(DataSnapshot snapshot) {
        int openCount = 0;
        int closedCount = 0;

        for (DataSnapshot txnSnapshot : snapshot.getChildren()) {
            String status = txnSnapshot.child("status").getValue(String.class);
            if ("Close".equalsIgnoreCase(status)) {
                closedCount++;
            } else {
                openCount++;
            }
        }

        tvStatusOpen.setText(String.valueOf(openCount));
        tvStatusClose.setText(String.valueOf(closedCount));
        tvTotalTransactions.setText(String.valueOf(snapshot.getChildrenCount()));
    }

    private void updatePaginationControls() {
        if (allTransactions.isEmpty()) {
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

    private void highlightTransaction(String transactionId) {
        new Handler().postDelayed(() -> {
            for (int i = 0; i < transactionContainer.getChildCount(); i++) {
                LinearLayout rowLayout = (LinearLayout) transactionContainer.getChildAt(i);
                for (int j = 0; j < rowLayout.getChildCount(); j++) {
                    View card = rowLayout.getChildAt(j);
                    CardView cardView = card.findViewById(R.id.card_view_container);
                    if (cardView != null) {
                        cardView.setCardBackgroundColor(getResources().getColor(R.color.sandal_color));
                        new Handler().postDelayed(() -> {
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

    private void displayCurrentPage() {
        transactionContainer.removeAllViews();

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());

        // Get current page transactions to check selection state
        List<String> currentPageIds = new ArrayList<>();
        for (int i = start; i < end; i++) {
            currentPageIds.add(allTransactions.get(i).getKey());
        }

        // Check if all items on current page are selected
        boolean allSelected = true;
        for (String id : currentPageIds) {
            if (!selectedTransactionIds.contains(id)) {
                allSelected = false;
                break;
            }
        }

        // Update Select All button state based on current page selection
        if (!currentPageIds.isEmpty()) {
            if (allSelected && selectedTransactionIds.size() >= currentPageIds.size()) {
                isSelectAll = true;
                btnSelectAll.setText("Deselect All");
            } else {
                isSelectAll = false;
                btnSelectAll.setText("Select All");
            }
        }

        for (int i = start; i < end; i += 2) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(2f);

            if (i < end) {
                addCardToRow(rowLayout, allTransactions.get(i), 0);
            }

            if (i + 1 < end) {
                addCardToRow(rowLayout, allTransactions.get(i + 1), 1);
            }

            transactionContainer.addView(rowLayout);
        }

        updatePaginationInfo();
        updateNavigationButtonsState();

        if (allTransactions.isEmpty()) {
            showEmptyState();
        }
    }

    private void goToFirstPage() {
        if (currentPage > 0) {
            currentPage = 0;
            displayCurrentPage();
            // Reset Select All state when changing pages
            if (isSelectAll) {
                isSelectAll = false;
                if (btnSelectAll != null) {
                    btnSelectAll.setText("Select All");
                }
            }
        }
    }

    private void goToLastPage() {
        int lastPage = getTotalPages() - 1;
        if (currentPage < lastPage) {
            currentPage = lastPage;
            displayCurrentPage();
            // Reset Select All state when changing pages
            if (isSelectAll) {
                isSelectAll = false;
                if (btnSelectAll != null) {
                    btnSelectAll.setText("Select All");
                }
            }
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

                // Reset Select All state when changing pages
                if (isSelectAll) {
                    isSelectAll = false;
                    if (btnSelectAll != null) {
                        btnSelectAll.setText("Select All");
                    }
                }
            } else {
                Toast.makeText(this, "Please enter a valid page number (1-" + totalPages + ")",
                        Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid page number", Toast.LENGTH_SHORT).show();
        }
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) allTransactions.size() / PAGE_SIZE);
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

    private void addCardToRow(LinearLayout rowLayout, DataSnapshot txnSnapshot, int column) {
        try {
            String transactionId = txnSnapshot.getKey();
            String transactionName = txnSnapshot.child("transactionName").getValue(String.class);
            String status = txnSnapshot.child("status").getValue(String.class);

            if (transactionName == null) transactionName = "Unnamed Transaction";
            if (status == null) status = "Open";

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

            // Status color: RED for CLOSE, GREEN for OPEN
            if ("Close".equalsIgnoreCase(status)) {
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }

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
                        if (btnSelectAll != null) {
                            btnSelectAll.setText("Select All");
                        }
                    }
                }
                updateCardColor(cardViewContainer, isChecked);
                updateCloseButton();
                updateFinalizeButton();

                // Update Select All button state
                updateSelectAllButtonState();
            });

            btnEdit.setOnClickListener(v -> editTransaction(transactionId, txnSnapshot));
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

    // Helper method to update Select All button state based on current page selection
    private void updateSelectAllButtonState() {
        if (btnSelectAll == null || allTransactions.isEmpty()) return;

        int start = currentPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allTransactions.size());

        if (start >= allTransactions.size()) {
            btnSelectAll.setText("Select All");
            isSelectAll = false;
            return;
        }

        // Check if all items on current page are selected
        boolean allSelected = true;
        for (int i = start; i < end; i++) {
            String id = allTransactions.get(i).getKey();
            if (!selectedTransactionIds.contains(id)) {
                allSelected = false;
                break;
            }
        }

        if (allSelected && !selectedTransactionIds.isEmpty()) {
            isSelectAll = true;
            btnSelectAll.setText("Deselect All");
        } else {
            isSelectAll = false;
            btnSelectAll.setText("Select All");
        }
    }

    private void updateCardColor(CardView cardView, boolean isSelected) {
        if (isSelected) {
            cardView.setCardBackgroundColor(getResources().getColor(R.color.sandal_color));
        } else {
            cardView.setCardBackgroundColor(getResources().getColor(android.R.color.white));
        }
    }

    private void showEmptyState() {
        TextView emptyText = new TextView(this);
        emptyText.setText("No transactions found\nAdd a transaction using the + button");
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

    private void updateFinalizeButton() {
        // btnFinalize removed - only updating selected count
        tvSelectedCount.setText(String.valueOf(selectedTransactionIds.size()));
    }

    private void editTransaction(String transactionId, DataSnapshot snapshot) {
        lastEditedTransactionId = transactionId;
        shouldRestorePage = true;

        Intent editIntent = new Intent(EditTransactionActivity.this, UpdateTransactionActivity.class);
        editIntent.putExtra("userUid", userUid);
        editIntent.putExtra("customerId", customerId);
        editIntent.putExtra("transactionId", transactionId);

        if (snapshot.child("transactionName").exists()) {
            editIntent.putExtra("transactionName", snapshot.child("transactionName").getValue(String.class));
        }
        if (snapshot.child("status").exists()) {
            editIntent.putExtra("status", snapshot.child("status").getValue(String.class));
        }
        if (snapshot.child("date").exists()) {
            editIntent.putExtra("date", snapshot.child("date").getValue(String.class));
        }

        startActivityForResult(editIntent, 1);
    }

    private void deleteTransaction(String transactionId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Transaction")
                .setMessage("Are you sure you want to move this transaction to recycle bin?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DatabaseReference recycleRef = FirebaseDatabase.getInstance()
                            .getReference("Users")
                            .child(userUid)
                            .child("recyclebin")
                            .child("transactions")
                            .child(transactionId);

                    transactionRef.child(transactionId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                HashMap<String, Object> txnData = (HashMap<String, Object>) snapshot.getValue();
                                if (txnData == null) txnData = new HashMap<>();
                                txnData.put("customerId", customerId);

                                recycleRef.setValue(txnData)
                                        .addOnSuccessListener(aVoid -> {
                                            transactionRef.child(transactionId).removeValue()
                                                    .addOnSuccessListener(aVoid2 -> {
                                                        Toast.makeText(EditTransactionActivity.this,
                                                                "Moved to Recycle Bin", Toast.LENGTH_SHORT).show();
                                                        selectedTransactionIds.remove(transactionId);
                                                        updateCloseButton();
                                                        updateFinalizeButton();
                                                        updateSelectAllButtonState();
                                                    })
                                                    .addOnFailureListener(e ->
                                                            Toast.makeText(EditTransactionActivity.this,
                                                                    "Failed to remove", Toast.LENGTH_SHORT).show());
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(EditTransactionActivity.this,
                                                        "Failed to move to recycle bin", Toast.LENGTH_SHORT).show());
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(EditTransactionActivity.this,
                                    "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("No", null)
                .show();
    }

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

                final String finalStatus = status != null ? status : "Open";
                final String finalDate = date != null ? date : new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
                final String finalParticulars = particulars != null ? particulars : "";

                final boolean showCashDetails = txnSnapshot.child("showCashDetails").exists() &&
                        Boolean.TRUE.equals(txnSnapshot.child("showCashDetails").getValue(Boolean.class));

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
                                    showCashDetails,
                                    entryNumber
                            );

                            runOnUiThread(() -> {
                                progressDialog.dismiss();
                                if (pdfBytes != null) {
                                    generatedPdfBytes = pdfBytes;
                                    showPdfOptionsDialog(customerName, finalTransactionName);
                                } else {
                                    Toast.makeText(EditTransactionActivity.this,
                                            "Failed to generate PDF", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(EditTransactionActivity.this,
                                    "Error fetching customer data", Toast.LENGTH_SHORT).show();
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(EditTransactionActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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

    private byte[] generateFastPDF(DataSnapshot txnSnapshot, String transactionName,
                                   String status, String customerName, String customerPhone,
                                   String customerAddress, String date, String particulars,
                                   boolean showCashDetails, int entryNumber) {
        try {
            final List<HashMap<String, Object>> transactionRows = new ArrayList<>();
            final List<HashMap<String, Object>> cashRows = new ArrayList<>();
            final HashMap<String, Object> totalsData = new HashMap<>();

            extractTransactionData(txnSnapshot, transactionRows, cashRows, totalsData, date, showCashDetails);

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
                    showCashDetails,
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

    private void showPdfOptionsDialog(String customerName, String transactionName) {
        try {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            View dialogView = getLayoutInflater().inflate(R.layout.pdf_options_dialog, null);
            builder.setView(dialogView);

            android.app.AlertDialog dialog = builder.create();
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

    // ===================== VIEW PDF CHOICE DIALOG (USES XML LAYOUT) =====================
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
                openPdfInAppViewer(customerName, transactionName);
            });

            btnViewOtherApp.setOnClickListener(v -> {
                dialog.dismiss();
                viewPdfInExternalApp(customerName, transactionName);
            });

            btnCancelChoice.setOnClickListener(v -> dialog.dismiss());

            // "Always Use" - Store preference and use the selected option
            btnAlwaysUse.setOnClickListener(v -> {
                dialog.dismiss();
                // You can save this preference in SharedPreferences
                // For now, just open in-app viewer by default
                openPdfInAppViewer(customerName, transactionName);
                Toast.makeText(this, "Preference saved. Opening in-app viewer.", Toast.LENGTH_SHORT).show();
            });

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: if XML dialog fails, open in-app viewer directly
            openPdfInAppViewer(customerName, transactionName);
        }
    }

    // ===================== OPEN PDF IN OUR OWN IN-APP VIEWER (NEW) =====================
    // Writes the PDF bytes to a cache file, then launches PdfViewerActivity which renders
    // every page using Android's built-in PdfRenderer - no external app or library needed,
    // and it always displays the PDF correctly since we render it ourselves.
    private void openPdfInAppViewer(String customerName, String transactionName) {
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

            Intent intent = new Intent(EditTransactionActivity.this, PdfViewerActivity.class);
            intent.putExtra("pdfFilePath", tempFile.getAbsolutePath());
            intent.putExtra("pdfTitle", transactionName + " - " + customerName);
            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ===================== VIEW PDF IN EXTERNAL APP (FIXED) =====================
    // FIX: original code used intent.setFlags(...) followed by intent.addFlags(...).
    // setFlags() OVERWRITES all flags, so it must never be called right before addFlags()
    // for a different flag - otherwise you lose whichever flag was set first depending on order.
    // Below uses addFlags() consistently for both flags so both are always present.
    private void viewPdfInExternalApp(String customerName, String transactionName) {
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
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
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

                android.app.AlertDialog dialog = builder.create();
                dialog.show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
            }
        });
    }

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

                    String timestamp = new SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(new Date());
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

    private void extractTransactionData(DataSnapshot txnSnapshot,
                                        List<HashMap<String, Object>> transactionRows,
                                        List<HashMap<String, Object>> cashRows,
                                        HashMap<String, Object> totalsData,
                                        String date,
                                        boolean showCashDetails) {
        try {
            // Extract static transaction row
            if (txnSnapshot.child("staticDebitPure").exists() || txnSnapshot.child("staticDebitWeight").exists()) {
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

            // Extract dynamic transaction rows
            DataSnapshot dynamicRows = txnSnapshot.child("dynamicRows");
            if (dynamicRows.exists()) {
                for (DataSnapshot row : dynamicRows.getChildren()) {
                    HashMap<String, Object> rowData = new HashMap<>();
                    rowData.put("date", row.child("date").getValue(String.class) != null ?
                            row.child("date").getValue(String.class) : date);

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

            // Extract CASH DETAILS
            DataSnapshot cashDetails = txnSnapshot.child("cashDetails");
            double totalCashDebitPure = 0.0;
            double totalCashCreditPure = 0.0;

            if (cashDetails.exists()) {
                String cashName = cashDetails.child("name").getValue(String.class);

                DataSnapshot staticCash = cashDetails.child("static");
                if (staticCash.exists()) {
                    HashMap<String, Object> staticCashRow = new HashMap<>();
                    staticCashRow.put("date", staticCash.child("date").getValue(String.class) != null ?
                            staticCash.child("date").getValue(String.class) : date);
                    staticCashRow.put("name", cashName != null && !cashName.isEmpty() ? cashName : "Cash");

                    double debitPure = safeParseDouble(staticCash.child("pureDebit").getValue());
                    staticCashRow.put("debitCash", safeParseDouble(staticCash.child("debit").getValue()));
                    staticCashRow.put("debitRate", safeParseDouble(staticCash.child("debitRateCutting").getValue()));
                    staticCashRow.put("pureDebit", debitPure);
                    totalCashDebitPure += debitPure;

                    double creditPure = safeParseDouble(staticCash.child("pureCredit").getValue());
                    staticCashRow.put("creditCash", safeParseDouble(staticCash.child("credit").getValue()));
                    staticCashRow.put("creditRate", safeParseDouble(staticCash.child("creditRateCutting").getValue()));
                    staticCashRow.put("pureCredit", creditPure);
                    totalCashCreditPure += creditPure;

                    cashRows.add(staticCashRow);
                }

                DataSnapshot dynamicCash = cashDetails.child("dynamic");
                if (dynamicCash.exists()) {
                    for (DataSnapshot cashRowSnapshot : dynamicCash.getChildren()) {
                        HashMap<String, Object> cashRowData = new HashMap<>();
                        cashRowData.put("date", cashRowSnapshot.child("date").getValue(String.class) != null ?
                                cashRowSnapshot.child("date").getValue(String.class) : date);

                        String dynamicCashName = cashRowSnapshot.child("name").getValue(String.class);
                        cashRowData.put("name", dynamicCashName != null ? dynamicCashName : "");

                        double debitPure = safeParseDouble(cashRowSnapshot.child("pureDebit").getValue());
                        cashRowData.put("debitCash", safeParseDouble(cashRowSnapshot.child("debitCash").getValue()));
                        cashRowData.put("debitRate", safeParseDouble(cashRowSnapshot.child("debitRate").getValue()));
                        cashRowData.put("pureDebit", debitPure);
                        totalCashDebitPure += debitPure;

                        double creditPure = safeParseDouble(cashRowSnapshot.child("pureCredit").getValue());
                        cashRowData.put("creditCash", safeParseDouble(cashRowSnapshot.child("creditCash").getValue()));
                        cashRowData.put("creditRate", safeParseDouble(cashRowSnapshot.child("creditRate").getValue()));
                        cashRowData.put("pureCredit", creditPure);
                        totalCashCreditPure += creditPure;

                        cashRows.add(cashRowData);
                    }
                }
            }

            // Extract transaction totals
            double transactionDebitPure = safeParseDouble(txnSnapshot.child("totalDebitPure").getValue());
            double transactionCreditPure = safeParseDouble(txnSnapshot.child("totalCreditPure").getValue());

            double finalDebitPure = transactionDebitPure;
            double finalCreditPure = transactionCreditPure;

            if (showCashDetails) {
                finalDebitPure = transactionDebitPure + totalCashDebitPure;
                finalCreditPure = transactionCreditPure + totalCashCreditPure;
            }

            double difference = finalDebitPure - finalCreditPure;
            boolean isBalancePure = difference >= 0;
            String balanceStatus = isBalancePure ? "Balance Pure" : "Ex Pure";
            double balanceAmount = Math.abs(difference);

            totalsData.put("totalDebitPure", transactionDebitPure);
            totalsData.put("totalCreditPure", transactionCreditPure);
            totalsData.put("totalCashDebitPure", totalCashDebitPure);
            totalsData.put("totalCashCreditPure", totalCashCreditPure);
            totalsData.put("combinedDebitPure", finalDebitPure);
            totalsData.put("combinedCreditPure", finalCreditPure);
            totalsData.put("combinedDifference", difference);
            totalsData.put("combinedBalanceStatus", balanceStatus);
            totalsData.put("combinedBalanceAmount", balanceAmount);
            totalsData.put("showCashDetails", showCashDetails);
            totalsData.put("cashRemaining", safeParseDouble(txnSnapshot.child("cashRemaining").getValue()));
            totalsData.put("cashRemainingLabel", txnSnapshot.child("cashRemainingLabel").getValue(String.class));

        } catch (Exception e) {
            Log.e("PDFData", "Error extracting transaction data: " + e.getMessage());
            e.printStackTrace();
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

    private void updateStats() {
        int openCount = 0;
        int closedCount = 0;

        for (DataSnapshot transaction : allTransactions) {
            String status = transaction.child("status").getValue(String.class);
            if ("Close".equalsIgnoreCase(status)) {
                closedCount++;
            } else {
                openCount++;
            }
        }

        tvStatusOpen.setText(String.valueOf(openCount));
        tvStatusClose.setText(String.valueOf(closedCount));
        tvTotalTransactions.setText(String.valueOf(allTransactions.size()));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) {
            shouldRestorePage = true;
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
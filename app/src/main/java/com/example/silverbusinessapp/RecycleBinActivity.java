package com.example.silverbusinessapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RecycleBinActivity extends AppCompatActivity {

    private TableLayout recycleBinTable;
    private DatabaseReference recycleBinRef, customerRef;
    private String userUid;

    // New UI components
    private TextView customersCount, transactionsCount, totalCount, pageInfo;
    private LinearLayout emptyState, pageNumbersContainer;
    private Spinner itemsPerPageSpinner;
    private ImageView btnFirstPage, btnPrevPage, btnNextPage, btnLastPage;
    private TextView btnEmptyBin, btnRestoreAll, sortButton;

    // Pagination variables
    private List<RecycleItem> allItems = new ArrayList<>();
    private List<RecycleItem> filteredItems = new ArrayList<>();
    private List<RecycleItem> currentPageItems = new ArrayList<>();
    private int currentPage = 1;
    private int itemsPerPage = 10;
    private int totalPages = 0;
    private int customerCount = 0;
    private int transactionCount = 0;

    // Filter variables
    private String currentFilter = "All"; // "All", "Customer", "Transaction"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycle_bin);

        userUid = getIntent().getStringExtra("userUid");
        if (userUid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupFirebase();
        setupEventListeners();
        loadRecycleBinData();
    }

    private void initializeViews() {
        recycleBinTable = findViewById(R.id.recycle_bin_table);

        // Statistics TextViews
        customersCount = findViewById(R.id.customers_count);
        transactionsCount = findViewById(R.id.transactions_count);
        totalCount = findViewById(R.id.total_count);

        // Pagination views
        pageInfo = findViewById(R.id.page_info);
        emptyState = findViewById(R.id.empty_state);
        pageNumbersContainer = findViewById(R.id.page_numbers_container);
        itemsPerPageSpinner = findViewById(R.id.items_per_page_spinner);

        // Buttons
        btnFirstPage = findViewById(R.id.btn_first_page);
        btnPrevPage = findViewById(R.id.btn_prev_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnLastPage = findViewById(R.id.btn_last_page);
        btnEmptyBin = findViewById(R.id.btn_empty_bin);
        btnRestoreAll = findViewById(R.id.btn_restore_all);
        sortButton = findViewById(R.id.sort_button);

        // Back button
        ImageView backButton = findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> finish());

        // Setup items per page spinner
        setupSpinner();
    }

    private void setupFirebase() {
        recycleBinRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userUid)
                .child("recyclebin");

        customerRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userUid)
                .child("customers");
    }

    private void setupEventListeners() {
        // Pagination buttons
        btnFirstPage.setOnClickListener(v -> goToFirstPage());
        btnPrevPage.setOnClickListener(v -> goToPreviousPage());
        btnNextPage.setOnClickListener(v -> goToNextPage());
        btnLastPage.setOnClickListener(v -> goToLastPage());

        // Action buttons
        btnEmptyBin.setOnClickListener(v -> confirmEmptyBin());
        btnRestoreAll.setOnClickListener(v -> confirmRestoreAll());

        // Filter button (renamed from sort)
        sortButton.setOnClickListener(v -> showFilterOptions());

        // Items per page spinner
        itemsPerPageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                itemsPerPage = Integer.parseInt(parent.getItemAtPosition(position).toString());
                currentPage = 1;
                updatePagination();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSpinner() {
        try {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                    R.array.items_per_page_options, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            itemsPerPageSpinner.setAdapter(adapter);

            for (int i = 0; i < itemsPerPageSpinner.getCount(); i++) {
                if (Integer.parseInt(itemsPerPageSpinner.getItemAtPosition(i).toString()) == itemsPerPage) {
                    itemsPerPageSpinner.setSelection(i);
                    break;
                }
            }
        } catch (Exception e) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item,
                    new String[]{"5", "10", "20", "50"});
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            itemsPerPageSpinner.setAdapter(adapter);
        }
    }

    private void loadRecycleBinData() {
        allItems.clear();
        customerCount = 0;
        transactionCount = 0;

        // Load deleted customers
        recycleBinRef.child("customers").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                customerCount = (int) snapshot.getChildrenCount();
                for (DataSnapshot custSnap : snapshot.getChildren()) {
                    HashMap<String, Object> custData = (HashMap<String, Object>) custSnap.getValue();
                    if (custData != null) {
                        allItems.add(new RecycleItem("Customer", custSnap.getKey(), custData));
                    }
                }

                // Load deleted transactions
                recycleBinRef.child("transactions").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot txnSnap) {
                        transactionCount = (int) txnSnap.getChildrenCount();
                        for (DataSnapshot txn : txnSnap.getChildren()) {
                            HashMap<String, Object> txnData = (HashMap<String, Object>) txn.getValue();
                            if (txnData != null) {
                                allItems.add(new RecycleItem("Transaction", txn.getKey(), txnData));
                            }
                        }

                        updateStatistics();
                        applyFilter(currentFilter);
                        updatePagination();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(RecycleBinActivity.this, "Error loading transactions: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RecycleBinActivity.this, "Error loading customers: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatistics() {
        customersCount.setText(String.valueOf(customerCount));
        transactionsCount.setText(String.valueOf(transactionCount));
        totalCount.setText(String.valueOf(customerCount + transactionCount));
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        filteredItems.clear();

        if (filter.equals("All")) {
            filteredItems.addAll(allItems);
        } else {
            for (RecycleItem item : allItems) {
                if (item.getType().equals(filter)) {
                    filteredItems.add(item);
                }
            }
        }

        // Update button text
        sortButton.setText("Filter: " + filter + " ▼");

        // Reset to first page when filter changes
        currentPage = 1;
        updatePagination();
    }

    private void updatePagination() {
        totalPages = (int) Math.ceil((double) filteredItems.size() / itemsPerPage);

        if (totalPages == 0) {
            totalPages = 1;
        }

        if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        updatePageItems();
        updatePageInfo();
        updatePageButtons();
        updatePageNumbers();
        updateEmptyState();
    }

    private void updatePageItems() {
        currentPageItems.clear();

        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            currentPageItems.add(filteredItems.get(i));
        }

        populateTable();
    }

    private void populateTable() {
        recycleBinTable.removeAllViews();

        for (RecycleItem item : currentPageItems) {
            addRow(item);
        }
    }

    // NEW METHOD: Check if customer exists in recycle bin for a transaction
    private boolean isCustomerInRecycleBin(String customerId) {
        for (RecycleItem item : allItems) {
            if (item.getType().equals("Customer") && item.getId().equals(customerId)) {
                return true;
            }
        }
        return false;
    }

    // NEW METHOD: Get customer name from recycle bin
    private String getCustomerNameFromRecycleBin(String customerId) {
        for (RecycleItem item : allItems) {
            if (item.getType().equals("Customer") && item.getId().equals(customerId)) {
                return item.getName();
            }
        }
        return "Unknown Customer";
    }

    // NEW METHOD: Get customer name from main database
    private void getCustomerName(String customerId, CustomerNameCallback callback) {
        customerRef.child(customerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    callback.onNameReceived(name != null ? name : "Unknown Customer");
                } else {
                    callback.onNameReceived("Customer Not Found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onNameReceived("Error Loading Customer");
            }
        });
    }

    interface CustomerNameCallback {
        void onNameReceived(String customerName);
    }

    private void addRow(RecycleItem item) {
        TableRow row = new TableRow(this);
        row.setPadding(16, 12, 16, 12);
        row.setBackground(ContextCompat.getDrawable(this, R.drawable.table_row_background));

        // Type column - using icons instead of text
        ImageView typeIcon = new ImageView(this);
        typeIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        // Set icon based on type
        if (item.getType().equals("Customer")) {
            typeIcon.setImageResource(R.drawable.person); // Customer icon
        } else {
            typeIcon.setImageResource(R.drawable.notes); // Transaction icon
        }

        // Set very compact size for icon
        TableRow.LayoutParams iconParams = new TableRow.LayoutParams(
                dpToPx(24), // Small icon size
                dpToPx(24)
        );
        iconParams.setMargins(8, 0, 12, 0); // Margins around icon
        iconParams.gravity = Gravity.CENTER_VERTICAL;
        typeIcon.setLayoutParams(iconParams);

        // Name column
        TextView nameView = new TextView(this);

        // For transactions, show transaction name + customer name if available
        if (item.getType().equals("Transaction")) {
            String customerId = (String) item.getData().get("customerId");
            if (customerId != null && isCustomerInRecycleBin(customerId)) {
                String customerName = getCustomerNameFromRecycleBin(customerId);
                nameView.setText(item.getName() + " (" + customerName + ")");
            } else {
                nameView.setText(item.getName());
            }
        } else {
            nameView.setText(item.getName());
        }

        nameView.setPadding(16, 8, 16, 8);
        nameView.setTextSize(14);
        nameView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        nameView.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));

        // Action buttons container - Use weight to push buttons to edges
        LinearLayout actionContainer = new LinearLayout(this);
        actionContainer.setOrientation(LinearLayout.HORIZONTAL);
        actionContainer.setLayoutParams(new TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f));
        actionContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Get the ripple effect from theme
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);

        // Restore button - positioned at start
        ImageView restoreBtn = new ImageView(this);
        restoreBtn.setImageResource(R.drawable.ic_restore);
        restoreBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(48, 48);
        restoreParams.gravity = android.view.Gravity.START;
        restoreBtn.setLayoutParams(restoreParams);
        restoreBtn.setPadding(12, 12, 12, 12);
        restoreBtn.setColorFilter(ContextCompat.getColor(this, R.color.green_500));
        restoreBtn.setBackgroundResource(R.drawable.button_border_green);
        restoreBtn.setClickable(true);
        restoreBtn.setFocusable(true);

        // NEW: Enhanced transaction restore logic
        if (item.getType().equals("Transaction")) {
            String customerId = (String) item.getData().get("customerId");
            if (customerId != null && isCustomerInRecycleBin(customerId)) {
                // Customer is in recycle bin - show customer name in restore dialog
                restoreBtn.setOnClickListener(v -> showCustomerRestoreFirstDialog(item, customerId));
            } else {
                // Customer is not in recycle bin - enable restore
                restoreBtn.setOnClickListener(v -> restoreItem(item));
            }
        } else {
            // Customer item - always enable restore
            restoreBtn.setOnClickListener(v -> restoreItem(item));
        }

        // Spacer view to push delete button to the end
        View spacer = new View(this);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 0, 1f); // Takes all available space
        spacer.setLayoutParams(spacerParams);

        // Delete button - positioned at end
        ImageView deleteBtn = new ImageView(this);
        deleteBtn.setImageResource(R.drawable.ic_delete);
        deleteBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(48, 48);
        deleteParams.gravity = android.view.Gravity.END;
        deleteBtn.setLayoutParams(deleteParams);
        deleteBtn.setPadding(12, 12, 12, 12);
        deleteBtn.setColorFilter(ContextCompat.getColor(this, R.color.red_500));
        deleteBtn.setBackgroundResource(R.drawable.button_border_red);
        deleteBtn.setClickable(true);
        deleteBtn.setFocusable(true);

        // NEW: Enhanced delete logic for customers (delete related transactions)
        if (item.getType().equals("Customer")) {
            deleteBtn.setOnClickListener(v -> confirmPermanentDeleteWithTransactions(item));
        } else {
            deleteBtn.setOnClickListener(v -> confirmPermanentDelete(item));
        }

        // Add views to action container
        actionContainer.addView(restoreBtn);  // Start
        actionContainer.addView(spacer);      // Middle spacer (pushes delete to end)
        actionContainer.addView(deleteBtn);   // End

        // Add views to row
        row.addView(typeIcon); // Use icon instead of text view
        row.addView(nameView);
        row.addView(actionContainer);

        recycleBinTable.addView(row);

        // Add divider
        View divider = new View(this);
        divider.setLayoutParams(new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
        recycleBinTable.addView(divider);
    }

    // NEW METHOD: Show dialog to restore customer first
    private void showCustomerRestoreFirstDialog(RecycleItem transactionItem, String customerId) {
        String customerName = getCustomerNameFromRecycleBin(customerId);

        new AlertDialog.Builder(this)
                .setTitle("Restore Customer First")
                .setMessage("This transaction belongs to customer: " + customerName +
                        "\n\nYou need to restore the customer first before restoring this transaction." +
                        "\n\nDo you want to restore the customer now?")
                .setPositiveButton("Restore Customer", (dialog, which) -> {
                    // Find and restore the customer
                    for (RecycleItem item : allItems) {
                        if (item.getType().equals("Customer") && item.getId().equals(customerId)) {
                            restoreItem(item);
                            break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("View Customer", (dialog, which) -> {
                    // Filter to show only customers to help user find the right one
                    applyFilter("Customer");
                })
                .show();
    }

    // NEW METHOD: Confirm delete customer with all its transactions
    private void confirmPermanentDeleteWithTransactions(RecycleItem customerItem) {
        String customerName = customerItem.getName();
        int transactionCount = countTransactionsForCustomer(customerItem.getId());

        String message;
        if (transactionCount > 0) {
            message = "Are you sure you want to permanently delete customer '" + customerName +
                    "' and all " + transactionCount + " associated transactions? This action cannot be undone.";
        } else {
            message = "Are you sure you want to permanently delete customer '" + customerName + "'?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Permanent Delete Customer")
                .setMessage(message)
                .setPositiveButton("Delete All", (dialog, which) -> permanentDeleteCustomerWithTransactions(customerItem))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // NEW METHOD: Count transactions for a customer in recycle bin
    private int countTransactionsForCustomer(String customerId) {
        int count = 0;
        for (RecycleItem item : allItems) {
            if (item.getType().equals("Transaction")) {
                String itemCustomerId = (String) item.getData().get("customerId");
                if (customerId.equals(itemCustomerId)) {
                    count++;
                }
            }
        }
        return count;
    }

    // NEW METHOD: Delete customer and all its transactions
    private void permanentDeleteCustomerWithTransactions(RecycleItem customerItem) {
        String customerId = customerItem.getId();
        int transactionCount = countTransactionsForCustomer(customerId);

        // Delete customer first
        DatabaseReference customerDeleteRef = recycleBinRef.child("customers").child(customerId);
        customerDeleteRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    // Now delete all transactions for this customer
                    deleteAllTransactionsForCustomer(customerId, transactionCount);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete customer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // NEW METHOD: Delete all transactions for a customer
    private void deleteAllTransactionsForCustomer(String customerId, int expectedCount) {
        DatabaseReference transactionsRef = recycleBinRef.child("transactions");
        final int[] deletedCount = {0};

        for (RecycleItem item : new ArrayList<>(allItems)) {
            if (item.getType().equals("Transaction")) {
                String itemCustomerId = (String) item.getData().get("customerId");
                if (customerId.equals(itemCustomerId)) {
                    transactionsRef.child(item.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                deletedCount[0]++;
                                if (deletedCount[0] == expectedCount) {
                                    runOnUiThread(() -> {
                                        String message = "Customer and " + deletedCount[0] + " transactions deleted permanently";
                                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                                        loadRecycleBinData();
                                    });
                                }
                            })
                            .addOnFailureListener(e -> {
                                deletedCount[0]++;
                                // Continue even if some deletions fail
                                if (deletedCount[0] == expectedCount) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(this, "Customer deleted, but some transactions may not be removed", Toast.LENGTH_SHORT).show();
                                        loadRecycleBinData();
                                    });
                                }
                            });
                }
            }
        }

        // If no transactions found, just show success message
        if (expectedCount == 0) {
            Toast.makeText(this, "Customer deleted permanently", Toast.LENGTH_SHORT).show();
            loadRecycleBinData();
        }
    }

    // Helper method to convert dp to pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void updatePageInfo() {
        int startItem = (currentPage - 1) * itemsPerPage + 1;
        int endItem = Math.min(currentPage * itemsPerPage, filteredItems.size());

        pageInfo.setText("Showing " + startItem + "-" + endItem + " of " + filteredItems.size() + " items");
    }

    private void updatePageButtons() {
        boolean hasPrevious = currentPage > 1;
        boolean hasNext = currentPage < totalPages;

        btnFirstPage.setEnabled(hasPrevious);
        btnPrevPage.setEnabled(hasPrevious);
        btnNextPage.setEnabled(hasNext);
        btnLastPage.setEnabled(hasNext);

        int enabledColor = ContextCompat.getColor(this, R.color.text_secondary);
        int disabledColor = ContextCompat.getColor(this, R.color.text_disabled);

        btnFirstPage.setColorFilter(hasPrevious ? enabledColor : disabledColor);
        btnPrevPage.setColorFilter(hasPrevious ? enabledColor : disabledColor);
        btnNextPage.setColorFilter(hasNext ? enabledColor : disabledColor);
        btnLastPage.setColorFilter(hasNext ? enabledColor : disabledColor);
    }

    private void updatePageNumbers() {
        pageNumbersContainer.removeAllViews();

        int startPage = Math.max(1, currentPage - 2);
        int endPage = Math.min(totalPages, currentPage + 2);

        for (int i = startPage; i <= endPage; i++) {
            final int page = i;
            TextView pageNumber = new TextView(this);
            pageNumber.setText(String.valueOf(page));
            pageNumber.setTextSize(14);
            pageNumber.setPadding(16, 8, 16, 8);
            pageNumber.setBackground(page == currentPage ?
                    ContextCompat.getDrawable(this, R.drawable.page_number_active) :
                    ContextCompat.getDrawable(this, R.drawable.page_number_inactive));
            pageNumber.setTextColor(page == currentPage ?
                    ContextCompat.getColor(this, android.R.color.white) :
                    ContextCompat.getColor(this, R.color.text_secondary));
            pageNumber.setOnClickListener(v -> goToPage(page));

            pageNumbersContainer.addView(pageNumber);
        }
    }

    private void updateEmptyState() {
        if (filteredItems.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recycleBinTable.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recycleBinTable.setVisibility(View.VISIBLE);
        }
    }

    // Pagination methods
    private void goToFirstPage() {
        currentPage = 1;
        updatePagination();
    }

    private void goToPreviousPage() {
        if (currentPage > 1) {
            currentPage--;
            updatePagination();
        }
    }

    private void goToNextPage() {
        if (currentPage < totalPages) {
            currentPage++;
            updatePagination();
        }
    }

    private void goToLastPage() {
        currentPage = totalPages;
        updatePagination();
    }

    private void goToPage(int page) {
        currentPage = page;
        updatePagination();
    }

    // Filter functionality
    private void showFilterOptions() {
        String[] filterOptions = {"All", "Customer", "Transaction"};

        new AlertDialog.Builder(this)
                .setTitle("Filter By Type")
                .setItems(filterOptions, (dialog, which) -> {
                    applyFilter(filterOptions[which]);
                })
                .show();
    }

    // Action methods
    private void confirmEmptyBin() {
        if (allItems.isEmpty()) {
            Toast.makeText(this, "Recycle bin is already empty", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Empty Recycle Bin")
                .setMessage("Are you sure you want to permanently delete all items? This action cannot be undone.")
                .setPositiveButton("Empty Bin", (dialog, which) -> emptyBin())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void emptyBin() {
        recycleBinRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Recycle bin emptied", Toast.LENGTH_SHORT).show();
                    allItems.clear();
                    filteredItems.clear();
                    updateStatistics();
                    updatePagination();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to empty bin: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void confirmRestoreAll() {
        if (filteredItems.isEmpty()) {
            Toast.makeText(this, "No items to restore", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Restore All " + currentFilter + "s")
                .setMessage("Are you sure you want to restore all " + currentFilter.toLowerCase() + "s?")
                .setPositiveButton("Restore All", (dialog, which) -> restoreAllItems())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreAllItems() {
        int totalItems = filteredItems.size();
        final int[] restoredCount = {0};

        for (RecycleItem item : new ArrayList<>(filteredItems)) {
            restoreItemInternal(item, new RestoreCallback() {
                @Override
                public void onComplete(boolean success) {
                    restoredCount[0]++;
                    if (restoredCount[0] == totalItems) {
                        runOnUiThread(() -> {
                            Toast.makeText(RecycleBinActivity.this,
                                    "Restored " + restoredCount[0] + " " + currentFilter.toLowerCase() + "(s)", Toast.LENGTH_SHORT).show();
                            loadRecycleBinData();
                        });
                    }
                }
            });
        }
    }

    private interface RestoreCallback {
        void onComplete(boolean success);
    }

    private void restoreItemInternal(RecycleItem item, RestoreCallback callback) {
        if (item.getType().equals("Transaction")) {
            String customerId = (String) item.getData().get("customerId");
            if (customerId == null || customerId.isEmpty()) {
                Toast.makeText(this, "Cannot restore: Missing customer reference", Toast.LENGTH_SHORT).show();
                callback.onComplete(false);
                return;
            }

            // Check if customer exists in main database
            customerRef.child(customerId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Customer exists in main database - proceed with restoration
                        DatabaseReference txnTarget = customerRef.child(customerId).child("transactions").child(item.getId());
                        txnTarget.setValue(item.getData())
                                .addOnSuccessListener(aVoid ->
                                        recycleBinRef.child("transactions").child(item.getId()).removeValue()
                                                .addOnSuccessListener(v -> callback.onComplete(true))
                                                .addOnFailureListener(e -> callback.onComplete(false))
                                )
                                .addOnFailureListener(e -> callback.onComplete(false));
                    } else {
                        // Customer doesn't exist in main database
                        if (isCustomerInRecycleBin(customerId)) {
                            // Customer is in recycle bin - show message with customer name
                            String customerName = getCustomerNameFromRecycleBin(customerId);
                            runOnUiThread(() -> {
                                Toast.makeText(RecycleBinActivity.this,
                                        "First restore the customer '" + customerName + "' to restore this transaction",
                                        Toast.LENGTH_LONG).show();
                            });
                            callback.onComplete(false);
                        } else {
                            // Customer doesn't exist anywhere
                            Toast.makeText(RecycleBinActivity.this, "Cannot restore: Customer not found", Toast.LENGTH_SHORT).show();
                            callback.onComplete(false);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    callback.onComplete(false);
                }
            });
        } else if (item.getType().equals("Customer")) {
            // Customer restoration logic
            DatabaseReference targetRef = customerRef.child(item.getId());
            targetRef.setValue(item.getData())
                    .addOnSuccessListener(aVoid ->
                            recycleBinRef.child("customers").child(item.getId()).removeValue()
                                    .addOnSuccessListener(v -> callback.onComplete(true))
                                    .addOnFailureListener(e -> callback.onComplete(false))
                    )
                    .addOnFailureListener(e -> callback.onComplete(false));
        } else {
            callback.onComplete(false);
        }
    }

    private void restoreItem(RecycleItem item) {
        restoreItemInternal(item, new RestoreCallback() {
            @Override
            public void onComplete(boolean success) {
                runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(RecycleBinActivity.this, item.getType() + " restored", Toast.LENGTH_SHORT).show();
                        loadRecycleBinData();
                    } else {
                        Toast.makeText(RecycleBinActivity.this, "Failed to restore " + item.getType(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void confirmPermanentDelete(RecycleItem item) {
        new AlertDialog.Builder(this)
                .setTitle("Permanent Delete")
                .setMessage("Are you sure you want to permanently delete this " + item.getType() + "?")
                .setPositiveButton("Delete", (dialog, which) -> permanentDelete(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void permanentDelete(RecycleItem item) {
        DatabaseReference deleteRef = recycleBinRef.child(item.getType().equals("Customer") ? "customers" : "transactions").child(item.getId());
        deleteRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, item.getType() + " deleted permanently", Toast.LENGTH_SHORT).show();
                    loadRecycleBinData();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // RecycleItem class
    private static class RecycleItem {
        private String type;
        private String id;
        private HashMap<String, Object> data;

        public RecycleItem(String type, String id, HashMap<String, Object> data) {
            this.type = type;
            this.id = id;
            this.data = data;
        }

        public String getType() { return type; }
        public String getId() { return id; }
        public HashMap<String, Object> getData() { return data; }

        public String getName() {
            if (type.equals("Customer")) {
                return String.valueOf(data.get("name"));
            } else {
                return String.valueOf(data.get("transactionName"));
            }
        }
    }
}
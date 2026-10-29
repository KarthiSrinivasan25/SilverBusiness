package com.example.silverbusinessapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

interface OnCustomerActionListener {
    void onEditCustomer(Customer customer);
    void onDeleteCustomer(Customer customer);
}

public class ViewCustomerActivity extends AppCompatActivity {

    private RecyclerView customerRecyclerView;
    private DatabaseReference customerRef;
    private DatabaseReference transactionRef, recycleBinRef;
    private String userUid;

    private static final int PAGE_SIZE = 6;
    private String lastKey = null;
    private Stack<String> previousPageKeys = new Stack<>();
    private List<Customer> currentCustomers = new ArrayList<>();

    private CustomerAdapter adapter;

    // Header views
    private TextView tvTotalCustomers, tvTotalPages, tvCurrentPage;
    private ProgressBar progressPage;
    private TextView tvProgressPercent;
    private int currentPage = 1;
    private int totalPages = 0;
    private int totalCustomerCount = 0;

    // New pagination views
    private ImageButton btnFirstPage, btnLastPage, btnNextPage, btnPreviousPage;
    private Button btnGoToPage;
    private EditText etGoToPage;
    private TextView tvTotalPagesDisplay, tvCurrentPageNumber;

    // Year filter card (the CURRENT stat card, now clickable)
    private View cardCurrentPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_customer);

        userUid = getIntent().getStringExtra("userUid");
        if (userUid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        customerRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userUid)
                .child("customers");

        transactionRef = FirebaseDatabase.getInstance().getReference("Users")
                .child(userUid)
                .child("transactions");
        recycleBinRef = FirebaseDatabase.getInstance().getReference("Users").child(userUid).child("recyclebin");

        // Initialize all views
        initializeViews();

        // Setup RecyclerView with GridLayoutManager (2 columns - 2 cards per row)
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        customerRecyclerView.setLayoutManager(layoutManager);

        // Initialize adapter with empty list
        adapter = new CustomerAdapter(new ArrayList<>());
        customerRecyclerView.setAdapter(adapter);

        // Set adapter listener
        adapter.setOnCustomerActionListener(new OnCustomerActionListener() {
            @Override
            public void onEditCustomer(Customer customer) {
                HashMap<String, Object> data = new HashMap<>();
                data.put("name", customer.getName());
                data.put("phone", customer.getPhone());
                data.put("date", customer.getDate());
                data.put("address", customer.getAddress());
                showEditOptionsDialog(customer.getId(), data);
            }

            @Override
            public void onDeleteCustomer(Customer customer) {
                moveToRecycleBin(customer.getId());
            }
        });

        // Setup pagination click listeners
        setupPaginationClickListeners();

        // Load total customer count first
        loadTotalCustomerCount();
        loadCustomerData();
    }

    private void initializeViews() {
        // RecyclerView
        customerRecyclerView = findViewById(R.id.customer_recycler_view);

        // Initialize header views
        tvTotalCustomers = findViewById(R.id.tv_total_customers);
        tvTotalPages = findViewById(R.id.tv_total_pages);
        tvCurrentPage = findViewById(R.id.tv_current_page);
        progressPage = findViewById(R.id.progress_page);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);

        // Initialize new pagination views
        btnFirstPage = findViewById(R.id.btn_first_page);
        btnPreviousPage = findViewById(R.id.btn_previous_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnLastPage = findViewById(R.id.btn_last_page);
        btnGoToPage = findViewById(R.id.btn_go_to_page);
        etGoToPage = findViewById(R.id.et_go_to_page);
        tvTotalPagesDisplay = findViewById(R.id.tv_total_pages_display);
        tvCurrentPageNumber = findViewById(R.id.tv_current_page_number);

        // Set initial text
        tvTotalPagesDisplay.setText("/ 0");
        if (tvCurrentPageNumber != null) {
            tvCurrentPageNumber.setText("1");
        }

        // CURRENT stat card - clicking opens YearlyCustomersActivity
        cardCurrentPage = findViewById(R.id.card_current_page);
        if (cardCurrentPage != null) {
            cardCurrentPage.setOnClickListener(v -> openYearlyCustomersScreen());
        } else if (tvCurrentPage != null) {
            tvCurrentPage.setOnClickListener(v -> openYearlyCustomersScreen());
        }
    }

    private void setupPaginationClickListeners() {
        btnFirstPage.setOnClickListener(v -> goToFirstPage());
        btnPreviousPage.setOnClickListener(v -> loadPreviousPage());
        btnNextPage.setOnClickListener(v -> loadNextPage());
        btnLastPage.setOnClickListener(v -> goToLastPage());
        btnGoToPage.setOnClickListener(v -> goToSpecificPage());

        etGoToPage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                goToSpecificPage();
                return true;
            }
            return false;
        });
    }

    // ===================== MODIFIED: Group customers by YEAR from their DATE field =====================
    private void openYearlyCustomersScreen() {
        // Get current year
        String currentYear = getCurrentYear();

        // Show loading indicator
        Toast.makeText(this, "Loading customers by year...", Toast.LENGTH_SHORT).show();

        // Fetch ALL customers from Firebase
        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Map to store customers grouped by year
                HashMap<String, ArrayList<Customer>> yearMap = new HashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    HashMap<String, Object> data = (HashMap<String, Object>) child.getValue();
                    if (data != null) {
                        String customerDate = data.get("date") != null ? data.get("date").toString() : "";
                        String customerYear = extractYear(customerDate);

                        // Skip if year is empty or current year
                        if (customerYear.isEmpty() || customerYear.equals(currentYear)) {
                            continue; // Current year customers stay in ViewCustomerActivity
                        }

                        // Create customer object
                        Customer customer = new Customer(
                                child.getKey(),
                                data.get("name") != null ? data.get("name").toString() : "",
                                data.get("phone") != null ? data.get("phone").toString() : "",
                                customerDate,
                                data.get("address") != null ? data.get("address").toString() : "",
                                0
                        );

                        // Add to year map
                        if (!yearMap.containsKey(customerYear)) {
                            yearMap.put(customerYear, new ArrayList<>());
                        }
                        yearMap.get(customerYear).add(customer);
                    }
                }

                // Check if there are any customers from other years
                if (yearMap.isEmpty()) {
                    Toast.makeText(ViewCustomerActivity.this,
                            "No customers found from other years. All customers are from " + currentYear,
                            Toast.LENGTH_LONG).show();
                } else {
                    // Log how many customers per year
                    for (String year : yearMap.keySet()) {
                        Log.d("YearlyFilter", "Year " + year + ": " + yearMap.get(year).size() + " customers");
                    }
                    Toast.makeText(ViewCustomerActivity.this,
                            "Found customers from " + yearMap.size() + " different years",
                            Toast.LENGTH_SHORT).show();
                }

                // Open YearlyCustomersActivity with grouped data
                Intent intent = new Intent(ViewCustomerActivity.this, YearlyCustomersActivity.class);
                intent.putExtra("userUid", userUid);
                intent.putExtra("currentYear", currentYear);
                intent.putExtra("yearMap", yearMap); // Pass the grouped map
                startActivity(intent);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ViewCustomerActivity.this,
                        "Error loading customers: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();

                // Fallback
                Intent intent = new Intent(ViewCustomerActivity.this, YearlyCustomersActivity.class);
                intent.putExtra("userUid", userUid);
                intent.putExtra("currentYear", getCurrentYear());
                startActivity(intent);
            }
        });
    }

    // Helper method to get current year
    private String getCurrentYear() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        return String.valueOf(year);
    }

    // Helper method to extract year from date string
    private String extractYear(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }

        try {
            // Format 1: "dd.MM.yyyy" (e.g., 31.09.2025)
            if (date.contains(".")) {
                String[] parts = date.split("\\.");
                if (parts.length >= 3) {
                    String year = parts[2].trim();
                    if (year.length() == 4) {
                        return year;
                    }
                }
            }
            // Format 2: "yyyy-MM-dd" (e.g., 2025-09-31)
            if (date.contains("-")) {
                String[] parts = date.split("-");
                if (parts.length >= 1) {
                    String year = parts[0].trim();
                    if (year.length() == 4) {
                        return year;
                    }
                }
            }
            // Format 3: "dd/MM/yyyy" (e.g., 31/09/2025)
            if (date.contains("/")) {
                String[] parts = date.split("/");
                if (parts.length >= 3) {
                    String year = parts[2].trim();
                    if (year.length() == 4) {
                        return year;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("DateExtract", "Error extracting year from: " + date, e);
        }

        return "";
    }

    private void loadTotalCustomerCount() {
        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                totalCustomerCount = (int) snapshot.getChildrenCount();
                updateHeaderInfo();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("CustomerCount", "Error loading total customer count: " + error.getMessage());
            }
        });
    }

    private void updateHeaderInfo() {
        tvTotalCustomers.setText(String.valueOf(totalCustomerCount));

        totalPages = (int) Math.ceil((double) totalCustomerCount / PAGE_SIZE);
        tvTotalPages.setText(String.valueOf(totalPages));

        tvCurrentPage.setText(String.valueOf(currentPage));

        tvTotalPagesDisplay.setText("/ " + totalPages);
        if (tvCurrentPageNumber != null) {
            tvCurrentPageNumber.setText(String.valueOf(currentPage));
        }

        updateProgressBar();
        updatePaginationControls();
    }

    private void updateProgressBar() {
        if (totalPages > 0) {
            int progress = (int) ((currentPage * 100f) / totalPages);
            progressPage.setProgress(progress);
            tvProgressPercent.setText(progress + "%");
        } else {
            progressPage.setProgress(0);
            tvProgressPercent.setText("0%");
        }
    }

    private void updatePaginationControls() {
        if (totalCustomerCount == 0) {
            btnFirstPage.setEnabled(false);
            btnPreviousPage.setEnabled(false);
            btnNextPage.setEnabled(false);
            btnLastPage.setEnabled(false);
            btnGoToPage.setEnabled(false);
            etGoToPage.setEnabled(false);
            setButtonsAlpha(0.3f);
        } else {
            btnFirstPage.setEnabled(true);
            btnPreviousPage.setEnabled(true);
            btnNextPage.setEnabled(true);
            btnLastPage.setEnabled(true);
            btnGoToPage.setEnabled(true);
            etGoToPage.setEnabled(true);

            btnFirstPage.setEnabled(currentPage > 1);
            btnPreviousPage.setEnabled(currentPage > 1);
            btnNextPage.setEnabled(currentPage < totalPages);
            btnLastPage.setEnabled(currentPage < totalPages);

            btnFirstPage.setAlpha(currentPage > 1 ? 1.0f : 0.3f);
            btnPreviousPage.setAlpha(currentPage > 1 ? 1.0f : 0.3f);
            btnNextPage.setAlpha(currentPage < totalPages ? 1.0f : 0.3f);
            btnLastPage.setAlpha(currentPage < totalPages ? 1.0f : 0.3f);
        }
    }

    private void setButtonsAlpha(float alpha) {
        btnFirstPage.setAlpha(alpha);
        btnPreviousPage.setAlpha(alpha);
        btnNextPage.setAlpha(alpha);
        btnLastPage.setAlpha(alpha);
    }

    private void goToFirstPage() {
        if (currentPage > 1) {
            previousPageKeys.clear();
            lastKey = null;
            currentPage = 1;
            loadCustomerData();
        }
    }

    private void goToLastPage() {
        if (totalPages > 0 && currentPage < totalPages) {
            customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    List<String> allKeys = new ArrayList<>();
                    for (DataSnapshot child : snapshot.getChildren()) {
                        allKeys.add(child.getKey());
                    }

                    if (!allKeys.isEmpty()) {
                        int startIndex = Math.max(0, allKeys.size() - PAGE_SIZE);
                        String startKey = allKeys.get(startIndex);

                        previousPageKeys.clear();

                        for (int i = 0; i < startIndex; i += PAGE_SIZE) {
                            if (i + PAGE_SIZE - 1 < allKeys.size()) {
                                previousPageKeys.push(allKeys.get(i + PAGE_SIZE - 1));
                            }
                        }

                        currentPage = totalPages;
                        customerRef.orderByKey().startAt(startKey).limitToFirst(PAGE_SIZE)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        showCustomerPage(snapshot, false);
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Toast.makeText(ViewCustomerActivity.this,
                                                "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(ViewCustomerActivity.this,
                            "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
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

            if (requestedPage >= 1 && requestedPage <= totalPages) {
                if (requestedPage == currentPage) {
                    etGoToPage.setText("");
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etGoToPage.getWindowToken(), 0);
                    return;
                }

                if (requestedPage == 1) {
                    goToFirstPage();
                } else if (requestedPage == totalPages) {
                    goToLastPage();
                } else {
                    customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            List<String> allKeys = new ArrayList<>();
                            for (DataSnapshot child : snapshot.getChildren()) {
                                allKeys.add(child.getKey());
                            }

                            if (!allKeys.isEmpty()) {
                                int startIndex = (requestedPage - 1) * PAGE_SIZE;
                                if (startIndex < allKeys.size()) {
                                    String startKey = allKeys.get(startIndex);

                                    previousPageKeys.clear();
                                    for (int i = 0; i < startIndex; i += PAGE_SIZE) {
                                        if (i + PAGE_SIZE - 1 < allKeys.size()) {
                                            previousPageKeys.push(allKeys.get(i + PAGE_SIZE - 1));
                                        }
                                    }

                                    currentPage = requestedPage;
                                    customerRef.orderByKey().startAt(startKey).limitToFirst(PAGE_SIZE)
                                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                    showCustomerPage(snapshot, false);
                                                    etGoToPage.setText("");
                                                    InputMethodManager imm = (InputMethodManager) getSystemService(
                                                            Context.INPUT_METHOD_SERVICE);
                                                    imm.hideSoftInputFromWindow(etGoToPage.getWindowToken(), 0);
                                                }

                                                @Override
                                                public void onCancelled(@NonNull DatabaseError error) {
                                                    Toast.makeText(ViewCustomerActivity.this,
                                                            "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(ViewCustomerActivity.this,
                                    "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } else {
                Toast.makeText(this, "Please enter a valid page number (1-" + totalPages + ")",
                        Toast.LENGTH_LONG).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid page number", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCustomerPage(DataSnapshot snapshot, boolean isFirstPage) {
        List<Customer> newCustomers = new ArrayList<>();
        int count = 0;
        String newLastKey = null;
        String currentYear = getCurrentYear();

        for (DataSnapshot child : snapshot.getChildren()) {
            HashMap<String, Object> data = (HashMap<String, Object>) child.getValue();
            if (data != null) {
                String customerDate = data.get("date") != null ? data.get("date").toString() : "";
                String customerYear = extractYear(customerDate);

                // IMPORTANT: Only show customers from the current year
                // Customers from other years should only appear in YearlyCustomersActivity
                if (!customerYear.isEmpty() && !customerYear.equals(currentYear)) {
                    continue; // Skip non-current year customers
                }

                Customer customer = new Customer(
                        child.getKey(),
                        data.get("name") != null ? data.get("name").toString() : "No Name",
                        data.get("phone") != null ? data.get("phone").toString() : "",
                        customerDate,
                        data.get("address") != null ? data.get("address").toString() : "",
                        0
                );
                newCustomers.add(customer);
                newLastKey = child.getKey();
                count++;
            }
        }

        lastKey = newLastKey;
        adapter.updateData(newCustomers);

        if (isFirstPage) {
            currentPage = 1;
        }
        updateHeaderInfo();
        loadTransactionCounts();

        if (count == 0) {
            Toast.makeText(this, "No customers found", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadNextPage() {
        if (lastKey == null) return;

        previousPageKeys.push(lastKey);
        currentPage++;

        customerRef.orderByKey().startAfter(lastKey).limitToFirst(PAGE_SIZE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showCustomerPage(snapshot, false);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ViewCustomerActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadPreviousPage() {
        if (previousPageKeys.isEmpty()) return;

        String previousKey = previousPageKeys.pop();
        currentPage--;

        customerRef.orderByKey().endAt(previousKey).limitToLast(PAGE_SIZE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showCustomerPage(snapshot, previousPageKeys.isEmpty());
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ViewCustomerActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadCustomerData() {
        previousPageKeys.clear();
        customerRef.orderByKey().limitToFirst(PAGE_SIZE)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        showCustomerPage(snapshot, true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(ViewCustomerActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadTransactionCounts() {
        List<Customer> customers = adapter.getCustomers();
        for (int i = 0; i < customers.size(); i++) {
            Customer customer = customers.get(i);
            final int position = i;

            DatabaseReference customerTransactionRef = FirebaseDatabase.getInstance().getReference("Users")
                    .child(userUid)
                    .child("customers")
                    .child(customer.getId())
                    .child("transactions");

            customerTransactionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    int count = (int) snapshot.getChildrenCount();
                    Log.d("TransactionDebug", "Transactions for " + customer.getName() + ": " + count);

                    List<Customer> currentList = adapter.getCustomers();
                    if (position < currentList.size()) {
                        currentList.get(position).setTransactionCount(count);
                        adapter.notifyItemChanged(position);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("TransactionDebug", "Error for customer " + customer.getName() + ": " + error.getMessage());
                }
            });
        }
    }

    private void moveToRecycleBin(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Move to Recycle Bin")
                .setMessage("Do you want to move this customer and their transactions to Recycle Bin?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    DatabaseReference customerDataRef = customerRef.child(id);
                    DatabaseReference recycleCustomerRef = recycleBinRef.child("customers").child(id);
                    DatabaseReference recycleTransactionsRef = recycleBinRef.child("transactions");

                    customerDataRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot customerSnap) {
                            if (customerSnap.exists()) {
                                recycleCustomerRef.setValue(customerSnap.getValue()).addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        transactionRef.orderByChild("customerId").equalTo(id)
                                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                                    @Override
                                                    public void onDataChange(@NonNull DataSnapshot transSnap) {
                                                        for (DataSnapshot trans : transSnap.getChildren()) {
                                                            recycleTransactionsRef.child(trans.getKey()).setValue(trans.getValue());
                                                            trans.getRef().removeValue();
                                                        }

                                                        customerDataRef.removeValue().addOnCompleteListener(t -> {
                                                            Toast.makeText(ViewCustomerActivity.this, "Moved to Recycle Bin", Toast.LENGTH_SHORT).show();
                                                            loadTotalCustomerCount();
                                                            loadCustomerData();
                                                        });
                                                    }

                                                    @Override
                                                    public void onCancelled(@NonNull DatabaseError error) {
                                                        Toast.makeText(ViewCustomerActivity.this, "Failed to move transactions", Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                    }
                                });
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCustomer(String id) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Customer")
                .setMessage("Are you sure you want to delete this customer? This will also delete all associated transactions.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    customerRef.child(id).removeValue().addOnSuccessListener(unused -> {
                        transactionRef.orderByChild("customerId").equalTo(id)
                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                        for (DataSnapshot transactionSnapshot : snapshot.getChildren()) {
                                            transactionSnapshot.getRef().removeValue();
                                        }
                                        Toast.makeText(ViewCustomerActivity.this, "Customer and transactions deleted", Toast.LENGTH_SHORT).show();
                                        loadTotalCustomerCount();
                                        loadCustomerData();
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError error) {
                                        Toast.makeText(ViewCustomerActivity.this, "Customer deleted but error deleting transactions", Toast.LENGTH_SHORT).show();
                                        loadTotalCustomerCount();
                                        loadCustomerData();
                                    }
                                });
                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditDialog(String id, HashMap<String, Object> data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_customer, null);
        builder.setView(view);

        EditText etName = view.findViewById(R.id.edit_customer_name);
        EditText etPhone = view.findViewById(R.id.edit_customer_phone);
        EditText etdate = view.findViewById(R.id.edit_customer_date);
        EditText etAddress = view.findViewById(R.id.edit_customer_address);
        Button btnSave = view.findViewById(R.id.save_button);

        etName.setText(data.get("name") != null ? data.get("name").toString() : "");
        etPhone.setText(data.get("phone") != null ? data.get("phone").toString() : "");
        etdate.setText(data.get("date") != null ? data.get("date").toString() : "");
        etAddress.setText(data.get("address") != null ? data.get("address").toString() : "");

        AlertDialog dialog = builder.create();
        dialog.show();

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String date = etdate.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (!name.isEmpty()) {
                HashMap<String, Object> updated = new HashMap<>();
                updated.put("name", name);
                updated.put("phone", phone);
                updated.put("date", date);
                updated.put("address", address);

                customerRef.child(id).updateChildren(updated).addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Customer updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadCustomerData();
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } else {
                Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditOptionsDialog(String customerId, HashMap<String, Object> data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_options, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        LinearLayout optionEditCustomer = dialogView.findViewById(R.id.optionEditCustomer);
        LinearLayout optionEditTransaction = dialogView.findViewById(R.id.optionEditTransaction);
        ImageButton btnClose = dialogView.findViewById(R.id.btnClose);

        optionEditCustomer.setOnClickListener(v -> {
            showEditDialog(customerId, data);
            dialog.dismiss();
        });

        optionEditTransaction.setOnClickListener(v -> {
            Intent intent = new Intent(ViewCustomerActivity.this, EditTransactionActivity.class);
            intent.putExtra("userUid", userUid);
            intent.putExtra("customerId", customerId);
            startActivity(intent);
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // RecyclerView Adapter
    private class CustomerAdapter extends RecyclerView.Adapter<CustomerAdapter.CustomerViewHolder> {
        private List<Customer> customers;
        private OnCustomerActionListener actionListener;

        public CustomerAdapter(List<Customer> customers) {
            this.customers = new ArrayList<>(customers);
        }

        public void setOnCustomerActionListener(OnCustomerActionListener listener) {
            this.actionListener = listener;
        }

        public void updateData(List<Customer> newCustomers) {
            this.customers = new ArrayList<>(newCustomers);
            notifyDataSetChanged();
        }

        public List<Customer> getCustomers() {
            return customers;
        }

        @NonNull
        @Override
        public CustomerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_customer, parent, false);
            return new CustomerViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CustomerViewHolder holder, int position) {
            if (position < 0 || position >= customers.size()) {
                return;
            }

            Customer customer = customers.get(position);
            holder.bind(customer);
        }

        @Override
        public int getItemCount() {
            return customers.size();
        }

        class CustomerViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName, tvTransactionCount;
            private ImageButton btnEdit, btnDelete;

            public CustomerViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvTransactionCount = itemView.findViewById(R.id.tv_transaction_count);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }

            public void bind(Customer customer) {
                tvName.setText(customer.getName());
                tvTransactionCount.setText(String.valueOf(customer.getTransactionCount()));

                btnEdit.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onEditCustomer(customer);
                    }
                });

                btnDelete.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onDeleteCustomer(customer);
                    }
                });
            }
        }
    }
}
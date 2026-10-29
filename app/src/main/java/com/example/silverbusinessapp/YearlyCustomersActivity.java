package com.example.silverbusinessapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class YearlyCustomersActivity extends AppCompatActivity {

    private String userUid;
    private String currentYear;
    private HashMap<String, ArrayList<Customer>> yearMap;

    private RecyclerView recyclerView;
    private TextView tvYearTitle, tvYearCount, tvEmptyState;
    private ImageButton btnPrevYear, btnNextYear;

    private YearlyAdapter adapter;
    private List<Customer> allCustomers = new ArrayList<>();

    private List<String> sortedYears = new ArrayList<>();
    private int currentYearIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_yearly_customers);

        // Get data from Intent
        userUid = getIntent().getStringExtra("userUid");
        currentYear = getIntent().getStringExtra("currentYear");
        yearMap = (HashMap<String, ArrayList<Customer>>) getIntent().getSerializableExtra("yearMap");

        if (userUid == null) {
            Toast.makeText(this, "Missing data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize views
        recyclerView = findViewById(R.id.yearly_customer_recycler_view);
        tvYearTitle = findViewById(R.id.tv_year_title);
        tvYearCount = findViewById(R.id.tv_year_count);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnPrevYear = findViewById(R.id.btn_prev_year);
        btnNextYear = findViewById(R.id.btn_next_year);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new YearlyAdapter(allCustomers);
        recyclerView.setAdapter(adapter);

        if (yearMap != null && !yearMap.isEmpty()) {

            // Sort years ascending (e.g. 2022, 2023, 2024, 2025)
            sortedYears = new ArrayList<>(yearMap.keySet());
            Collections.sort(sortedYears);

            // Start at currentYear if it was passed in and exists in the map,
            // otherwise fall back to the first available year
            currentYearIndex = sortedYears.indexOf(currentYear);
            if (currentYearIndex == -1) {
                currentYearIndex = 0;
            }

            btnPrevYear.setVisibility(View.VISIBLE);
            btnNextYear.setVisibility(View.VISIBLE);

            displayYear(sortedYears.get(currentYearIndex));

            btnPrevYear.setOnClickListener(v -> {
                if (currentYearIndex > 0) {
                    currentYearIndex--;
                    displayYear(sortedYears.get(currentYearIndex));
                } else {
                    Toast.makeText(this, "This is the earliest year", Toast.LENGTH_SHORT).show();
                }
            });

            btnNextYear.setOnClickListener(v -> {
                if (currentYearIndex < sortedYears.size() - 1) {
                    currentYearIndex++;
                    displayYear(sortedYears.get(currentYearIndex));
                } else {
                    Toast.makeText(this, "This is the latest year", Toast.LENGTH_SHORT).show();
                }
            });

        } else {
            tvYearTitle.setText("No other years");
            tvYearCount.setText("0 customers");
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            btnPrevYear.setVisibility(View.GONE);
            btnNextYear.setVisibility(View.GONE);
            Toast.makeText(this, "No customers found from other years", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayYear(String year) {
        allCustomers.clear();

        ArrayList<Customer> yearCustomers = yearMap.get(year);
        if (yearCustomers != null) {
            allCustomers.addAll(yearCustomers);
        }
        adapter.notifyDataSetChanged();

        tvYearTitle.setText("Year " + year);
        tvYearCount.setText(allCustomers.size() + " customers");

        updateEmptyState();

        // Visual feedback: fade arrow when at the start/end of the year list
        btnPrevYear.setAlpha(currentYearIndex == 0 ? 0.4f : 1f);
        btnNextYear.setAlpha(currentYearIndex == sortedYears.size() - 1 ? 0.4f : 1f);
    }

    private void updateEmptyState() {
        if (allCustomers.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Deletes a customer from the currently displayed list, keeps yearMap in sync,
     * and refreshes the header count / empty state.
     *
     * NOTE: This only removes the item from the in-memory list/adapter.
     * If this data is also stored in Firebase/DB, add the actual delete call
     * here using customer.getId() before removing it from the list.
     */
    private void deleteCustomer(int position) {
        if (position < 0 || position >= allCustomers.size()) return;

        Customer removed = allCustomers.get(position);

        // TODO: Call your backend/Firebase delete here, e.g.:
        // FirebaseFirestore.getInstance()
        //         .collection("users").document(userUid)
        //         .collection("customers").document(removed.getId())
        //         .delete();

        allCustomers.remove(position);
        adapter.notifyItemRemoved(position);
        adapter.notifyItemRangeChanged(position, allCustomers.size());

        // Keep yearMap in sync so switching years and coming back stays correct
        if (!sortedYears.isEmpty()) {
            ArrayList<Customer> yearList = yearMap.get(sortedYears.get(currentYearIndex));
            if (yearList != null) {
                yearList.remove(removed);
            }
        }

        tvYearCount.setText(allCustomers.size() + " customers");
        updateEmptyState();

        Toast.makeText(this, "Customer removed", Toast.LENGTH_SHORT).show();
    }

    // RecyclerView Adapter
    private class YearlyAdapter extends RecyclerView.Adapter<YearlyAdapter.YearlyViewHolder> {
        private List<Customer> customers;

        public YearlyAdapter(List<Customer> customers) {
            this.customers = customers;
        }

        @NonNull
        @Override
        public YearlyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_customer, parent, false);
            return new YearlyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull YearlyViewHolder holder, int position) {
            if (position < 0 || position >= customers.size()) return;
            holder.bind(customers.get(position));
        }

        @Override
        public int getItemCount() {
            return customers.size();
        }

        class YearlyViewHolder extends RecyclerView.ViewHolder {
            private TextView tvName, tvTransactionCount;
            private ImageButton btnEdit, btnDelete;

            public YearlyViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_name);
                tvTransactionCount = itemView.findViewById(R.id.tv_transaction_count);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);

                // Edit is hidden on this screen; delete is shown (handled in bind())
                if (btnEdit != null) btnEdit.setVisibility(View.GONE);
            }

            public void bind(Customer customer) {
                tvName.setText(customer.getName());
                tvTransactionCount.setText("Year: " + extractYear(customer.getDate()));

                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(YearlyCustomersActivity.this, EditTransactionActivity.class);
                    intent.putExtra("userUid", userUid);
                    intent.putExtra("customerId", customer.getId());
                    startActivity(intent);
                });

                if (btnDelete != null) {
                    btnDelete.setVisibility(View.VISIBLE);
                    btnDelete.setOnClickListener(v -> {
                        int pos = getAdapterPosition();
                        if (pos == RecyclerView.NO_POSITION) return;

                        new AlertDialog.Builder(itemView.getContext())
                                .setTitle("Delete customer")
                                .setMessage("Remove \"" + customer.getName() + "\"? This cannot be undone.")
                                .setPositiveButton("Delete", (dialog, which) -> deleteCustomer(pos))
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                }
            }
        }
    }

    private String extractYear(String date) {
        if (date == null || date.isEmpty()) return "";
        try {
            if (date.contains(".")) {
                String[] parts = date.split("\\.");
                if (parts.length >= 3) return parts[2].trim();
            }
            if (date.contains("-")) {
                String[] parts = date.split("-");
                if (parts.length >= 1) return parts[0].trim();
            }
            if (date.contains("/")) {
                String[] parts = date.split("/");
                if (parts.length >= 3) return parts[2].trim();
            }
        } catch (Exception e) {
            Log.e("YearlyActivity", "Error extracting year", e);
        }
        return "";
    }
}
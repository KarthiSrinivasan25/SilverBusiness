package com.example.silverbusinessapp;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public class AddCustomerActivity extends AppCompatActivity {

    EditText etName, etPhone, etDate, etAddress;
    Button btnSave;
    DatabaseReference customerRef;
    String userUid; // Firebase Authenticated User UID
    Calendar calendar;
    SimpleDateFormat dateFormatter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_customer);

        etName = findViewById(R.id.et_customer_name);
        etPhone = findViewById(R.id.et_customer_phone);
        etDate = findViewById(R.id.et_customer_date);
        etAddress = findViewById(R.id.et_customer_address);
        btnSave = findViewById(R.id.btn_add_customer);

        // Initialize date formatter and calendar
        dateFormatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        calendar = Calendar.getInstance();

        // Set input filters to block emojis and special characters
        setInputFilters();

        // Set up date picker
        setupDatePicker();

        // ✅ Get UID from Intent (Passed from Dashboard)
        userUid = getIntent().getStringExtra("userUid");
        if (userUid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        customerRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(userUid)
                .child("customers");

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String date = etDate.getText().toString().trim();
            String address = etAddress.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Enter customer name");
                return;
            }

            // Additional name validation
            if (!isValidName(name)) {
                etName.setError("Only letters and spaces allowed");
                return;
            }

            if (phone.isEmpty() || phone.length() < 10) {
                etPhone.setError("Enter valid 10-digit phone number");
                return;
            }

            // Additional phone validation
            if (!isValidPhone(phone)) {
                etPhone.setError("Only numbers allowed");
                return;
            }

            if (date.isEmpty()) {
                etDate.setError("Select a date");
                return;
            }

            if (address.isEmpty()) {
                etAddress.setError("Enter customer address");
                return;
            }

            // Additional address validation
            if (!isValidAddress(address)) {
                etAddress.setError("Only letters, numbers, spaces, commas, periods and hyphens allowed");
                return;
            }

            String customerId = customerRef.push().getKey();

            HashMap<String, String> customerMap = new HashMap<>();
            customerMap.put("name", name);
            customerMap.put("phone", phone);
            customerMap.put("date", date);
            customerMap.put("address", address);

            assert customerId != null;
            customerRef.child(customerId).setValue(customerMap)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Customer added successfully", Toast.LENGTH_SHORT).show();
                        finish(); // Close the activity
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to add: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> showDatePickerDialog());
        etDate.setFocusable(false); // Prevent keyboard from opening
    }

    private void showDatePickerDialog() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    // Update calendar with selected date
                    calendar.set(year, month, dayOfMonth);
                    // Format and display the date
                    etDate.setText(dateFormatter.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void setInputFilters() {
        // Filter for name: only letters and spaces
        InputFilter nameFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (!Character.isLetter(c) && !Character.isSpaceChar(c)) {
                        return "";
                    }
                }
                return null;
            }
        };

        // Filter for phone: only numbers
        InputFilter phoneFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    if (!Character.isDigit(source.charAt(i))) {
                        return "";
                    }
                }
                return null;
            }
        };

        // Filter for address: letters, numbers, spaces, and basic punctuation
        InputFilter addressFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (!Character.isLetterOrDigit(c) &&
                            !Character.isSpaceChar(c) &&
                            c != ',' && c != '.' && c != '-' && c != '/') {
                        return "";
                    }
                }
                return null;
            }
        };

        // Apply filters to EditText fields
        etName.setFilters(new InputFilter[]{nameFilter, new InputFilter.LengthFilter(50)});
        etPhone.setFilters(new InputFilter[]{phoneFilter, new InputFilter.LengthFilter(10)});
        etAddress.setFilters(new InputFilter[]{addressFilter, new InputFilter.LengthFilter(200)});
    }

    private boolean isValidName(String name) {
        return Pattern.matches("^[a-zA-Z ]+$", name);
    }

    private boolean isValidPhone(String phone) {
        return Pattern.matches("^[0-9]+$", phone);
    }

    private boolean isValidAddress(String address) {
        return Pattern.matches("^[a-zA-Z0-9 ,./-]+$", address);
    }
}
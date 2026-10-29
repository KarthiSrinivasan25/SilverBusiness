package com.example.silverbusinessapp;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KachaActivity extends AppCompatActivity {

    // ============================================================
    // FIREBASE
    // ============================================================

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private String userId;
    private ValueEventListener kachaListener;

    // ============================================================
    // TOP
    // ============================================================

    private LinearLayout btnAddKacha;
    private TextView tvTotalKacha;
    private TextView tvPageInfo;
    private TextView tvCurrentPage;
    private TextView tvRecordRange;

    // ============================================================
    // PAGINATION
    // ============================================================

    private Button btnFirstPage;
    private Button btnPreviousPage;
    private Button btnNextPage;
    private Button btnLastPage;
    private EditText etGoPage;
    private Button btnGoPage;

    // ============================================================
    // GRID
    // ============================================================

    private GridLayout kachaGrid;
    private LinearLayout emptyState;

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private LinearLayout bottomHome;
    private LinearLayout bottomKacha;
    private View homeSelectedDot;
    private View kachaSelectedDot;
    private ObjectAnimator kachaDotAnimator;

    // ============================================================
    // DATA
    // ============================================================

    private final List<KachaData> kachaList = new ArrayList<>();

    // ============================================================
    // PAGINATION
    // ============================================================

    private static final int ITEMS_PER_PAGE = 6; // 3 rows × 2 columns
    private static final int COLUMNS = 2;
    private int currentPage = 1;
    private int totalPages = 0;

    // ============================================================
    // REQUEST CODE
    // ============================================================

    private static final int ADD_KACHA_REQUEST = 1001;
    private static final int EDIT_KACHA_REQUEST = 1002;

    // ============================================================
    // COLOR PALETTE FOR AVATARS
    // ============================================================

    private final int[] avatarColors = {
            Color.rgb(26, 35, 126),   // Indigo
            Color.rgb(211, 47, 47),   // Red
            Color.rgb(46, 125, 50),   // Green
            Color.rgb(237, 108, 2),   // Orange
            Color.rgb(2, 136, 209),   // Blue
            Color.rgb(106, 27, 154),  // Purple
            Color.rgb(0, 131, 143),   // Teal
            Color.rgb(194, 24, 91),   // Pink
    };

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kacha);

        initializeFirebase();
        initializeViews();
        setupAddButton();
        setupPagination();
        setupBottomNavigation();
        setKachaSelected();
        loadKachaData();
    }

    // ============================================================
    // INITIALIZE FIREBASE
    // ============================================================

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

    // ============================================================
    // LOAD DATA FROM FIREBASE
    // ============================================================

    private void loadKachaData() {
        if (kachaListener != null) {
            databaseReference.removeEventListener(kachaListener);
        }

        kachaListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                kachaList.clear();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    String key = dataSnapshot.getKey();
                    String party = dataSnapshot.child("party").getValue(String.class);

                    KachaData data = new KachaData(
                            key,
                            party != null ? party : "No Party"
                    );

                    kachaList.add(data);
                }

                calculateTotalPages();
                updatePagination();

                if (kachaList.isEmpty()) {
                    kachaGrid.setVisibility(View.GONE);
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    kachaGrid.setVisibility(View.VISIBLE);
                    emptyState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(KachaActivity.this,
                        "Failed to load data: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        databaseReference.addValueEventListener(kachaListener);
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private void initializeViews() {
        btnAddKacha = findViewById(R.id.btn_add_kacha);
        tvTotalKacha = findViewById(R.id.tv_total_kacha);
        tvPageInfo = findViewById(R.id.tv_page_info);
        tvCurrentPage = findViewById(R.id.tv_current_page);
        tvRecordRange = findViewById(R.id.tv_record_range);

        btnFirstPage = findViewById(R.id.btn_first_page);
        btnPreviousPage = findViewById(R.id.btn_previous_page);
        btnNextPage = findViewById(R.id.btn_next_page);
        btnLastPage = findViewById(R.id.btn_last_page);
        etGoPage = findViewById(R.id.et_go_page);
        btnGoPage = findViewById(R.id.btn_go_page);

        kachaGrid = findViewById(R.id.kacha_grid);
        emptyState = findViewById(R.id.empty_state);

        bottomHome = findViewById(R.id.bottom_home);
        bottomKacha = findViewById(R.id.bottom_kacha);
        homeSelectedDot = findViewById(R.id.home_selected_dot);
        kachaSelectedDot = findViewById(R.id.kacha_selected_dot);
    }

    // ============================================================
    // ADD BUTTON
    // ============================================================

    private void setupAddButton() {
        btnAddKacha.setOnClickListener(v -> {
            Intent intent = new Intent(KachaActivity.this, AddKachaActivity.class);
            startActivityForResult(intent, ADD_KACHA_REQUEST);
        });
    }

    // ============================================================
    // RECEIVE ADD/EDIT RESULT
    // ============================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == ADD_KACHA_REQUEST) {
            Toast.makeText(this, "Kacha saved successfully", Toast.LENGTH_SHORT).show();
        } else if (requestCode == EDIT_KACHA_REQUEST) {
            Toast.makeText(this, "Kacha updated successfully", Toast.LENGTH_SHORT).show();
        }
    }

    // ============================================================
    // PAGINATION SETUP
    // ============================================================

    private void setupPagination() {
        btnFirstPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage = 1;
                updatePagination();
            }
        });

        btnPreviousPage.setOnClickListener(v -> {
            if (currentPage > 1) {
                currentPage--;
                updatePagination();
            }
        });

        btnNextPage.setOnClickListener(v -> {
            if (currentPage < totalPages) {
                currentPage++;
                updatePagination();
            }
        });

        btnLastPage.setOnClickListener(v -> {
            if (totalPages > 0) {
                currentPage = totalPages;
                updatePagination();
            }
        });

        btnGoPage.setOnClickListener(v -> goToPage());
    }

    // ============================================================
    // GO TO PAGE
    // ============================================================

    private void goToPage() {
        String text = etGoPage.getText().toString().trim();

        if (TextUtils.isEmpty(text)) {
            etGoPage.setError("Enter page");
            return;
        }

        try {
            int page = Integer.parseInt(text);

            if (totalPages == 0) {
                Toast.makeText(this, "No pages available", Toast.LENGTH_SHORT).show();
                return;
            }

            if (page < 1 || page > totalPages) {
                etGoPage.setError("Enter 1 - " + totalPages);
                return;
            }

            currentPage = page;
            updatePagination();
            etGoPage.setText("");

        } catch (NumberFormatException e) {
            etGoPage.setError("Invalid page");
        }
    }

    // ============================================================
    // CALCULATE TOTAL PAGES
    // ============================================================

    private void calculateTotalPages() {
        if (kachaList.isEmpty()) {
            totalPages = 0;
            return;
        }
        totalPages = (int) Math.ceil((double) kachaList.size() / ITEMS_PER_PAGE);
    }

    // ============================================================
    // UPDATE PAGINATION
    // ============================================================

    private void updatePagination() {
        calculateTotalPages();

        if (totalPages == 0) {
            currentPage = 1;
        } else if (currentPage > totalPages) {
            currentPage = totalPages;
        }

        tvTotalKacha.setText(String.valueOf(kachaList.size()));
        tvPageInfo.setText(currentPage + " / " + totalPages);
        tvCurrentPage.setText(String.valueOf(currentPage));

        if (kachaList.isEmpty()) {
            tvRecordRange.setText("0 records");
        } else {
            int start = (currentPage - 1) * ITEMS_PER_PAGE;
            int end = Math.min(start + ITEMS_PER_PAGE, kachaList.size());
            tvRecordRange.setText((start + 1) + "-" + end + " of " + kachaList.size());
        }

        boolean hasPages = totalPages > 0;

        btnFirstPage.setEnabled(hasPages && currentPage > 1);
        btnPreviousPage.setEnabled(hasPages && currentPage > 1);
        btnNextPage.setEnabled(hasPages && currentPage < totalPages);
        btnLastPage.setEnabled(hasPages && currentPage < totalPages);

        displayCurrentPage();
    }

    // ============================================================
    // DISPLAY CURRENT PAGE (2 Cards Per Row - Fixed)
    // ============================================================

    private void displayCurrentPage() {
        kachaGrid.removeAllViews();

        if (kachaList.isEmpty()) {
            return;
        }

        int startIndex = (currentPage - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, kachaList.size());

        int itemsOnPage = endIndex - startIndex;

        // Set grid to 2 columns
        kachaGrid.setColumnCount(COLUMNS);

        // Calculate rows needed for this page
        int rows = (int) Math.ceil((double) itemsOnPage / COLUMNS);
        kachaGrid.setRowCount(rows);

        for (int i = startIndex; i < endIndex; i++) {
            KachaData data = kachaList.get(i);
            addKachaCard(i, data);
        }

        // ============================================================
        // FIX: If there's only 1 card on the last page, make it span full width
        // ============================================================
        if (itemsOnPage == 1 && kachaGrid.getChildCount() == 1) {
            View singleCard = kachaGrid.getChildAt(0);
            GridLayout.LayoutParams params = (GridLayout.LayoutParams) singleCard.getLayoutParams();
            params.columnSpec = GridLayout.spec(0, 2, 1f);
            params.width = 0;
            params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
            singleCard.setLayoutParams(params);
        }
    }

    // ============================================================
    // ADD KACHA CARD (2 Per Row)
    // ============================================================

    private void addKachaCard(int position, KachaData data) {
        // Inflate card layout
        View cardView = LayoutInflater.from(this)
                .inflate(R.layout.item_kacha_card, kachaGrid, false);

        // Find views
        TextView tvAvatar = cardView.findViewById(R.id.tv_avatar);
        TextView tvPartyName = cardView.findViewById(R.id.tv_party_name);
        TextView tvCardNumber = cardView.findViewById(R.id.tv_card_number);
        ImageView btnEdit = cardView.findViewById(R.id.btn_edit);
        ImageView btnDelete = cardView.findViewById(R.id.btn_delete);

        // Set avatar
        String firstLetter = data.party.substring(0, 1).toUpperCase();
        tvAvatar.setText(firstLetter);

        // Set avatar color
        int colorIndex = Math.abs(data.party.hashCode()) % avatarColors.length;
        int avatarColor = avatarColors[colorIndex];
        GradientDrawable avatarBg = (GradientDrawable) tvAvatar.getBackground();
        if (avatarBg != null) {
            avatarBg.setColor(avatarColor);
        }

        // Set party name
        tvPartyName.setText(data.party);

        // Set card number
        int globalPosition = (currentPage - 1) * ITEMS_PER_PAGE + position + 1;
        tvCardNumber.setText("#" + String.format(Locale.US, "%02d", globalPosition));

        // Set click listeners
        btnEdit.setOnClickListener(v -> editKacha(data.key, position));
        btnDelete.setOnClickListener(v -> deleteKacha(data.key, position));

        // Add to grid with 2 columns layout
        GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
        gridParams.width = 0;
        gridParams.height = GridLayout.LayoutParams.WRAP_CONTENT;
        gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gridParams.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));

        kachaGrid.addView(cardView, gridParams);
    }

    // ============================================================
    // DP TO PX
    // ============================================================

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // EDIT KACHA
    // ============================================================

    private void editKacha(String key, int position) {
        if (key == null) {
            Toast.makeText(this, "Error: Cannot edit this record", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(KachaActivity.this, EditKachaActivity.class);
        intent.putExtra("kacha_key", key);
        startActivityForResult(intent, EDIT_KACHA_REQUEST);
    }

    // ============================================================
    // DELETE KACHA
    // ============================================================

    private void deleteKacha(String key, int position) {
        if (key == null) {
            Toast.makeText(this, "Error: Cannot delete this record", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Delete Kacha")
                .setMessage("Are you sure you want to delete this kacha entry?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    databaseReference.child(key).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(KachaActivity.this,
                                        "Kacha deleted successfully",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(KachaActivity.this,
                                        "Failed to delete: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            });
                })
                .show();
    }

    // ============================================================
    // BOTTOM NAVIGATION
    // ============================================================

    private void setupBottomNavigation() {
        bottomHome.setOnClickListener(v -> {
            stopKachaDotBlink();
            Intent intent = new Intent(KachaActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        bottomKacha.setOnClickListener(v -> setKachaSelected());
    }

    // ============================================================
    // KACHA SELECTED
    // ============================================================

    private void setKachaSelected() {
        if (homeSelectedDot != null) {
            homeSelectedDot.clearAnimation();
            homeSelectedDot.setVisibility(View.GONE);
        }

        if (kachaSelectedDot != null) {
            kachaSelectedDot.setVisibility(View.VISIBLE);
            startKachaDotBlink();
        }
    }

    // ============================================================
    // BLINK ANIMATION
    // ============================================================

    private void startKachaDotBlink() {
        if (kachaSelectedDot == null) return;

        if (kachaDotAnimator != null) {
            kachaDotAnimator.cancel();
        }

        kachaDotAnimator = ObjectAnimator.ofFloat(
                kachaSelectedDot, "alpha", 1f, 0.2f, 1f
        );
        kachaDotAnimator.setDuration(900);
        kachaDotAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        kachaDotAnimator.start();
    }

    private void stopKachaDotBlink() {
        if (kachaDotAnimator != null) {
            kachaDotAnimator.cancel();
            kachaDotAnimator = null;
        }

        if (kachaSelectedDot != null) {
            kachaSelectedDot.clearAnimation();
            kachaSelectedDot.setAlpha(1f);
            kachaSelectedDot.setVisibility(View.GONE);
        }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    @Override
    protected void onResume() {
        super.onResume();
        setKachaSelected();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopKachaDotBlink();
    }

    @Override
    protected void onDestroy() {
        stopKachaDotBlink();
        if (kachaListener != null) {
            databaseReference.removeEventListener(kachaListener);
        }
        super.onDestroy();
    }

    // ============================================================
    // DATA MODEL
    // ============================================================

    private static class KachaData {
        String key;
        String party;

        KachaData(String key, String party) {
            this.key = key;
            this.party = party;
        }
    }
}
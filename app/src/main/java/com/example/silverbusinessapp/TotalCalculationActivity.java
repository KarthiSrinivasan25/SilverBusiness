package com.example.silverbusinessapp;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.LinearGradient;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TotalCalculationActivity extends AppCompatActivity {

    TextView tvTotalDebit, tvTotalCredit, tvTotalSum, tvTransactionDate, tvBalanceStatus, tvPageIndicator;
    TextView tvSimpleDebitTotal, tvSimpleCreditTotal, tvRemainderDebit, tvRemainderCredit;

    RecyclerView transactionRecycler;
    ImageView btnExportPdf, btnPrevious, btnNext; //btnShare
    LinearLayout llSimpleTotal;
    LinearLayout llTotalRemainderWrapper;

    TextView tvRemainderLabel;
    LinearLayout llRemainderContainer;



    String userEmail, customerId;
    ArrayList<String> selectedTransactionIds;
    DatabaseReference transactionRef, customerRef;

    // Add customer name variable
    String customerName = "Customer";

    String phoneNumber = "Not Available";

    // ===== FIX: Flag to know whether these IDs live under closed_transactions or transactions =====
    private boolean isFromClosedTransactions = false;

    double totalDebit = 0.0;
    double totalCredit = 0.0;
    double totalCashRemainingDebit = 0.0;
    double totalCashRemainingCredit = 0.0;
    int processed = 0;

    // Store transaction data for PDF generation and RecyclerView
    ArrayList<Map<String, Object>> transactionDataList = new ArrayList<>();
    ArrayList<Map<String, Object>> allTransactionDataList = new ArrayList<>(); // All data for PDF
    private TransactionAdapter transactionAdapter;

    private android.app.AlertDialog downloadDialog;


    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int MIN_DOWNLOAD_DURATION = 2500;

    // Pagination variables
    private int currentPage = 1;
    private int itemsPerPage = 10;
    private int totalPages = 1;

    // Add missing variable
    private byte[] generatedPdfBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_total_calculation);

        initializeViews();
        setupRecyclerView();

        customerId = getIntent().getStringExtra("customerId");
        selectedTransactionIds = getIntent().getStringArrayListExtra("selectedTransactionIds");
        // ===== FIX: read the flag sent from ClosedTransactionsActivity =====
        isFromClosedTransactions = getIntent().getBooleanExtra("isFromClosedTransactions", false);

        if (customerId == null || selectedTransactionIds == null) {
            Toast.makeText(this, "Missing data!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = user.getUid();

        // ===== FIX: point transactionRef to the correct Firebase node =====
        if (isFromClosedTransactions) {
            // Selected IDs came from CLOSED transactions list
            transactionRef = FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(uid)
                    .child("closed_transactions")
                    .child(customerId);
        } else {
            // Normal / open transactions flow (unchanged, original behavior)
            transactionRef = FirebaseDatabase.getInstance()
                    .getReference("Users")
                    .child(uid)
                    .child("customers")
                    .child(customerId)
                    .child("transactions");
        }

        // Customer reference stays the same regardless of source
        customerRef = FirebaseDatabase.getInstance()
                .getReference("Users")
                .child(uid)
                .child("customers")
                .child(customerId);

        fetchCustomerName();
        fetchPhoneNumber();
        fetchTransactionData();
        setupClickListeners();
        updateTransactionDate();
        updatePaginationUI();
    }

    private void initializeViews() {
        tvTotalDebit = findViewById(R.id.tv_total_debit);
        tvTotalCredit = findViewById(R.id.tv_total_credit);
        tvTotalSum = findViewById(R.id.tv_total_sum);
        tvTransactionDate = findViewById(R.id.tv_transaction_date);
        tvBalanceStatus = findViewById(R.id.tv_balance_status);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);

        // Simple Total Row views
        tvSimpleDebitTotal = findViewById(R.id.tv_simple_debit_total);
        tvSimpleCreditTotal = findViewById(R.id.tv_simple_credit_total);

        // Remainder views (under Total)
        tvRemainderDebit = findViewById(R.id.tv_remainder_debit);
        tvRemainderCredit = findViewById(R.id.tv_remainder_credit);
        llSimpleTotal = findViewById(R.id.ll_simple_total);
        llTotalRemainderWrapper = findViewById(R.id.ll_total_remainder_wrapper);
        llRemainderContainer = findViewById(R.id.ll_remainder_container);
        tvRemainderLabel = findViewById(R.id.tv_remainder_label);




        transactionRecycler = findViewById(R.id.transaction_recycler);

        btnExportPdf = findViewById(R.id.btn_export_pdf);
        //   btnShare = findViewById(R.id.btn_share);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
    }

    private void setupRecyclerView() {
        transactionRecycler.setLayoutManager(new LinearLayoutManager(this));
        transactionAdapter = new TransactionAdapter(new ArrayList<>());
        transactionRecycler.setAdapter(transactionAdapter);
    }

    private void setupClickListeners() {
        btnExportPdf.setOnClickListener(v -> generateAndViewPdf());
        //btnShare.setOnClickListener(v -> shareTransactionSummary());
        btnPrevious.setOnClickListener(v -> navigateToPrevious());
        btnNext.setOnClickListener(v -> navigateToNext());
    }

    private void updateTransactionDate() {
        String currentDate = new java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm a")
                .format(new java.util.Date());
        tvTransactionDate.setText(currentDate);
    }

    private void fetchCustomerName() {
        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !name.trim().isEmpty()) {
                        customerName = name.trim();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Use default customer name if there's an error
            }
        });
    }

    private void fetchPhoneNumber() {
        customerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("phone").getValue(String.class);
                    if (name != null && !name.trim().isEmpty()) {
                        phoneNumber = name.trim();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Use default customer name if there's an error
            }
        });
    }

    private void fetchTransactionData() {
        for (String txnId : selectedTransactionIds) {
            transactionRef.child(txnId).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String txnName = snapshot.child("transactionName").getValue(String.class);
                    if (txnName == null) txnName = "Unnamed";

                    double txnDebit = 0.0;
                    double txnCredit = 0.0;

                    // Base "Debit Pure" / "Credit Pure" from Transaction Details (static + dynamic rows total)
                    Double baseDebit = snapshot.child("totalDebitPure").getValue(Double.class);
                    Double baseCredit = snapshot.child("totalCreditPure").getValue(Double.class);

                    if (baseDebit != null) txnDebit = baseDebit;
                    if (baseCredit != null) txnCredit = baseCredit;

                    // ===== FIX: When Show Cash Details is ON for this record, add the cash-side
                    // pure debit/credit (from cashDetails.static + cashDetails.dynamic rows) on top
                    // of the base Debit Pure / Credit Pure — exactly like the "Total DP" / "Total CP"
                    // shown live on the edit screen. When Show Cash Details is OFF, we simply keep
                    // the plain Transaction Details value (base only) — no cash math at all. =====
                    Boolean showCashDetails = snapshot.child("showCashDetails").getValue(Boolean.class);
                    if (showCashDetails == null) showCashDetails = false;

                    if (showCashDetails) {
                        double cashDebitPure = 0.0;
                        double cashCreditPure = 0.0;

                        DataSnapshot cashDetailsSnap = snapshot.child("cashDetails");
                        if (cashDetailsSnap.exists()) {
                            // Static cash entry (single Debits cash / Credits cash block)
                            DataSnapshot staticCash = cashDetailsSnap.child("static");
                            if (staticCash.exists()) {
                                Double staticPureDebit = staticCash.child("pureDebit").getValue(Double.class);
                                Double staticPureCredit = staticCash.child("pureCredit").getValue(Double.class);
                                if (staticPureDebit != null) cashDebitPure += staticPureDebit;
                                if (staticPureCredit != null) cashCreditPure += staticPureCredit;
                            }

                            // Dynamic cash rows (additional cash entries, if any were added)
                            DataSnapshot dynamicCash = cashDetailsSnap.child("dynamic");
                            if (dynamicCash.exists()) {
                                for (DataSnapshot cashRow : dynamicCash.getChildren()) {
                                    Double rowPureDebit = cashRow.child("pureDebit").getValue(Double.class);
                                    Double rowPureCredit = cashRow.child("pureCredit").getValue(Double.class);
                                    if (rowPureDebit != null) cashDebitPure += rowPureDebit;
                                    if (rowPureCredit != null) cashCreditPure += rowPureCredit;
                                }
                            }
                        }

                        txnDebit += cashDebitPure;
                        txnCredit += cashCreditPure;
                    }

                    // Only add to totals and show row if at least one value is non-zero
                    if (txnDebit > 0.0 || txnCredit > 0.0) {
                        totalDebit += txnDebit;
                        totalCredit += txnCredit;

                        // Store transaction data for PDF and RecyclerView
                        Map<String, Object> txnData = new HashMap<>();
                        txnData.put("name", txnName);
                        txnData.put("debit", txnDebit);
                        txnData.put("credit", txnCredit);
                        txnData.put("isCashRemaining", false);
                        txnData.put("isTransaction", true);
                        allTransactionDataList.add(txnData);
                    }

                    processed++;
                    if (processed == selectedTransactionIds.size()) {
                        // Calculate total pages
                        totalPages = (int) Math.ceil((double) allTransactionDataList.size() / itemsPerPage);
                        updateUI();
                        updateSimpleTotal();
                        updatePaginationUI();
                        showCurrentPageData();
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Toast.makeText(TotalCalculationActivity.this, "Error loading data", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void updateUI() {
        // Update summary cards
        tvTotalDebit.setText("₹" + formatValue(totalDebit));
        tvTotalCredit.setText("₹" + formatValue(totalCredit));

        double remainder = totalDebit - totalCredit;
        tvTotalSum.setText("₹" + formatValue(Math.abs(remainder)));

        // Update balance status with background colors
        if (remainder > 0) {
            tvBalanceStatus.setText("Balance Pure");
            tvTotalSum.setTextColor(Color.parseColor("#2E7D32")); // Dark green text
            tvBalanceStatus.setTextColor(Color.parseColor("#2E7D32")); // Dark green text
            //tvTotalSum.setBackgroundColor(Color.parseColor("#E8F5E9")); // Light green background


        } else if (remainder < 0) {
            tvBalanceStatus.setText("Ex Pure");
            tvTotalSum.setTextColor(Color.parseColor("#C62828")); // Dark red text
            tvBalanceStatus.setTextColor(Color.parseColor("#C62828")); // Dark red text
            // Optionally set background for the card
            //tvTotalSum.setBackgroundColor(Color.parseColor("#FFEBEE")); // Light red background
        } else {
            tvBalanceStatus.setText("Balanced");
            tvTotalSum.setTextColor(Color.parseColor("#EF6C00")); // Orange for balanced
            tvBalanceStatus.setTextColor(Color.parseColor("#EF6C00")); // Orange for balanced
            tvTotalSum.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private void updateSimpleTotal() {
        // Update total row
        tvSimpleDebitTotal.setText("₹" + formatValue(totalDebit));
        tvSimpleCreditTotal.setText("₹" + formatValue(totalCredit));

        // Calculate remainder
        double remainder = totalDebit - totalCredit;

        // Clear both remainder columns first
        tvRemainderDebit.setText("");
        tvRemainderCredit.setText("");
        tvRemainderLabel.setText("");

        // Show/hide remainder container based on whether there's a remainder
        if (remainder != 0.0) {
            llRemainderContainer.setVisibility(View.VISIBLE);

            if (remainder > 0) {
                // Positive remainder (Balance Pure) - show in debit column
                tvRemainderLabel.setText("Balance Pure");
                tvRemainderDebit.setText("₹" + formatValue(remainder));

                // Set colors and background
                tvRemainderLabel.setTextColor(Color.parseColor("#2E7D32")); // Dark green text
                tvRemainderDebit.setTextColor(Color.parseColor("#2E7D32")); // Dark green text

                // Set green background (like PDF)
                llRemainderContainer.setBackgroundResource(R.drawable.balance_pure_background);

                tvRemainderCredit.setText("");

            } else {
                // Negative remainder (Ex Pure) - show in credit column
                tvRemainderLabel.setText("Ex Pure");
                tvRemainderCredit.setText("₹" + formatValue(Math.abs(remainder)));

                // Set colors and background
                tvRemainderLabel.setTextColor(Color.parseColor("#C62828")); // Dark red text
                tvRemainderCredit.setTextColor(Color.parseColor("#C62828")); // Dark red text

                // Set red background (like PDF)
                llRemainderContainer.setBackgroundResource(R.drawable.ex_pure_background);

                tvRemainderDebit.setText("");
            }
        } else {
            // Zero remainder - show balanced style
            llRemainderContainer.setVisibility(View.VISIBLE);
            tvRemainderLabel.setText("Balanced");
            tvRemainderDebit.setText("₹0.00");
            tvRemainderCredit.setText("₹0.00");

            // Set balanced colors and background
            tvRemainderLabel.setTextColor(Color.parseColor("#EF6C00")); // Orange text
            tvRemainderDebit.setTextColor(Color.parseColor("#EF6C00")); // Orange text
            tvRemainderCredit.setTextColor(Color.parseColor("#EF6C00")); // Orange text

            // Set orange background
            llRemainderContainer.setBackgroundResource(R.drawable.balanced_background);
        }
    }

    private void showCurrentPageData() {
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allTransactionDataList.size());

        ArrayList<Map<String, Object>> currentPageData = new ArrayList<>();
        for (int i = startIndex; i < endIndex; i++) {
            currentPageData.add(allTransactionDataList.get(i));
        }

        transactionAdapter.updateData(currentPageData);

        // Show/hide total and remainder wrapper based on page
        if (currentPage == totalPages) {
            llTotalRemainderWrapper.setVisibility(View.VISIBLE);
            updateSimpleTotal(); // This updates both total and remainder
        } else {
            llTotalRemainderWrapper.setVisibility(View.GONE);
        }

        updatePaginationUI();
    }


    private void updatePaginationUI() {
        // Update page indicator
        tvPageIndicator.setText("Page " + currentPage + " of " + totalPages);

        // Show/hide navigation buttons
        if (totalPages <= 1) {
            btnPrevious.setVisibility(View.INVISIBLE);
            btnNext.setVisibility(View.INVISIBLE);
        } else {
            btnPrevious.setVisibility(currentPage > 1 ? View.VISIBLE : View.INVISIBLE);
            btnNext.setVisibility(currentPage < totalPages ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void navigateToPrevious() {
        if (currentPage > 1) {
            currentPage--;
            showCurrentPageData();
        }
    }

    private void navigateToNext() {
        if (currentPage < totalPages) {
            currentPage++;
            showCurrentPageData();
        }
    }

    private void generatePdf() {
        try {
            int pageWidth = 595;
            int pageHeight = 842;
            int marginBottom = 80;
            int marginTop = 120;

            PdfDocument pdfDocument = new PdfDocument();

            // Create paints with better styling
            Paint textPaint = createTextPaint(Color.BLACK, 8, Typeface.NORMAL);
            Paint boldTextPaint = createTextPaint(Color.BLACK, 8, Typeface.BOLD);
            Paint italicTextPaint = createTextPaint(Color.BLACK, 8, Typeface.ITALIC);
            Paint headerTextPaint = createTextPaint(Color.WHITE, 10, Typeface.BOLD);
            Paint titleTextPaint = createTextPaint(Color.DKGRAY, 10, Typeface.BOLD);
            Paint footerTextPaint = createTextPaint(Color.GRAY, 8, Typeface.NORMAL);
            Paint summaryValuePaint = createTextPaint(Color.WHITE, 10, Typeface.BOLD);
            Paint summaryLabelPaint = createTextPaint(Color.WHITE, 8, Typeface.NORMAL);

            // Border paints
            Paint thinBorderPaint = createBorderPaint(Color.LTGRAY, 1);
            Paint thickBorderPaint = createBorderPaint(Color.DKGRAY, 2);
            Paint headerBorderPaint = createBorderPaint(Color.parseColor("#2E7D32"), 1);

            // Background paints
            Paint headerBgPaint = createFillPaint(Color.parseColor("#2E7D32"));
            Paint alternateRowPaint = createFillPaint(Color.parseColor("#F5F5F5"));
            Paint totalRowPaint = createFillPaint(Color.parseColor("#E8F5E8"));

            // Summary card background paints
            Paint debitCardPaint = createFillPaint(Color.parseColor("#E8F5E9"));
            Paint creditCardPaint = createFillPaint(Color.parseColor("#FFEBEE"));
            Paint balanceCardPaint = createFillPaint(Color.parseColor("#E3F2FD"));

            // Create first page
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdfDocument.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            // Draw background
            drawBackground(canvas, pageWidth, pageHeight);

            int startX = 88; // centered: (pageWidth 595 - total table width 420) / 2
            int startY = marginTop;
            int currentY = startY;
            int currentPage = 1;

            // Draw header with logo - ONLY ON FIRST PAGE
            drawHeader(canvas, pageWidth, marginTop, customerName, titleTextPaint, headerBgPaint);
            currentY -= 20;

            // Company info section - ONLY ON FIRST PAGE
            currentY = drawCompanyInfo(canvas, startX, currentY, textPaint, boldTextPaint);
            currentY += 0;

            // Summary section with 3-card design - ONLY ON FIRST PAGE
            currentY = drawSummarySectionThreeCards(canvas, startX, currentY, totalDebit, totalCredit,
                    summaryLabelPaint, summaryValuePaint, debitCardPaint, creditCardPaint, balanceCardPaint);
            currentY += 20;

            // Table Headers
            String[] headers = {"Transaction", "Debit Pure", "Credit Pure"};
            int[] colWidths = {220, 100, 100};
            int baseRowHeight = 20;

            // Draw table header
            currentY = drawTableHeader(canvas, startX, currentY, headers, colWidths, baseRowHeight,
                    headerTextPaint, headerBorderPaint, headerBgPaint);

            // Data Rows with dynamic height
            boolean alternate = false;
            for (int i = 0; i < allTransactionDataList.size(); i++) {
                Map<String, Object> txnData = allTransactionDataList.get(i);
                String name = (String) txnData.get("name");
                double debit = (Double) txnData.get("debit");
                double credit = (Double) txnData.get("credit");
                boolean isCashRemaining = (Boolean) txnData.get("isCashRemaining");

                // Skip rows where both values are 0.00
                if (debit == 0.0 && credit == 0.0) {
                    continue;
                }

                // Calculate required row height for this transaction
                int requiredRowHeight = calculateRowHeight(name, colWidths[0] - 20, textPaint, baseRowHeight);

                // Check if we need a new page
                if (currentY + requiredRowHeight > pageHeight - marginBottom) {
                    // Draw footer for current page before finishing
                    drawFooter(canvas, pageWidth, pageHeight, currentPage, footerTextPaint);
                    pdfDocument.finishPage(page);

                    currentPage++;
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create();
                    page = pdfDocument.startPage(pageInfo);
                    canvas = page.getCanvas();
                    drawBackground(canvas, pageWidth, pageHeight);

                    int reducedTopMargin = 60; // Reduced from 120 to 60
                    currentY = reducedTopMargin;

                    // NO HEADER on subsequent pages - start directly with table header

                    currentY = drawTableHeader(canvas, startX, currentY, headers, colWidths, baseRowHeight,
                            headerTextPaint, headerBorderPaint, headerBgPaint);
                    alternate = false;
                }

                Paint currentPaint = isCashRemaining ? italicTextPaint : textPaint;

                // Alternate row background
                if (alternate) {
                    canvas.drawRect(startX, currentY, startX + colWidths[0] + colWidths[1] + colWidths[2],
                            currentY + requiredRowHeight, alternateRowPaint);
                }

                // Draw table row with dynamic height
                drawTableRowWithWrapping(canvas, startX, currentY, name, debit, credit, colWidths,
                        requiredRowHeight, currentPaint, thinBorderPaint);

                currentY += requiredRowHeight;
                alternate = !alternate;
            }

            // Draw totals section
            if (currentY + (baseRowHeight * 3) > pageHeight - marginBottom) {
                // Draw footer for current page before finishing
                drawFooter(canvas, pageWidth, pageHeight, currentPage, footerTextPaint);
                pdfDocument.finishPage(page);

                currentPage++;
                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create();
                page = pdfDocument.startPage(pageInfo);
                canvas = page.getCanvas();
                drawBackground(canvas, pageWidth, pageHeight);
                currentY = marginTop; // Reduced top margin for subsequent pages
            }

            currentY += 20;
            currentY = drawTotalsSection(canvas, startX, currentY, totalDebit, totalCredit,
                    colWidths, baseRowHeight, boldTextPaint, thickBorderPaint, totalRowPaint);

            // Draw footer for the last page
            drawFooter(canvas, pageWidth, pageHeight, currentPage, footerTextPaint);

            // Finish the last page
            pdfDocument.finishPage(page);

            // Write PDF to Downloads
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            pdfDocument.writeTo(byteArrayOutputStream);
            pdfDocument.close();

            savePdfToDownloads(byteArrayOutputStream.toByteArray());

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    // New method for 3-card summary design
    private int drawSummarySectionThreeCards(Canvas canvas, int startX, int currentY,
                                             double totalDebit, double totalCredit,
                                             Paint labelPaint, Paint valuePaint,
                                             Paint debitBgPaint, Paint creditBgPaint, Paint balanceBgPaint) {
        // Card dimensions and spacing
        int cardWidth = 120;
        int cardHeight = 45;
        int cardSpacing = 15;

        // Calculate total width of all cards including spacing
        int totalCardsWidth = (cardWidth * 3) + (cardSpacing * 2);

        // Get canvas width to calculate center position
        int canvasWidth = canvas.getWidth();

        // Calculate starting X position to center all three cards
        int centerStartX = (canvasWidth - totalCardsWidth) / 2;

        // Save original font sizes
        float originalLabelSize = labelPaint.getTextSize();
        float originalValueSize = valuePaint.getTextSize();

        // Set smaller font sizes for cards
        labelPaint.setTextSize(8f);
        valuePaint.setTextSize(9f);

        // Draw Debit Card - GREEN BACKGROUND
        drawSummaryCard(canvas, centerStartX, currentY, cardWidth, cardHeight,
                "Total Debit", "₹" + formatValue(totalDebit),
                debitBgPaint, labelPaint, valuePaint);

        // Draw Credit Card - RED BACKGROUND
        drawSummaryCard(canvas, centerStartX + cardWidth + cardSpacing, currentY, cardWidth, cardHeight,
                "Total Credit", "₹" + formatValue(totalCredit),
                creditBgPaint, labelPaint, valuePaint);

        // Draw Balance Card - dynamic based on balance
        double balance = totalDebit - totalCredit;
        String balanceLabel = balance >= 0 ? "Balance Pure" : "Ex Pure";
        String balanceValue = "₹" + formatValue(Math.abs(balance));

        // Choose background color based on balance type
        Paint balanceCardBgPaint;
        if (balance >= 0) {
            // Balance Pure - GREEN background
            balanceCardBgPaint = debitBgPaint; // Green
        } else {
            // Ex Pure - RED background
            balanceCardBgPaint = creditBgPaint; // Red
        }

        drawSummaryCard(canvas, centerStartX + (cardWidth + cardSpacing) * 2, currentY, cardWidth, cardHeight,
                balanceLabel, balanceValue,
                balanceCardBgPaint, labelPaint, valuePaint);

        // Restore original font sizes for other drawing operations
        labelPaint.setTextSize(originalLabelSize);
        valuePaint.setTextSize(originalValueSize);

        return currentY + cardHeight;
    }

    // Helper method to draw individual summary cards
    // Helper method to draw individual summary cards with dark text
    private void drawSummaryCard(Canvas canvas, float x, float y, float width, float height,
                                 String label, String value, Paint bgPaint,
                                 Paint labelPaint, Paint valuePaint) {
        // Draw shadow first
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(Color.parseColor("#40000000"));
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x + 2, y + 2, x + width + 2, y + height + 2, shadowPaint);

        // Draw card background
        canvas.drawRect(x, y, x + width, y + height, bgPaint);

        // Draw card border
        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#20000000"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1);
        canvas.drawRect(x, y, x + width, y + height, borderPaint);

        // Create NEW paint objects with dark colors
        Paint darkLabelPaint = new Paint();
        Paint darkValuePaint = new Paint();

        // Set text properties
        darkLabelPaint.setTextSize(8f);
        darkLabelPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        darkLabelPaint.setAntiAlias(true);
        darkLabelPaint.setTextAlign(Paint.Align.CENTER);

        darkValuePaint.setTextSize(9f);
        darkValuePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        darkValuePaint.setAntiAlias(true);
        darkValuePaint.setTextAlign(Paint.Align.CENTER);

        // Determine text color based on label
        if (label.contains("Debit")) {
            // Debit card (green background) -> dark green text
            darkLabelPaint.setColor(Color.parseColor("#2E7D32")); // Dark green
            darkValuePaint.setColor(Color.parseColor("#2E7D32")); // Dark green
        } else if (label.contains("Credit")) {
            // Credit card (red background) -> dark red text
            darkLabelPaint.setColor(Color.parseColor("#C62828")); // Dark red
            darkValuePaint.setColor(Color.parseColor("#C62828")); // Dark red
        } else if (label.contains("Ex Pure")) {
            // Negative balance (red background) -> dark red text
            darkLabelPaint.setColor(Color.parseColor("#C62828")); // Dark red
            darkValuePaint.setColor(Color.parseColor("#C62828")); // Dark red
        } else {
            // Positive balance (green background) -> dark green text
            darkLabelPaint.setColor(Color.parseColor("#2E7D32")); // Dark green
            darkValuePaint.setColor(Color.parseColor("#2E7D32")); // Dark green
        }

        // Calculate text positions
        float centerX = x + width / 2;

        // Draw label (centered) with dark color
        float labelY = y + height / 2 - 5; // Position above center
        canvas.drawText(label, centerX, labelY, darkLabelPaint);

        // Draw value (centered) with dark color
        float valueY = y + height / 2 + 12; // Position below center
        canvas.drawText(value, centerX, valueY, darkValuePaint);

        // Reset text alignment on original paints if needed elsewhere
        labelPaint.setTextAlign(Paint.Align.LEFT);
        valuePaint.setTextAlign(Paint.Align.LEFT);
    }


    // Helper method to create text paint
    private Paint createTextPaint(int color, float textSize, int style) {
        Paint paint = new Paint();
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        paint.setAntiAlias(true);
        return paint;
    }

    // Helper method to create border paint
    private Paint createBorderPaint(int color, float strokeWidth) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        return paint;
    }

    // Helper method to create fill paint
    private Paint createFillPaint(int color) {
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    // Helper method to draw background
    // Helper method to draw background with watermark
    private void drawBackground(Canvas canvas, int pageWidth, int pageHeight) {
        // Draw white background
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, pageWidth, pageHeight, bgPaint);

    }


    private void drawHeader(Canvas canvas, int pageWidth, int marginTop, String customerName,
                            Paint titlePaint, Paint bgPaint) {

        LinearGradient gradient = new LinearGradient(
                0, 0, pageWidth, 0,
                Color.parseColor("#4A148C"),   // Deep Purple
                Color.parseColor("#7B1FA2"),   // Purple Accent
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);

        // Header height
        int headerHeight = 80;

        // Draw gradient background
        canvas.drawRect(0, 0, pageWidth, headerHeight, bgPaint);

        // ====== DRAW CIRCULAR LOGO ======
        try {
            // Load your logo
            Bitmap originalLogo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_demo);

            // Create circular logo
            Bitmap circularLogo = getCircularBitmap(originalLogo);

            // Scale to appropriate size
            int logoSize = 50;
            Bitmap scaledLogo = Bitmap.createScaledBitmap(circularLogo, logoSize, logoSize, true);

            // Add white border around circular logo
            Paint borderPaint = new Paint();
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3);
            borderPaint.setAntiAlias(true);

            // Draw logo with shadow effect
            Paint shadowPaint = new Paint();
            shadowPaint.setColor(Color.parseColor("#80000000"));

            // Draw shadow first
            canvas.drawCircle(20 + logoSize/2 + 2, headerHeight/2 + 2, logoSize/2 + 1, shadowPaint);

            // Draw logo
            canvas.drawBitmap(scaledLogo, 20, (headerHeight - logoSize) / 2, null);

            // Draw white border around logo
            canvas.drawCircle(20 + logoSize/2, headerHeight/2, logoSize/2, borderPaint);

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: Draw a simple circle if logo fails
            Paint circlePaint = new Paint();
            circlePaint.setColor(Color.WHITE);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(20 + 45, headerHeight/2, 45, circlePaint);
        }

        // ====== COMPANY NAME CENTERED ======
        Paint companyTextPaint = new Paint();
        companyTextPaint.setColor(Color.WHITE);
        companyTextPaint.setTextSize(20f);
        companyTextPaint.setFakeBoldText(true);
        companyTextPaint.setAntiAlias(true);
        companyTextPaint.setTextAlign(Paint.Align.CENTER);

        String companyName = "PST Silver Ledger";

        // Get text bounds for perfect vertical centering
        Paint.FontMetrics textMetrics = companyTextPaint.getFontMetrics();
        float centerX = pageWidth / 2f;
        float centerY = headerHeight / 2f - (textMetrics.ascent + textMetrics.descent) / 2;

        // Add subtle text shadow
        Paint textShadowPaint = new Paint();
        textShadowPaint.set(companyTextPaint);
        textShadowPaint.setColor(Color.parseColor("#80000000"));
        canvas.drawText(companyName, centerX + 1, centerY + 1, textShadowPaint);

        // Draw main company text
        canvas.drawText(companyName, centerX, centerY, companyTextPaint);

        // ====== CUSTOMER INFO BOX ON RIGHT SIDE ======
        int rightMargin = 12;
        int boxWidth = 140;  // Fixed width for the box
        int boxPadding = 8;

        // Calculate box dimensions
        float boxLeft = pageWidth - boxWidth - rightMargin;
        float boxTop = headerHeight / 2f - 27;  // Adjusted to center vertically
        float boxRight = pageWidth - rightMargin;
        float boxBottom = boxTop + 55;  // Height for 3 lines with padding

        // Draw box background
        Paint boxBgPaint = new Paint();
        boxBgPaint.setColor(Color.parseColor("#20FFFFFF"));  // Semi-transparent white
        boxBgPaint.setStyle(Paint.Style.FILL);
        boxBgPaint.setAntiAlias(true);

        // Draw box border
        Paint boxBorderPaint = new Paint();
        boxBorderPaint.setColor(Color.WHITE);
        boxBorderPaint.setStyle(Paint.Style.STROKE);
        boxBorderPaint.setStrokeWidth(1.5f);
        boxBorderPaint.setAntiAlias(true);

        // Draw box shadow
        Paint shadowBoxPaint = new Paint();
        shadowBoxPaint.setColor(Color.parseColor("#40000000"));
        canvas.drawRect(boxLeft + 2, boxTop + 2, boxRight + 2, boxBottom + 2, shadowBoxPaint);

        // Draw box background and border
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, boxBgPaint);
        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, boxBorderPaint);

        // Draw rounded corners (optional)
        Paint roundCornerPaint = new Paint();
        roundCornerPaint.setColor(Color.parseColor("#4A148C"));
        roundCornerPaint.setStyle(Paint.Style.FILL);
        int cornerRadius = 8;

        // Draw rounded corners by drawing small circles
        canvas.drawCircle(boxLeft, boxTop, cornerRadius, roundCornerPaint);  // Top-left
        canvas.drawCircle(boxRight, boxTop, cornerRadius, roundCornerPaint); // Top-right
        canvas.drawCircle(boxLeft, boxBottom, cornerRadius, roundCornerPaint);  // Bottom-left
        canvas.drawCircle(boxRight, boxBottom, cornerRadius, roundCornerPaint); // Bottom-right

        // Text paints for customer info
        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.parseColor("#FFD700"));  // Gold color for labels
        labelPaint.setTextSize(8f);
        labelPaint.setFakeBoldText(true);
        labelPaint.setAntiAlias(true);
        labelPaint.setTextAlign(Paint.Align.LEFT);

        Paint valuePaint = new Paint();
        valuePaint.setColor(Color.WHITE);
        valuePaint.setTextSize(8f);
        valuePaint.setAntiAlias(true);
        valuePaint.setTextAlign(Paint.Align.LEFT);

        // Get current date
        String date = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());

        // Calculate starting position inside the box
        float textStartX = boxLeft + boxPadding;
        float currentY = boxTop + boxPadding + 7;  // Start from top + padding + baseline adjustment

        // Draw customer info with proper alignment
        // Customer name
        canvas.drawText("Customer:", textStartX, currentY, labelPaint);
        float customerNameWidth = labelPaint.measureText("Customer:");
        canvas.drawText(truncateText(customerName, valuePaint, boxWidth - boxPadding * 2 - customerNameWidth - 5),
                textStartX + customerNameWidth + 5, currentY, valuePaint);

        // Date
        currentY += 15;
        canvas.drawText("Date:", textStartX, currentY, labelPaint);
        float dateLabelWidth = labelPaint.measureText("Date:");
        canvas.drawText(date, textStartX + dateLabelWidth + 5, currentY, valuePaint);

        // Phone number
        currentY += 15;
        canvas.drawText("Phone:", textStartX, currentY, labelPaint);
        float phoneLabelWidth = labelPaint.measureText("Phone:");
        canvas.drawText(truncateText(phoneNumber, valuePaint, boxWidth - boxPadding * 2 - phoneLabelWidth - 5),
                textStartX + phoneLabelWidth + 5, currentY, valuePaint);

        // ====== HEADER BOTTOM BORDER ======
        Paint bottomBorderPaint = new Paint();
        bottomBorderPaint.setColor(Color.parseColor("#FFD700")); // Gold border
        bottomBorderPaint.setStrokeWidth(2);
        canvas.drawLine(0, headerHeight, pageWidth, headerHeight, bottomBorderPaint);
    }

    // Helper method to create circular bitmap
    private Bitmap getCircularBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);

        // Draw circle
        canvas.drawCircle(bitmap.getWidth() / 2f,
                bitmap.getHeight() / 2f,
                bitmap.getWidth() / 2f,
                paint);

        // Use source in mode to crop bitmap to circle
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    // Helper method to truncate text if it's too long
    private String truncateText(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }

        // Try to truncate with ellipsis
        String ellipsis = "...";
        float ellipsisWidth = paint.measureText(ellipsis);

        int end = text.length();
        while (paint.measureText(text.substring(0, end)) + ellipsisWidth > maxWidth && end > 0) {
            end--;
        }

        if (end == 0) {
            return ellipsis;
        }

        return text.substring(0, end) + ellipsis;
    }



    private void drawSubsequentPageHeader(Canvas canvas, int pageWidth, int marginTop) {
        // Simple title for subsequent pages
        Paint titlePaint = createTextPaint(Color.DKGRAY, 18, Typeface.BOLD);
        String title = customerName + " - Statement (Continued)";

        titlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, pageWidth / 2, marginTop - 40, titlePaint);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        // Simple line separator
        Paint linePaint = createBorderPaint(Color.LTGRAY, 1);
        canvas.drawLine(40, marginTop - 20, pageWidth - 40, marginTop - 20, linePaint);
    }


    // Helper method to draw company info
    private int drawCompanyInfo(Canvas canvas, int startX, int currentY, Paint textPaint, Paint boldPaint) {
//        String date = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
//
//        canvas.drawText("Statement Date: ", startX, currentY, boldPaint);
//        canvas.drawText(date, startX + 150, currentY, textPaint);
//
//        currentY += 25;
//        canvas.drawText("Customer: ", startX, currentY, boldPaint);
//        canvas.drawText(customerName, startX + 150, currentY, textPaint);
//
//        currentY += 25;
//        canvas.drawText("Phone No: ", startX, currentY, boldPaint);
//        canvas.drawText(phoneNumber, startX + 150, currentY, textPaint);
        return currentY;
    }

    // Helper method to draw table header
    private int drawTableHeader(Canvas canvas, int startX, int currentY,
                                String[] headers, int[] colWidths, int rowHeight,
                                Paint textPaint, Paint borderPaint, Paint bgPaint) {
        int cellX = startX;
        int totalWidth = colWidths[0] + colWidths[1] + colWidths[2];

        // Draw header background
        canvas.drawRect(startX, currentY, startX + totalWidth, currentY + rowHeight, bgPaint);

        // Draw borders
        canvas.drawRect(startX, currentY, startX + totalWidth, currentY + rowHeight, borderPaint);
        canvas.drawRect(cellX, currentY, cellX + colWidths[0], currentY + rowHeight, borderPaint);
        canvas.drawRect(cellX + colWidths[0], currentY, cellX + colWidths[0] + colWidths[1], currentY + rowHeight, borderPaint);
        canvas.drawRect(cellX + colWidths[0] + colWidths[1], currentY, startX + totalWidth, currentY + rowHeight, borderPaint);

        // Draw header texts with centered alignment
        for (int i = 0; i < headers.length; i++) {
            float cellCenterX = cellX + colWidths[i] / 2;
            float textY = currentY + rowHeight / 2 - (textPaint.descent() + textPaint.ascent()) / 2;

            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(headers[i], cellCenterX, textY, textPaint);
            cellX += colWidths[i];
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
        return currentY + rowHeight;
    }

    // Calculate required row height based on text content
    private int calculateRowHeight(String text, int maxWidth, Paint paint, int baseRowHeight) {
        if (text == null || text.isEmpty()) {
            return baseRowHeight;
        }

        List<String> lines = breakTextIntoLines(text, maxWidth, paint);
        int lineCount = lines.size();

        // If single line, return base height, otherwise calculate based on line count
        if (lineCount <= 1) {
            return baseRowHeight;
        } else {
            Paint.FontMetrics fm = paint.getFontMetrics();
            float lineHeight = fm.descent - fm.ascent;
            return (int) (baseRowHeight + (lineCount - 1) * lineHeight);
        }
    }

    // Break text into lines based on max width
    private List<String> breakTextIntoLines(String text, float maxWidth, Paint paint) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return lines;
        }

        String[] words = text.split(" ");
        String currentLine = "";

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            float testWidth = paint.measureText(testLine);

            if (testWidth <= maxWidth) {
                currentLine = testLine;
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine);
                }
                currentLine = word;
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        return lines;
    }

    // Draw table row with text wrapping
    private void drawTableRowWithWrapping(Canvas canvas, int startX, int startY,
                                          String name, double debit, double credit,
                                          int[] colWidths, int rowHeight, Paint textPaint,
                                          Paint borderPaint) {
        int cellX = startX;
        int totalWidth = colWidths[0] + colWidths[1] + colWidths[2];

        // Draw cell borders
        canvas.drawRect(startX, startY, startX + totalWidth, startY + rowHeight, borderPaint);
        canvas.drawRect(cellX, startY, cellX + colWidths[0], startY + rowHeight, borderPaint);
        canvas.drawRect(cellX + colWidths[0], startY, cellX + colWidths[0] + colWidths[1], startY + rowHeight, borderPaint);
        canvas.drawRect(cellX + colWidths[0] + colWidths[1], startY, startX + totalWidth, startY + rowHeight, borderPaint);

        // Draw transaction name with wrapping
        drawWrappedText(canvas, name, startX + 5, startY, colWidths[0] - 10,
                rowHeight, textPaint, Paint.Align.LEFT);

        // Draw debit amount (right aligned)
        if (debit > 0.0) {
            String debitText = "₹" + formatValue(debit);
            drawSingleLineText(canvas, debitText, startX + colWidths[0] + 5, startY,
                    colWidths[1] - 10, rowHeight, textPaint, Paint.Align.RIGHT);
        }

        // Draw credit amount (right aligned)
        if (credit > 0.0) {
            String creditText = "₹" + formatValue(credit);
            drawSingleLineText(canvas, creditText, startX + colWidths[0] + colWidths[1] + 5, startY,
                    colWidths[2] - 10, rowHeight, textPaint, Paint.Align.RIGHT);
        }
    }

    // Draw wrapped text for transaction names
    private void drawWrappedText(Canvas canvas, String text, float x, float y,
                                 float maxWidth, float maxHeight, Paint paint, Paint.Align alignment) {
        if (text == null || text.isEmpty()) {
            return;
        }

        List<String> lines = breakTextIntoLines(text, maxWidth, paint);
        if (lines.isEmpty()) {
            return;
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = fm.descent - fm.ascent;
        float totalTextHeight = lines.size() * lineHeight;
        float startY = y + (maxHeight - totalTextHeight) / 2 - fm.ascent;

        paint.setTextAlign(alignment);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float drawY = startY + (i * lineHeight);

            float drawX = x;
            if (alignment == Paint.Align.CENTER) {
                drawX = x + maxWidth / 2;
            } else if (alignment == Paint.Align.RIGHT) {
                drawX = x + maxWidth;
            }

            canvas.drawText(line, drawX, drawY, paint);
        }

        paint.setTextAlign(Paint.Align.LEFT);
    }

    // Draw single line text for amounts (no wrapping needed)
    private void drawSingleLineText(Canvas canvas, String text, float x, float y,
                                    float maxWidth, float maxHeight, Paint paint, Paint.Align alignment) {
        if (text == null || text.isEmpty()) {
            return;
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = y + (maxHeight - (fm.descent - fm.ascent)) / 2 - fm.ascent;

        paint.setTextAlign(alignment);

        float drawX = x;
        if (alignment == Paint.Align.CENTER) {
            drawX = x + maxWidth / 2;
        } else if (alignment == Paint.Align.RIGHT) {
            drawX = x + maxWidth;
        }

        canvas.drawText(text, drawX, textY, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // Draw totals section
    // Draw totals section WITHOUT background for total row, only for final balance
    private int drawTotalsSection(Canvas canvas, int startX, int currentY,
                                  double totalDebit, double totalCredit,
                                  int[] colWidths, int rowHeight, Paint boldPaint,
                                  Paint borderPaint, Paint bgPaint) {
        int totalWidth = colWidths[0] + colWidths[1] + colWidths[2];

        // Draw colored backgrounds for each column
        Paint debitBgPaint = createFillPaint(Color.parseColor("#E8F5E9")); // Light green
        Paint creditBgPaint = createFillPaint(Color.parseColor("#FFEBEE")); // Light red

        // Draw "Total" cell background (no color)
        canvas.drawRect(startX, currentY, startX + colWidths[0], currentY + rowHeight, borderPaint);

        // Draw debit total cell background
        canvas.drawRect(startX + colWidths[0], currentY, startX + colWidths[0] + colWidths[1], currentY + rowHeight, debitBgPaint);
        canvas.drawRect(startX + colWidths[0], currentY, startX + colWidths[0] + colWidths[1], currentY + rowHeight, borderPaint);

        // Draw credit total cell background
        canvas.drawRect(startX + colWidths[0] + colWidths[1], currentY, startX + totalWidth, currentY + rowHeight, creditBgPaint);
        canvas.drawRect(startX + colWidths[0] + colWidths[1], currentY, startX + totalWidth, currentY + rowHeight, borderPaint);

        // Draw "Grand Total" text centered vertically
        float textY = currentY + rowHeight / 2 - (boldPaint.descent() + boldPaint.ascent()) / 2;

        boldPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Total", startX + 10, textY, boldPaint);

        // Create colored paints for debit and credit totals
        Paint debitTotalPaint = createTextPaint(Color.parseColor("#2E7D32"), boldPaint.getTextSize(), Typeface.BOLD); // Green
        Paint creditTotalPaint = createTextPaint(Color.parseColor("#C62828"), boldPaint.getTextSize(), Typeface.BOLD); // Red

        // Draw totals with colored text
        debitTotalPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("₹" + formatValue(totalDebit), startX + colWidths[0] + colWidths[1] - 10, textY, debitTotalPaint);

        creditTotalPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("₹" + formatValue(totalCredit), startX + totalWidth - 10, textY, creditTotalPaint);

        currentY += rowHeight;

        // Final balance row - STYLED BASED ON BALANCE TYPE
        double remain = totalDebit - totalCredit;
        String label = remain > 0 ? "Balance Pure" : "Ex Pure";
        String amount = "₹" + formatValue(Math.abs(remain));

        // Set colors based on balance type
        int bgColor, textColor;
        if (remain > 0) { // Balance Pure (Debit)
            bgColor = Color.parseColor("#E8F5E9"); // Light green background
            textColor = Color.parseColor("#2E7D32"); // Dark green text
        } else { // Ex Pure (Credit)
            bgColor = Color.parseColor("#FFEBEE"); // Light red background
            textColor = Color.parseColor("#C62828"); // Dark red text
        }

        // Create paints for this specific row
        Paint balanceBgPaint = createFillPaint(bgColor);
        Paint balanceTextPaint = createTextPaint(textColor, boldPaint.getTextSize(), Typeface.BOLD);

        // Draw background for balance row
        canvas.drawRect(startX, currentY, startX + totalWidth, currentY + rowHeight, balanceBgPaint);
        canvas.drawRect(startX, currentY, startX + totalWidth, currentY + rowHeight, borderPaint);

        // Draw label and amount centered vertically
        textY = currentY + rowHeight / 2 - (boldPaint.descent() + boldPaint.ascent()) / 2;

        balanceTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(label, startX + 10, textY, balanceTextPaint);

        balanceTextPaint.setTextAlign(Paint.Align.RIGHT);
        if (remain > 0) {
            canvas.drawText(amount, startX + colWidths[0] + colWidths[1] - 10, textY, balanceTextPaint);
        } else {
            canvas.drawText(amount, startX + totalWidth - 10, textY, balanceTextPaint);
        }

        boldPaint.setTextAlign(Paint.Align.LEFT);
        return currentY + rowHeight;
    }

    // Draw footer
    private void drawFooter(Canvas canvas, int pageWidth, int pageHeight, int currentPage, Paint textPaint) {
        String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String footerText = "Page " + currentPage + " | Generated on " + timestamp;
        String companyText = "PST Silver Ledger";

        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(footerText, pageWidth / 2, pageHeight - 30, textPaint);
        canvas.drawText(companyText, pageWidth / 2, pageHeight - 15, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Draw footer line
        Paint linePaint = createBorderPaint(Color.LTGRAY, 1);
        canvas.drawLine(40, pageHeight - 50, pageWidth - 40, pageHeight - 50, linePaint);
    }

    // Format double value to string
    private String formatValue(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(value);
    }

    // Save PDF to Downloads
    private void savePdfToDownloads(byte[] pdfData) {
        try {
            // Show downloading dialog immediately
            showDownloadingDialog();

            // Record start time
            final long startTime = System.currentTimeMillis();

            new Thread(() -> {
                boolean success = false;
                String fileName = "";
                String errorMessage = "";

                try {
                    Thread.sleep(300); // Small initial delay to ensure dialog is shown

                    String timestamp = new SimpleDateFormat("ddMMyyyy_HHmmss", Locale.getDefault()).format(new Date());
                    fileName = customerName.replaceAll("[^a-zA-Z0-9]", "_") /*+
                            "_Statement_" + timestamp */ + ".pdf";

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

                // Calculate remaining time to show animation for at least MIN_DOWNLOAD_DURATION
                final long elapsedTime = System.currentTimeMillis() - startTime;
                final long remainingTime = Math.max(0, MIN_DOWNLOAD_DURATION - elapsedTime);

                handler.postDelayed(() -> {
                    // Dismiss downloading dialog
                    dismissDownloadDialog();

                    // Show result dialog
                    showDownloadResultDialog(finalSuccess, finalFileName, finalErrorMessage);
                }, remainingTime);

            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            dismissDownloadDialog();
            showDownloadResultDialog(false, "", e.getMessage());
        }
    }

    // Add this helper method to dismiss dialog
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

    // Update the showDownloadingDialog method:
    private void showDownloadingDialog() {
        runOnUiThread(() -> {
            try {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_downloading, null);

                // Get Lottie animation view
                LottieAnimationView lottieAnimationView = dialogView.findViewById(R.id.lottie_animation);
                TextView tvStatus = dialogView.findViewById(R.id.tv_status);
                TextView tvMessage = dialogView.findViewById(R.id.tv_message);

                // Set animation with fallback
                try {
                    // Try to load from raw resources
                    lottieAnimationView.setAnimation(R.raw.download_animation);
                } catch (Exception e) {
                    // Try to load from assets
                    try {
                        lottieAnimationView.setAnimation("downloading.json");
                    } catch (Exception ex) {
                        // Use a simple animation if file not found
                        lottieAnimationView.setAnimation("loading.json");
                    }
                }

                lottieAnimationView.playAnimation();
                lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);

                // Set texts
                tvStatus.setText("Downloading PDF...");
                tvMessage.setText("Please wait");

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setView(dialogView);
                builder.setCancelable(false);

                downloadDialog = builder.create();

                // Make dialog background transparent
                if (downloadDialog.getWindow() != null) {
                    downloadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                // Prevent dismiss on touch outside
                downloadDialog.setCanceledOnTouchOutside(false);

                downloadDialog.show();

                // Force the animation to start
                lottieAnimationView.resumeAnimation();

            } catch (Exception e) {
                e.printStackTrace();
                // Fallback to simple progress dialog if Lottie fails
                ProgressDialog progressDialog = new ProgressDialog(this);
                progressDialog.setMessage("Downloading PDF...");
                progressDialog.setCancelable(false);
                progressDialog.show();
                downloadDialog = progressDialog;
            }
        });
    }

    // Update the showDownloadResultDialog method:
    private void showDownloadResultDialog(boolean success, String fileName, String errorMessage) {
        runOnUiThread(() -> {
            try {
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_downloading, null);

                LottieAnimationView lottieAnimationView = dialogView.findViewById(R.id.lottie_animation);
                TextView tvStatus = dialogView.findViewById(R.id.tv_status);
                TextView tvMessage = dialogView.findViewById(R.id.tv_message);

                if (success) {
                    // Success animation and message
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
                    tvMessage.setTextColor(Color.parseColor("#666666"));
                } else {
                    // Error animation and message
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
                    tvMessage.setTextColor(Color.parseColor("#666666"));
                }

                lottieAnimationView.playAnimation();
                lottieAnimationView.setRepeatCount(0); // Play once

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setView(dialogView);

                final android.app.AlertDialog resultDialog = builder.create();

                if (resultDialog.getWindow() != null) {
                    resultDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                // Auto-dismiss after delay
                int dismissDelay = success ? 2500 : 3000;

                resultDialog.show();

                handler.postDelayed(() -> {
                    if (resultDialog != null && resultDialog.isShowing()) {
                        try {
                            resultDialog.dismiss();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, dismissDelay);

            } catch (Exception e) {
                e.printStackTrace();
                // Silent fallback - no toast
            }
        });
    }

    private void generateAndViewPdf() {
        try {
            // Show loading dialog
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Generating PDF...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            new Thread(() -> {
                try {
                    // Generate PDF using the same method but store bytes
                    int pageWidth = 595;
                    int pageHeight = 842;
                    int marginBottom = 80;
                    int marginTop = 120;

                    PdfDocument pdfDocument = new PdfDocument();

                    // Create paints with better styling
                    Paint textPaint = createTextPaint(Color.BLACK, 8, Typeface.NORMAL);
                    Paint boldTextPaint = createTextPaint(Color.BLACK, 8, Typeface.BOLD);
                    Paint italicTextPaint = createTextPaint(Color.BLACK, 8, Typeface.ITALIC);
                    Paint headerTextPaint = createTextPaint(Color.WHITE, 10, Typeface.BOLD);
                    Paint titleTextPaint = createTextPaint(Color.DKGRAY, 10, Typeface.BOLD);
                    Paint footerTextPaint = createTextPaint(Color.GRAY, 8, Typeface.NORMAL);
                    Paint summaryValuePaint = createTextPaint(Color.WHITE, 10, Typeface.BOLD);
                    Paint summaryLabelPaint = createTextPaint(Color.WHITE, 8, Typeface.NORMAL);

                    // Border paints
                    Paint thinBorderPaint = createBorderPaint(Color.LTGRAY, 1);
                    Paint thickBorderPaint = createBorderPaint(Color.DKGRAY, 2);
                    Paint headerBorderPaint = createBorderPaint(Color.parseColor("#2E7D32"), 1);

                    // Background paints
                    Paint headerBgPaint = createFillPaint(Color.parseColor("#2E7D32"));
                    Paint alternateRowPaint = createFillPaint(Color.parseColor("#F5F5F5"));
                    Paint totalRowPaint = createFillPaint(Color.parseColor("#E8F5E8"));

                    // Summary card background paints
                    Paint debitCardPaint = createFillPaint(Color.parseColor("#E8F5E9"));
                    Paint creditCardPaint = createFillPaint(Color.parseColor("#FFEBEE"));
                    Paint balanceCardPaint = createFillPaint(Color.parseColor("#E3F2FD"));

                    // Create first page
                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
                    PdfDocument.Page page = pdfDocument.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    // Draw background
                    drawBackground(canvas, pageWidth, pageHeight);

                    int startX = 88; // centered: (pageWidth 595 - total table width 420) / 2
                    int startY = marginTop;
                    int currentY = startY;
                    int currentPageNum = 1;

                    // Draw header with logo - ONLY ON FIRST PAGE
                    drawHeader(canvas, pageWidth, marginTop, customerName, titleTextPaint, headerBgPaint);
                    currentY -= 20;

                    // Company info section - ONLY ON FIRST PAGE
                    currentY = drawCompanyInfo(canvas, startX, currentY, textPaint, boldTextPaint);
                    currentY += 0;

                    // Summary section with 3-card design - ONLY ON FIRST PAGE
                    currentY = drawSummarySectionThreeCards(canvas, startX, currentY, totalDebit, totalCredit,
                            summaryLabelPaint, summaryValuePaint, debitCardPaint, creditCardPaint, balanceCardPaint);
                    currentY += 20;

                    // Table Headers
                    String[] headers = {"Transaction", "Debit Pure", "Credit Pure"};
                    int[] colWidths = {220, 100, 100};
                    int baseRowHeight = 20;

                    // Draw table header
                    currentY = drawTableHeader(canvas, startX, currentY, headers, colWidths, baseRowHeight,
                            headerTextPaint, headerBorderPaint, headerBgPaint);

                    // Data Rows with dynamic height - USE allTransactionDataList
                    boolean alternate = false;
                    for (int i = 0; i < allTransactionDataList.size(); i++) {
                        Map<String, Object> txnData = allTransactionDataList.get(i);
                        String name = (String) txnData.get("name");
                        double debit = (Double) txnData.get("debit");
                        double credit = (Double) txnData.get("credit");
                        boolean isCashRemaining = (Boolean) txnData.get("isCashRemaining");

                        // Skip rows where both values are 0.00
                        if (debit == 0.0 && credit == 0.0) {
                            continue;
                        }

                        // Calculate required row height for this transaction
                        int requiredRowHeight = calculateRowHeight(name, colWidths[0] - 20, textPaint, baseRowHeight);

                        // Check if we need a new page
                        if (currentY + requiredRowHeight > pageHeight - marginBottom) {
                            // Draw footer for current page before finishing
                            drawFooter(canvas, pageWidth, pageHeight, currentPageNum, footerTextPaint);
                            pdfDocument.finishPage(page);

                            currentPageNum++;
                            pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create();
                            page = pdfDocument.startPage(pageInfo);
                            canvas = page.getCanvas();
                            drawBackground(canvas, pageWidth, pageHeight);

                            int reducedTopMargin = 60;
                            currentY = reducedTopMargin;

                            currentY = drawTableHeader(canvas, startX, currentY, headers, colWidths, baseRowHeight,
                                    headerTextPaint, headerBorderPaint, headerBgPaint);
                            alternate = false;
                        }

                        Paint currentPaint = isCashRemaining ? italicTextPaint : textPaint;

                        // Alternate row background
                        if (alternate) {
                            canvas.drawRect(startX, currentY, startX + colWidths[0] + colWidths[1] + colWidths[2],
                                    currentY + requiredRowHeight, alternateRowPaint);
                        }

                        // Draw table row with dynamic height
                        drawTableRowWithWrapping(canvas, startX, currentY, name, debit, credit, colWidths,
                                requiredRowHeight, currentPaint, thinBorderPaint);

                        currentY += requiredRowHeight;
                        alternate = !alternate;
                    }

                    // Draw totals section
                    if (currentY + (baseRowHeight * 3) > pageHeight - marginBottom) {
                        drawFooter(canvas, pageWidth, pageHeight, currentPageNum, footerTextPaint);
                        pdfDocument.finishPage(page);

                        currentPageNum++;
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNum).create();
                        page = pdfDocument.startPage(pageInfo);
                        canvas = page.getCanvas();
                        drawBackground(canvas, pageWidth, pageHeight);
                        currentY = marginTop;
                    }

                    currentY += 20;
                    currentY = drawTotalsSection(canvas, startX, currentY, totalDebit, totalCredit,
                            colWidths, baseRowHeight, boldTextPaint, thickBorderPaint, null); // Pass null for bgPaint

                    // Draw footer for the last page
                    drawFooter(canvas, pageWidth, pageHeight, currentPageNum, footerTextPaint);

                    // Finish the last page
                    pdfDocument.finishPage(page);

                    // Convert to bytes
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    pdfDocument.writeTo(byteArrayOutputStream);
                    pdfDocument.close();

                    generatedPdfBytes = byteArrayOutputStream.toByteArray();

                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        // Show options dialog to view or save
                        showPdfOptionsDialog();
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(TotalCalculationActivity.this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error generating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showPdfOptionsDialog() {
        // Create custom dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.pdf_options_dialog, null);
        builder.setView(dialogView);

        android.app.AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();

        // Initialize views - Use View type to avoid casting issues
        View btnViewPdf = dialogView.findViewById(R.id.btn_view_pdf);
        View btnSavePdf = dialogView.findViewById(R.id.btn_save_pdf);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View closeBtn = dialogView.findViewById(R.id.btn_close_dialog);

        // Set click listeners
        btnViewPdf.setOnClickListener(v -> {
            dialog.dismiss();
            // Show PDF view choice dialog
            showPdfViewChoiceDialog();
        });

        btnSavePdf.setOnClickListener(v -> {
            dialog.dismiss();
            if (generatedPdfBytes != null) {
                savePdfToDownloads(generatedPdfBytes);
            } else {
                Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        closeBtn.setOnClickListener(v -> dialog.dismiss());
    }

    private void showPdfViewChoiceDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_pdf_view_choice, null);
        builder.setView(dialogView);

        android.app.AlertDialog choiceDialog = builder.create();
        choiceDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        choiceDialog.show();

        // Initialize views - Use View type to avoid casting issues
        View btnViewInApp = dialogView.findViewById(R.id.btn_view_this_app);
        View btnViewWithExternal = dialogView.findViewById(R.id.btn_view_other_app);
        View closeBtn = dialogView.findViewById(R.id.btn_cancel_choice);

        btnViewInApp.setOnClickListener(v -> {
            choiceDialog.dismiss();
            viewPdfInApp();
        });

        btnViewWithExternal.setOnClickListener(v -> {
            choiceDialog.dismiss();
            viewPdfWithExternalApp();
        });

        closeBtn.setOnClickListener(v -> choiceDialog.dismiss());
    }

    private void viewPdfInApp() {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create a unique file name in cache directory
            String fileName = "preview_" + System.currentTimeMillis() + ".pdf";
            File tempFile = new File(getCacheDir(), fileName);

            // Write PDF bytes to temporary file
            try (OutputStream out = new FileOutputStream(tempFile)) {
                out.write(generatedPdfBytes);
            }

            // Get URI using FileProvider
            Uri pdfUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    tempFile
            );

            // Pass PDF data to PdfViewerActivity - Use the exact key names it expects
            Intent intent = new Intent(this, PdfViewerActivity.class);

            // IMPORTANT: Use exactly these key names that PdfViewerActivity expects
            intent.putExtra("pdfFilePath", tempFile.getAbsolutePath());
            intent.putExtra("pdfTitle", customerName + " - Statement");

            // Additional data (PdfViewerActivity will ignore these, but they're here for reference)
            intent.putExtra("pdf_uri", pdfUri.toString());
            intent.putExtra("customer_name", customerName);
            intent.putExtra("total_debit", totalDebit);
            intent.putExtra("total_credit", totalCredit);

            // Grant permission to the URI
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error viewing PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void viewPdfWithExternalApp() {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "PDF not generated yet", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create a unique file name in cache directory
            String fileName = "preview_" + System.currentTimeMillis() + ".pdf";
            File tempFile = new File(getCacheDir(), fileName);

            // Write PDF bytes to temporary file
            try (OutputStream out = new FileOutputStream(tempFile)) {
                out.write(generatedPdfBytes);
            }

            // Get URI using FileProvider
            Uri pdfUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".provider",
                    tempFile
            );

            // Create intent to view PDF with external app
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Verify there's an app to handle PDF viewing
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                showNoPdfViewerDialog();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error viewing PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showNoPdfViewerDialog() {
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
                    // Only download if user explicitly chooses this option
                    savePdfToDownloads(generatedPdfBytes);
                });

                android.app.AlertDialog dialog = builder.create();
                dialog.show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void drawMergedRowRightAligned(Canvas canvas, String title, String debit, String credit,
                                           int startX, int startY, int[] colWidths, int rowHeight,
                                           Paint textPaint, Paint borderPaint) {

        int titleWidth = colWidths[0];
        int cellX = startX;

        canvas.drawRect(cellX, startY, cellX + titleWidth, startY + rowHeight, borderPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(title, cellX + titleWidth - 10, startY + 25, textPaint);

        cellX += titleWidth;
        canvas.drawRect(cellX, startY, cellX + colWidths[1], startY + rowHeight, borderPaint);
        if (!debit.isEmpty()) {
            canvas.drawText(debit, cellX + colWidths[1] - 10, startY + 25, textPaint);
        }

        cellX += colWidths[1];
        canvas.drawRect(cellX, startY, cellX + colWidths[2], startY + rowHeight, borderPaint);
        if (!credit.isEmpty()) {
            canvas.drawText(credit, cellX + colWidths[2] - 10, startY + 25, textPaint);
        }

        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void shareTransactionSummary() {
        if (generatedPdfBytes == null) {
            Toast.makeText(this, "Please generate PDF first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Create temporary file
            File tempFile = File.createTempFile("transaction_summary", ".pdf", getCacheDir());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(generatedPdfBytes);
            }

            // Share via intent
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            Uri pdfUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", tempFile);
            shareIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT,
                    "Transaction Summary - " + customerName);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "Share PDF via"));

        } catch (Exception e) {
            Toast.makeText(this, "Error sharing PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

}
package com.example.silverbusinessapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PdfExportUtil {

    private Context context;
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 20;
    private static final int CARD_PADDING = 12;
    private static final int CARD_MARGIN = 8;
    private static final int MIN_ROW_HEIGHT = 18;
    private static final int LINE_SPACING = 4;
    private static final int MAX_LINES_PER_CELL = 5;
    private static final int COLUMN_PADDING = 8;
    private static final int FOOTER_HEIGHT = 40;
    private static final int MAX_CONTENT_HEIGHT = PAGE_HEIGHT - FOOTER_HEIGHT - MARGIN;
    private static final int FOOTER_START_Y = PAGE_HEIGHT - FOOTER_HEIGHT;

    private static final int DEBIT_HEADER_BG_COLOR = Color.parseColor("#D5F5E3");
    private static final int CREDIT_HEADER_BG_COLOR = Color.parseColor("#FADBD8");
    private static final int DEBIT_HEADER_TEXT_COLOR = Color.parseColor("#1E8449");
    private static final int CREDIT_HEADER_TEXT_COLOR = Color.parseColor("#922B21");

    private boolean transactionTableStarted = false;
    private boolean cashTableStarted = false;
    private int transactionRowStart = 0;
    private int cashRowStart = 0;
    private boolean showCashDetails = false;

    public PdfExportUtil(Context context) {
        this.context = context;
    }

    public interface OnPdfExportListener {
        void onPdfExportSuccess(String filePath);
        void onPdfExportFailed(String error);
    }

    // ===================== HELPER: GET PDF FILE =====================

    /**
     * Returns a File object in internal storage for saving the PDF.
     * Use this to create the pdfFile before calling exportTransactionToPdf.
     */
    public static File getPdfFile(Context context, String transactionId) {
        File pdfDir = new File(context.getCacheDir(), "pdfs");
        if (!pdfDir.exists()) pdfDir.mkdirs();
        return new File(pdfDir, "transaction_" + transactionId + ".pdf");
    }

    // ===================== OVERLOADED EXPORT METHODS =====================

    public void exportTransactionToPdf(String transactionId, String transactionName, String status,
                                       String customerName, String customerPhone, String customerAddress,
                                       String date, List<HashMap<String, Object>> transactionRows,
                                       List<HashMap<String, Object>> cashRows,
                                       HashMap<String, Object> totalsData,
                                       File pdfFile, OnPdfExportListener listener) {
        exportTransactionToPdf(transactionId, transactionName, status, customerName,
                customerPhone, customerAddress, date, transactionRows,
                cashRows, totalsData, pdfFile, listener, false, 0);
    }

    public void exportTransactionToPdf(String transactionId, String transactionName, String status,
                                       String customerName, String customerPhone, String customerAddress,
                                       String date, List<HashMap<String, Object>> transactionRows,
                                       List<HashMap<String, Object>> cashRows,
                                       HashMap<String, Object> totalsData,
                                       File pdfFile, OnPdfExportListener listener,
                                       boolean showCashDetailsParam) {
        exportTransactionToPdf(transactionId, transactionName, status, customerName,
                customerPhone, customerAddress, date, transactionRows,
                cashRows, totalsData, pdfFile, listener, showCashDetailsParam, 0);
    }

    public void exportTransactionToPdf(String transactionId, String transactionName, String status,
                                       String customerName, String customerPhone, String customerAddress,
                                       String date, List<HashMap<String, Object>> transactionRows,
                                       List<HashMap<String, Object>> cashRows,
                                       HashMap<String, Object> totalsData,
                                       File pdfFile, OnPdfExportListener listener,
                                       boolean showCashDetailsParam, int entryNumber) {

        final String finalTransactionId = (transactionId != null) ? transactionId : "";
        final String finalTransactionName = (transactionName != null) ? transactionName : "";
        final String finalStatus = (status != null) ? status : "";
        final String finalCustomerName = (customerName != null) ? customerName : "";
        final String finalCustomerPhone = (customerPhone != null) ? customerPhone : "";
        final String finalCustomerAddress = (customerAddress != null) ? customerAddress : "";
        final String finalDate = (date != null) ? date : "";
        final int finalEntryNumber = entryNumber;

        final List<HashMap<String, Object>> finalTransactionRows =
                (transactionRows != null) ? new ArrayList<>(transactionRows) : new ArrayList<>();
        final List<HashMap<String, Object>> finalCashRows =
                (cashRows != null) ? new ArrayList<>(cashRows) : new ArrayList<>();
        final HashMap<String, Object> finalTotalsData =
                (totalsData != null) ? new HashMap<>(totalsData) : new HashMap<>();

        this.showCashDetails = showCashDetailsParam;

        transactionTableStarted = false;
        cashTableStarted = false;
        transactionRowStart = 0;
        cashRowStart = 0;

        new Thread(() -> {
            PdfDocument document = null;
            try {
                document = new PdfDocument();

                int availableWidth = PAGE_WIDTH - (2 * MARGIN);
                Paint testPaint = new Paint();
                testPaint.setTextSize(8);

                int[] transactionColumnWidths = calculateTransactionColumnWidths(
                        finalTransactionRows, finalTotalsData, availableWidth, testPaint);
                int[] cashColumnWidths = calculateCashColumnWidthsFullWidth(
                        finalCashRows, finalTotalsData, availableWidth, testPaint);

                int currentPage = 1;
                int yPosition = MARGIN;
                boolean drawHeaderAndCards = true;
                boolean hasMoreTransactionRows = false;
                boolean hasMoreCashRows = false;
                boolean transactionTableComplete = false;
                boolean cashTableComplete = false;
                boolean totalsDrawn = false;
                boolean isComplete = false;

                while (!isComplete) {
                    boolean pageHasContent = false;

                    PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                            PAGE_WIDTH, PAGE_HEIGHT, currentPage).create();
                    PdfDocument.Page page = document.startPage(pageInfo);
                    Canvas canvas = page.getCanvas();

                    Paint paint = new Paint();
                    paint.setAntiAlias(true);

                    yPosition = MARGIN;

                    if (currentPage == 1) {
                        yPosition = drawHeader(canvas, paint, yPosition, currentPage);
                        pageHasContent = true;

                        if (drawHeaderAndCards) {
                            yPosition = drawTwoCardsDesign(canvas, paint, yPosition,
                                    finalCustomerName, finalCustomerPhone, finalCustomerAddress,
                                    finalTransactionName, finalStatus, finalDate, finalEntryNumber);
                            drawHeaderAndCards = false;
                            pageHasContent = true;
                        }
                        yPosition += 10;
                    } else {
                        yPosition = MARGIN;
                    }

                    // DRAW TRANSACTION TABLE
                    if (!finalTransactionRows.isEmpty() && transactionRowStart < finalTransactionRows.size()) {
                        int estimatedHeaderHeight = 20;
                        if (!transactionTableStarted &&
                                yPosition + estimatedHeaderHeight + MIN_ROW_HEIGHT < FOOTER_START_Y - 20) {
                            yPosition = drawTransactionTableHeader(canvas, paint, yPosition, transactionColumnWidths);
                            transactionTableStarted = true;
                            pageHasContent = true;
                        }

                        if (transactionTableStarted) {
                            TableDrawResult transactionResult = drawTransactionRowsInDatabaseOrder(
                                    canvas, paint, yPosition, finalTransactionRows,
                                    transactionColumnWidths, transactionRowStart);

                            if (transactionResult.rowsDrawn > transactionRowStart) pageHasContent = true;

                            yPosition = transactionResult.yPosition;
                            transactionRowStart = transactionResult.rowsDrawn;
                            hasMoreTransactionRows = transactionResult.hasMoreRows;

                            if (!hasMoreTransactionRows && transactionRowStart >= finalTransactionRows.size()) {
                                transactionTableComplete = true;
                            }
                        }
                    } else {
                        transactionTableComplete = true;
                    }

                    if (transactionTableComplete && !showCashDetails && !totalsDrawn) {
                        if (yPosition + 80 < FOOTER_START_Y - 20) {
                            yPosition = drawTransactionTotalsRow(canvas, paint, yPosition,
                                    finalTotalsData, transactionColumnWidths);
                            totalsDrawn = true;
                            pageHasContent = true;
                        }
                    }

                    // DRAW CASH TABLE
                    if (showCashDetails && transactionTableComplete &&
                            !finalCashRows.isEmpty() && cashRowStart < finalCashRows.size()) {
                        yPosition += 10;

                        if (!cashTableStarted && yPosition + 40 < FOOTER_START_Y - 20) {
                            yPosition = drawCashTableHeaderFullWidth(canvas, paint, yPosition, cashColumnWidths);
                            cashTableStarted = true;
                            pageHasContent = true;
                        }

                        if (cashTableStarted) {
                            TableDrawResult cashResult = drawCashRowsInDatabaseOrder(
                                    canvas, paint, yPosition, finalCashRows, finalTotalsData,
                                    cashColumnWidths, cashRowStart);

                            if (cashResult.rowsDrawn > cashRowStart) pageHasContent = true;

                            yPosition = cashResult.yPosition;
                            cashRowStart = cashResult.rowsDrawn;
                            hasMoreCashRows = cashResult.hasMoreRows;

                            if (!hasMoreCashRows && cashRowStart >= finalCashRows.size()) {
                                cashTableComplete = true;
                            }
                        }
                    } else if (showCashDetails && transactionTableComplete) {
                        cashTableComplete = true;
                    }

                    if (showCashDetails && transactionTableComplete && cashTableComplete && !totalsDrawn) {
                        if (yPosition + 80 < FOOTER_START_Y - 20) {
                            double totalCashDebitPure = calculateTotalCashDebitPure(finalCashRows);
                            double totalCashCreditPure = calculateTotalCashCreditPure(finalCashRows);
                            double transactionDebitPure = safeParseDouble(finalTotalsData.get("totalDebitPure"));
                            double transactionCreditPure = safeParseDouble(finalTotalsData.get("totalCreditPure"));
                            double combinedDebitPure = transactionDebitPure + totalCashDebitPure;
                            double combinedCreditPure = transactionCreditPure + totalCashCreditPure;
                            double combinedDifference = combinedDebitPure - combinedCreditPure;

                            yPosition = drawCombinedTotalsRow(canvas, paint, yPosition,
                                    combinedDebitPure, combinedCreditPure, combinedDifference, cashColumnWidths);
                            totalsDrawn = true;
                            pageHasContent = true;
                        }
                    }

                    if (pageHasContent && yPosition < FOOTER_START_Y - 20) {
                        drawFooter(canvas, paint, currentPage);
                    }

                    document.finishPage(page);

                    boolean transactionComplete = (transactionRowStart >= finalTransactionRows.size());
                    boolean cashComplete = (!showCashDetails || cashRowStart >= finalCashRows.size());

                    if (!showCashDetails) {
                        isComplete = (transactionComplete && totalsDrawn);
                    } else {
                        isComplete = (transactionComplete && cashComplete && totalsDrawn);
                    }

                    if (!isComplete) {
                        currentPage++;
                        if (hasMoreTransactionRows) transactionTableStarted = false;
                        if (hasMoreCashRows) cashTableStarted = false;
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    document.writeTo(fos);
                }

                document.close();

                new Handler(Looper.getMainLooper()).post(() ->
                        listener.onPdfExportSuccess(pdfFile.getAbsolutePath()));

            } catch (Exception e) {
                if (document != null) {
                    try { document.close(); } catch (Exception ex) {
                        Log.e("PdfExportUtil", "Error closing document: " + ex.getMessage());
                    }
                }
                final Exception finalException = e;
                new Handler(Looper.getMainLooper()).post(() ->
                        listener.onPdfExportFailed("Error creating PDF: " + finalException.getMessage()));
                Log.e("PdfExportUtil", "Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    // ===================== CASH TOTALS =====================

    private double calculateTotalCashDebitPure(List<HashMap<String, Object>> cashRows) {
        if (cashRows == null || cashRows.isEmpty()) return 0.0;
        double total = 0.0;
        for (HashMap<String, Object> row : cashRows) {
            if (row != null) total += safeParseDouble(row.get("pureDebit"));
        }
        return total;
    }

    private double calculateTotalCashCreditPure(List<HashMap<String, Object>> cashRows) {
        if (cashRows == null || cashRows.isEmpty()) return 0.0;
        double total = 0.0;
        for (HashMap<String, Object> row : cashRows) {
            if (row != null) total += safeParseDouble(row.get("pureCredit"));
        }
        return total;
    }

    // ===================== PAGINATION =====================

    private class TableDrawResult {
        int yPosition;
        int rowsDrawn;
        boolean hasMoreRows;

        TableDrawResult(int yPosition, int rowsDrawn, boolean hasMoreRows) {
            this.yPosition = yPosition;
            this.rowsDrawn = rowsDrawn;
            this.hasMoreRows = hasMoreRows;
        }
    }

    // ===================== DRAW ROWS =====================

    private TableDrawResult drawTransactionRowsInDatabaseOrder(Canvas canvas, Paint paint, int startY,
                                                               List<HashMap<String, Object>> transactionRows,
                                                               int[] columnWidths, int startRow) {
        int yPosition = startY;
        int rowsDrawn = 0;
        boolean hasMoreRows = false;

        if (transactionRows == null || transactionRows.isEmpty() || startRow >= transactionRows.size()) {
            return new TableDrawResult(yPosition, startRow, false);
        }

        paint.setColor(Color.BLACK);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(0.5f);

        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;
        int maxYPosition = FOOTER_START_Y - 100;

        for (int i = startRow; i < transactionRows.size(); i++) {
            HashMap<String, Object> row = transactionRows.get(i);
            if (row == null) { rowsDrawn++; continue; }

            int rowHeight = calculateTransactionRowHeight(row, paint, columnWidths);

            if (yPosition + rowHeight > maxYPosition) {
                hasMoreRows = true;
                break;
            }

            if (i % 2 == 0) {
                Paint rowBgPaint = new Paint();
                rowBgPaint.setColor(Color.parseColor("#F8F9F9"));
                rowBgPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + rowHeight, rowBgPaint);
            }

            int xPos = MARGIN;
            for (int j = 0; j < columnWidths.length; j++) {
                xPos += columnWidths[j];
                if (j < columnWidths.length - 1)
                    canvas.drawLine(xPos, yPosition, xPos, yPosition + rowHeight, linePaint);
            }

            xPos = MARGIN;
            String rowDate = (String) row.getOrDefault("date", "");
            if (rowDate != null && !rowDate.isEmpty())
                canvas.drawText(rowDate, xPos + COLUMN_PADDING, yPosition + 12, paint);
            xPos += columnWidths[0];

            String particular = (String) row.getOrDefault("particular", "");
            if (particular != null && !particular.isEmpty())
                drawMultilineText(canvas, particular, xPos + COLUMN_PADDING, yPosition + 4,
                        columnWidths[1] - COLUMN_PADDING * 2, paint, false, null, rowHeight);
            xPos += columnWidths[1];

            float numericY = yPosition + (rowHeight / 2) + 4;

            String[] numericKeys = {"debitWeight", "debitTouch", "debitWastage", "debitPure",
                    "creditWeight", "creditTouch", "creditWastage", "creditPure"};
            for (int k = 0; k < numericKeys.length; k++) {
                String val = formatNumberOrEmpty(row.get(numericKeys[k]));
                if (val != null && !val.isEmpty()) {
                    float tw = paint.measureText(val);
                    canvas.drawText(val, xPos + (columnWidths[2 + k] / 2) - (tw / 2), numericY, paint);
                }
                xPos += columnWidths[2 + k];
            }

            canvas.drawLine(MARGIN, yPosition + rowHeight, tableEndX, yPosition + rowHeight, linePaint);
            yPosition += rowHeight;
            rowsDrawn++;
        }

        return new TableDrawResult(yPosition, startRow + rowsDrawn, hasMoreRows);
    }

    private TableDrawResult drawCashRowsInDatabaseOrder(Canvas canvas, Paint paint, int startY,
                                                        List<HashMap<String, Object>> cashRows,
                                                        HashMap<String, Object> totalsData,
                                                        int[] columnWidths, int startRow) {
        int yPosition = startY;
        int rowsDrawn = 0;
        boolean hasMoreRows = false;

        if (cashRows == null || cashRows.isEmpty() || startRow >= cashRows.size()) {
            return new TableDrawResult(yPosition, startRow, false);
        }

        paint.setColor(Color.BLACK);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(0.5f);

        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;
        int maxYPosition = FOOTER_START_Y - 100;

        for (int i = startRow; i < cashRows.size(); i++) {
            HashMap<String, Object> row = cashRows.get(i);
            if (row == null) { rowsDrawn++; continue; }

            String name = (String) row.getOrDefault("name", "");
            int nameHeight = calculateMultilineTextHeight(name, paint, columnWidths[1] - COLUMN_PADDING * 2);
            int actualRowHeight = Math.max(MIN_ROW_HEIGHT + 2, nameHeight);

            if (yPosition + actualRowHeight > maxYPosition) {
                hasMoreRows = true;
                break;
            }

            if (i % 2 == 0) {
                Paint rowBgPaint = new Paint();
                rowBgPaint.setColor(Color.parseColor("#F8F9F9"));
                rowBgPaint.setStyle(Paint.Style.FILL);
                canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + actualRowHeight, rowBgPaint);
            }

            int xPos = MARGIN;
            for (int j = 0; j < columnWidths.length; j++) {
                xPos += columnWidths[j];
                if (j < columnWidths.length - 1)
                    canvas.drawLine(xPos, yPosition, xPos, yPosition + actualRowHeight, linePaint);
            }

            xPos = MARGIN;
            String rowDate = (String) row.getOrDefault("date", "");
            if (rowDate != null && !rowDate.isEmpty())
                canvas.drawText(rowDate, xPos + COLUMN_PADDING, yPosition + 12, paint);
            xPos += columnWidths[0];

            if (name != null && !name.isEmpty())
                drawMultilineText(canvas, name, xPos + COLUMN_PADDING, yPosition + 4,
                        columnWidths[1] - COLUMN_PADDING * 2, paint, false, null, actualRowHeight);
            xPos += columnWidths[1];

            float numericY = yPosition + (actualRowHeight / 2) + 4;

            double debitCashValue = safeParseDouble(row.get("debitCash"));
            double debitRateValue = safeParseDouble(row.get("debitRate"));
            double debitPureValue = safeParseDouble(row.get("pureDebit"));
            double creditCashValue = safeParseDouble(row.get("creditCash"));
            double creditRateValue = safeParseDouble(row.get("creditRate"));
            double creditPureValue = safeParseDouble(row.get("pureCredit"));

            drawColoredCell(canvas, paint, xPos, columnWidths[2], numericY,
                    formatCurrencyOrEmpty(debitCashValue), Math.abs(debitCashValue) > 0.001, true);
            xPos += columnWidths[2];

            drawColoredCell(canvas, paint, xPos, columnWidths[3], numericY,
                    formatNumberOrEmpty(debitRateValue), Math.abs(debitRateValue) > 0.001, true);
            xPos += columnWidths[3];

            drawColoredCell(canvas, paint, xPos, columnWidths[4], numericY,
                    formatNumberOrEmpty(debitPureValue), Math.abs(debitPureValue) > 0.001, true);
            xPos += columnWidths[4];

            drawColoredCell(canvas, paint, xPos, columnWidths[5], numericY,
                    formatCurrencyOrEmpty(creditCashValue), Math.abs(creditCashValue) > 0.001, false);
            xPos += columnWidths[5];

            drawColoredCell(canvas, paint, xPos, columnWidths[6], numericY,
                    formatNumberOrEmpty(creditRateValue), Math.abs(creditRateValue) > 0.001, false);
            xPos += columnWidths[6];

            drawColoredCell(canvas, paint, xPos, columnWidths[7], numericY,
                    formatNumberOrEmpty(creditPureValue), Math.abs(creditPureValue) > 0.001, false);

            canvas.drawLine(MARGIN, yPosition + actualRowHeight, tableEndX,
                    yPosition + actualRowHeight, linePaint);

            yPosition += actualRowHeight;
            rowsDrawn++;
        }

        return new TableDrawResult(yPosition, startRow + rowsDrawn, hasMoreRows);
    }

    private void drawColoredCell(Canvas canvas, Paint paint, int xPos, int colWidth,
                                 float numericY, String text, boolean hasValue, boolean isDebit) {
        if (text != null && !text.isEmpty() && hasValue) {
            float centerX = xPos + (colWidth / 2);
            float textWidth = paint.measureText(text);
            Paint colorPaint = new Paint(paint);
            colorPaint.setColor(isDebit ? DEBIT_HEADER_TEXT_COLOR : CREDIT_HEADER_TEXT_COLOR);
            canvas.drawText(text, centerX - (textWidth / 2), numericY, colorPaint);
        }
    }

    // ===================== TOTALS =====================

    private int drawTransactionTotalsRow(Canvas canvas, Paint paint, int yPosition,
                                         HashMap<String, Object> totalsData, int[] columnWidths) {
        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;

        if (yPosition + 80 > FOOTER_START_Y - 20) return yPosition;

        Paint separatorPaint = new Paint();
        separatorPaint.setColor(Color.parseColor("#7F8C8D"));
        separatorPaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, yPosition, tableEndX, yPosition, separatorPaint);
        yPosition += 10;

        Paint totalBgPaint = new Paint();
        totalBgPaint.setColor(Color.parseColor("#E8DAEF"));
        totalBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + 25, totalBgPaint);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(0.5f);

        int xPos = MARGIN;
        for (int i = 0; i < columnWidths.length; i++) {
            xPos += columnWidths[i];
            if (i < columnWidths.length - 1)
                canvas.drawLine(xPos, yPosition, xPos, yPosition + 25, linePaint);
        }

        paint.setColor(Color.BLACK);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

        xPos = MARGIN;
        canvas.drawText("Total", xPos + COLUMN_PADDING, yPosition + 16, paint);

        xPos += columnWidths[0] + columnWidths[1] + columnWidths[2] + columnWidths[3] + columnWidths[4];
        String totalDebitPure = formatNumberOrEmpty(totalsData.get("totalDebitPure"));
        if (totalDebitPure != null && !totalDebitPure.isEmpty()) {
            Paint debitPaint = new Paint(paint);
            debitPaint.setColor(Color.parseColor("#1B5E20"));
            float tw = debitPaint.measureText(totalDebitPure);
            canvas.drawText(totalDebitPure, xPos + (columnWidths[5] / 2) - (tw / 2), yPosition + 16, debitPaint);
        }

        xPos += columnWidths[5] + columnWidths[6] + columnWidths[7] + columnWidths[8];
        String totalCreditPure = formatNumberOrEmpty(totalsData.get("totalCreditPure"));
        if (totalCreditPure != null && !totalCreditPure.isEmpty()) {
            Paint creditPaint = new Paint(paint);
            creditPaint.setColor(Color.parseColor("#B71C1C"));
            float tw = creditPaint.measureText(totalCreditPure);
            canvas.drawText(totalCreditPure, xPos + (columnWidths[9] / 2) - (tw / 2), yPosition + 16, creditPaint);
        }

        canvas.drawLine(MARGIN, yPosition + 25, tableEndX, yPosition + 25, separatorPaint);
        yPosition += 25;

        double debitPure = safeParseDouble(totalsData.get("totalDebitPure"));
        double creditPure = safeParseDouble(totalsData.get("totalCreditPure"));
        double difference = debitPure - creditPure;

        yPosition = drawPureMinusRow(canvas, paint, yPosition, tableEndX, separatorPaint,
                debitPure, creditPure);
        yPosition = drawBalanceRow(canvas, paint, yPosition, tableEndX, separatorPaint, difference);

        return yPosition;
    }

    private int drawCombinedTotalsRow(Canvas canvas, Paint paint, int yPosition,
                                      double combinedDebitPure, double combinedCreditPure,
                                      double combinedDifference, int[] columnWidths) {
        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;

        if (yPosition + 80 > FOOTER_START_Y) return yPosition;

        yPosition += 5;
        Paint separatorPaint = new Paint();
        separatorPaint.setColor(Color.parseColor("#7F8C8D"));
        separatorPaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, yPosition, tableEndX, yPosition, separatorPaint);
        yPosition += 10;

        Paint combinedBgPaint = new Paint();
        combinedBgPaint.setColor(Color.parseColor("#E8DAEF"));
        combinedBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + MIN_ROW_HEIGHT + 8, combinedBgPaint);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(0.5f);
        int xPos = MARGIN;
        for (int j = 0; j < columnWidths.length; j++) {
            xPos += columnWidths[j];
            if (j < columnWidths.length - 1)
                canvas.drawLine(xPos, yPosition, xPos, yPosition + MIN_ROW_HEIGHT + 8, linePaint);
        }

        xPos = MARGIN + columnWidths[0];
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        paint.setTextSize(8);
        paint.setColor(Color.parseColor("#6C3483"));
        canvas.drawText("Total", xPos + COLUMN_PADDING, yPosition + 15, paint);

        xPos = MARGIN + columnWidths[0] + columnWidths[1] + columnWidths[2] + columnWidths[3];
        paint.setColor(DEBIT_HEADER_TEXT_COLOR);
        String combinedDebitText = formatNumberOrEmpty(combinedDebitPure);
        if (combinedDebitText != null && !combinedDebitText.isEmpty()) {
            float tw = paint.measureText(combinedDebitText);
            canvas.drawText(combinedDebitText, xPos + (columnWidths[4] / 2) - (tw / 2), yPosition + 15, paint);
        }

        xPos = MARGIN + columnWidths[0] + columnWidths[1] + columnWidths[2] + columnWidths[3]
                + columnWidths[4] + columnWidths[5] + columnWidths[6];
        paint.setColor(CREDIT_HEADER_TEXT_COLOR);
        String combinedCreditText = formatNumberOrEmpty(combinedCreditPure);
        if (combinedCreditText != null && !combinedCreditText.isEmpty()) {
            float tw = paint.measureText(combinedCreditText);
            canvas.drawText(combinedCreditText, xPos + (columnWidths[7] / 2) - (tw / 2), yPosition + 15, paint);
        }

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(8);
        canvas.drawLine(MARGIN, yPosition + MIN_ROW_HEIGHT + 8, tableEndX,
                yPosition + MIN_ROW_HEIGHT + 8, separatorPaint);
        yPosition += MIN_ROW_HEIGHT + 8 + 5;

        yPosition = drawPureMinusRow(canvas, paint, yPosition, tableEndX, separatorPaint,
                combinedDebitPure, combinedCreditPure);
        yPosition = drawBalanceRow(canvas, paint, yPosition, tableEndX, separatorPaint, combinedDifference);

        return yPosition;
    }

    private int drawPureMinusRow(Canvas canvas, Paint paint, int yPosition, int tableEndX,
                                 Paint separatorPaint, double debitPure, double creditPure) {
        if (yPosition + 25 > FOOTER_START_Y) return yPosition;

        Paint diffBgPaint = new Paint();
        diffBgPaint.setColor(Color.parseColor("#F5F5DC"));
        diffBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + 22, diffBgPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.LTGRAY);
        borderPaint.setStrokeWidth(0.5f);
        canvas.drawLine(MARGIN, yPosition, MARGIN, yPosition + 22, borderPaint);
        canvas.drawLine(tableEndX, yPosition, tableEndX, yPosition + 22, borderPaint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        paint.setColor(Color.parseColor("#8B4513"));
        paint.setTextSize(9);

        String pureMinusText = String.format(Locale.getDefault(),
                "Pure minus : %.2f - %.2f", debitPure, creditPure);
        float textWidth = paint.measureText(pureMinusText);
        canvas.drawText(pureMinusText, tableEndX - COLUMN_PADDING - textWidth, yPosition + 15, paint);

        canvas.drawLine(MARGIN, yPosition + 22, tableEndX, yPosition + 22, separatorPaint);
        return yPosition + 25;
    }

    private int drawBalanceRow(Canvas canvas, Paint paint, int yPosition, int tableEndX,
                               Paint separatorPaint, double difference) {
        if (Math.abs(difference) <= 0.001) return yPosition;
        if (yPosition + 25 > FOOTER_START_Y) return yPosition;

        boolean isBalance = difference >= 0;
        String labelText = isBalance ? "Balance Pure" : "Ex Pure";
        int bgColor = isBalance ? DEBIT_HEADER_BG_COLOR : CREDIT_HEADER_BG_COLOR;
        int textColor = isBalance ? DEBIT_HEADER_TEXT_COLOR : CREDIT_HEADER_TEXT_COLOR;

        Paint balanceBgPaint = new Paint();
        balanceBgPaint.setColor(bgColor);
        balanceBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + 22, balanceBgPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.LTGRAY);
        borderPaint.setStrokeWidth(0.5f);
        canvas.drawLine(MARGIN, yPosition, MARGIN, yPosition + 22, borderPaint);
        canvas.drawLine(tableEndX, yPosition, tableEndX, yPosition + 22, borderPaint);

        Paint textPaint = new Paint(paint);
        textPaint.setColor(textColor);
        textPaint.setTextSize(9);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

        String balanceText = String.format(Locale.getDefault(),
                "%s : %.2f", labelText, Math.abs(difference));
        float textWidth = textPaint.measureText(balanceText);
        canvas.drawText(balanceText, tableEndX - COLUMN_PADDING - textWidth, yPosition + 15, textPaint);

        canvas.drawLine(MARGIN, yPosition + 22, tableEndX, yPosition + 22, separatorPaint);
        return yPosition + 25;
    }

    // ===================== HEADER AND FOOTER =====================

    private int drawHeader(Canvas canvas, Paint paint, int yPosition, int pageNumber) {

        // ===============================
        // Header Background
        // ===============================
        Paint headerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerBgPaint.setColor(Color.parseColor("#C2B280"));
        headerBgPaint.setStyle(Paint.Style.FILL);

        int headerHeight = 65;

        canvas.drawRect(
                0,
                0,
                PAGE_WIDTH,
                headerHeight,
                headerBgPaint
        );

        // ===============================
        // Logo
        // ===============================
        int logoSize = 42;

        try {
            Bitmap originalLogo = BitmapFactory.decodeResource(
                    context.getResources(),
                    R.drawable.logo_demo
            );

            if (originalLogo != null) {

                Bitmap circularLogo = getCircularBitmap(originalLogo);

                Bitmap logo = Bitmap.createScaledBitmap(
                        circularLogo,
                        logoSize,
                        logoSize,
                        true
                );

                // Shadow
                Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                shadowPaint.setColor(Color.parseColor("#60000000"));

                float logoCenterX = MARGIN + logoSize / 2f + 1;
                float logoCenterY = headerHeight / 2f + 1;

                canvas.drawCircle(
                        logoCenterX,
                        logoCenterY,
                        logoSize / 2f + 1,
                        shadowPaint
                );

                // Logo vertically centered
                float logoX = MARGIN;
                float logoY = (headerHeight - logoSize) / 2f;

                canvas.drawBitmap(
                        logo,
                        logoX,
                        logoY,
                        paint
                );

                // White border
                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(Color.WHITE);
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(1.5f);

                canvas.drawCircle(
                        MARGIN + logoSize / 2f,
                        headerHeight / 2f,
                        logoSize / 2f,
                        borderPaint
                );
            }

        } catch (Exception e) {

            Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(Color.WHITE);
            circlePaint.setStyle(Paint.Style.FILL);

            canvas.drawCircle(
                    MARGIN + logoSize / 2f,
                    headerHeight / 2f,
                    logoSize / 2f,
                    circlePaint
            );
        }

        // ===============================
        // Company Name
        // ===============================
        Paint companyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        companyPaint.setColor(Color.WHITE);
        companyPaint.setTextSize(17);
        companyPaint.setTypeface(Typeface.DEFAULT_BOLD);
        companyPaint.setTextAlign(Paint.Align.CENTER);

        String companyName = "PST Silver Ledger";

        float centerX = PAGE_WIDTH / 2f;

        // ===============================
        // Quote / Subtitle
        // ===============================
        Paint quotePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        quotePaint.setColor(Color.WHITE);
        quotePaint.setTextSize(9);
        quotePaint.setTypeface(
                Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        );
        quotePaint.setTextAlign(Paint.Align.CENTER);

        String quote = "முருகன் துணை";

        // ===============================
        // Vertical Center Calculation
        // ===============================

        // Two lines total:
        // Company Name
        // Subtitle
        float companyBaseline = 29;
        float quoteBaseline = 45;

        // Company shadow
        Paint shadowTextPaint = new Paint(companyPaint);
        shadowTextPaint.setColor(Color.parseColor("#60000000"));

        canvas.drawText(
                companyName,
                centerX + 1,
                companyBaseline + 1,
                shadowTextPaint
        );

        // Company name
        canvas.drawText(
                companyName,
                centerX,
                companyBaseline,
                companyPaint
        );

        // Subtitle
        canvas.drawText(
                quote,
                centerX,
                quoteBaseline,
                quotePaint
        );

        // ===============================
        // Return next content position
        // ===============================
        return headerHeight + 8;
    }
    private void drawFooter(Canvas canvas, Paint paint, int pageNumber) {
        int footerY = PAGE_HEIGHT - FOOTER_HEIGHT;

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, footerY - 25, PAGE_WIDTH - MARGIN, footerY - 25, linePaint);

        String timestamp = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        String footerText = "Page " + pageNumber + " | Generated on " + timestamp;

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.BLACK);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(footerText, PAGE_WIDTH / 2, footerY - 10, paint);
        canvas.drawText("PST Silver Ledger", PAGE_WIDTH / 2, footerY + 5, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ===================== COLUMN WIDTH CALCULATIONS =====================

    private int[] calculateTransactionColumnWidths(List<HashMap<String, Object>> rows,
                                                   HashMap<String, Object> totalsData,
                                                   int availableWidth, Paint paint) {
        String[] columnKeys = {"date", "particular", "debitWeight", "debitTouch", "debitWastage",
                "debitPure", "creditWeight", "creditTouch", "creditWastage", "creditPure"};
        String[] columnHeaders = {"Date", "Particular", "Weight", "Touch", "Wastage", "Pure",
                "Weight", "Touch", "Wastage", "Pure"};
        int[] minWidths = {55, 90, 38, 38, 45, 42, 38, 38, 45, 42};
        int[] maxWidths = {75, 160, 55, 55, 65, 60, 55, 55, 65, 60};

        int[] calculatedWidths = new int[columnKeys.length];
        System.arraycopy(minWidths, 0, calculatedWidths, 0, minWidths.length);

        paint.setTextSize(8);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        for (int i = 0; i < columnHeaders.length; i++) {
            float headerWidth = paint.measureText(columnHeaders[i]) + COLUMN_PADDING * 2;
            calculatedWidths[i] = Math.max(calculatedWidths[i], (int) headerWidth);
        }

        paint.setTypeface(Typeface.DEFAULT);
        if (rows != null) {
            for (HashMap<String, Object> row : rows) {
                for (int i = 0; i < columnKeys.length; i++) {
                    if (i == 1) {
                        String text = (String) row.getOrDefault("particular", "");
                        if (text != null && !text.isEmpty()) {
                            for (String word : text.split("\\s+")) {
                                if (word != null && word.length() > 20) word = word.substring(0, 20) + "...";
                                float w = paint.measureText(word);
                                calculatedWidths[i] = Math.max(calculatedWidths[i], (int) w + COLUMN_PADDING * 2);
                            }
                        }
                    } else {
                        String text = formatNumberOrEmpty(row.get(columnKeys[i]));
                        if (text != null && !text.isEmpty())
                            calculatedWidths[i] = Math.max(calculatedWidths[i],
                                    (int) paint.measureText(text) + COLUMN_PADDING * 2);
                    }
                }
            }
        }

        for (int i = 0; i < calculatedWidths.length; i++)
            calculatedWidths[i] = Math.min(calculatedWidths[i], maxWidths[i]);

        int totalWidth = 0;
        for (int w : calculatedWidths) totalWidth += w;

        if (totalWidth > availableWidth) {
            float scale = (float) availableWidth / totalWidth;
            totalWidth = 0;
            for (int i = 0; i < calculatedWidths.length; i++) {
                calculatedWidths[i] = Math.max((int) (calculatedWidths[i] * scale), minWidths[i]);
                totalWidth += calculatedWidths[i];
            }
            if (totalWidth > availableWidth)
                calculatedWidths[1] = Math.max(minWidths[1], calculatedWidths[1] - (totalWidth - availableWidth));
        }

        totalWidth = 0;
        for (int w : calculatedWidths) totalWidth += w;
        if (totalWidth < availableWidth) calculatedWidths[1] += availableWidth - totalWidth;

        return calculatedWidths;
    }

    private int[] calculateCashColumnWidthsFullWidth(List<HashMap<String, Object>> rows,
                                                     HashMap<String, Object> totalsData,
                                                     int availableWidth, Paint paint) {
        int[] minWidths = {55, 90, 45, 45, 42, 45, 45, 42};
        int[] calculatedWidths = new int[8];
        System.arraycopy(minWidths, 0, calculatedWidths, 0, minWidths.length);

        paint.setTextSize(8);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

        String[] subHeaders = {"Cash", "Rate Cutting", "Pure", "Cash", "Rate Cutting", "Pure"};
        for (int i = 0; i < subHeaders.length; i++) {
            int colIndex = (i < 3) ? 2 + i : 5 + (i - 3);
            float w = paint.measureText(subHeaders[i]) + COLUMN_PADDING * 2;
            calculatedWidths[colIndex] = Math.max(calculatedWidths[colIndex], (int) w);
        }

        calculatedWidths[0] = Math.max(calculatedWidths[0], (int) paint.measureText("Date") + COLUMN_PADDING * 2);
        calculatedWidths[1] = Math.max(calculatedWidths[1], (int) paint.measureText("Name") + COLUMN_PADDING * 2);

        paint.setTypeface(Typeface.DEFAULT);
        if (rows != null) {
            for (HashMap<String, Object> row : rows) {
                String date = (String) row.getOrDefault("date", "");
                if (date != null && !date.isEmpty())
                    calculatedWidths[0] = Math.max(calculatedWidths[0], (int) paint.measureText(date) + COLUMN_PADDING * 2);

                String name = (String) row.getOrDefault("name", "");
                if (name != null && !name.isEmpty())
                    calculatedWidths[1] = Math.max(calculatedWidths[1],
                            (int) Math.min(paint.measureText(name) + COLUMN_PADDING * 2, 180));

                double[] vals = {
                        safeParseDouble(row.get("debitCash")), safeParseDouble(row.get("debitRate")),
                        safeParseDouble(row.get("pureDebit")), safeParseDouble(row.get("creditCash")),
                        safeParseDouble(row.get("creditRate")), safeParseDouble(row.get("pureCredit"))
                };
                boolean[] isCurrency = {true, false, false, true, false, false};
                for (int i = 0; i < vals.length; i++) {
                    if (Math.abs(vals[i]) > 0.001) {
                        String text = isCurrency[i] ? formatCurrencyOrEmpty(vals[i]) : formatNumberOrEmpty(vals[i]);
                        if (text != null && !text.isEmpty()) {
                            int colIndex = 2 + i;
                            calculatedWidths[colIndex] = Math.max(calculatedWidths[colIndex],
                                    (int) paint.measureText(text) + COLUMN_PADDING * 2);
                        }
                    }
                }
            }
        }

        int totalContentWidth = 0;
        for (int w : calculatedWidths) totalContentWidth += w;

        if (totalContentWidth > 0 && totalContentWidth != availableWidth) {
            int totalAllocated = 0;
            for (int i = 0; i < calculatedWidths.length; i++) {
                calculatedWidths[i] = (int) (availableWidth * ((float) calculatedWidths[i] / totalContentWidth));
                totalAllocated += calculatedWidths[i];
            }
            if (totalAllocated != availableWidth)
                calculatedWidths[1] += availableWidth - totalAllocated;
        }

        for (int i = 0; i < calculatedWidths.length; i++)
            calculatedWidths[i] = Math.max(calculatedWidths[i], minWidths[i]);

        return calculatedWidths;
    }

    // ===================== TABLE HEADER DRAWING =====================

    private int drawTransactionTableHeader(Canvas canvas, Paint paint, int yPosition, int[] columnWidths) {
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.parseColor("#C2B280"));
        headerPaint.setStyle(Paint.Style.FILL);

        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + 40, headerPaint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

        Paint linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(1);

        int xPosition = MARGIN;
        canvas.drawText("Date", xPosition + COLUMN_PADDING, yPosition + 15, paint);
        xPosition += columnWidths[0];
        canvas.drawLine(xPosition, yPosition, xPosition, yPosition + 40, linePaint);

        canvas.drawText("Particular", xPosition + COLUMN_PADDING, yPosition + 15, paint);
        xPosition += columnWidths[1];
        canvas.drawLine(xPosition, yPosition, xPosition, yPosition + 40, linePaint);

        int debitGroupWidth = columnWidths[2] + columnWidths[3] + columnWidths[4] + columnWidths[5];
        int creditGroupWidth = columnWidths[6] + columnWidths[7] + columnWidths[8] + columnWidths[9];

        int debitStartX = xPosition;
        int debitEndX = debitStartX + debitGroupWidth;

        Paint debitBgPaint = new Paint();
        debitBgPaint.setColor(DEBIT_HEADER_BG_COLOR);
        debitBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(debitStartX, yPosition, debitEndX, yPosition + 20, debitBgPaint);
        paint.setColor(DEBIT_HEADER_TEXT_COLOR);
        float tw = paint.measureText("Debit");
        canvas.drawText("Debit", debitStartX + (debitGroupWidth / 2) - (tw / 2), yPosition + 15, paint);

        int creditStartX = debitEndX;
        int creditEndX = creditStartX + creditGroupWidth;
        Paint creditBgPaint = new Paint();
        creditBgPaint.setColor(CREDIT_HEADER_BG_COLOR);
        creditBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(creditStartX, yPosition, creditEndX, yPosition + 20, creditBgPaint);
        paint.setColor(CREDIT_HEADER_TEXT_COLOR);
        tw = paint.measureText("Credit");
        canvas.drawText("Credit", creditStartX + (creditGroupWidth / 2) - (tw / 2), yPosition + 15, paint);

        canvas.drawLine(MARGIN, yPosition + 20, tableEndX, yPosition + 20, linePaint);

        paint.setTextSize(8);
        int subHeaderY = yPosition + 30;
        xPosition = MARGIN + columnWidths[0] + columnWidths[1];

        String[] subHeaders = {"Weight", "Touch", "Wastage", "Pure", "Weight", "Touch", "Wastage", "Pure"};
        for (int i = 0; i < subHeaders.length; i++) {
            int colIndex = 2 + i;
            boolean isDebit = i < 4;

            Paint subBg = new Paint();
            subBg.setColor(isDebit ? DEBIT_HEADER_BG_COLOR : CREDIT_HEADER_BG_COLOR);
            subBg.setStyle(Paint.Style.FILL);
            canvas.drawRect(xPosition, yPosition + 20,
                    xPosition + columnWidths[colIndex], yPosition + 40, subBg);

            paint.setColor(isDebit ? DEBIT_HEADER_TEXT_COLOR : CREDIT_HEADER_TEXT_COLOR);
            tw = paint.measureText(subHeaders[i]);
            canvas.drawText(subHeaders[i],
                    xPosition + (columnWidths[colIndex] / 2) - (tw / 2), subHeaderY, paint);

            xPosition += columnWidths[colIndex];
            if (i < subHeaders.length - 1)
                canvas.drawLine(xPosition, yPosition + 20, xPosition, yPosition + 40, linePaint);
        }

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#E74C3C"));
        borderPaint.setStrokeWidth(1);
        canvas.drawLine(MARGIN, yPosition + 40, tableEndX, yPosition + 40, borderPaint);

        return yPosition + 45;
    }

    private int drawCashTableHeaderFullWidth(Canvas canvas, Paint paint, int yPosition, int[] columnWidths) {
        Paint headerPaint = new Paint();
        headerPaint.setColor(Color.parseColor("#C2B280"));
        headerPaint.setStyle(Paint.Style.FILL);

        int tableWidth = calculateTableWidth(columnWidths);
        int tableEndX = MARGIN + tableWidth;
        canvas.drawRect(MARGIN, yPosition, tableEndX, yPosition + 40, headerPaint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(8);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));

        Paint linePaint = new Paint();
        linePaint.setColor(Color.WHITE);
        linePaint.setStrokeWidth(1);

        int xPosition = MARGIN;
        canvas.drawText("Date", xPosition + COLUMN_PADDING, yPosition + 15, paint);
        xPosition += columnWidths[0];
        canvas.drawLine(xPosition, yPosition, xPosition, yPosition + 40, linePaint);

        canvas.drawText("Name", xPosition + COLUMN_PADDING, yPosition + 15, paint);
        xPosition += columnWidths[1];
        canvas.drawLine(xPosition, yPosition, xPosition, yPosition + 40, linePaint);

        int debitGroupWidth = columnWidths[2] + columnWidths[3] + columnWidths[4];
        int debitStartX = xPosition;
        int debitEndX = debitStartX + debitGroupWidth;

        Paint debitBgPaint = new Paint();
        debitBgPaint.setColor(DEBIT_HEADER_BG_COLOR);
        debitBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(debitStartX, yPosition, debitEndX, yPosition + 20, debitBgPaint);
        paint.setColor(DEBIT_HEADER_TEXT_COLOR);
        float tw = paint.measureText("Debit");
        canvas.drawText("Debit", debitStartX + (debitGroupWidth / 2) - (tw / 2), yPosition + 15, paint);

        int creditGroupWidth = columnWidths[5] + columnWidths[6] + columnWidths[7];
        int creditStartX = debitEndX;
        int creditEndX = creditStartX + creditGroupWidth;

        Paint creditBgPaint = new Paint();
        creditBgPaint.setColor(CREDIT_HEADER_BG_COLOR);
        creditBgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(creditStartX, yPosition, creditEndX, yPosition + 20, creditBgPaint);
        paint.setColor(CREDIT_HEADER_TEXT_COLOR);
        tw = paint.measureText("Credit");
        canvas.drawText("Credit", creditStartX + (creditGroupWidth / 2) - (tw / 2), yPosition + 15, paint);

        canvas.drawLine(MARGIN, yPosition + 20, tableEndX, yPosition + 20, linePaint);

        String[] subHeaders = {"Cash", "Rate Cutting", "Pure"};
        int subHeaderY = yPosition + 32;
        int subXPos = MARGIN + columnWidths[0] + columnWidths[1];

        for (int i = 0; i < subHeaders.length; i++) {
            int colIndex = 2 + i;
            Paint subBg = new Paint();
            subBg.setColor(DEBIT_HEADER_BG_COLOR);
            subBg.setStyle(Paint.Style.FILL);
            canvas.drawRect(subXPos, yPosition + 20,
                    subXPos + columnWidths[colIndex], yPosition + 40, subBg);
            paint.setColor(DEBIT_HEADER_TEXT_COLOR);
            tw = paint.measureText(subHeaders[i]);
            canvas.drawText(subHeaders[i],
                    subXPos + (columnWidths[colIndex] / 2) - (tw / 2), subHeaderY, paint);
            subXPos += columnWidths[colIndex];
            if (i < subHeaders.length - 1)
                canvas.drawLine(subXPos, yPosition + 20, subXPos, yPosition + 40, linePaint);
        }

        canvas.drawLine(subXPos, yPosition + 20, subXPos, yPosition + 40, linePaint);

        for (int i = 0; i < subHeaders.length; i++) {
            int colIndex = 5 + i;
            Paint subBg = new Paint();
            subBg.setColor(CREDIT_HEADER_BG_COLOR);
            subBg.setStyle(Paint.Style.FILL);
            canvas.drawRect(subXPos, yPosition + 20,
                    subXPos + columnWidths[colIndex], yPosition + 40, subBg);
            paint.setColor(CREDIT_HEADER_TEXT_COLOR);
            tw = paint.measureText(subHeaders[i]);
            canvas.drawText(subHeaders[i],
                    subXPos + (columnWidths[colIndex] / 2) - (tw / 2), subHeaderY, paint);
            subXPos += columnWidths[colIndex];
            if (i < subHeaders.length - 1)
                canvas.drawLine(subXPos, yPosition + 20, subXPos, yPosition + 40, linePaint);
        }

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#E74C3C"));
        borderPaint.setStrokeWidth(2);
        canvas.drawLine(MARGIN, yPosition + 40, tableEndX, yPosition + 40, borderPaint);

        return yPosition + 45;
    }

    // ===================== CARD DESIGN =====================

    private int drawTwoCardsDesign(Canvas canvas, Paint paint, int yPosition,
                                   String customerName, String customerPhone, String customerAddress,
                                   String transactionName, String status, String date, int entryNumber) {

        int cardWidth = (PAGE_WIDTH - (2 * MARGIN) - CARD_MARGIN) / 2;
        int leftCardHeight = calculateAddressCardHeight(customerAddress, paint, cardWidth);
        int rightCardHeight = 100;
        int cardHeight = Math.max(leftCardHeight, rightCardHeight);

        // LEFT CARD
        int leftCardX = MARGIN;
        drawCard(canvas, paint, leftCardX, yPosition, cardWidth, cardHeight, "", Color.parseColor("#C2B280"));

        paint.setColor(Color.WHITE);
        paint.setTextSize(11);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("CUSTOMER DETAILS", leftCardX + CARD_PADDING, yPosition + 22, paint);

        int contentY = yPosition + 38;
        paint.setColor(Color.BLACK);
        paint.setTextSize(9);
        paint.setTextAlign(Paint.Align.LEFT);

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Name:", leftCardX + CARD_PADDING, contentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        int nameH = drawMultilineText(canvas, getDisplayText(customerName),
                leftCardX + CARD_PADDING + 40, contentY - 10,
                cardWidth - CARD_PADDING - 50, paint, false, null, 15);
        contentY += Math.max(12, nameH);

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Phone:", leftCardX + CARD_PADDING, contentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        int phoneH = drawMultilineText(canvas, getDisplayText(customerPhone),
                leftCardX + CARD_PADDING + 40, contentY - 10,
                cardWidth - CARD_PADDING - 50, paint, false, null, 15);
        contentY += Math.max(12, phoneH);

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Address:", leftCardX + CARD_PADDING, contentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        drawMultilineText(canvas, getDisplayText(customerAddress),
                leftCardX + CARD_PADDING + 50, contentY - 10,
                cardWidth - CARD_PADDING - 60, paint, false, null,
                cardHeight - contentY + yPosition - 10);

        // RIGHT CARD
        int rightCardX = MARGIN + cardWidth + CARD_MARGIN;
        drawCard(canvas, paint, rightCardX, yPosition, cardWidth, cardHeight, "", Color.parseColor("#C2B280"));

        paint.setColor(Color.WHITE);
        paint.setTextSize(11);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("TRANSACTION DETAILS", rightCardX + cardWidth - CARD_PADDING, yPosition + 22, paint);

        int transactionContentY = yPosition + 38;
        paint.setColor(Color.BLACK);
        paint.setTextSize(9);

        int labelRightX = rightCardX + cardWidth - CARD_PADDING - 80;
        int valueRightX = rightCardX + cardWidth - CARD_PADDING;
        paint.setTextAlign(Paint.Align.RIGHT);

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Transaction:", labelRightX, transactionContentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(getDisplayText(transactionName), valueRightX, transactionContentY, paint);
        transactionContentY += 18;

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Entry:", labelRightX, transactionContentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(entryNumber > 0 ? String.valueOf(entryNumber) : "", valueRightX, transactionContentY, paint);
        transactionContentY += 18;

        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Status:", labelRightX, transactionContentY, paint);
        Paint statusPaint = new Paint(paint);
        statusPaint.setTextSize(9);
        statusPaint.setTypeface(Typeface.DEFAULT_BOLD);
        statusPaint.setTextAlign(Paint.Align.RIGHT);
        statusPaint.setColor("CLOSE".equalsIgnoreCase(status) ? Color.RED : Color.parseColor("#27AE60"));
        canvas.drawText(getDisplayText(status), valueRightX, transactionContentY, statusPaint);
        transactionContentY += 18;

        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("Date:", labelRightX, transactionContentY, paint);
        paint.setTypeface(Typeface.DEFAULT);
        canvas.drawText(getDisplayText(date), valueRightX, transactionContentY, paint);

        paint.setTextAlign(Paint.Align.LEFT);
        return yPosition + cardHeight;
    }

    private int calculateAddressCardHeight(String address, Paint paint, int cardWidth) {
        if (address == null || address.trim().isEmpty()) return 85;
        TextPaint textPaint = new TextPaint(paint);
        textPaint.setTextSize(9);
        List<String> lines = breakTextIntoLines(address, textPaint, cardWidth - CARD_PADDING - 60);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) + LINE_SPACING;
        float addressHeight = Math.min(lines.size(), MAX_LINES_PER_CELL) * lineHeight;
        return (int) Math.max(85, 65 + addressHeight + 15);
    }

    private void drawCard(Canvas canvas, Paint paint, int x, int y, int width, int height,
                          String title, int headerColor) {
        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.WHITE);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + width, y + height, bgPaint);

        Paint borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#B0B0B0"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1);
        canvas.drawRect(x, y, x + width, y + height, borderPaint);

        Paint headerPaint = new Paint();
        headerPaint.setColor(headerColor);
        headerPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x, y, x + width, y + 28, headerPaint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(9);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, x + (width / 2), y + 19, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ===================== UTILITY =====================

    private int calculateTableWidth(int[] columnWidths) {
        int total = 0;
        for (int width : columnWidths) total += width;
        return total;
    }

    private int drawMultilineText(Canvas canvas, String text, float x, float y,
                                  float maxWidth, Paint paint, boolean drawBackground,
                                  Paint bgPaint, float bgHeight) {
        if (text == null || text.trim().isEmpty()) return MIN_ROW_HEIGHT;

        TextPaint textPaint = new TextPaint(paint);
        textPaint.setTextSize(paint.getTextSize());
        textPaint.setColor(paint.getColor());
        textPaint.setTypeface(paint.getTypeface());
        textPaint.setAntiAlias(true);

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) + LINE_SPACING;

        List<String> lines = breakTextIntoLines(text, textPaint, maxWidth);
        if (lines.size() > MAX_LINES_PER_CELL) {
            lines = lines.subList(0, MAX_LINES_PER_CELL);
            lines.set(MAX_LINES_PER_CELL - 1, lines.get(MAX_LINES_PER_CELL - 1) + "...");
        }

        float actualHeight = Math.max(lines.size() * lineHeight, bgHeight);
        if (drawBackground && bgPaint != null)
            canvas.drawRect(x, y, x + maxWidth, y + actualHeight, bgPaint);

        float currentY = y + (-fm.ascent) + LINE_SPACING / 2;
        for (String line : lines) {
            canvas.drawText(line.trim(), x, currentY, textPaint);
            currentY += lineHeight;
        }

        return (int) Math.ceil(actualHeight);
    }

    private List<String> breakTextIntoLines(String text, TextPaint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) { lines.add(""); return lines; }

        for (String paragraph : text.split("\n")) {
            String[] words = paragraph.split(" ");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                if (word == null) continue;
                String testLine = currentLine.length() > 0 ? currentLine + " " + word : word;
                if (paint.measureText(testLine) > maxWidth && currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    if (currentLine.length() > 0) currentLine.append(" ").append(word);
                    else currentLine.append(word);
                }
            }
            if (currentLine.length() > 0) lines.add(currentLine.toString());
        }

        if (lines.isEmpty() || (lines.size() == 1 && paint.measureText(lines.get(0)) > maxWidth))
            lines = breakLongWord(text, paint, maxWidth);

        return lines;
    }

    private List<String> breakLongWord(String word, TextPaint paint, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            currentLine.append(c);
            if (paint.measureText(currentLine.toString()) > maxWidth) {
                lines.add(currentLine.substring(0, currentLine.length() - 1));
                currentLine = new StringBuilder(String.valueOf(c));
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    private int calculateMultilineTextHeight(String text, Paint paint, float maxWidth) {
        if (text == null || text.trim().isEmpty()) return MIN_ROW_HEIGHT;
        TextPaint textPaint = new TextPaint(paint);
        textPaint.setTextSize(paint.getTextSize());
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) + LINE_SPACING;
        List<String> lines = breakTextIntoLines(text, textPaint, maxWidth);
        if (lines.size() > MAX_LINES_PER_CELL) lines = lines.subList(0, MAX_LINES_PER_CELL);
        return (int) Math.max(lines.size() * lineHeight, MIN_ROW_HEIGHT) + 8;
    }

    private int calculateTransactionRowHeight(HashMap<String, Object> row, Paint paint, int[] columnWidths) {
        int maxLines = 1;
        String particular = (String) row.getOrDefault("particular", "");
        if (particular != null && !particular.trim().isEmpty()) {
            TextPaint textPaint = new TextPaint(paint);
            textPaint.setTextSize(paint.getTextSize());
            List<String> lines = breakTextIntoLines(particular, textPaint, columnWidths[1] - COLUMN_PADDING * 2);
            maxLines = Math.min(lines.size(), MAX_LINES_PER_CELL);
        }
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineHeight = (fm.descent - fm.ascent) + LINE_SPACING;
        return Math.max((int) (maxLines * lineHeight) + 8, MIN_ROW_HEIGHT);
    }

    private Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(bitmap.getWidth() / 2f, bitmap.getHeight() / 2f, bitmap.getWidth() / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    private String formatNumberOrEmpty(Object value) {
        if (value == null) return "";
        try {
            String s = String.valueOf(value);
            if (s.isEmpty() || s.equalsIgnoreCase("null")) return "";
            double num = Double.parseDouble(s);
            if (Math.abs(num) < 0.001) return "";
            return String.format(Locale.getDefault(), "%.2f", num).replaceAll("\\.00$", "");
        } catch (Exception e) { return ""; }
    }

    private String formatNumberOrEmpty(double value) {
        if (Math.abs(value) < 0.001) return "";
        return String.format(Locale.getDefault(), "%.2f", value).replaceAll("\\.00$", "");
    }

    private String formatCurrencyOrEmpty(Object value) {
        if (value == null) return "";
        try {
            String s = String.valueOf(value);
            if (s.isEmpty() || s.equalsIgnoreCase("null")) return "";
            double num = Double.parseDouble(s);
            if (Math.abs(num) < 0.001) return "";
            return String.format(Locale.getDefault(), "%.2f", num).replaceAll("\\.00$", "");
        } catch (Exception e) { return ""; }
    }

    private String formatCurrencyOrEmpty(double value) {
        if (Math.abs(value) < 0.001) return "";
        return String.format(Locale.getDefault(), "%.2f", value).replaceAll("\\.00$", "");
    }

    private String getDisplayText(String value) {
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")
                || value.equalsIgnoreCase("n/a") || value.equalsIgnoreCase("not available"))
            return "";
        return value;
    }

    private double safeParseDouble(Object value) {
        if (value == null) return 0.0;
        try {
            if (value instanceof Double) return (Double) value;
            if (value instanceof Long) return ((Long) value).doubleValue();
            if (value instanceof Integer) return ((Integer) value).doubleValue();
            String s = value.toString().trim();
            if (s.isEmpty() || s.equalsIgnoreCase("null")) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception e) { return 0.0; }
    }
}
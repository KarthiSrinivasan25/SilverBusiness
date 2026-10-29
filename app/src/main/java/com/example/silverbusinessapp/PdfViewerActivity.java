package com.example.silverbusinessapp;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.airbnb.lottie.LottieAnimationView;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PdfViewerActivity extends AppCompatActivity {

    // ============================================================
    // VIEWS
    // ============================================================

    private PhotoView pdfImageView;
    private ProgressBar progressBar;
    private TextView tvTitle;
    private TextView tvPageCount;
    private TextView tvZoomLevel;
    private TextView tvCurrentPage;

    private ImageView btnClose;
    private ImageView btnDownload;
    private ImageView btnPrint;
    private ImageView btnZoomIn;
    private ImageView btnZoomOut;
    private ImageView btnPrevPage;
    private ImageView btnNextPage;

    private LinearLayout pageNavigation;

    // ============================================================
    // PDF DATA
    // ============================================================

    private String pdfFilePath;
    private String titleText;

    private final List<Bitmap> pageBitmaps = new ArrayList<>();

    private int currentPage = 0;
    private int totalPages = 0;

    private float currentZoom = 1.0f;

    // ============================================================
    // ZOOM SETTINGS
    // ============================================================

    private static final float MIN_ZOOM = 1.0f;
    private static final float MEDIUM_ZOOM = 2.5f;
    private static final float MAX_ZOOM = 5.0f;
    private static final float ZOOM_FACTOR = 1.2f;

    // ============================================================
    // DOWNLOAD SETTINGS
    // ============================================================

    private static final int REQUEST_WRITE_STORAGE = 112;

    private static final String LEDGER_FOLDER_NAME =
            "PST Silver Ledger";

    // ============================================================
    // DOWNLOAD DIALOG
    // ============================================================

    private Dialog downloadingDialog;

    // ============================================================
    // HANDLER
    // ============================================================

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_pdf_viewer);

        Intent intent = getIntent();

        if (intent != null) {
            pdfFilePath = intent.getStringExtra("pdfFilePath");
            titleText = intent.getStringExtra("pdfTitle");
        }

        initializeViews();

        setupUI();

        setupZoomControls();

        setupPageNavigation();

        // --------------------------------------------------------
        // Validate PDF
        // --------------------------------------------------------

        if (pdfFilePath == null || pdfFilePath.isEmpty()) {

            Toast.makeText(
                    this,
                    "PDF file path is empty",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        File pdfFile = new File(pdfFilePath);

        if (!pdfFile.exists()) {

            Toast.makeText(
                    this,
                    "PDF file not found",
                    Toast.LENGTH_LONG
            ).show();

            finish();
            return;
        }

        // --------------------------------------------------------
        // Render PDF
        // --------------------------------------------------------

        renderPdfPages();
    }

    // ============================================================
    // INITIALIZE VIEWS
    // ============================================================

    private void initializeViews() {

        pdfImageView = findViewById(R.id.pdf_image_view);

        progressBar = findViewById(R.id.progress_bar);

        tvTitle = findViewById(R.id.tv_pdf_title);

        tvPageCount = findViewById(R.id.tv_page_count);

        tvZoomLevel = findViewById(R.id.tv_zoom_level);

        tvCurrentPage = findViewById(R.id.tv_current_page);

        btnClose = findViewById(R.id.btn_close_viewer);

        btnDownload = findViewById(R.id.btn_download);

        btnPrint = findViewById(R.id.btn_print);

        btnZoomIn = findViewById(R.id.btn_zoom_in);

        btnZoomOut = findViewById(R.id.btn_zoom_out);

        btnPrevPage = findViewById(R.id.btn_prev_page);

        btnNextPage = findViewById(R.id.btn_next_page);

        pageNavigation = findViewById(R.id.page_navigation);
    }

    // ============================================================
    // SETUP UI
    // ============================================================

    private void setupUI() {

        // --------------------------------------------------------
        // Title
        // --------------------------------------------------------

        if (tvTitle != null) {

            if (titleText != null &&
                    !titleText.trim().isEmpty()) {

                tvTitle.setText(titleText);

            } else {

                tvTitle.setText("PDF Preview");
            }
        }

        // --------------------------------------------------------
        // Close
        // --------------------------------------------------------

        if (btnClose != null) {

            btnClose.setOnClickListener(v -> finish());
        }

        // --------------------------------------------------------
        // DOWNLOAD
        // --------------------------------------------------------

        if (btnDownload != null) {

            btnDownload.setOnClickListener(v ->
                    downloadPdf());
        }

        // --------------------------------------------------------
        // PRINT
        // --------------------------------------------------------

        if (btnPrint != null) {

            btnPrint.setOnClickListener(v ->
                    showPrintDialog());
        }

        // --------------------------------------------------------
        // PHOTO VIEW
        // --------------------------------------------------------

        if (pdfImageView != null) {

            pdfImageView.setMinimumScale(MIN_ZOOM);

            pdfImageView.setMediumScale(MEDIUM_ZOOM);

            pdfImageView.setMaximumScale(MAX_ZOOM);

            pdfImageView.setOnPhotoTapListener(
                    (view, x, y) ->
                            toggleNavigationVisibility()
            );

            pdfImageView.setOnMatrixChangeListener(rect -> {

                float scale =
                        pdfImageView.getScale();

                if (scale < MIN_ZOOM) {

                    scale = MIN_ZOOM;

                    pdfImageView.setScale(
                            MIN_ZOOM,
                            false
                    );
                }

                if (scale > MAX_ZOOM) {

                    scale = MAX_ZOOM;

                    pdfImageView.setScale(
                            MAX_ZOOM,
                            false
                    );
                }

                currentZoom = scale;

                updateZoomLevel();
            });
        }

        currentZoom = MIN_ZOOM;

        updateZoomLevel();
    }

    // ============================================================
    // ZOOM CONTROLS
    // ============================================================

    private void setupZoomControls() {

        if (btnZoomIn != null) {

            btnZoomIn.setOnClickListener(v ->
                    zoomIn());
        }

        if (btnZoomOut != null) {

            btnZoomOut.setOnClickListener(v ->
                    zoomOut());
        }
    }

    private void zoomIn() {

        if (pdfImageView == null) return;

        float currentScale =
                pdfImageView.getScale();

        float newScale =
                currentScale * ZOOM_FACTOR;

        if (newScale > MAX_ZOOM) {

            newScale = MAX_ZOOM;
        }

        pdfImageView.setScale(
                newScale,
                true
        );

        currentZoom = newScale;

        updateZoomLevel();
    }

    private void zoomOut() {

        if (pdfImageView == null) return;

        float currentScale =
                pdfImageView.getScale();

        float newScale =
                currentScale / ZOOM_FACTOR;

        if (newScale < MIN_ZOOM) {

            newScale = MIN_ZOOM;
        }

        pdfImageView.setScale(
                newScale,
                true
        );

        currentZoom = newScale;

        updateZoomLevel();
    }

    private void updateZoomLevel() {

        if (tvZoomLevel != null) {

            int percentage =
                    Math.round(currentZoom * 100);

            tvZoomLevel.setText(
                    percentage + "%"
            );
        }
    }

    // ============================================================
    // PAGE NAVIGATION
    // ============================================================

    private void setupPageNavigation() {

        if (btnPrevPage != null) {

            btnPrevPage.setOnClickListener(v ->
                    goToPreviousPage());
        }

        if (btnNextPage != null) {

            btnNextPage.setOnClickListener(v ->
                    goToNextPage());
        }
    }

    private void goToPreviousPage() {

        if (totalPages <= 0) return;

        if (currentPage > 0) {

            currentPage--;

            displayPage(currentPage);

        } else {

            Toast.makeText(
                    this,
                    "Already on first page",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void goToNextPage() {

        if (totalPages <= 0) return;

        if (currentPage < totalPages - 1) {

            currentPage++;

            displayPage(currentPage);

        } else {

            Toast.makeText(
                    this,
                    "Already on last page",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void toggleNavigationVisibility() {

        if (pageNavigation == null) return;

        if (pageNavigation.getVisibility()
                == View.VISIBLE) {

            pageNavigation.setVisibility(
                    View.GONE
            );

        } else {

            pageNavigation.setVisibility(
                    View.VISIBLE
            );
        }
    }

    // ============================================================
    // RENDER PDF
    // ============================================================

    private void renderPdfPages() {

        if (progressBar != null) {

            progressBar.setVisibility(
                    View.VISIBLE
            );
        }

        recyclePageBitmaps();

        new Thread(() -> {

            File pdfFile =
                    new File(pdfFilePath);

            try (
                    ParcelFileDescriptor fileDescriptor =
                            ParcelFileDescriptor.open(
                                    pdfFile,
                                    ParcelFileDescriptor.MODE_READ_ONLY
                            );

                    PdfRenderer pdfRenderer =
                            new PdfRenderer(fileDescriptor)
            ) {

                int targetWidth =
                        getResources()
                                .getDisplayMetrics()
                                .widthPixels;

                totalPages =
                        pdfRenderer.getPageCount();

                for (int i = 0;
                     i < totalPages;
                     i++) {

                    PdfRenderer.Page page =
                            pdfRenderer.openPage(i);

                    try {

                        float scale =
                                (float) targetWidth
                                        / page.getWidth();

                        int bitmapWidth =
                                targetWidth;

                        int bitmapHeight =
                                Math.round(
                                        page.getHeight()
                                                * scale
                                );

                        Bitmap bitmap =
                                Bitmap.createBitmap(
                                        bitmapWidth,
                                        bitmapHeight,
                                        Bitmap.Config.ARGB_8888
                                );

                        bitmap.eraseColor(
                                Color.WHITE
                        );

                        page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page
                                        .RENDER_MODE_FOR_DISPLAY
                        );

                        pageBitmaps.add(bitmap);

                    } finally {

                        page.close();
                    }
                }

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    if (progressBar != null) {

                        progressBar.setVisibility(
                                View.GONE
                        );
                    }

                    Toast.makeText(
                            PdfViewerActivity.this,
                            "Failed to open PDF: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });

                return;
            }

            runOnUiThread(() -> {

                if (progressBar != null) {

                    progressBar.setVisibility(
                            View.GONE
                    );
                }

                if (!pageBitmaps.isEmpty()) {

                    currentPage = 0;

                    displayPage(0);

                    updatePageInfo();

                    if (pageNavigation != null) {

                        pageNavigation.setVisibility(
                                View.VISIBLE
                        );
                    }

                } else {

                    Toast.makeText(
                            PdfViewerActivity.this,
                            "No pages to display",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

        }).start();
    }

    // ============================================================
    // DISPLAY PAGE
    // ============================================================

    private void displayPage(int pageIndex) {

        if (pageIndex < 0 ||
                pageIndex >= pageBitmaps.size()) {

            return;
        }

        Bitmap bitmap =
                pageBitmaps.get(pageIndex);

        if (bitmap == null) return;

        pdfImageView.setImageBitmap(bitmap);

        pdfImageView.setMinimumScale(
                MIN_ZOOM
        );

        pdfImageView.setMediumScale(
                MEDIUM_ZOOM
        );

        pdfImageView.setMaximumScale(
                MAX_ZOOM
        );

        pdfImageView.setScale(
                MIN_ZOOM,
                false
        );

        currentZoom = MIN_ZOOM;

        updateZoomLevel();

        updatePageInfo();
    }

    private void updatePageInfo() {

        if (tvPageCount != null) {

            String text =
                    totalPages == 1
                            ? "1 page"
                            : totalPages + " pages";

            tvPageCount.setText(text);
        }

        if (tvCurrentPage != null) {

            tvCurrentPage.setText(
                    (currentPage + 1)
                            + " / "
                            + totalPages
            );
        }
    }

    // ============================================================
    // DOWNLOAD PDF
    // ============================================================

    private void downloadPdf() {

        if (pdfFilePath == null ||
                !new File(pdfFilePath).exists()) {

            Toast.makeText(
                    this,
                    "PDF file not available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        // Android 9 and below
        if (Build.VERSION.SDK_INT
                < Build.VERSION_CODES.Q) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission
                                        .WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_WRITE_STORAGE
                );

                return;
            }
        }

        // Start download
        showDownloadingDialog();

        performDownloadWithThreeSecondDialog();
    }

    // ============================================================
    // SHOW DOWNLOADING DIALOG
    // ============================================================

    private void showDownloadingDialog() {

        downloadingDialog =
                new Dialog(this);

        downloadingDialog.setContentView(
                R.layout.dialog_downloading1
        );

        downloadingDialog.setCancelable(false);

        if (downloadingDialog.getWindow()
                != null) {

            downloadingDialog.getWindow()
                    .setBackgroundDrawableResource(
                            android.R.color.transparent
                    );

            downloadingDialog.getWindow()
                    .setLayout(
                            dpToPx(300),
                            android.view.WindowManager
                                    .LayoutParams.WRAP_CONTENT
                    );
        }

        downloadingDialog.show();

        if (downloadingDialog.getWindow()
                != null) {

            downloadingDialog.getWindow()
                    .setLayout(
                            dpToPx(300),
                            android.view.WindowManager
                                    .LayoutParams.WRAP_CONTENT
                    );
        }

        // --------------------------------------------------------
        // Lottie
        // --------------------------------------------------------

        LottieAnimationView lottie =
                downloadingDialog.findViewById(
                        R.id.lottie_animation
                );

        if (lottie != null) {

            lottie.setAnimation(
                    R.raw.download_animation
            );

            lottie.setRepeatCount(
                    com.airbnb.lottie.LottieDrawable
                            .INFINITE
            );

            lottie.playAnimation();
        }
    }

    // ============================================================
    // 3 SECOND DOWNLOAD
    // ============================================================

    private void performDownloadWithThreeSecondDialog() {

        // --------------------------------------------------------
        // Disable download button
        // --------------------------------------------------------

        if (btnDownload != null) {

            btnDownload.setEnabled(false);
        }

        // --------------------------------------------------------
        // Start actual file operation
        // --------------------------------------------------------

        new Thread(() -> {

            File sourceFile =
                    new File(pdfFilePath);

            String cleanTitle =
                    (titleText != null &&
                            !titleText.trim().isEmpty())
                            ?
                            titleText.replaceAll(
                                    "[^a-zA-Z0-9]",
                                    "_"
                            )
                            :
                            "document";

            String fileName =
                    cleanTitle + ".pdf";

            boolean success = false;

            String savedName = fileName;

            String errorMessage = null;

            try {

                if (!sourceFile.exists()) {

                    throw new IOException(
                            "Source file not found"
                    );
                }

                // ------------------------------------------------
                // Android 10+
                // ------------------------------------------------

                if (Build.VERSION.SDK_INT
                        >= Build.VERSION_CODES.Q) {

                    savedName =
                            saveViaMediaStore(
                                    sourceFile,
                                    fileName
                            );

                    success =
                            savedName != null;

                    if (!success) {

                        errorMessage =
                                "Could not save PDF";
                    }

                }

                // ------------------------------------------------
                // Android 9 and below
                // ------------------------------------------------

                else {

                    File baseDownloadDir =
                            Environment
                                    .getExternalStoragePublicDirectory(
                                            Environment
                                                    .DIRECTORY_DOWNLOADS
                                    );

                    File downloadDir =
                            new File(
                                    baseDownloadDir,
                                    LEDGER_FOLDER_NAME
                            );

                    if (!downloadDir.exists()) {

                        boolean created =
                                downloadDir.mkdirs();

                        if (!created &&
                                !downloadDir.exists()) {

                            throw new IOException(
                                    "Could not create folder"
                            );
                        }
                    }

                    File destFile =
                            getUniqueFile(
                                    downloadDir,
                                    fileName
                            );

                    copyFile(
                            sourceFile,
                            destFile
                    );

                    savedName =
                            destFile.getName();

                    success = true;
                }

            } catch (Exception e) {

                e.printStackTrace();

                errorMessage =
                        e.getMessage();
            }

            boolean finalSuccess =
                    success;

            String finalSavedName =
                    savedName;

            String finalErrorMessage =
                    errorMessage;

            // ----------------------------------------------------
            // IMPORTANT:
            // Wait exactly 3 seconds before closing dialog
            // ----------------------------------------------------

            handler.postDelayed(() -> {

                closeDownloadingDialog();

                if (btnDownload != null) {

                    btnDownload.setEnabled(true);
                }

                if (finalSuccess) {

                    Toast.makeText(
                            PdfViewerActivity.this,
                            "PDF downloaded successfully\n"
                                    + "Downloads/"
                                    + LEDGER_FOLDER_NAME
                                    + "/"
                                    + finalSavedName,
                            Toast.LENGTH_LONG
                    ).show();

                } else {

                    Toast.makeText(
                            PdfViewerActivity.this,
                            "Download failed"
                                    + (
                                    finalErrorMessage != null
                                            ?
                                            ": "
                                                    + finalErrorMessage
                                            :
                                            ""
                            ),
                            Toast.LENGTH_LONG
                    ).show();
                }

            }, 3000);

        }).start();
    }

    // ============================================================
    // CLOSE DOWNLOAD DIALOG
    // ============================================================

    private void closeDownloadingDialog() {

        if (downloadingDialog != null &&
                downloadingDialog.isShowing()) {

            LottieAnimationView lottie =
                    downloadingDialog.findViewById(
                            R.id.lottie_animation
                    );

            if (lottie != null) {

                lottie.cancelAnimation();
            }

            downloadingDialog.dismiss();
        }

        downloadingDialog = null;
    }

    // ============================================================
    // DP TO PX
    // ============================================================

    private int dpToPx(int dp) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(dp * density);
    }

    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                REQUEST_WRITE_STORAGE) {

            if (grantResults.length > 0 &&
                    grantResults[0]
                            == PackageManager.PERMISSION_GRANTED) {

                downloadPdf();

            } else {

                Toast.makeText(
                        this,
                        "Storage permission required to download PDF",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    // ============================================================
    // SAVE USING MEDIA STORE
    // ============================================================

    private String saveViaMediaStore(
            File sourceFile,
            String fileName) {

        Uri fileUri = null;

        try {

            String relativePath =
                    Environment.DIRECTORY_DOWNLOADS
                            + File.separator
                            + LEDGER_FOLDER_NAME;

            ContentValues values =
                    new ContentValues();

            values.put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    fileName
            );

            values.put(
                    MediaStore.Downloads.MIME_TYPE,
                    "application/pdf"
            );

            values.put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    relativePath
            );

            values.put(
                    MediaStore.Downloads.IS_PENDING,
                    1
            );

            ContentResolver resolver =
                    getContentResolver();

            Uri collection =
                    MediaStore.Downloads
                            .getContentUri(
                                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                            );

            fileUri =
                    resolver.insert(
                            collection,
                            values
                    );

            if (fileUri == null) {

                return null;
            }

            try (
                    InputStream in =
                            new FileInputStream(
                                    sourceFile
                            );

                    OutputStream out =
                            resolver.openOutputStream(
                                    fileUri
                            )
            ) {

                if (out == null) {

                    resolver.delete(
                            fileUri,
                            null,
                            null
                    );

                    return null;
                }

                byte[] buffer =
                        new byte[8192];

                int length;

                while (
                        (length = in.read(buffer))
                                > 0
                ) {

                    out.write(
                            buffer,
                            0,
                            length
                    );
                }

                out.flush();
            }

            ContentValues updateValues =
                    new ContentValues();

            updateValues.put(
                    MediaStore.Downloads.IS_PENDING,
                    0
            );

            resolver.update(
                    fileUri,
                    updateValues,
                    null,
                    null
            );

            return fileName;

        } catch (Exception e) {

            e.printStackTrace();

            if (fileUri != null) {

                try {

                    getContentResolver()
                            .delete(
                                    fileUri,
                                    null,
                                    null
                            );

                } catch (Exception ignored) {
                }
            }

            return null;
        }
    }

    // ============================================================
    // UNIQUE FILE
    // ============================================================

    private File getUniqueFile(
            File directory,
            String fileName) {

        File file =
                new File(
                        directory,
                        fileName
                );

        if (!file.exists()) {

            return file;
        }

        String baseName =
                fileName;

        if (baseName.toLowerCase()
                .endsWith(".pdf")) {

            baseName =
                    baseName.substring(
                            0,
                            baseName.length() - 4
                    );
        }

        String extension = ".pdf";

        int counter = 1;

        while (file.exists()) {

            String newFileName =
                    baseName
                            + "_"
                            + counter
                            + extension;

            file =
                    new File(
                            directory,
                            newFileName
                    );

            counter++;
        }

        return file;
    }

    // ============================================================
    // COPY FILE
    // ============================================================

    private void copyFile(
            File source,
            File destination)
            throws IOException {

        try (
                InputStream in =
                        new FileInputStream(source);

                OutputStream out =
                        new FileOutputStream(destination)
        ) {

            byte[] buffer =
                    new byte[8192];

            int length;

            while (
                    (length = in.read(buffer))
                            > 0
            ) {

                out.write(
                        buffer,
                        0,
                        length
                );
            }

            out.flush();
        }
    }

    // ============================================================
    // PRINT DIALOG
    // ============================================================

    private void showPrintDialog() {

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        View dialogView =
                getLayoutInflater().inflate(
                        R.layout.dialog_print_options,
                        null
                );

        RadioGroup rgPrintRange =
                dialogView.findViewById(
                        R.id.rg_print_range
                );

        RadioButton rbAllPages =
                dialogView.findViewById(
                        R.id.rb_all_pages
                );

        RadioButton rbCurrentPage =
                dialogView.findViewById(
                        R.id.rb_current_page
                );

        RadioButton rbCustomRange =
                dialogView.findViewById(
                        R.id.rb_custom_range
                );

        EditText etStartPage =
                dialogView.findViewById(
                        R.id.et_start_page
                );

        EditText etEndPage =
                dialogView.findViewById(
                        R.id.et_end_page
                );

        LinearLayout rangeLayout =
                dialogView.findViewById(
                        R.id.range_layout
                );

        NumberPicker npCopies =
                dialogView.findViewById(
                        R.id.np_copies
                );

        Spinner spinnerOrientation =
                dialogView.findViewById(
                        R.id.spinner_orientation
                );

        if (npCopies != null) {

            npCopies.setMinValue(1);

            npCopies.setMaxValue(10);

            npCopies.setValue(1);
        }

        if (spinnerOrientation != null) {

            String[] orientationItems = {
                    "Auto",
                    "Portrait",
                    "Landscape"
            };

            ArrayAdapter<String> adapter =
                    new ArrayAdapter<>(
                            this,
                            android.R.layout
                                    .simple_spinner_item,
                            orientationItems
                    );

            adapter.setDropDownViewResource(
                    android.R.layout
                            .simple_spinner_dropdown_item
            );

            spinnerOrientation.setAdapter(
                    adapter
            );
        }

        if (etStartPage != null) {

            etStartPage.setHint("1");
        }

        if (etEndPage != null) {

            etEndPage.setHint(
                    String.valueOf(totalPages)
            );
        }

        rgPrintRange.setOnCheckedChangeListener(
                (group, checkedId) -> {

                    if (rangeLayout != null) {

                        rangeLayout.setVisibility(
                                checkedId ==
                                        R.id.rb_custom_range
                                        ?
                                        View.VISIBLE
                                        :
                                        View.GONE
                        );
                    }
                }
        );

        builder.setView(dialogView)
                .setTitle("Print Options")
                .setPositiveButton(
                        "Print",
                        (dialog, which) -> {

                            int copies =
                                    npCopies.getValue();

                            int orientation =
                                    spinnerOrientation
                                            .getSelectedItemPosition();

                            int selectedId =
                                    rgPrintRange
                                            .getCheckedRadioButtonId();

                            if (selectedId ==
                                    R.id.rb_all_pages) {

                                printPdf(
                                        copies,
                                        orientation
                                );

                            } else if (
                                    selectedId ==
                                            R.id.rb_current_page
                            ) {

                                printCurrentPage(
                                        copies,
                                        orientation
                                );

                            } else if (
                                    selectedId ==
                                            R.id.rb_custom_range
                            ) {

                                try {

                                    String startText =
                                            etStartPage
                                                    .getText()
                                                    .toString();

                                    String endText =
                                            etEndPage
                                                    .getText()
                                                    .toString();

                                    if (startText.isEmpty()
                                            ||
                                            endText.isEmpty()) {

                                        Toast.makeText(
                                                this,
                                                "Please enter page range",
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        return;
                                    }

                                    int start =
                                            Integer.parseInt(
                                                    startText
                                            ) - 1;

                                    int end =
                                            Integer.parseInt(
                                                    endText
                                            ) - 1;

                                    if (start >= 0 &&
                                            end < totalPages &&
                                            start <= end) {

                                        printPageRange(
                                                start,
                                                end,
                                                copies,
                                                orientation
                                        );

                                    } else {

                                        Toast.makeText(
                                                this,
                                                "Invalid page range",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }

                                } catch (
                                        NumberFormatException e) {

                                    Toast.makeText(
                                            this,
                                            "Enter valid page numbers",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    // ============================================================
    // PRINT PDF
    // ============================================================

    private void printPdf(
            int copies,
            int orientation) {

        if (pdfFilePath == null ||
                !new File(pdfFilePath).exists()) {

            Toast.makeText(
                    this,
                    "PDF file not available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        PrintManager printManager =
                (PrintManager)
                        getSystemService(
                                Context.PRINT_SERVICE
                        );

        if (printManager == null) {

            Toast.makeText(
                    this,
                    "Printing not supported",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            PrintDocumentAdapter adapter =
                    new PdfPrintDocumentAdapter(
                            this,
                            pdfFilePath
                    );

            PrintAttributes attributes =
                    new PrintAttributes.Builder()
                            .setMediaSize(
                                    PrintAttributes
                                            .MediaSize.ISO_A4
                            )
                            .setColorMode(
                                    PrintAttributes
                                            .COLOR_MODE_COLOR
                            )
                            .setMinMargins(
                                    PrintAttributes
                                            .Margins.NO_MARGINS
                            )
                            .build();

            printManager.print(
                    "Print "
                            + getDocumentName(),
                    adapter,
                    attributes
            );

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "Error printing: "
                            + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ============================================================
    // PRINT CURRENT PAGE
    // ============================================================

    private void printCurrentPage(
            int copies,
            int orientation) {

        if (pageBitmaps.isEmpty() ||
                currentPage < 0 ||
                currentPage >= pageBitmaps.size()) {

            Toast.makeText(
                    this,
                    "No page to print",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        PrintManager printManager =
                (PrintManager)
                        getSystemService(
                                Context.PRINT_SERVICE
                        );

        if (printManager == null) return;

        PrintAttributes attributes =
                new PrintAttributes.Builder()
                        .setMediaSize(
                                PrintAttributes
                                        .MediaSize.ISO_A4
                        )
                        .setColorMode(
                                PrintAttributes
                                        .COLOR_MODE_COLOR
                        )
                        .build();

        PrintDocumentAdapter adapter =
                new PrintDocumentAdapter() {

                    @Override
                    public void onLayout(
                            PrintAttributes oldAttributes,
                            PrintAttributes newAttributes,
                            CancellationSignal cancellationSignal,
                            LayoutResultCallback callback,
                            Bundle extras) {

                        PrintDocumentInfo info =
                                new PrintDocumentInfo.Builder(
                                        "Page "
                                                + (currentPage + 1)
                                )
                                        .setContentType(
                                                PrintDocumentInfo
                                                        .CONTENT_TYPE_DOCUMENT
                                        )
                                        .setPageCount(1)
                                        .build();

                        callback.onLayoutFinished(
                                info,
                                true
                        );
                    }

                    @Override
                    public void onWrite(
                            PageRange[] pages,
                            ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {

                        try {

                            Bitmap bitmap =
                                    pageBitmaps.get(
                                            currentPage
                                    );

                            android.graphics.pdf
                                    .PdfDocument pdfDocument =
                                    new android.graphics.pdf
                                            .PdfDocument();

                            android.graphics.pdf
                                    .PdfDocument.PageInfo pageInfo =
                                    new android.graphics.pdf
                                            .PdfDocument.PageInfo
                                            .Builder(
                                            bitmap.getWidth(),
                                            bitmap.getHeight(),
                                            1
                                    )
                                            .create();

                            android.graphics.pdf
                                    .PdfDocument.Page page =
                                    pdfDocument.startPage(
                                            pageInfo
                                    );

                            page.getCanvas()
                                    .drawBitmap(
                                            bitmap,
                                            0,
                                            0,
                                            null
                                    );

                            pdfDocument.finishPage(
                                    page
                            );

                            pdfDocument.writeTo(
                                    new FileOutputStream(
                                            destination
                                                    .getFileDescriptor()
                                    )
                            );

                            pdfDocument.close();

                            callback.onWriteFinished(
                                    new PageRange[]{
                                            pages[0]
                                    }
                            );

                        } catch (Exception e) {

                            e.printStackTrace();

                            callback.onWriteFailed(
                                    e.getMessage()
                            );
                        }
                    }
                };

        printManager.print(
                "Print Page "
                        + (currentPage + 1),
                adapter,
                attributes
        );
    }

    // ============================================================
    // PRINT RANGE
    // ============================================================

    private void printPageRange(
            int startPage,
            int endPage,
            int copies,
            int orientation) {

        PrintManager printManager =
                (PrintManager)
                        getSystemService(
                                Context.PRINT_SERVICE
                        );

        if (printManager == null) return;

        PrintDocumentAdapter adapter =
                new PrintDocumentAdapter() {

                    @Override
                    public void onLayout(
                            PrintAttributes oldAttributes,
                            PrintAttributes newAttributes,
                            CancellationSignal cancellationSignal,
                            LayoutResultCallback callback,
                            Bundle extras) {

                        PrintDocumentInfo info =
                                new PrintDocumentInfo.Builder(
                                        "Document Pages "
                                                + (startPage + 1)
                                                + "-"
                                                + (endPage + 1)
                                )
                                        .setContentType(
                                                PrintDocumentInfo
                                                        .CONTENT_TYPE_DOCUMENT
                                        )
                                        .setPageCount(
                                                endPage
                                                        - startPage
                                                        + 1
                                        )
                                        .build();

                        callback.onLayoutFinished(
                                info,
                                true
                        );
                    }

                    @Override
                    public void onWrite(
                            PageRange[] pages,
                            ParcelFileDescriptor destination,
                            CancellationSignal cancellationSignal,
                            WriteResultCallback callback) {

                        try {

                            android.graphics.pdf
                                    .PdfDocument pdfDocument =
                                    new android.graphics.pdf
                                            .PdfDocument();

                            for (int i = startPage;
                                 i <= endPage;
                                 i++) {

                                if (cancellationSignal
                                        .isCanceled()) {

                                    break;
                                }

                                Bitmap bitmap =
                                        pageBitmaps.get(i);

                                if (bitmap == null) continue;

                                android.graphics.pdf
                                        .PdfDocument.PageInfo pageInfo =
                                        new android.graphics.pdf
                                                .PdfDocument.PageInfo
                                                .Builder(
                                                bitmap.getWidth(),
                                                bitmap.getHeight(),
                                                i - startPage + 1
                                        )
                                                .create();

                                android.graphics.pdf
                                        .PdfDocument.Page page =
                                        pdfDocument.startPage(
                                                pageInfo
                                        );

                                page.getCanvas()
                                        .drawBitmap(
                                                bitmap,
                                                0,
                                                0,
                                                null
                                        );

                                pdfDocument.finishPage(
                                        page
                                );
                            }

                            pdfDocument.writeTo(
                                    new FileOutputStream(
                                            destination
                                                    .getFileDescriptor()
                                    )
                            );

                            pdfDocument.close();

                            callback.onWriteFinished(
                                    new PageRange[]{
                                            pages[0]
                                    }
                            );

                        } catch (Exception e) {

                            e.printStackTrace();

                            callback.onWriteFailed(
                                    e.getMessage()
                            );
                        }
                    }
                };

        printManager.print(
                "Document Pages "
                        + (startPage + 1)
                        + "-"
                        + (endPage + 1),
                adapter,
                new PrintAttributes.Builder()
                        .setMediaSize(
                                PrintAttributes
                                        .MediaSize.ISO_A4
                        )
                        .setColorMode(
                                PrintAttributes
                                        .COLOR_MODE_COLOR
                        )
                        .build()
        );
    }

    // ============================================================
    // DOCUMENT NAME
    // ============================================================

    private String getDocumentName() {

        if (titleText != null &&
                !titleText.trim().isEmpty()) {

            return titleText;
        }

        return "Document";
    }

    // ============================================================
    // PDF PRINT ADAPTER
    // ============================================================

    private static class PdfPrintDocumentAdapter
            extends PrintDocumentAdapter {

        private final Context context;

        private final String filePath;

        private int totalPages;

        private ParcelFileDescriptor fileDescriptor;

        PdfPrintDocumentAdapter(
                Context context,
                String filePath) {

            this.context = context;

            this.filePath = filePath;
        }

        @Override
        public void onLayout(
                PrintAttributes oldAttributes,
                PrintAttributes newAttributes,
                CancellationSignal cancellationSignal,
                LayoutResultCallback callback,
                Bundle extras) {

            try {

                File pdfFile =
                        new File(filePath);

                fileDescriptor =
                        ParcelFileDescriptor.open(
                                pdfFile,
                                ParcelFileDescriptor
                                        .MODE_READ_ONLY
                        );

                PdfRenderer renderer =
                        new PdfRenderer(
                                fileDescriptor
                        );

                totalPages =
                        renderer.getPageCount();

                renderer.close();

                PrintDocumentInfo info =
                        new PrintDocumentInfo.Builder(
                                "Document"
                        )
                                .setContentType(
                                        PrintDocumentInfo
                                                .CONTENT_TYPE_DOCUMENT
                                )
                                .setPageCount(
                                        totalPages
                                )
                                .build();

                callback.onLayoutFinished(
                        info,
                        true
                );

            } catch (Exception e) {

                e.printStackTrace();

                callback.onLayoutFailed(
                        e.getMessage()
                );
            }
        }

        @Override
        public void onWrite(
                PageRange[] pages,
                ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal,
                WriteResultCallback callback) {

            try {

                PdfRenderer renderer =
                        new PdfRenderer(
                                fileDescriptor
                        );

                android.graphics.pdf
                        .PdfDocument pdfDocument =
                        new android.graphics.pdf
                                .PdfDocument();

                int startPage =
                        pages[0].getStart();

                int endPage =
                        pages[0].getEnd();

                if (startPage == 0 &&
                        endPage == 0) {

                    startPage = 0;

                    endPage =
                            totalPages - 1;
                }

                for (int i = startPage;
                     i <= endPage;
                     i++) {

                    if (cancellationSignal
                            .isCanceled()) {

                        break;
                    }

                    PdfRenderer.Page page =
                            renderer.openPage(i);

                    android.graphics.pdf
                            .PdfDocument.PageInfo pageInfo =
                            new android.graphics.pdf
                                    .PdfDocument.PageInfo
                                    .Builder(
                                    page.getWidth(),
                                    page.getHeight(),
                                    i + 1
                            )
                                    .create();

                    android.graphics.pdf
                            .PdfDocument.Page newPage =
                            pdfDocument.startPage(
                                    pageInfo
                            );

                    Bitmap bitmap =
                            Bitmap.createBitmap(
                                    page.getWidth(),
                                    page.getHeight(),
                                    Bitmap.Config.ARGB_8888
                            );

                    bitmap.eraseColor(
                            Color.WHITE
                    );

                    page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page
                                    .RENDER_MODE_FOR_PRINT
                    );

                    newPage.getCanvas()
                            .drawBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    null
                            );

                    pdfDocument.finishPage(
                            newPage
                    );

                    page.close();

                    bitmap.recycle();
                }

                pdfDocument.writeTo(
                        new FileOutputStream(
                                destination
                                        .getFileDescriptor()
                        )
                );

                pdfDocument.close();

                renderer.close();

                fileDescriptor.close();

                callback.onWriteFinished(
                        new PageRange[]{
                                pages[0]
                        }
                );

            } catch (Exception e) {

                e.printStackTrace();

                callback.onWriteFailed(
                        e.getMessage()
                );
            }
        }
    }

    // ============================================================
    // RECYCLE BITMAPS
    // ============================================================

    private void recyclePageBitmaps() {

        synchronized (pageBitmaps) {

            for (Bitmap bitmap :
                    pageBitmaps) {

                if (bitmap != null &&
                        !bitmap.isRecycled()) {

                    bitmap.recycle();
                }
            }

            pageBitmaps.clear();
        }
    }

    // ============================================================
    // ON DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        closeDownloadingDialog();

        if (pdfImageView != null) {

            pdfImageView.setImageDrawable(
                    null
            );
        }

        recyclePageBitmaps();

        super.onDestroy();
    }
}
package com.mugime.kotonote.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mugime.kotonote.R;
import com.mugime.kotonote.databinding.ActivityPaintBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Draw on a white sheet. "Done" writes the picture to {@link #EXTRA_OUTPUT} as a PNG and
 * returns RESULT_OK; leaving any other way writes nothing. When {@link #EXTRA_BASE} is
 * given, that earlier drawing is the starting point (re-editing).
 */
public class PaintActivity extends AppCompatActivity {

    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_BASE = "base";

    private static final int[] COLORS = {
            0xFF212121, 0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFFFB8C00
    };
    private static final int[] SIZE_DP = {3, 7, 14};

    private ActivityPaintBinding binding;
    private final View[] colorViews = new View[COLORS.length];
    private final View[] sizeViews = new View[SIZE_DP.length];
    private int selectedColor = 0;
    private int selectedSize = 1;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPaintBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.topBar.setPadding(
                    bars.left + dp(4), bars.top, bars.right + dp(4), 0);
            binding.tools.setPadding(
                    bars.left + dp(8), dp(4), bars.right + dp(8), bars.bottom + dp(4));
            return WindowInsetsCompat.CONSUMED;
        });

        String basePath = getIntent().getStringExtra(EXTRA_BASE);
        if (basePath != null) {
            binding.paint.setSource(BitmapFactory.decodeFile(basePath));
        }

        buildPalette();
        buildSizes();
        selectColor(0);
        selectSize(1);

        binding.btnUndo.setOnClickListener(v -> binding.paint.undo());
        binding.btnClear.setOnClickListener(v -> binding.paint.clearSheet());
        binding.btnEraser.setOnClickListener(v -> toggleEraser());
        binding.btnClose.setOnClickListener(v -> confirmLeave());
        binding.btnDone.setOnClickListener(v -> save());
        binding.paint.setOnChangeListener(this::updateUndo);
        updateUndo();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        });
    }

    private void buildPalette() {
        LinearLayout palette = binding.palette;
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            View swatch = new View(this);
            swatch.setContentDescription(getString(R.string.paint_color));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.setMargins(dp(6), dp(4), dp(6), dp(4));
            swatch.setLayoutParams(lp);
            swatch.setOnClickListener(v -> selectColor(index));
            palette.addView(swatch);
            colorViews[i] = swatch;
        }
    }

    private void buildSizes() {
        for (int i = 0; i < SIZE_DP.length; i++) {
            final int index = i;
            View dot = new View(this);
            dot.setContentDescription(getString(R.string.paint_size));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> selectSize(index));
            binding.sizes.addView(dot);
            sizeViews[i] = dot;
        }
    }

    private void selectColor(int index) {
        selectedColor = index;
        binding.paint.setColor(COLORS[index]);
        for (int i = 0; i < colorViews.length; i++) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(COLORS[i]);
            d.setStroke(dp(i == index ? 3 : 1), i == index ? Color.WHITE : 0x66FFFFFF);
            colorViews[i].setBackground(d);
        }
        renderEraser();
    }

    private void selectSize(int index) {
        selectedSize = index;
        binding.paint.setStrokeWidth(dp(SIZE_DP[index]));
        for (int i = 0; i < sizeViews.length; i++) {
            int d = dp(SIZE_DP[i]) + dp(4);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(i == index ? Color.WHITE : 0x66FFFFFF);
            dot.setSize(d, d);
            android.graphics.drawable.InsetDrawable inset = new android.graphics.drawable.InsetDrawable(
                    dot, Math.max(0, (dp(40) - d) / 2));
            sizeViews[i].setBackground(inset);
        }
    }

    private void toggleEraser() {
        binding.paint.setEraser(!binding.paint.isEraser());
        renderEraser();
    }

    private void renderEraser() {
        binding.btnEraser.setAlpha(binding.paint.isEraser() ? 1f : 0.55f);
        binding.btnEraser.setBackgroundResource(binding.paint.isEraser()
                ? R.drawable.bg_tool_selected : 0);
    }

    private void updateUndo() {
        boolean can = binding.paint.hasChanges();
        binding.btnUndo.setEnabled(can);
        binding.btnUndo.setAlpha(can ? 1f : 0.4f);
    }

    private void confirmLeave() {
        if (saving) {
            return;
        }
        if (!binding.paint.hasChanges()) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.paint_discard_title)
                .setMessage(R.string.paint_discard_message)
                .setNegativeButton(R.string.paint_keep, null)
                .setPositiveButton(R.string.paint_discard, (d, w) -> finish())
                .show();
    }

    private void save() {
        String out = getIntent().getStringExtra(EXTRA_OUTPUT);
        if (saving || out == null) {
            return;
        }
        saving = true;
        final Bitmap bitmap = binding.paint.render();
        new Thread(() -> {
            boolean ok = false;
            try (OutputStream os = new FileOutputStream(new File(out))) {
                ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            } catch (IOException e) {
                ok = false;
            }
            final boolean saved = ok;
            runOnUiThread(() -> {
                if (saved) {
                    setResult(RESULT_OK, new Intent());
                    finish();
                } else {
                    saving = false;
                    Toast.makeText(this, R.string.paint_save_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

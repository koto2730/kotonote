package com.mugime.kotonote.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.mugime.kotonote.R;

import java.io.IOException;

/** Full-screen picture viewer. Tap anywhere to close. */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "path";
    private static final int MAX_EDGE = 2048;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ImageView view = new ImageView(this);
        view.setBackgroundColor(0xFF000000);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setContentDescription(getString(R.string.attachment_image));
        view.setOnClickListener(v -> finish());
        setContentView(view);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        new Thread(() -> {
            Bitmap bitmap = path == null ? null : decode(path);
            runOnUiThread(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (bitmap == null) {
                    Toast.makeText(this, R.string.viewer_failed, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    view.setImageBitmap(bitmap);
                }
            });
        }).start();
    }

    private static Bitmap decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (longest / sample > MAX_EDGE) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null) {
            return null;
        }
        int degrees = exifDegrees(path);
        if (degrees == 0) {
            return bitmap;
        }
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
    }

    private static int exifDegrees(String path) {
        try {
            int o = new ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }
}

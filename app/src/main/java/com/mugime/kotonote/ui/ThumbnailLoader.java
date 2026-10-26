package com.mugime.kotonote.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.BlockType;
import com.mugime.kotonote.elements.ThumbnailSpec;
import com.mugime.kotonote.files.AttachmentStore;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Makes the thumbnails shown inside the note text: rounded pictures for images and
 * drawings, and the first frame with a play mark for videos. Work happens on two
 * background threads; results are cached (they can always be rebuilt from the file).
 */
public final class ThumbnailLoader {

    public interface Callback {
        /** Called on the main thread; {@code bitmap} is null when the file could not be read. */
        void onLoaded(Bitmap bitmap);
    }

    private static final int MAX_VIDEO_FRAME_EDGE = 1600;

    private final AttachmentStore store;
    private final float density;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    public ThumbnailLoader(AttachmentStore store, float density) {
        this.store = store;
        this.density = density;
    }

    private static String key(Block b, int edgePx) {
        return b.filePath + "|" + b.sizeLevel + "|" + edgePx;
    }

    /** The finished thumbnail if it is already in memory, else null. */
    public Bitmap cached(Block block, int edgePx) {
        return block.filePath == null ? null : cache.get(key(block, edgePx));
    }

    public void load(Block block, int edgePx, Callback callback) {
        final String path = block.filePath;
        if (path == null) {
            callback.onLoaded(null);
            return;
        }
        final int level = block.sizeLevel;
        final BlockType type = block.blockType();
        final String key = key(block, edgePx);
        pool.execute(() -> {
            Bitmap result = null;
            try {
                result = render(store.resolve(path), type, level, edgePx);
            } catch (RuntimeException | OutOfMemoryError e) {
                // Unreadable or too big: the note keeps showing its icon.
            }
            if (result != null) {
                cache.put(key, result);
            }
            final Bitmap done = result;
            main.post(() -> callback.onLoaded(done));
        });
    }

    private Bitmap render(File file, BlockType type, int level, int edgePx) {
        Bitmap src = type == BlockType.VIDEO ? videoFrame(file) : decodeImage(file, level, edgePx);
        if (src == null) {
            return null;
        }
        int[] size = ThumbnailSpec.outputSize(level, src.getWidth(), src.getHeight(), edgePx);
        int w = size[0];
        int h = size[1];

        // Which part of the source fills the tile: a centred square for small, all of it otherwise.
        RectF from;
        if (ThumbnailSpec.clampLevel(level) == ThumbnailSpec.SMALL) {
            int side = Math.min(src.getWidth(), src.getHeight());
            float left = (src.getWidth() - side) / 2f;
            float top = (src.getHeight() - side) / 2f;
            from = new RectF(left, top, left + side, top + side);
        } else {
            from = new RectF(0, 0, src.getWidth(), src.getHeight());
        }
        Matrix matrix = new Matrix();
        matrix.setRectToRect(from, new RectF(0, 0, w, h), Matrix.ScaleToFit.FILL);
        BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(matrix);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(shader);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        float radius = Math.min(8 * density, Math.min(w, h) / 6f);
        canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
        if (type == BlockType.VIDEO) {
            drawPlayMark(canvas, w, h);
        }
        src.recycle();
        return out;
    }

    private Bitmap decodeImage(File file, int level, int edgePx) {
        String path = file.getAbsolutePath();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int degrees = exifDegrees(path);
        boolean swap = degrees == 90 || degrees == 270;
        int shownW = swap ? bounds.outHeight : bounds.outWidth;
        int shownH = swap ? bounds.outWidth : bounds.outHeight;

        // Decode at the largest power-of-two reduction that still covers the tile.
        float needed = ThumbnailSpec.clampLevel(level) == ThumbnailSpec.SMALL
                ? (float) edgePx / Math.min(shownW, shownH)
                : (float) edgePx / Math.max(shownW, shownH);
        int sample = 1;
        while (needed > 0 && sample * 2 <= 1f / needed) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(path, opts);
        if (bitmap == null || degrees == 0) {
            return bitmap;
        }
        Matrix m = new Matrix();
        m.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
        if (rotated != bitmap) {
            bitmap.recycle();
        }
        return rotated;
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

    private static Bitmap videoFrame(File file) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());
            Bitmap frame = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                return null;
            }
            int longest = Math.max(frame.getWidth(), frame.getHeight());
            if (longest > MAX_VIDEO_FRAME_EDGE) {
                float scale = (float) MAX_VIDEO_FRAME_EDGE / longest;
                Bitmap smaller = Bitmap.createScaledBitmap(frame,
                        Math.max(1, Math.round(frame.getWidth() * scale)),
                        Math.max(1, Math.round(frame.getHeight() * scale)), true);
                if (smaller != frame) {
                    frame.recycle();
                }
                return smaller;
            }
            return frame;
        } catch (RuntimeException e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // Nothing to do.
            }
        }
    }

    private void drawPlayMark(Canvas canvas, int w, int h) {
        float r = Math.min(Math.min(w, h) * 0.22f, 22 * density);
        float cx = w / 2f;
        float cy = h / 2f;
        Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
        disc.setColor(0x99000000);
        canvas.drawCircle(cx, cy, r, disc);
        Path triangle = new Path();
        triangle.moveTo(cx - r * 0.28f, cy - r * 0.5f);
        triangle.lineTo(cx - r * 0.28f, cy + r * 0.5f);
        triangle.lineTo(cx + r * 0.55f, cy);
        triangle.close();
        Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        mark.setColor(0xFFFFFFFF);
        canvas.drawPath(triangle, mark);
    }
}

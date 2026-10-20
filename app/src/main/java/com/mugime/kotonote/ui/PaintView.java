package com.mugime.kotonote.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A white sheet you draw on with a finger. Strokes are kept as paths on top of the base
 * image (the previous drawing, when re-editing), so undo is exact. The eraser paints in
 * the paper colour, and "clear" is just one more undoable stroke covering everything.
 */
public class PaintView extends View {

    public interface OnChangeListener {
        void onChanged();
    }

    private static final class Stroke {
        final Path path;
        final Paint paint;

        Stroke(Path path, Paint paint) {
            this.path = path;
            this.paint = paint;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap base;
    @Nullable
    private Bitmap source;

    private int color = Color.BLACK;
    private float width = 8f;
    private boolean eraser;

    @Nullable
    private Path current;
    @Nullable
    private Paint currentPaint;
    private float lastX;
    private float lastY;
    @Nullable
    private OnChangeListener listener;

    public PaintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnChangeListener(@Nullable OnChangeListener listener) {
        this.listener = listener;
    }

    /** The drawing to continue from, scaled to fit the sheet. */
    public void setSource(@Nullable Bitmap bitmap) {
        source = bitmap;
        if (getWidth() > 0) {
            rebuildBase();
            invalidate();
        }
    }

    public void setColor(int color) {
        this.color = color;
        this.eraser = false;
    }

    public void setStrokeWidth(float px) {
        this.width = px;
    }

    public void setEraser(boolean eraser) {
        this.eraser = eraser;
    }

    public boolean isEraser() {
        return eraser;
    }

    public boolean hasChanges() {
        return !strokes.isEmpty();
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
            notifyChanged();
        }
    }

    /** Covers the whole sheet with paper. Undoable. */
    public void clearSheet() {
        Path all = new Path();
        all.addRect(new RectF(0, 0, getWidth(), getHeight()), Path.Direction.CW);
        Paint paper = new Paint();
        paper.setColor(Color.WHITE);
        paper.setStyle(Paint.Style.FILL);
        strokes.add(new Stroke(all, paper));
        invalidate();
        notifyChanged();
    }

    /** The finished picture at the sheet's pixel size. */
    public Bitmap render() {
        Bitmap out = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        draw(new Canvas(out), false);
        return out;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            rebuildBase();
        }
    }

    private void rebuildBase() {
        Bitmap next = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(next);
        canvas.drawColor(Color.WHITE);
        if (source != null) {
            float scale = Math.min((float) getWidth() / source.getWidth(),
                    (float) getHeight() / source.getHeight());
            float w = source.getWidth() * scale;
            float h = source.getHeight() * scale;
            float left = (getWidth() - w) / 2f;
            float top = (getHeight() - h) / 2f;
            canvas.drawBitmap(source, null, new RectF(left, top, left + w, top + h), bitmapPaint);
        }
        base = next;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        draw(canvas, true);
    }

    private void draw(Canvas canvas, boolean includeCurrent) {
        if (base != null) {
            canvas.drawBitmap(base, 0, 0, null);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        for (Stroke s : strokes) {
            canvas.drawPath(s.path, s.paint);
        }
        if (includeCurrent && current != null && currentPaint != null) {
            canvas.drawPath(current, currentPaint);
        }
    }

    private Paint newPaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(eraser ? width * 2.5f : width);
        p.setColor(eraser ? Color.WHITE : color);
        return p;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                current = new Path();
                currentPaint = newPaint();
                current.moveTo(x, y);
                // A zero-length stroke with round caps is a dot: a tap leaves a mark.
                current.lineTo(x + 0.1f, y);
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (current != null) {
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        extend(event.getHistoricalX(i), event.getHistoricalY(i));
                    }
                    extend(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (current != null && currentPaint != null) {
                    current.lineTo(x, y);
                    strokes.add(new Stroke(current, currentPaint));
                    current = null;
                    currentPaint = null;
                    invalidate();
                    notifyChanged();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                current = null;
                currentPaint = null;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void extend(float x, float y) {
        current.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f);
        lastX = x;
        lastY = y;
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onChanged();
        }
    }
}

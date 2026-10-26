package com.mugime.kotonote.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;

import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.BlockType;
import com.mugime.kotonote.elements.ThumbnailSpec;

import java.util.function.IntSupplier;

/**
 * Decides what an attachment looks like inside the note: voice recordings are a small icon
 * chip; pictures, drawings and videos are a thumbnail whose size is the block's level.
 * While a thumbnail is still being made, a chip of the same size stands in for it.
 */
public final class AttachmentLook {

    private final Context context;
    private final int tint;
    private final float density;
    private final int chipPx;
    private final IntSupplier contentWidthPx;

    public AttachmentLook(Context context, int tint, int chipPx, IntSupplier contentWidthPx) {
        this.context = context;
        this.tint = tint;
        this.density = context.getResources().getDisplayMetrics().density;
        this.chipPx = chipPx;
        this.contentWidthPx = contentWidthPx;
    }

    public static boolean isVisual(Block block) {
        BlockType type = block.blockType();
        return type == BlockType.IMAGE || type == BlockType.PAINT || type == BlockType.VIDEO;
    }

    /** The longest edge this attachment's thumbnail is drawn at. */
    public int edgePx(Block block) {
        return ThumbnailSpec.edgePx(block.sizeLevel, density, contentWidthPx.getAsInt());
    }

    /** The icon chip: used for audio, and for a picture until its thumbnail is ready. */
    public Drawable placeholder(Block block, boolean active) {
        int size = isVisual(block) ? edgePx(block) : chipPx;
        return NoteEditorCodec.icon(context, block.blockType(), size, tint, active);
    }

    public Drawable thumbnail(Bitmap bitmap) {
        BitmapDrawable picture = new BitmapDrawable(context.getResources(), bitmap);
        int gapH = Math.round(3 * density);
        int gapV = Math.round(2 * density);
        InsetDrawable spaced = new InsetDrawable(picture, gapH, gapV, gapH, gapV);
        spaced.setBounds(0, 0, bitmap.getWidth() + 2 * gapH, bitmap.getHeight() + 2 * gapV);
        return spaced;
    }
}

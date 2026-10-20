package com.mugime.kotonote.ui;

import android.graphics.drawable.Drawable;
import android.text.style.ImageSpan;

import com.mugime.kotonote.elements.Block;

/**
 * The icon that stands for one attachment inside the note text. It occupies exactly one
 * character (U+FFFC), so the keyboard's delete key removes it like any letter.
 */
public class AttachmentSpan extends ImageSpan {

    public final Block block;

    public AttachmentSpan(Drawable icon, Block block) {
        super(icon, ImageSpan.ALIGN_BOTTOM);
        this.block = block;
    }
}

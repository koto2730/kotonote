package com.mugime.kotonote.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.mugime.kotonote.R;
import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.BlockType;
import com.mugime.kotonote.elements.NoteText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maps between a note's blocks and the editor's text: attachments are single U+FFFC
 * characters carrying an {@link AttachmentSpan}. The database never sees spans - only
 * blocks - so the editor can change without touching the stored format.
 */
public final class NoteEditorCodec {

    public static final String OBJECT_CHAR = "\uFFFC";

    private NoteEditorCodec() {
    }

    /** The chip drawn for an attachment: its type icon on a soft rounded square. */
    public static Drawable icon(Context context, BlockType type, int sizePx, int tintColor) {
        return icon(context, type, sizePx, tintColor, false);
    }

    /** {@code active}: an audio chip that is playing shows a stop symbol instead of the mic. */
    public static Drawable icon(Context context, BlockType type, int sizePx, int tintColor,
                                boolean active) {
        Drawable glyph = ContextCompat.getDrawable(context, iconRes(type, active)).mutate();
        glyph.setTint(tintColor);

        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.RECTANGLE);
        chip.setCornerRadius(sizePx * 0.25f);
        chip.setColor((tintColor & 0x00FFFFFF) | 0x26000000);

        int pad = sizePx / 6;
        LayerDrawable layers = new LayerDrawable(new Drawable[]{chip, glyph});
        layers.setLayerInset(1, pad, pad, pad, pad);

        int gap = Math.max(1, sizePx / 12);
        InsetDrawable spaced = new InsetDrawable(layers, gap, 0, gap, 0);
        spaced.setBounds(0, 0, sizePx + 2 * gap, sizePx);
        return spaced;
    }

    @DrawableRes
    private static int iconRes(BlockType type, boolean active) {
        switch (type) {
            case AUDIO:
                return active ? R.drawable.ic_stop : R.drawable.ic_mic;
            case VIDEO:
                return R.drawable.ic_videocam;
            case PAINT:
                return R.drawable.ic_brush;
            case IMAGE:
            default:
                return R.drawable.ic_image;
        }
    }

    /** Builds the editor text for a note. Attachments become icon characters. */
    public static Editable build(List<Block> blocks, AttachmentLook look) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        for (Block b : blocks) {
            if (b.blockType().isAttachment()) {
                int at = out.length();
                out.append(OBJECT_CHAR);
                out.setSpan(new AttachmentSpan(look.placeholder(b, false), b),
                        at, at + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (b.text != null) {
                out.append(b.text);
            }
        }
        return out;
    }

    /** Splits the editor text back into blocks, in order. */
    public static List<Block> collect(int slot, Editable text, long now) {
        AttachmentSpan[] spans = liveSpans(text);
        Arrays.sort(spans, Comparator.comparingInt(text::getSpanStart));

        List<Object> parts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int pos = 0;
        for (AttachmentSpan span : spans) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            if (start < pos) {
                continue;
            }
            parts.add(text.subSequence(pos, start).toString());
            pos = end;
            // The same file twice (a duplicated span) would be deleted with the first.
            if (span.block.filePath == null || seen.add(span.block.filePath)) {
                parts.add(span.block);
            }
        }
        parts.add(text.subSequence(pos, text.length()).toString());
        return NoteText.compose(slot, parts, now);
    }

    public static int countAttachments(Editable text) {
        return liveSpans(text).length;
    }

    /**
     * The spans whose icon character is still in the text. Deleting the character can
     * leave its span behind with zero length; that attachment is gone and must not be saved.
     */
    private static AttachmentSpan[] liveSpans(Editable text) {
        List<AttachmentSpan> live = new ArrayList<>();
        for (AttachmentSpan span : text.getSpans(0, text.length(), AttachmentSpan.class)) {
            int start = text.getSpanStart(span);
            if (text.getSpanEnd(span) - start == 1 && text.charAt(start) == '\uFFFC') {
                live.add(span);
            }
        }
        return live.toArray(new AttachmentSpan[0]);
    }

    /** Removes icon characters that carry no attachment (e.g. pasted from elsewhere). */
    public static boolean stripStray(Editable text) {
        boolean changed = false;
        for (int i = text.length() - 1; i >= 0; i--) {
            if (text.charAt(i) == '\uFFFC'
                    && text.getSpans(i, i + 1, AttachmentSpan.class).length == 0) {
                text.delete(i, i + 1);
                changed = true;
            }
        }
        return changed;
    }
}

package com.mugime.kotonote.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts between what the editor holds and a note's blocks. The editor yields an
 * ordered list of parts: {@link String} runs of text and attachment {@link Block}s.
 * A blank note (no attachments, no visible text) has no blocks at all, which is what
 * lets "has content" be a plain row-count check.
 */
public final class NoteText {

    private NoteText() {
    }

    public static List<Block> toBlocks(int slot, String text, long now) {
        return compose(slot, Collections.<Object>singletonList(text), now);
    }

    /** Builds the block list for a note from text runs (String) and attachments (Block). */
    public static List<Block> compose(int slot, List<Object> parts, long now) {
        boolean hasAttachment = false;
        boolean hasText = false;
        for (Object part : parts) {
            if (part instanceof Block) {
                hasAttachment = true;
            } else if (part instanceof String && !((String) part).trim().isEmpty()) {
                hasText = true;
            }
        }
        List<Block> blocks = new ArrayList<>();
        if (!hasAttachment && !hasText) {
            return blocks;
        }
        for (Object part : parts) {
            if (part instanceof Block) {
                blocks.add(((Block) part).copyAt(slot, blocks.size()));
            } else if (part instanceof String && !((String) part).isEmpty()) {
                blocks.add(Block.text(slot, blocks.size(), (String) part, now));
            }
        }
        return blocks;
    }

    public static String fromBlocks(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b.blockType() == BlockType.TEXT && b.text != null) {
                sb.append(b.text);
            }
        }
        return sb.toString();
    }
}

package com.mugime.kotonote.elements;

/**
 * The product constraints, in one place. Platform-neutral on purpose: an iOS port
 * should carry these numbers over unchanged.
 */
public final class NoteRules {

    /** The number of sticky notes. Fixed; there is no way to add or remove one. */
    public static final int SLOT_COUNT = 5;

    /** Attachments (audio, image, video, paint) allowed on one note. */
    public static final int MAX_ATTACHMENTS = 10;

    public static final long MAX_VIDEO_MS = 60_000L;

    public static final long MAX_AUDIO_MS = 5 * 60_000L;

    private NoteRules() {
    }

    public static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }
}

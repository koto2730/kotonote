package com.mugime.kotonote.elements;

/**
 * How big an attachment's thumbnail is drawn. Three levels, chosen per attachment:
 * small (a square tile, ten fit comfortably on a note), medium (about two per row) and
 * large (as wide as the note). Platform-neutral so an iOS port can reuse the numbers.
 */
public final class ThumbnailSpec {

    public static final int SMALL = 0;
    public static final int MEDIUM = 1;
    public static final int LARGE = 2;

    public static final int SMALL_DP = 72;
    public static final int MEDIUM_DP = 176;

    private ThumbnailSpec() {
    }

    public static int clampLevel(int level) {
        return Math.max(SMALL, Math.min(LARGE, level));
    }

    /**
     * The longest edge of a thumbnail, in pixels. {@code contentWidthPx} is the width of the
     * note's text area; medium and large never exceed it.
     */
    public static int edgePx(int level, float density, int contentWidthPx) {
        int small = Math.round(SMALL_DP * density);
        switch (clampLevel(level)) {
            case SMALL:
                return small;
            case MEDIUM:
                return Math.max(small, Math.min(Math.round(MEDIUM_DP * density), contentWidthPx));
            default:
                return Math.max(small, contentWidthPx);
        }
    }

    /**
     * The drawn size {width, height} for a source picture. Small is always a square (the
     * picture is cropped to fit); medium and large keep the picture's proportions, with the
     * long side equal to {@code edgePx}.
     */
    public static int[] outputSize(int level, int srcW, int srcH, int edgePx) {
        if (clampLevel(level) == SMALL || srcW <= 0 || srcH <= 0) {
            return new int[]{edgePx, edgePx};
        }
        float scale = (float) edgePx / Math.max(srcW, srcH);
        return new int[]{
                Math.max(1, Math.round(srcW * scale)),
                Math.max(1, Math.round(srcH * scale))};
    }
}

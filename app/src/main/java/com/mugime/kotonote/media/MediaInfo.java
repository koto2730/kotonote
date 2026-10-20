package com.mugime.kotonote.media;

import android.media.MediaMetadataRetriever;

import java.io.File;

public final class MediaInfo {

    private MediaInfo() {
    }

    /** Length of an audio or video file in ms, or 0 if it cannot be read. */
    public static long durationMs(File file) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(file.getAbsolutePath());
            String value = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (RuntimeException e) {
            return 0;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // Nothing to do.
            }
        }
    }
}

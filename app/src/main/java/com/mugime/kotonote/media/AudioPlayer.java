package com.mugime.kotonote.media;

import android.media.MediaPlayer;

import java.io.File;
import java.io.IOException;

/** Plays one audio attachment at a time. */
public class AudioPlayer {

    private MediaPlayer player;

    /** Starts playing; {@code onDone} runs when it ends on its own. Throws if it cannot play. */
    public void play(File file, Runnable onDone) throws IOException {
        stop();
        MediaPlayer p = new MediaPlayer();
        try {
            p.setDataSource(file.getAbsolutePath());
            p.setOnCompletionListener(mp -> {
                if (player == mp) {
                    stop();
                    onDone.run();
                }
            });
            p.prepare();
            p.start();
        } catch (IOException | RuntimeException e) {
            p.release();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        player = p;
    }

    public void stop() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}

package com.mugime.kotonote.media;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;
import java.io.IOException;

/** Records AAC audio (.m4a) from the microphone into a file. One recording at a time. */
public class AudioRecorder {

    private final Context context;
    private MediaRecorder recorder;
    private File file;
    private long startedAt;

    public AudioRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isRecording() {
        return recorder != null;
    }

    @SuppressWarnings("deprecation")
    public void start(File target) throws IOException {
        MediaRecorder r = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new MediaRecorder(context) : new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioSamplingRate(44100);
            r.setAudioEncodingBitRate(64000);
            r.setOutputFile(target.getAbsolutePath());
            r.prepare();
            r.start();
        } catch (IOException | RuntimeException e) {
            r.release();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        recorder = r;
        file = target;
        startedAt = SystemClock.elapsedRealtime();
    }

    public long elapsedMs() {
        return recorder == null ? 0 : SystemClock.elapsedRealtime() - startedAt;
    }

    /** Stops and returns the recorded length in ms, or -1 if nothing usable was recorded. */
    public long stop() {
        if (recorder == null) {
            return -1;
        }
        long length = elapsedMs();
        boolean ok = true;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // Thrown when stopped immediately after start: no audio data.
            ok = false;
        }
        release();
        return ok ? length : -1;
    }

    /** Stops and deletes the file. */
    public void cancel() {
        if (recorder == null) {
            return;
        }
        File target = file;
        try {
            recorder.stop();
        } catch (RuntimeException ignored) {
            // Nothing recorded; the file is deleted anyway.
        }
        release();
        //noinspection ResultOfMethodCallIgnored
        target.delete();
    }

    private void release() {
        recorder.release();
        recorder = null;
    }
}

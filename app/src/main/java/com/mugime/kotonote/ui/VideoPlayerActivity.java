package com.mugime.kotonote.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.mugime.kotonote.R;

/** Full-screen video player. Starts playing at once; tap for the controls, Back to close. */
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        VideoView video = new VideoView(this);
        video.setContentDescription(getString(R.string.attachment_video));
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
        setContentView(root);

        MediaController controller = new MediaController(this);
        controller.setAnchorView(video);
        video.setMediaController(controller);
        video.setOnPreparedListener(mp -> video.start());
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, R.string.play_failed, Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        video.setVideoPath(path);
    }
}

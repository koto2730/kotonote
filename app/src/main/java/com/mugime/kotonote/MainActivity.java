package com.mugime.kotonote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.mugime.kotonote.data.NoteRepository;
import com.mugime.kotonote.databinding.ActivityMainBinding;
import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.BlockType;
import com.mugime.kotonote.elements.NoteRules;
import com.mugime.kotonote.files.AttachmentStore;
import com.mugime.kotonote.files.ShareHelper;
import com.mugime.kotonote.media.AudioPlayer;
import com.mugime.kotonote.media.AudioRecorder;
import com.mugime.kotonote.media.MediaInfo;
import com.mugime.kotonote.ui.AttachmentLook;
import com.mugime.kotonote.ui.AttachmentSpan;
import com.mugime.kotonote.ui.CaptureVideoContract;
import com.mugime.kotonote.ui.ImageViewerActivity;
import com.mugime.kotonote.ui.NoteEditorCodec;
import com.mugime.kotonote.ui.PaintActivity;
import com.mugime.kotonote.ui.ThumbnailLoader;
import com.mugime.kotonote.ui.VideoPlayerActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The whole app: five sticky notes behind five tabs. Whatever is typed is written to
 * the database a moment later (and always on {@code onStop}), so closing or killing
 * the app never loses a note. Attachments are icons inside the text; deleting the icon
 * character deletes the attachment.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "kotonote";
    private static final String PREF_SLOT = "slot";
    private static final String STATE_PENDING_PHOTO = "pending_photo";
    private static final String STATE_PENDING_VIDEO = "pending_video";
    private static final String STATE_PENDING_PAINT = "pending_paint";
    private static final String STATE_PENDING_PAINT_OLD = "pending_paint_old";
    /** Camera apps may overshoot the requested video length slightly. */
    private static final long VIDEO_TOLERANCE_MS = 1500L;
    private static final long TICK_MS = 250L;
    private static final long SAVE_DELAY_MS = 500L;

    private ActivityMainBinding binding;
    private NoteRepository repo;
    private AttachmentStore store;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TextView[] dots = new TextView[NoteRules.SLOT_COUNT];
    private final boolean[] filled = new boolean[NoteRules.SLOT_COUNT];
    private int[] noteColors;

    private int currentSlot;
    /** The slot whose text is currently in the editor; -1 while a load is in flight. */
    private int loadedSlot = -1;
    /** True while we change the editor text ourselves, so it is not mistaken for typing. */
    private boolean suppressWatcher;
    private boolean menuOpen;
    /** Relative path of the photo the camera is currently writing, if any. */
    private String pendingPhoto;
    /** Relative path of the video the camera is currently writing, if any. */
    private String pendingVideo;
    /** Relative path of the drawing PaintActivity will write, and the one it replaces (re-edit). */
    private String pendingPaint;
    private String pendingPaintOld;

    private AudioRecorder recorder;
    private final AudioPlayer player = new AudioPlayer();
    private int recordSlot = -1;
    private String recordPath;
    /** The audio attachment that is playing right now, if any. */
    private Block playingBlock;

    private AttachmentLook look;
    private ThumbnailLoader thumbs;
    /** Where the finger last went down on the editor, for long-press hit testing. */
    private float lastDownX;
    private float lastDownY;

    /** Pictures and videos shared into the app, waiting for their note to finish loading. */
    private static final class PendingImport {
        final int slot;
        final List<Uri> uris;

        PendingImport(int slot, List<Uri> uris) {
            this.slot = slot;
            this.uris = uris;
        }
    }

    private PendingImport pendingImport;

    private final Runnable saveRunnable = this::saveNow;
    private final Runnable recordTick = new Runnable() {
        @Override
        public void run() {
            if (recorder == null || !recorder.isRecording()) {
                return;
            }
            long elapsed = recorder.elapsedMs();
            long seconds = elapsed / 1000;
            binding.recordingTime.setText(String.format(
                    java.util.Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60));
            if (elapsed >= NoteRules.MAX_AUDIO_MS) {
                finishRecording();
            } else {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPicked);
    private final ActivityResultLauncher<Uri> takePhoto =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), this::onPhotoTaken);
    private final ActivityResultLauncher<Uri> takeVideo =
            registerForActivityResult(new CaptureVideoContract(), this::onVideoTaken);
    private final ActivityResultLauncher<Intent> paint = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> onPainted(result.getResultCode() == RESULT_OK));
    private final ActivityResultLauncher<String> requestMic =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    beginRecording();
                } else {
                    Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repo = new NoteRepository(this);
        store = new AttachmentStore(this);
        recorder = new AudioRecorder(this);
        float density = getResources().getDisplayMetrics().density;
        thumbs = new ThumbnailLoader(store, density);
        look = new AttachmentLook(this, getColor(R.color.note_text), iconSize(), this::contentWidthPx);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null) {
            pendingPhoto = savedInstanceState.getString(STATE_PENDING_PHOTO);
            pendingVideo = savedInstanceState.getString(STATE_PENDING_VIDEO);
            pendingPaint = savedInstanceState.getString(STATE_PENDING_PAINT);
            pendingPaintOld = savedInstanceState.getString(STATE_PENDING_PAINT_OLD);
        }

        TypedArray colors = getResources().obtainTypedArray(R.array.note_colors);
        noteColors = new int[NoteRules.SLOT_COUNT];
        for (int i = 0; i < noteColors.length; i++) {
            noteColors[i] = colors.getColor(i, 0);
        }
        colors.recycle();

        setUpWindow();
        setUpTabs();
        setUpEditor();
        setUpButtons();

        int slot = prefs.getInt(PREF_SLOT, 0);
        currentSlot = NoteRules.isValidSlot(slot) ? slot : 0;
        TabLayout.Tab tab = binding.tabs.getTabAt(currentSlot);
        if (tab != null) {
            tab.select();
        }
        showCurrent();
        repo.filledSlots(set -> runOnUiThread(() -> applyFilled(set)));
        if (savedInstanceState == null) {
            handleShareIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void setUpWindow() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(!night);

        final int pad = dp(20);
        final int fabArea = dp(96);
        final int fabMargin = dp(16);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            binding.header.setPadding(bars.left, bars.top, bars.right, 0);
            binding.editor.setPadding(pad + bars.left, pad, pad + bars.right, fabArea + bottom);

            ViewGroup.MarginLayoutParams clear =
                    (ViewGroup.MarginLayoutParams) binding.fabClear.getLayoutParams();
            clear.leftMargin = fabMargin + bars.left;
            clear.bottomMargin = fabMargin + bottom;
            binding.fabClear.setLayoutParams(clear);

            ViewGroup.MarginLayoutParams menu =
                    (ViewGroup.MarginLayoutParams) binding.fabMenu.getLayoutParams();
            menu.rightMargin = fabMargin + bars.right;
            menu.bottomMargin = fabMargin + bottom;
            binding.fabMenu.setLayoutParams(menu);

            ViewGroup.MarginLayoutParams bar =
                    (ViewGroup.MarginLayoutParams) binding.recordingBar.getLayoutParams();
            bar.bottomMargin = fabMargin + bottom;
            binding.recordingBar.setLayoutParams(bar);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setUpTabs() {
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < NoteRules.SLOT_COUNT; i++) {
            View view = inflater.inflate(R.layout.tab_note, binding.tabs, false);
            dots[i] = view.findViewById(R.id.dot);
            dots[i].setText(String.valueOf(i + 1));
            TabLayout.Tab tab = binding.tabs.newTab().setCustomView(view);
            binding.tabs.addTab(tab, false);
            renderDot(i);
        }
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switchTo(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setUpEditor() {
        binding.editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressWatcher || loadedSlot != currentSlot) {
                    return;
                }
                suppressWatcher = true;
                NoteEditorCodec.stripStray(s);
                suppressWatcher = false;
                contentChanged();
            }
        });

        GestureDetector taps = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                openAttachmentAt(e.getX(), e.getY());
                return false;
            }
        });
        binding.editor.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastDownX = event.getX();
                lastDownY = event.getY();
            }
            taps.onTouchEvent(event);
            return false;
        });
        // Long-pressing a picture or video chooses its thumbnail size. Returning true keeps
        // the editor from starting a text selection on the icon character.
        binding.editor.setOnLongClickListener(v -> {
            AttachmentSpan span = spanAt(lastDownX, lastDownY);
            if (span != null && AttachmentLook.isVisual(span.block)) {
                showSizeMenu(span.block);
                return true;
            }
            return false;
        });
    }

    private void setUpButtons() {
        binding.fabClear.setOnClickListener(v -> confirmClear());
        binding.btnShare.setOnClickListener(v -> share());
        binding.fabAdd.setOnClickListener(v -> setMenuOpen(!menuOpen));
        binding.fabGallery.setOnClickListener(v -> {
            setMenuOpen(false);
            if (canAddAttachment()) {
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build());
            }
        });
        binding.fabCamera.setOnClickListener(v -> {
            setMenuOpen(false);
            if (canAddAttachment()) {
                launchCamera();
            }
        });
        binding.fabVideo.setOnClickListener(v -> {
            setMenuOpen(false);
            if (canAddAttachment()) {
                launchVideo();
            }
        });
        binding.fabPaint.setOnClickListener(v -> {
            setMenuOpen(false);
            if (canAddAttachment()) {
                launchPaint(null);
            }
        });
        binding.fabMic.setOnClickListener(v -> {
            setMenuOpen(false);
            startRecording();
        });
        binding.recordingStop.setOnClickListener(v -> finishRecording());
        binding.recordingCancel.setOnClickListener(v -> cancelRecording());
    }

    private void setMenuOpen(boolean open) {
        menuOpen = open;
        int visibility = open ? View.VISIBLE : View.GONE;
        binding.fabGallery.setVisibility(visibility);
        binding.fabCamera.setVisibility(visibility);
        binding.fabVideo.setVisibility(visibility);
        binding.fabMic.setVisibility(visibility);
        binding.fabPaint.setVisibility(visibility);
        binding.fabAdd.setImageResource(open ? R.drawable.ic_close : R.drawable.ic_add);
        binding.fabAdd.setContentDescription(
                getString(open ? R.string.action_close_menu : R.string.action_add));
    }

    // ---- switching, loading, saving ------------------------------------------------

    private void switchTo(int slot) {
        if (slot == currentSlot) {
            return;
        }
        finishRecording();
        stopPlayback();
        saveNow();
        setMenuOpen(false);
        currentSlot = slot;
        showCurrent();
    }

    /** Paints the current note's colour and loads its content into the editor. */
    private void showCurrent() {
        final int slot = currentSlot;
        binding.surface.setBackgroundColor(noteColors[slot]);
        loadedSlot = -1;
        binding.editor.setEnabled(false);
        repo.load(slot, blocks -> runOnUiThread(() -> {
            if (slot != currentSlot || isDestroyed()) {
                return;
            }
            Editable content = NoteEditorCodec.build(blocks, look);
            setEditorContent(content);
            // The cursor sits at the end, which scrolls a tall note (thumbnails make them tall)
            // to its bottom; a sticky note should open at the top.
            binding.editor.postDelayed(() -> {
                if (!isDestroyed() && loadedSlot == slot) {
                    binding.editor.scrollTo(0, 0);
                }
            }, 100);
            loadedSlot = slot;
            binding.editor.setEnabled(true);
            setFilled(slot, !blocks.isEmpty());
            updateShareEnabled();
            loadThumbnails();
            if (pendingImport != null && pendingImport.slot == slot) {
                runPendingImport();
            }
        }));
    }

    private void setEditorContent(CharSequence content) {
        suppressWatcher = true;
        binding.editor.setText(content);
        binding.editor.setSelection(binding.editor.length());
        suppressWatcher = false;
    }

    private void contentChanged() {
        Editable text = binding.editor.getText();
        setFilled(currentSlot, hasContent(text));
        updateShareEnabled();
        handler.removeCallbacks(saveRunnable);
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS);
    }

    private static boolean hasContent(Editable text) {
        return !text.toString().trim().isEmpty();
    }

    private void saveNow() {
        handler.removeCallbacks(saveRunnable);
        if (loadedSlot != currentSlot || loadedSlot < 0) {
            return;
        }
        repo.save(currentSlot, currentBlocks());
    }

    private List<Block> currentBlocks() {
        return NoteEditorCodec.collect(
                currentSlot, binding.editor.getText(), System.currentTimeMillis());
    }

    // ---- clear ---------------------------------------------------------------------

    private void confirmClear() {
        final int slot = currentSlot;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_title)
                .setMessage(getString(R.string.clear_message, slot + 1))
                .setNegativeButton(R.string.clear_cancel, null)
                .setPositiveButton(R.string.clear_confirm, (d, w) -> clearCurrent(slot))
                .show();
    }

    private void clearCurrent(int slot) {
        if (slot != currentSlot) {
            return;
        }
        cancelRecording();
        stopPlayback();
        handler.removeCallbacks(saveRunnable);
        loadedSlot = -1;
        setEditorContent("");
        setFilled(slot, false);
        updateShareEnabled();
        repo.clear(slot, () -> runOnUiThread(() -> {
            if (slot == currentSlot) {
                loadedSlot = slot;
            }
        }));
    }

    // ---- attachments ---------------------------------------------------------------

    private boolean canAddAttachment() {
        if (loadedSlot != currentSlot) {
            return false;
        }
        if (NoteEditorCodec.countAttachments(binding.editor.getText()) >= NoteRules.MAX_ATTACHMENTS) {
            Toast.makeText(this, getString(R.string.limit_reached, NoteRules.MAX_ATTACHMENTS),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void launchCamera() {
        File file = store.newFile(currentSlot, "jpg");
        pendingPhoto = store.relative(file);
        Uri uri = FileProvider.getUriForFile(this, ShareHelper.authority(this), file);
        takePhoto.launch(uri);
    }

    private void onPhotoTaken(Boolean success) {
        String rel = pendingPhoto;
        pendingPhoto = null;
        if (rel == null) {
            return;
        }
        File file = store.resolve(rel);
        if (Boolean.TRUE.equals(success) && file.length() > 0) {
            Block block = Block.attachment(
                    BlockType.IMAGE, rel, "image/jpeg", System.currentTimeMillis());
            addAttachment(slotOf(rel), block);
        } else {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private void onPicked(Uri uri) {
        if (uri != null) {
            importUri(uri, currentSlot);
        }
    }

    /** Copies a picture or video into the note's storage, then puts it into the note. */
    private void importUri(Uri uri, int slot) {
        repo.io(() -> {
            File file = null;
            try {
                String mime = getContentResolver().getType(uri);
                if (mime != null && !mime.startsWith("image/") && !mime.startsWith("video/")) {
                    throw new IOException("not a picture or video: " + mime);
                }
                boolean video = mime != null && mime.startsWith("video/");
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                file = store.newFile(slot, ext == null ? (video ? "mp4" : "jpg") : ext);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(file)) {
                    if (in == null) {
                        throw new IOException("no stream");
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                }
                Block block;
                if (video) {
                    long duration = MediaInfo.durationMs(file);
                    if (duration > NoteRules.MAX_VIDEO_MS + VIDEO_TOLERANCE_MS) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        runOnUiThread(this::toastVideoTooLong);
                        return;
                    }
                    block = Block.attachment(BlockType.VIDEO, store.relative(file), mime,
                            System.currentTimeMillis());
                    block.durationMs = duration;
                } else {
                    block = Block.attachment(BlockType.IMAGE, store.relative(file),
                            mime == null ? "image/jpeg" : mime, System.currentTimeMillis());
                }
                final Block result = block;
                runOnUiThread(() -> addAttachment(slot, result));
            } catch (IOException | RuntimeException e) {
                if (file != null) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                runOnUiThread(() -> Toast.makeText(
                        this, R.string.import_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void toastVideoTooLong() {
        Toast.makeText(this, getString(R.string.video_too_long,
                (int) (NoteRules.MAX_VIDEO_MS / 1000)), Toast.LENGTH_SHORT).show();
    }

    private void launchVideo() {
        File file = store.newFile(currentSlot, "mp4");
        pendingVideo = store.relative(file);
        Uri uri = FileProvider.getUriForFile(this, ShareHelper.authority(this), file);
        takeVideo.launch(uri);
    }

    private void onVideoTaken(Boolean success) {
        String rel = pendingVideo;
        pendingVideo = null;
        if (rel == null) {
            return;
        }
        File file = store.resolve(rel);
        if (!Boolean.TRUE.equals(success) || file.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return;
        }
        final int slot = slotOf(rel);
        repo.io(() -> {
            long duration = MediaInfo.durationMs(file);
            if (duration > NoteRules.MAX_VIDEO_MS + VIDEO_TOLERANCE_MS) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                runOnUiThread(this::toastVideoTooLong);
                return;
            }
            Block block = Block.attachment(BlockType.VIDEO, rel, "video/mp4",
                    System.currentTimeMillis());
            block.durationMs = duration;
            runOnUiThread(() -> addAttachment(slot, block));
        });
    }

    // ---- drawing ---------------------------------------------------------------------

    /** Opens the paint screen: blank when {@code editing} is null, else continuing that drawing. */
    private void launchPaint(Block editing) {
        File out = store.newFile(currentSlot, "png");
        pendingPaint = store.relative(out);
        pendingPaintOld = editing == null ? null : editing.filePath;
        Intent intent = new Intent(this, PaintActivity.class)
                .putExtra(PaintActivity.EXTRA_OUTPUT, out.getAbsolutePath());
        if (editing != null && editing.filePath != null) {
            intent.putExtra(PaintActivity.EXTRA_BASE,
                    store.resolve(editing.filePath).getAbsolutePath());
        }
        paint.launch(intent);
    }

    private void onPainted(boolean ok) {
        String rel = pendingPaint;
        String old = pendingPaintOld;
        pendingPaint = null;
        pendingPaintOld = null;
        if (rel == null) {
            return;
        }
        File file = store.resolve(rel);
        if (!ok || file.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return;
        }
        int slot = slotOf(rel);
        if (old == null) {
            addAttachment(slot, Block.attachment(
                    BlockType.PAINT, rel, "image/png", System.currentTimeMillis()));
            return;
        }
        // Re-edit: the existing icon now points at the new picture; saving drops the old file.
        Editable text = binding.editor.getText();
        if (slot == currentSlot && loadedSlot == slot) {
            for (AttachmentSpan span : text.getSpans(0, text.length(), AttachmentSpan.class)) {
                if (old.equals(span.block.filePath)) {
                    span.block.filePath = rel;
                    refreshAttachment(span.block, false);
                    contentChanged();
                    saveNow();
                    return;
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ---- audio recording and playback ----------------------------------------------

    private void startRecording() {
        if (recorder.isRecording() || !canAddAttachment()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            beginRecording();
        } else {
            requestMic.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void beginRecording() {
        if (recorder.isRecording() || loadedSlot != currentSlot) {
            return;
        }
        stopPlayback();
        File file = store.newFile(currentSlot, "m4a");
        try {
            recorder.start(file);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        recordSlot = currentSlot;
        recordPath = store.relative(file);
        binding.recordingTime.setText("0:00");
        binding.recordingBar.setVisibility(View.VISIBLE);
        handler.postDelayed(recordTick, TICK_MS);
    }

    /** Stops recording and puts the audio icon into the note. No-op when not recording. */
    private void finishRecording() {
        if (!recorder.isRecording()) {
            return;
        }
        handler.removeCallbacks(recordTick);
        binding.recordingBar.setVisibility(View.GONE);
        long duration = recorder.stop();
        String rel = recordPath;
        int slot = recordSlot;
        recordPath = null;
        recordSlot = -1;
        if (duration <= 0) {
            store.delete(rel);
            return;
        }
        Block block = Block.attachment(BlockType.AUDIO, rel, "audio/mp4", System.currentTimeMillis());
        block.durationMs = Math.min(duration, NoteRules.MAX_AUDIO_MS);
        addAttachment(slot, block);
    }

    private void cancelRecording() {
        if (!recorder.isRecording()) {
            return;
        }
        handler.removeCallbacks(recordTick);
        binding.recordingBar.setVisibility(View.GONE);
        recorder.cancel();
        recordPath = null;
        recordSlot = -1;
    }

    private void toggleAudio(AttachmentSpan span) {
        boolean wasThis = playingBlock == span.block;
        stopPlayback();
        if (wasThis || span.block.filePath == null) {
            return;
        }
        try {
            player.play(store.resolve(span.block.filePath), this::stopPlayback);
        } catch (IOException e) {
            Toast.makeText(this, R.string.play_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        playingBlock = span.block;
        setAudioIcon(span.block, true);
    }

    private void stopPlayback() {
        player.stop();
        if (playingBlock != null) {
            Block block = playingBlock;
            playingBlock = null;
            setAudioIcon(block, false);
        }
    }

    /** Swaps the icon of the span that carries this block (mic or stop). */
    private void setAudioIcon(Block block, boolean active) {
        replaceDrawable(block, look.placeholder(block, active));
    }

    private static int slotOf(String relativePath) {
        return Integer.parseInt(relativePath.substring(0, relativePath.indexOf('/')));
    }

    /** Puts the attachment's icon into the text at the cursor. Discards the file if it cannot. */
    private void addAttachment(int slot, Block block) {
        Editable text = binding.editor.getText();
        boolean fits = !isDestroyed() && slot == currentSlot && loadedSlot == slot
                && NoteEditorCodec.countAttachments(text) < NoteRules.MAX_ATTACHMENTS;
        if (!fits) {
            if (block.filePath != null) {
                store.delete(block.filePath);
            }
            if (!isDestroyed() && slot == currentSlot && loadedSlot == slot) {
                Toast.makeText(this, getString(R.string.limit_reached, NoteRules.MAX_ATTACHMENTS),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        int start = binding.editor.getSelectionStart();
        int end = binding.editor.getSelectionEnd();
        if (start < 0 || end < 0) {
            start = end = text.length();
        }
        int from = Math.min(start, end);
        int to = Math.max(start, end);

        suppressWatcher = true;
        text.replace(from, to, NoteEditorCodec.OBJECT_CHAR);
        text.setSpan(new AttachmentSpan(look.placeholder(block, false), block),
                from, from + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        suppressWatcher = false;
        binding.editor.setSelection(from + 1);
        contentChanged();
        saveNow();
        loadThumbnail(block);
    }

    /** The attachment icon or thumbnail under a point in the editor, or null. */
    private AttachmentSpan spanAt(float px, float py) {
        Layout layout = binding.editor.getLayout();
        Editable text = binding.editor.getText();
        if (layout == null || text.length() == 0) {
            return null;
        }
        float x = px - binding.editor.getTotalPaddingLeft() + binding.editor.getScrollX();
        float y = py - binding.editor.getTotalPaddingTop() + binding.editor.getScrollY();
        if (y < 0) {
            return null;
        }
        int line = layout.getLineForVertical((int) y);
        if (y > layout.getLineBottom(line)) {
            return null;
        }
        int offset = layout.getOffsetForHorizontal(line, x);
        AttachmentSpan[] spans = text.getSpans(
                Math.max(0, offset - 1), Math.min(text.length(), offset + 1), AttachmentSpan.class);
        for (AttachmentSpan span : spans) {
            int start = text.getSpanStart(span);
            if (layout.getLineForOffset(start) != line) {
                continue;
            }
            // The icon occupies its own width starting at the character's position.
            float x0 = layout.getPrimaryHorizontal(start);
            float x1 = x0 + span.getDrawable().getBounds().width();
            if (x >= x0 && x <= x1) {
                return span;
            }
        }
        return null;
    }

    /** Opens the attachment whose icon sits under the tapped point, if there is one. */
    private void openAttachmentAt(float px, float py) {
        AttachmentSpan span = spanAt(px, py);
        if (span != null) {
            openAttachment(span);
        }
    }

    private void openAttachment(AttachmentSpan span) {
        Block block = span.block;
        if (block.filePath == null) {
            return;
        }
        switch (block.blockType()) {
            case AUDIO:
                toggleAudio(span);
                break;
            case PAINT:
                launchPaint(block);
                break;
            case VIDEO:
                startActivity(new Intent(this, VideoPlayerActivity.class).putExtra(
                        VideoPlayerActivity.EXTRA_PATH, store.resolve(block.filePath).getAbsolutePath()));
                break;
            case IMAGE:
                startActivity(new Intent(this, ImageViewerActivity.class).putExtra(
                        ImageViewerActivity.EXTRA_PATH, store.resolve(block.filePath).getAbsolutePath()));
                break;
            default:
                break;
        }
    }

    // ---- thumbnails ----------------------------------------------------------------

    /** Width of the note's text area, which the large thumbnail size fills. */
    private int contentWidthPx() {
        int width = binding.editor.getWidth();
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        return Math.max(dp(120),
                width - binding.editor.getPaddingLeft() - binding.editor.getPaddingRight());
    }

    private void loadThumbnails() {
        Editable text = binding.editor.getText();
        for (AttachmentSpan span : text.getSpans(0, text.length(), AttachmentSpan.class)) {
            loadThumbnail(span.block);
        }
    }

    /** Fetches the thumbnail of a picture or video and swaps it in for its placeholder. */
    private void loadThumbnail(Block block) {
        if (!AttachmentLook.isVisual(block) || block.filePath == null) {
            return;
        }
        final String path = block.filePath;
        final int level = block.sizeLevel;
        final int edge = look.edgePx(block);
        thumbs.load(block, edge, bitmap -> {
            // The attachment may have been deleted, resized or redrawn while this was loading.
            if (bitmap == null || isDestroyed() || !path.equals(block.filePath)
                    || level != block.sizeLevel) {
                return;
            }
            replaceDrawable(block, look.thumbnail(bitmap));
        });
    }

    /**
     * Shows the attachment afresh after its file or size changed: the cached thumbnail at once
     * if there is one, otherwise a placeholder of the right size until it is made.
     */
    private void refreshAttachment(Block block, boolean active) {
        Bitmap cached = AttachmentLook.isVisual(block) ? thumbs.cached(block, look.edgePx(block)) : null;
        replaceDrawable(block, cached != null ? look.thumbnail(cached) : look.placeholder(block, active));
        if (cached == null) {
            loadThumbnail(block);
        }
    }

    /** Swaps the picture drawn for the span that carries {@code block}. */
    private void replaceDrawable(Block block, Drawable drawable) {
        Editable text = binding.editor.getText();
        for (AttachmentSpan old : text.getSpans(0, text.length(), AttachmentSpan.class)) {
            if (old.block != block) {
                continue;
            }
            int start = text.getSpanStart(old);
            int end = text.getSpanEnd(old);
            if (end - start != 1) {
                return;
            }
            suppressWatcher = true;
            text.removeSpan(old);
            text.setSpan(new AttachmentSpan(drawable, block),
                    start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            suppressWatcher = false;
            return;
        }
    }

    private void showSizeMenu(Block block) {
        String[] labels = {
                getString(R.string.thumb_small),
                getString(R.string.thumb_medium),
                getString(R.string.thumb_large)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.thumb_size_title)
                .setSingleChoiceItems(labels, block.sizeLevel, (dialog, which) -> {
                    dialog.dismiss();
                    if (which != block.sizeLevel) {
                        block.sizeLevel = which;
                        refreshAttachment(block, false);
                        contentChanged();
                        saveNow();
                    }
                })
                .show();
    }

    // ---- shared into the app -------------------------------------------------------

    /** A picture or video shared from another app (a screenshot, say): ask which note gets it. */
    private void handleShareIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = streamExtra(intent);
            if (uri != null) {
                uris.add(uri);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            uris.addAll(streamListExtra(intent));
        } else {
            return;
        }
        // Each share is handled once, even if the activity is recreated.
        setIntent(new Intent(Intent.ACTION_MAIN));
        if (uris.isEmpty()) {
            return;
        }
        repo.filledSlots(set -> runOnUiThread(() -> {
            if (!isDestroyed()) {
                showSlotPicker(uris, set);
            }
        }));
    }

    @SuppressWarnings("deprecation")
    private static Uri streamExtra(Intent intent) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                : (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    @SuppressWarnings("deprecation")
    private static List<Uri> streamListExtra(Intent intent) {
        List<Uri> list = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class)
                : intent.<Uri>getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        return list == null ? new ArrayList<>() : list;
    }

    /** Five coloured tabs to pick from; the note you were on is ringed. */
    private void showSlotPicker(List<Uri> uris, Set<Integer> filledSlots) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(8), dp(16), dp(8), dp(8));
        int onSurface = MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSurface);
        final androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];

        for (int i = 0; i < NoteRules.SLOT_COUNT; i++) {
            final int slot = i;
            boolean isFilled = filledSlots.contains(i);
            TextView dot = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            lp.setMargins(dp(6), 0, dp(6), 0);
            dot.setLayoutParams(lp);
            dot.setGravity(Gravity.CENTER);
            dot.setText(String.valueOf(i + 1));
            dot.setTextSize(16);
            dot.setTypeface(dot.getTypeface(), android.graphics.Typeface.BOLD);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if (isFilled) {
                bg.setColor(noteColors[i]);
                dot.setTextColor(getColor(R.color.note_text));
            } else {
                bg.setColor(0x00000000);
                bg.setStroke(dp(2), noteColors[i]);
                dot.setTextColor(onSurface);
            }
            if (i == currentSlot) {
                bg.setStroke(dp(3), onSurface);
            }
            dot.setBackground(bg);
            dot.setContentDescription(getString(
                    isFilled ? R.string.tab_description_filled : R.string.tab_description, i + 1));
            dot.setOnClickListener(v -> {
                dialog[0].dismiss();
                chooseSlotAndImport(slot, uris);
            });
            row.addView(dot);
        }
        dialog[0] = new MaterialAlertDialogBuilder(this)
                .setTitle(getResources().getQuantityString(
                        R.plurals.share_in_title, uris.size(), uris.size()))
                .setView(row)
                .setNegativeButton(R.string.clear_cancel, null)
                .show();
    }

    private void chooseSlotAndImport(int slot, List<Uri> uris) {
        pendingImport = new PendingImport(slot, uris);
        if (slot != currentSlot) {
            TabLayout.Tab tab = binding.tabs.getTabAt(slot);
            if (tab != null) {
                tab.select();   // loads that note; runPendingImport() follows
            }
        } else if (loadedSlot == slot) {
            runPendingImport();
        }
        // else: this note is still loading; showCurrent() will run the import when it is ready.
    }

    private void runPendingImport() {
        PendingImport pending = pendingImport;
        pendingImport = null;
        if (pending == null || loadedSlot != pending.slot) {
            return;
        }
        int room = NoteRules.MAX_ATTACHMENTS
                - NoteEditorCodec.countAttachments(binding.editor.getText());
        if (room <= 0) {
            Toast.makeText(this, getString(R.string.limit_reached, NoteRules.MAX_ATTACHMENTS),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (pending.uris.size() > room) {
            Toast.makeText(this, getString(R.string.share_in_skipped, room),
                    Toast.LENGTH_SHORT).show();
        }
        // Shared pictures go at the end of the note, in the order they were shared.
        binding.editor.setSelection(binding.editor.length());
        for (Uri uri : pending.uris.subList(0, Math.min(room, pending.uris.size()))) {
            importUri(uri, pending.slot);
        }
    }

    // ---- share ---------------------------------------------------------------------

    private void share() {
        if (loadedSlot != currentSlot) {
            return;
        }
        Intent chooser = ShareHelper.buildChooser(this, currentBlocks(), store);
        if (chooser != null) {
            startActivity(chooser);
        }
    }

    private void updateShareEnabled() {
        boolean has = hasContent(binding.editor.getText());
        binding.btnShare.setEnabled(has);
        binding.btnShare.setAlpha(has ? 1f : 0.4f);
    }

    // ---- tabs ----------------------------------------------------------------------

    private void applyFilled(Set<Integer> slots) {
        for (int i = 0; i < NoteRules.SLOT_COUNT; i++) {
            // The editor is the freshest truth for the open note.
            if (i != currentSlot) {
                setFilled(i, slots.contains(i));
            }
        }
    }

    private void setFilled(int slot, boolean value) {
        if (filled[slot] != value) {
            filled[slot] = value;
            renderDot(slot);
        }
    }

    private void renderDot(int slot) {
        TextView dot = dots[slot];
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (filled[slot]) {
            bg.setColor(noteColors[slot]);
            dot.setTextColor(getColor(R.color.note_text));
        } else {
            bg.setColor(0x00000000);
            bg.setStroke(dp(2), noteColors[slot]);
            dot.setTextColor(getColor(R.color.white));
        }
        dot.setBackground(bg);
        dot.setContentDescription(getString(
                filled[slot] ? R.string.tab_description_filled : R.string.tab_description, slot + 1));
    }

    private int iconSize() {
        return Math.round(binding.editor.getTextSize() * 1.5f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_PHOTO, pendingPhoto);
        outState.putString(STATE_PENDING_VIDEO, pendingVideo);
        outState.putString(STATE_PENDING_PAINT, pendingPaint);
        outState.putString(STATE_PENDING_PAINT_OLD, pendingPaintOld);
    }

    @Override
    protected void onStop() {
        finishRecording();
        stopPlayback();
        saveNow();
        prefs.edit().putInt(PREF_SLOT, currentSlot).apply();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        player.stop();
        if (recorder.isRecording()) {
            recorder.cancel();
        }
        super.onDestroy();
    }
}

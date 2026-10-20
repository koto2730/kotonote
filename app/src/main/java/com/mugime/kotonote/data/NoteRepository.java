package com.mugime.kotonote.data;

import android.content.Context;

import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.files.AttachmentStore;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All database access goes through one single-threaded executor, which both keeps
 * Room off the main thread and keeps writes in the order they were requested (an
 * {@code onStop} save is always applied before the next {@code onCreate} load).
 * Callbacks run on the executor thread; post to the main thread before touching views.
 */
public class NoteRepository {

    public interface Callback<T> {
        void onResult(T value);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long SWEEP_MIN_AGE_MS = 10 * 60_000L;

    private final NoteDao dao;
    private final AttachmentStore store;

    public NoteRepository(Context context) {
        this.dao = KotonoteDatabase.getInstance(context).noteDao();
        this.store = new AttachmentStore(context);
    }

    /** Runs work on the same ordered background thread as the database. */
    public void io(Runnable work) {
        IO.execute(work);
    }

    public void load(int slot, Callback<List<Block>> callback) {
        IO.execute(() -> callback.onResult(dao.blocksOf(slot)));
    }

    public void filledSlots(Callback<Set<Integer>> callback) {
        IO.execute(() -> callback.onResult(new HashSet<>(dao.filledSlots())));
    }

    /** Saves a note and deletes the files of attachments that are no longer on it. */
    public void save(int slot, List<Block> blocks) {
        IO.execute(() -> {
            Set<String> before = pathsOf(dao.blocksOf(slot));
            dao.replaceBlocks(slot, blocks, System.currentTimeMillis());
            before.removeAll(pathsOf(blocks));
            for (String path : before) {
                store.delete(path);
            }
        });
    }

    /** Clears a note. Database first: a leftover file is harmless, a dangling row is not. */
    public void clear(int slot, Runnable done) {
        IO.execute(() -> {
            dao.replaceBlocks(slot, Collections.emptyList(), System.currentTimeMillis());
            store.deleteSlot(slot);
            if (done != null) {
                done.run();
            }
        });
    }

    /** Removes attachment files nothing refers to. Call once at process start. */
    public void sweepOrphans() {
        IO.execute(() -> store.sweep(new HashSet<>(dao.allFilePaths()), SWEEP_MIN_AGE_MS));
    }

    private static Set<String> pathsOf(List<Block> blocks) {
        Set<String> paths = new HashSet<>();
        for (Block b : blocks) {
            if (b.filePath != null) {
                paths.add(b.filePath);
            }
        }
        return paths;
    }
}

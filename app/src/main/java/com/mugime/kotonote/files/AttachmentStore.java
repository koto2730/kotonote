package com.mugime.kotonote.files;

import android.content.Context;

import java.io.File;
import java.util.Set;
import java.util.UUID;

/**
 * Where attachment files live: {@code filesDir/notes/<slot>/}. Files are copied in, so
 * a note survives its source being deleted from the gallery. The database stores only
 * paths relative to {@code filesDir/notes}, such as {@code 2/3f9c….jpg}.
 */
public final class AttachmentStore {

    private final File root;

    public AttachmentStore(Context context) {
        this.root = new File(context.getApplicationContext().getFilesDir(), "notes");
    }

    public File slotDir(int slot) {
        return new File(root, String.valueOf(slot));
    }

    /** A new, not yet existing file for an attachment of the given note. */
    public File newFile(int slot, String extension) {
        File dir = slotDir(slot);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, UUID.randomUUID() + "." + extension);
    }

    public File resolve(String relativePath) {
        return new File(root, relativePath);
    }

    public String relative(File file) {
        return file.getParentFile().getName() + "/" + file.getName();
    }

    public void delete(String relativePath) {
        //noinspection ResultOfMethodCallIgnored
        resolve(relativePath).delete();
    }

    /** Removes every attachment file of one note. */
    public void deleteSlot(int slot) {
        deleteRecursively(slotDir(slot));
    }

    /**
     * Deletes files no block refers to (left behind by a crash or a cancelled capture).
     * Files younger than {@code minAgeMs} are spared: a camera app may still be writing one.
     */
    public void sweep(Set<String> referenced, long minAgeMs) {
        File[] dirs = root.listFiles();
        if (dirs == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (!referenced.contains(relative(f)) && now - f.lastModified() > minAgeMs) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}

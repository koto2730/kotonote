package com.mugime.kotonote.elements;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One piece of a note, in reading order: a run of text or an attachment. Attachment
 * files live under the app's private storage and are referenced by relative path.
 */
@Entity(
        tableName = "blocks",
        foreignKeys = @ForeignKey(
                entity = Note.class,
                parentColumns = "slot",
                childColumns = "slot",
                onDelete = ForeignKey.CASCADE),
        indices = @Index(value = {"slot", "position"}))
public class Block {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public int slot;

    public int position;

    public String type;

    @Nullable
    public String text;

    @Nullable
    public String filePath;

    @Nullable
    public String mime;

    public long durationMs;

    /** Thumbnail size, a {@link ThumbnailSpec} level. Only pictures and videos use it. */
    public int sizeLevel;

    public long createdAt;

    public BlockType blockType() {
        return BlockType.valueOf(type);
    }

    /** A fresh (unsaved) copy placed at the given slot and position. */
    public Block copyAt(int slot, int position) {
        Block b = new Block();
        b.slot = slot;
        b.position = position;
        b.type = type;
        b.text = text;
        b.filePath = filePath;
        b.mime = mime;
        b.durationMs = durationMs;
        b.sizeLevel = sizeLevel;
        b.createdAt = createdAt;
        return b;
    }

    public static Block attachment(BlockType type, String filePath, @Nullable String mime, long now) {
        Block b = new Block();
        b.type = type.name();
        b.filePath = filePath;
        b.mime = mime;
        b.createdAt = now;
        return b;
    }

    public static Block text(int slot, int position, String text, long now) {
        Block b = new Block();
        b.slot = slot;
        b.position = position;
        b.type = BlockType.TEXT.name();
        b.text = text;
        b.createdAt = now;
        return b;
    }
}

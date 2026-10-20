package com.mugime.kotonote.elements;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * One of the five sticky notes. Rows are created once, when the database is created,
 * and are never inserted or deleted afterwards - a cleared note is simply one with no
 * {@link Block}s.
 */
@Entity(tableName = "notes")
public class Note {

    @PrimaryKey
    public int slot;

    public long updatedAt;

    public Note(int slot, long updatedAt) {
        this.slot = slot;
        this.updatedAt = updatedAt;
    }
}

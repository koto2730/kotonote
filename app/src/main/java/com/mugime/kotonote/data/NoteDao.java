package com.mugime.kotonote.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.mugime.kotonote.elements.Block;

import java.util.List;

@Dao
public abstract class NoteDao {

    @Query("SELECT * FROM blocks WHERE slot = :slot ORDER BY position")
    public abstract List<Block> blocksOf(int slot);

    @Query("SELECT DISTINCT slot FROM blocks")
    public abstract List<Integer> filledSlots();

    @Query("SELECT filePath FROM blocks WHERE filePath IS NOT NULL")
    public abstract List<String> allFilePaths();

    @Query("DELETE FROM blocks WHERE slot = :slot")
    abstract void deleteBlocks(int slot);

    @Insert
    abstract void insertBlocks(List<Block> blocks);

    /**
     * An UPDATE, never an INSERT OR REPLACE: REPLACE deletes the notes row first, and the
     * ON DELETE CASCADE would take every block of that note with it.
     */
    @Query("UPDATE notes SET updatedAt = :now WHERE slot = :slot")
    abstract void touchNote(int slot, long now);

    /** Replaces everything on a note in one transaction. An empty list clears it. */
    @Transaction
    public void replaceBlocks(int slot, List<Block> blocks, long now) {
        deleteBlocks(slot);
        if (!blocks.isEmpty()) {
            insertBlocks(blocks);
        }
        touchNote(slot, now);
    }
}

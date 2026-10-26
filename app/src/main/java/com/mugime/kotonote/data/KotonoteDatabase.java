package com.mugime.kotonote.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.Note;
import com.mugime.kotonote.elements.NoteRules;

/**
 * The on-device SQLite database. There is no server and no sync: this file is the
 * single source of truth. The five {@code notes} rows are created with the database
 * and never inserted or deleted again, so "five notes only" is enforced by data.
 */
@Database(entities = {Note.class, Block.class}, version = 2, exportSchema = false)
public abstract class KotonoteDatabase extends RoomDatabase {

    private static final String DB_NAME = "kotonote.db";

    private static volatile KotonoteDatabase instance;

    /** Adds {@link Block#sizeLevel}, the thumbnail size of an attachment (0 = small). */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE blocks ADD COLUMN sizeLevel INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract NoteDao noteDao();

    public static KotonoteDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (KotonoteDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    KotonoteDatabase.class,
                                    DB_NAME)
                            .addMigrations(MIGRATION_1_2)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    long now = System.currentTimeMillis();
                                    for (int slot = 0; slot < NoteRules.SLOT_COUNT; slot++) {
                                        db.execSQL("INSERT INTO notes (slot, updatedAt) VALUES (?, ?)",
                                                new Object[]{slot, now});
                                    }
                                }
                            })
                            .build();
                }
            }
        }
        return instance;
    }
}

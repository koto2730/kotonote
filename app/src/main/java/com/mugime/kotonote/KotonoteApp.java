package com.mugime.kotonote;

import android.app.Application;

import com.mugime.kotonote.data.NoteRepository;

public class KotonoteApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        new NoteRepository(this).sweepOrphans();
    }
}

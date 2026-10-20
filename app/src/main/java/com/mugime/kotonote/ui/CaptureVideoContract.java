package com.mugime.kotonote.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mugime.kotonote.elements.NoteRules;

/** Like the stock CaptureVideo contract, but asks the camera app to stop at the length limit. */
public class CaptureVideoContract extends ActivityResultContract<Uri, Boolean> {

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull Uri output) {
        return new Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, output)
                .putExtra(MediaStore.EXTRA_DURATION_LIMIT, (int) (NoteRules.MAX_VIDEO_MS / 1000));
    }

    @Override
    public Boolean parseResult(int resultCode, @Nullable Intent intent) {
        return resultCode == Activity.RESULT_OK;
    }
}

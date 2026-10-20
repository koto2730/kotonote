package com.mugime.kotonote.files;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.mugime.kotonote.R;
import com.mugime.kotonote.elements.Block;
import com.mugime.kotonote.elements.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the share intent: the way out of kotonote. Text goes as text; attachments go
 * as content:// streams through {@link FileProvider}. Whether a receiving app accepts
 * a given kind (many take pictures, few take audio) is up to that app.
 */
public final class ShareHelper {

    private ShareHelper() {
    }

    public static String authority(Context context) {
        return context.getPackageName() + ".fileprovider";
    }

    /** Returns null when there is nothing to share. */
    public static Intent buildChooser(Context context, List<Block> blocks, AttachmentStore store) {
        StringBuilder text = new StringBuilder();
        ArrayList<Uri> uris = new ArrayList<>();
        boolean onlyImages = true;
        for (Block b : blocks) {
            if (b.blockType() == BlockType.TEXT) {
                if (b.text != null) {
                    text.append(b.text);
                }
            } else if (b.filePath != null) {
                uris.add(FileProvider.getUriForFile(context, authority(context),
                        store.resolve(b.filePath)));
                onlyImages &= b.blockType() == BlockType.IMAGE || b.blockType() == BlockType.PAINT;
            }
        }
        String body = text.toString().trim();
        if (body.isEmpty() && uris.isEmpty()) {
            return null;
        }

        Intent send;
        if (uris.isEmpty()) {
            send = new Intent(Intent.ACTION_SEND).setType("text/plain");
        } else if (uris.size() == 1) {
            send = new Intent(Intent.ACTION_SEND)
                    .setType(onlyImages ? "image/*" : "*/*")
                    .putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setType(onlyImages ? "image/*" : "*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        if (!body.isEmpty()) {
            send.putExtra(Intent.EXTRA_TEXT, body);
        }
        if (!uris.isEmpty()) {
            ClipData clip = ClipData.newRawUri(null, uris.get(0));
            for (int i = 1; i < uris.size(); i++) {
                clip.addItem(new ClipData.Item(uris.get(i)));
            }
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        return Intent.createChooser(send, context.getString(R.string.share_chooser));
    }
}

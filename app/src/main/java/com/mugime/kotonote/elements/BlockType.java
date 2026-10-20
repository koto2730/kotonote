package com.mugime.kotonote.elements;

/** What a {@link Block} holds. Stored by name, so never rename a constant. */
public enum BlockType {
    TEXT,
    AUDIO,
    IMAGE,
    VIDEO,
    PAINT;

    public boolean isAttachment() {
        return this != TEXT;
    }
}

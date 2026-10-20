package com.mugime.kotonote.elements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class NoteTextTest {

    private static Block image(String path) {
        return Block.attachment(BlockType.IMAGE, path, "image/jpeg", 1L);
    }

    @Test
    public void blankTextHasNoBlocks() {
        assertTrue(NoteText.toBlocks(0, "", 1L).isEmpty());
        assertTrue(NoteText.toBlocks(0, " \n\t", 1L).isEmpty());
        assertTrue(NoteText.toBlocks(0, null, 1L).isEmpty());
    }

    @Test
    public void textRoundTripsExactly() {
        String text = "  買い物\n- 牛乳\n- パン  ";
        List<Block> blocks = NoteText.toBlocks(3, text, 1L);
        assertEquals(1, blocks.size());
        assertEquals(3, blocks.get(0).slot);
        assertEquals(text, NoteText.fromBlocks(blocks));
    }

    @Test
    public void attachmentsKeepTheirPlaceBetweenText() {
        List<Object> parts = Arrays.<Object>asList("before ", image("2/a.jpg"), " after");
        List<Block> blocks = NoteText.compose(2, parts, 1L);
        assertEquals(3, blocks.size());
        assertEquals(BlockType.TEXT, blocks.get(0).blockType());
        assertEquals(BlockType.IMAGE, blocks.get(1).blockType());
        assertEquals("2/a.jpg", blocks.get(1).filePath);
        assertEquals(BlockType.TEXT, blocks.get(2).blockType());
        for (int i = 0; i < blocks.size(); i++) {
            assertEquals(i, blocks.get(i).position);
            assertEquals(2, blocks.get(i).slot);
            assertEquals(0L, blocks.get(i).id);
        }
    }

    @Test
    public void attachmentAloneIsContentEvenWithBlankText() {
        List<Object> parts = Arrays.<Object>asList("  ", image("0/a.jpg"), "");
        List<Block> blocks = NoteText.compose(0, parts, 1L);
        assertEquals(2, blocks.size());
        assertEquals("  ", blocks.get(0).text);
        assertEquals(BlockType.IMAGE, blocks.get(1).blockType());
    }

    @Test
    public void slotValidation() {
        assertTrue(NoteRules.isValidSlot(0));
        assertTrue(NoteRules.isValidSlot(4));
        assertFalse(NoteRules.isValidSlot(5));
        assertFalse(NoteRules.isValidSlot(-1));
    }
}

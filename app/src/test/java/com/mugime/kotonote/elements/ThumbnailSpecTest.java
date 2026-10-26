package com.mugime.kotonote.elements;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ThumbnailSpecTest {

    @Test
    public void smallIsAlwaysTheSameSquare() {
        assertEquals(189, ThumbnailSpec.edgePx(ThumbnailSpec.SMALL, 2.625f, 975));
        assertArrayEquals(new int[]{189, 189},
                ThumbnailSpec.outputSize(ThumbnailSpec.SMALL, 1080, 2400, 189));
        assertArrayEquals(new int[]{189, 189},
                ThumbnailSpec.outputSize(ThumbnailSpec.SMALL, 4000, 3000, 189));
    }

    @Test
    public void mediumAndLargeKeepProportions() {
        // A tall screenshot: the long (vertical) side gets the edge.
        assertArrayEquals(new int[]{208, 462},
                ThumbnailSpec.outputSize(ThumbnailSpec.MEDIUM, 1080, 2400, 462));
        // A wide photo.
        assertArrayEquals(new int[]{975, 731},
                ThumbnailSpec.outputSize(ThumbnailSpec.LARGE, 4000, 3000, 975));
    }

    @Test
    public void largeIsAsWideAsTheNoteAndMediumNeverExceedsIt() {
        assertEquals(975, ThumbnailSpec.edgePx(ThumbnailSpec.LARGE, 2.625f, 975));
        assertEquals(462, ThumbnailSpec.edgePx(ThumbnailSpec.MEDIUM, 2.625f, 975));
        // A very narrow note: medium is capped at the note width but never below small.
        assertEquals(300, ThumbnailSpec.edgePx(ThumbnailSpec.MEDIUM, 2.625f, 300));
        assertEquals(189, ThumbnailSpec.edgePx(ThumbnailSpec.MEDIUM, 2.625f, 100));
    }

    @Test
    public void unknownLevelsAreClamped() {
        assertEquals(ThumbnailSpec.SMALL, ThumbnailSpec.clampLevel(-3));
        assertEquals(ThumbnailSpec.LARGE, ThumbnailSpec.clampLevel(9));
    }

    @Test
    public void sizeLevelSurvivesComposing() {
        Block image = Block.attachment(BlockType.IMAGE, "0/a.jpg", "image/jpeg", 1L);
        image.sizeLevel = ThumbnailSpec.LARGE;
        List<Object> parts = Arrays.<Object>asList("x", image, "y");
        List<Block> blocks = NoteText.compose(0, parts, 1L);
        assertEquals(ThumbnailSpec.LARGE, blocks.get(1).sizeLevel);
    }
}

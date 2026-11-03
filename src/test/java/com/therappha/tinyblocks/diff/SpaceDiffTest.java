package com.therappha.tinyblocks.diff;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The comparison every parity test leans on: a mistake in it would pass everything or fail everything. */
class SpaceDiffTest {

    private static SpaceDiff.Difference difference(String field) {
        return new SpaceDiff.Difference(BlockPos.ZERO, field, "a", "b");
    }

    @Test
    void equalTotalsAreNoDifference() {
        assertTrue(SpaceDiff.compareTotals(Map.of("item apple", 2), Map.of("item apple", 2)).isEmpty());
    }

    @Test
    void aTotalOnOneSideOnlyIsCountedAgainstNone() {
        List<SpaceDiff.Difference> differences = SpaceDiff.compareTotals(Map.of("item apple", 2), Map.of());
        assertEquals(1, differences.size());
        assertEquals("entity.item apple", differences.get(0).field());
        assertEquals("2", differences.get(0).left());
        assertEquals("0", differences.get(0).right());
    }

    @Test
    void eachNameIsItsOwnDifference() {
        assertEquals(2, SpaceDiff.compareTotals(Map.of("arrow", 1, "tnt", 1), Map.of("arrow", 2, "item", 0, "tnt", 2)).size());
    }

    @Test
    void comparingWithAPrefixKeepsOnlyThoseFields() {
        Map<BlockPos, Map<String, String>> left = Map.of(BlockPos.ZERO, Map.of("state.lit", "true", "working.items", "x"));
        Map<BlockPos, Map<String, String>> right = Map.of(BlockPos.ZERO, Map.of("state.lit", "false", "working.items", "y"));
        List<SpaceDiff.Difference> differences = SpaceDiff.compare(left, right, "state.");
        assertEquals(1, differences.size());
        assertEquals("state.lit", differences.get(0).field());
    }

    @Test
    void aCellMissingOnTheRightCountsAsEmpty() {
        Map<BlockPos, Map<String, String>> left = Map.of(BlockPos.ZERO, Map.of("state.block", "stone"));
        List<SpaceDiff.Difference> differences = SpaceDiff.compare(left, Map.of(), "state.");
        assertEquals("", differences.get(0).right());
        assertTrue(SpaceDiff.describe(differences.get(0), "world", "tiny").endsWith("tiny nothing"));
    }

    @Test
    void theBoxLeavesOutItsFarEnd() {
        List<BlockPos> box = SpaceDiff.box(2, 3, 4);
        assertEquals(24, box.size());
        assertTrue(box.contains(new BlockPos(1, 2, 3)));
        assertFalse(box.contains(new BlockPos(2, 0, 0)));
    }

    @Test
    void consecutiveTicksJoinIntoOneSpan() {
        SpaceDiff.Timeline timeline = new SpaceDiff.Timeline();
        timeline.record(3, List.of(difference("state.lit")));
        timeline.record(4, List.of(difference("state.lit")));
        timeline.record(5, List.of());
        assertEquals(1, timeline.spans().size());
        assertEquals(2, timeline.spans().get(0).length());
        assertFalse(timeline.endsDifferent(timeline.spans().get(0)));
    }

    @Test
    void aGapStartsANewSpan() {
        SpaceDiff.Timeline timeline = new SpaceDiff.Timeline();
        timeline.record(3, List.of(difference("state.lit")));
        timeline.record(5, List.of(difference("state.lit")));
        assertEquals(2, timeline.spans().size());
        assertTrue(timeline.endsDifferent(timeline.spans().get(1)));
    }

    @Test
    void spansOfDifferentFieldsAreKeptApartAndSortedByWhenTheyBegan() {
        SpaceDiff.Timeline timeline = new SpaceDiff.Timeline();
        timeline.record(7, List.of(difference("state.a")));
        timeline.record(2, List.of(difference("state.b")));
        assertEquals("state.b", timeline.spans().get(0).first().field());
    }
}

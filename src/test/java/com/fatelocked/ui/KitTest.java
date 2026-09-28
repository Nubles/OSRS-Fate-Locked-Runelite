package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.preview.SwingSnapshot;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class KitTest
{
    @Test
    public void statTilesTakeTheirHeight() throws Exception
    {
        int[] height = new int[1];
        SwingUtilities.invokeAndWait(() -> {
            StatTiles tiles = new StatTiles();
            tiles.show(new StatTiles.Tile(1, "Can do", Palette.Tone.GOOD),
                new StatTiles.Tile(2, "Locked", Palette.Tone.BAD));
            height[0] = SwingSnapshot.settle(tiles, 200);
            assertEquals(2, tiles.getComponentCount());
        });
        assertEquals(38, height[0]);
    }

    @Test
    public void aLongReasonStopsAtTwoLinesWithAnEllipsisAndATooltip() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            TextBlock block = new TextBlock(Type.small(), Palette.TEXT_MUTED, 2, 120);
            block.setText("Needs Cook's Assistant, Fishing Contest, Goblin Diplomacy, Big Chompy Bird Hunting and more");
            List<String> lines = block.lines();
            assertEquals(2, lines.size());
            assertTrue(lines.get(1), lines.get(1).endsWith("…"));
            assertEquals(block.getText(), block.getToolTipText());

            block.setText("Short reason");
            assertEquals(List.of("Short reason"), block.lines());
            assertNull("nothing is cut, so no tooltip", block.getToolTipText());
        });
    }

    @Test
    public void aRowShowsItsReasonOnlyWhenItHasOne() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ItemRow row = new ItemRow().show("Cook's Assistant", "Can do", Palette.Tone.GOOD, null);
            assertFalse(row.reason().isVisible());
            assertEquals("Can do", row.pill().getWord());
            row.show("Black Knights' Fortress", "Locked", Palette.Tone.BAD, "Unlock it in the tracker");
            assertTrue(row.reason().isVisible());
            assertEquals(Palette.Tone.BAD, row.pill().getTone());
            assertEquals("Black Knights' Fortress", row.title());
        });
    }

    @Test
    public void aPillWithNoWordIsHidden() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            StatusPill pill = new StatusPill("Locked", Palette.Tone.BAD);
            assertTrue(pill.isVisible());
            pill.show(null, Palette.Tone.NEUTRAL);
            assertFalse(pill.isVisible());
        });
    }

    @Test
    public void progressStaysBetweenNoneAndAll()
    {
        ProgressBar bar = new ProgressBar();
        bar.setFraction(1.5);
        assertEquals(1.0, bar.getFraction(), 0);
        bar.setFraction(-0.5);
        assertEquals(0.0, bar.getFraction(), 0);
    }

    @Test
    public void buttonsAreAsTallAsEachOtherAndALinkIsJustItsText() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            FlatButton primary = new FlatButton("Connect tracker", FlatButton.Kind.PRIMARY);
            FlatButton secondary = new FlatButton("Cancel", FlatButton.Kind.SECONDARY);
            FlatButton link = new FlatButton("Open tracker", FlatButton.Kind.LINK);
            assertEquals(primary.getPreferredSize().height, secondary.getPreferredSize().height);
            assertTrue(link.getPreferredSize().height < primary.getPreferredSize().height);
            assertTrue(primary.getPreferredSize().width > link.getPreferredSize().width);
        });
    }

    @Test
    public void aSectionOpensAndClosesAndTellsItsListeners() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Section section = new Section("Here", false);
            boolean[] told = new boolean[1];
            section.onToggle(open -> told[0] = open);
            assertFalse(section.body().isVisible());
            section.setExpanded(true);
            assertTrue(section.body().isVisible());
            assertTrue(told[0]);
        });
    }
}

package com.fatelocked;

import javax.swing.JLabel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CollapsiblePanelSectionTest
{
    @Test
    public void headerTogglesOnlyItsOwnBody()
    {
        CollapsiblePanelSection section =
            new CollapsiblePanelSection("Warnings", false);

        assertFalse(section.isExpanded());
        section.headerForTest().doClick();
        assertTrue(section.isExpanded());
        assertTrue(section.body().isVisible());
    }

    @Test
    public void closingRestoresHeaderAndKeepsBodyChildren()
    {
        CollapsiblePanelSection section =
            new CollapsiblePanelSection("Warnings", true);
        JLabel child = new JLabel("Chat on entry");
        section.body().add(child);

        section.setExpanded(false);

        assertFalse(section.isExpanded());
        assertFalse(section.body().isVisible());
        assertEquals("\u25b6 Warnings", section.headerForTest().getText());
        assertEquals(1, section.body().getComponentCount());
        assertSame(child, section.body().getComponent(0));
    }
    @Test
    public void bodyAddedAfterConstructionCanGrowToFitItsChild()
    {
        CollapsiblePanelSection section =
            new CollapsiblePanelSection("Warnings", true);
        JLabel child = new JLabel("Chat on entry");

        section.body().add(child);

        assertTrue(section.body().getPreferredSize().height > 0);
        assertTrue(section.body().getMaximumSize().height >=
            child.getPreferredSize().height);
    }

    @Test
    public void headerHeightFitsItsArrowAndTitleInBothStates()
    {
        CollapsiblePanelSection section =
            new CollapsiblePanelSection("Rendering", false);

        assertTrue(section.headerForTest().getMaximumSize().height
            >= section.headerForTest().getPreferredSize().height);

        section.setExpanded(true);

        assertTrue(section.headerForTest().getMaximumSize().height
            >= section.headerForTest().getPreferredSize().height);
    }
}
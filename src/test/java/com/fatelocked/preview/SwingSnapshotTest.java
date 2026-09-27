package com.fatelocked.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class SwingSnapshotTest
{
    @Test
    public void paintsAComponentWithNoDisplayAtItsSettledHeight() throws Exception
    {
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(Color.RED);
            JPanel block = new JPanel();
            block.setPreferredSize(new Dimension(50, 40));
            block.setBackground(Color.BLUE);
            panel.add(block, BorderLayout.NORTH);
            image[0] = SwingSnapshot.paint(panel, 120, 0);
        });

        assertEquals(120, image[0].getWidth());
        assertEquals(40, image[0].getHeight());
        assertEquals(Color.BLUE.getRGB(), image[0].getRGB(60, 20));
    }

    @Test
    public void anHtmlLabelWrapsToTheWidthItIsGiven() throws Exception
    {
        int[] heights = new int[2];
        SwingUtilities.invokeAndWait(() -> {
            String words = "<html>" + "Rules up to date and synced a moment ago. ".repeat(6) + "</html>";
            JPanel oneLine = new JPanel(new BorderLayout());
            oneLine.add(new JLabel("Rules up to date"), BorderLayout.NORTH);
            JPanel wrapped = new JPanel(new BorderLayout());
            wrapped.add(new JLabel(words), BorderLayout.NORTH);
            heights[0] = SwingSnapshot.settle(oneLine, 120);
            heights[1] = SwingSnapshot.settle(wrapped, 120);
        });

        assertTrue("the long label wraps onto several lines: " + heights[1] + " vs " + heights[0],
            heights[1] > heights[0] * 3);
    }
}

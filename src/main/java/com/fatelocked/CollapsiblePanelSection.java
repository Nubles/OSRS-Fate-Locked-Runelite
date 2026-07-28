package com.fatelocked;

import java.awt.Color;

import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

final class CollapsiblePanelSection extends JPanel
{
    private final String title;
    private final JButton header = new JButton();
    private final JPanel body = new JPanel();
    private boolean expanded;

    CollapsiblePanelSection(String title, boolean expanded)
    {
        this.title = title;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setAlignmentX(JComponent.LEFT_ALIGNMENT);

        header.setFont(header.getFont().deriveFont(Font.BOLD, 10f));
        header.setForeground(Color.LIGHT_GRAY);
        header.setContentAreaFilled(false);
        header.setBorder(new EmptyBorder(0, 0, 4, 0));
        header.setFocusPainted(false);
        header.setHorizontalAlignment(JButton.LEFT);
        header.addActionListener(event -> setExpanded(!this.expanded));

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ColorScheme.DARK_GRAY_COLOR);
        body.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        fullWidthAndGrow(body);

        add(header);
        add(body);
        setExpanded(expanded);
    }

    JPanel body()
    {
        return body;
    }

    boolean isExpanded()
    {
        return expanded;
    }

    void setExpanded(boolean expanded)
    {
        this.expanded = expanded;
        body.setVisible(expanded);
        updateHeader();
        revalidate();
        repaint();
    }

    private void updateHeader()
    {
        header.setText((expanded ? "▼ " : "▶ ") + title);
        fullWidth(header);
    }

    JButton headerForTest()
    {
        return header;
    }

    private static void fullWidth(JComponent component)
    {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(
            Integer.MAX_VALUE, component.getPreferredSize().height));
    }
    private static void fullWidthAndGrow(JComponent component)
    {
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
package com.fatelocked;

import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FateLockedPanelStatusTest
{
    @Test
    public void rendersCompactSyncHealth() throws Exception
    {
        FateLockedPanel panel = new FateLockedPanel();
        panel.updateSyncHealth(4, 2, 1,
            Instant.parse("2026-07-24T14:05:06Z"), false);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("4", panel.queuedTextForTest());
        assertEquals("2", panel.reviewTextForTest());
        assertEquals("1 active", panel.warningTextForTest());
        assertTrue(panel.lastSyncTextForTest().contains("14:05:06 UTC"));
    }

    @Test
    public void labelsOfflineWithoutDiscardingCounts() throws Exception
    {
        FateLockedPanel panel = new FateLockedPanel();
        panel.updateSyncHealth(3, 1, 0, null, true);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals("3", panel.queuedTextForTest());
        assertEquals("Offline", panel.lastSyncTextForTest());
    }

    @Test
    public void encodesThePairingCodeInTheRollInboxUrl()
    {
        assertEquals(
            "https://tracker.example/app?open=roll-inbox&code=AB+%26%2F%3F",
            FateLockedPanel.rollInboxUrl(
                "https://tracker.example/app",
                "AB &/?"));
    }

    @Test
    public void strictModeUsesOneSharedPauseControlWithoutATravelToggle()
        throws Exception
    {
        FateLockedPanel panel = new FateLockedPanel();

        panel.updateStrictMode(false, false, 0);
        SwingUtilities.invokeAndWait(() -> { });
        JButton pause = buttonWithText(panel, "Pause Strict Mode for 60 seconds");
        assertFalse(pause.isVisible());

        panel.updateStrictMode(true, false, 0);
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue(pause.isVisible());
        assertEquals("Pause Strict Mode for 60 seconds", pause.getText());

        panel.updateStrictMode(true, true, 60);
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals("Resume Strict Mode \u00b7 60s", pause.getText());
        assertFalse(hasTravelGuardianCheckbox(panel));
    }

    @Test
    public void strictModeIntroExplainsKnownTravelAndUncertainMovement()
    {
        FateLockedPanel panel = new FateLockedPanel();

        JLabel intro = labelStartingWith(panel, "<html>Strict Mode");
        assertEquals(
            "<html>Strict Mode prevents only actions proven locked by fresh rules. "
                + "Known locked travel clicks can be stopped; uncertain movement is never blocked. "
                + "Pause it for 60 seconds here or turn it off immediately in plugin settings.</html>",
            intro.getText());
    }

    private static JButton buttonWithText(Container root, String text)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JButton
                && text.equals(((JButton) component).getText()))
            {
                return (JButton) component;
            }
            if (component instanceof Container)
            {
                try
                {
                    return buttonWithText((Container) component, text);
                }
                catch (AssertionError ignored)
                {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No button with text: " + text);
    }

    private static JLabel labelStartingWith(Container root, String prefix)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JLabel
                && ((JLabel) component).getText().startsWith(prefix))
            {
                return (JLabel) component;
            }
            if (component instanceof Container)
            {
                try
                {
                    return labelStartingWith((Container) component, prefix);
                }
                catch (AssertionError ignored)
                {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No label starting with: " + prefix);
    }

    private static boolean hasTravelGuardianCheckbox(Container root)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JCheckBox
                && ((JCheckBox) component).getText() != null
                && ((JCheckBox) component).getText().contains("Travel Guardian"))
            {
                return true;
            }
            if (component instanceof Container
                && hasTravelGuardianCheckbox((Container) component))
            {
                return true;
            }
        }
        return false;
    }
}

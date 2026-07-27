package com.fatelocked;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FateLockedPanelStatusTest
{
    private FateLockedConfig config;
    private ConfigManager configManager;

    @Before
    public void setUp()
    {
        config = mock(FateLockedConfig.class, CALLS_REAL_METHODS);
        configManager = mock(ConfigManager.class);
    }

    @Test
    public void composesTheCompleteSidebarInTheApprovedOrder()
    {
        FateLockedPanel panel = panel();

        assertEquals(Arrays.asList(
            "Current chunk", "Guardian", "Roll inbox", "Run",
            "Bundle", "Warnings", "Rendering"),
            panel.sectionTitlesForTest());
        assertTrue(panel.sectionForTest("Current chunk").isExpanded());
        assertTrue(panel.sectionForTest("Guardian").isExpanded());
        assertFalse(panel.sectionForTest("Roll inbox").isExpanded());
        assertFalse(panel.sectionForTest("Run").isExpanded());
        assertFalse(panel.sectionForTest("Bundle").isExpanded());
        assertFalse(panel.sectionForTest("Warnings").isExpanded());
        assertFalse(panel.sectionForTest("Rendering").isExpanded());
    }

    @Test
    public void rendersExactlyTheRetainedSidebarSettings()
    {
        FateLockedPanel panel = panel();

        assertEquals(30, panel.settingKeysForTest().size());
        assertEquals(new LinkedHashSet<>(Arrays.asList(
            "autoReload", "reimportHotkey",
            "chatOnEnter", "warnOnLocked", "warnLockedBank", "flashOnLocked",
            "warnAccountMismatch", "tagLockedMenus", "tagLockedTeleports",
            "showHud", "showNearest", "showChunkContentBox", "useNotifier",
            "warnLockedSlayer", "warnOverTierGear", "showInfoBoxes", "rollNudges",
            "strictMode",
            "drawWorldMap", "drawScene", "drawMinimap",
            "highlightLockedBorders", "shadeNearbyLocked", "worldMapMarkers",
            "worldMapTooltip", "worldMapTooltipContent",
            "unlockedColor", "frontierColor", "lockedColor", "unauthoredColor")),
            panel.settingKeysForTest());
        assertFalse(panel.settingKeysForTest().contains("onlineSync"));
        assertFalse(panel.settingKeysForTest().contains("syncCode"));
        assertFalse(panel.settingKeysForTest().contains("relayUrl"));
        assertFalse(hasTravelGuardianCheckbox(panel));
    }

    @Test
    public void displaysConnectionAccountAndIpDisclosure() throws Exception
    {
        FateLockedPanel panel = panel();

        panel.updateConnection(TrackerConnectionSnapshot.waiting());
        flushSwing();
        assertEquals("Waiting for tracker", panel.connectionTextForTest());

        panel.updateConnection(TrackerConnectionSnapshot.connected(
            Instant.parse("2026-07-27T14:05:06Z"), "6"));
        flushSwing();
        assertTrue(panel.connectionTextForTest().contains("Connected"));
        assertTrue(panel.connectionTextForTest().contains("14:05:06 UTC"));
        assertEquals("14:05:06 UTC", panel.lastSyncTextForTest());

        panel.updateTrackerAccount("Nubles");
        flushSwing();
        assertEquals("Nubles", panel.trackerAccountTextForTest());
        assertTrue(panel.hasTextForTest(
            "your IP address is visible to the Fate Locked relay"));
    }

    @Test
    public void connectTrackerButtonInvokesItsCallbackExactlyOnce()
        throws Exception
    {
        FateLockedPanel panel = panel();
        AtomicInteger connections = new AtomicInteger();
        panel.setCallbacks(json -> { }, () -> { }, connections::incrementAndGet);

        SwingUtilities.invokeAndWait(
            () -> panel.connectButtonForTest().doClick());

        assertEquals(1, connections.get());
    }

    @Test
    public void syncHealthKeepsCountsAndConnectionCopyTogether()
        throws Exception
    {
        FateLockedPanel panel = panel();
        panel.updateSyncHealth(4, 2, 1,
            TrackerConnectionSnapshot.connected(
                Instant.parse("2026-07-24T14:05:06Z"), "6"));
        flushSwing();

        assertEquals("4", panel.queuedTextForTest());
        assertEquals("2", panel.reviewTextForTest());
        assertEquals("1 active", panel.warningTextForTest());
        assertEquals("Connected \u00b7 14:05:06 UTC",
            panel.connectionTextForTest());
    }

    @Test
    public void preservesVisiblePreferredHeightAndTopStatusPlacement()
        throws Exception
    {
        FateLockedPanel panel = panel();
        panel.flashStatus("pairing code detected \u2014 use Connect tracker", false);
        flushSwing();

        assertTrue(panel.getPreferredSize().height > 0);
        assertTrue(topLevelIndex(panel, panel.connectButtonForTest())
            < topLevelIndex(panel, findLabel(panel, "Not connected")));
        assertTrue(topLevelIndex(panel, findLabel(panel,
            "pairing code detected \u2014 use Connect tracker"))
            < topLevelIndex(panel, panel.sectionForTest("Current chunk")));
    }

    @Test
    public void refreshConfigUpdatesTheMatchingSidebarControl()
        throws Exception
    {
        FateLockedPanel panel = panel();
        JCheckBox hud = checkboxWithText(panel, "Show in-game HUD");
        assertTrue(hud.isSelected());
        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, "showHud", Boolean.class))
            .thenReturn(false);

        panel.refreshConfig("showHud");
        flushSwing();

        assertFalse(hud.isSelected());
    }
    @Test
    public void strictModeKeepsOnePauseControlAndUpdatedIntroduction()
        throws Exception
    {
        FateLockedPanel panel = panel();

        panel.updateStrictMode(true, true, 60);
        flushSwing();

        assertEquals("Resume Strict Mode \u00b7 60s",
            buttonWithText(panel, "Resume Strict Mode \u00b7 60s").getText());
        assertFalse(hasTravelGuardianCheckbox(panel));
        assertTrue(labelStartingWith(panel, "<html>Strict Mode")
            .getText().endsWith("turn it off above.</html>"));
    }

    @Test
    public void encodesThePairingCodeInTheRollInboxUrl()
    {
        assertEquals(
            "https://tracker.example/app?open=roll-inbox&code=AB+%26%2F%3F",
            FateLockedPanel.rollInboxUrl(
                "https://tracker.example/app", "AB &/?"));
    }

    private FateLockedPanel panel()
    {
        return new FateLockedPanel(config, configManager);
    }

    private static void flushSwing() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
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

    private static JCheckBox checkboxWithText(Container root, String text)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JCheckBox
                && text.equals(((JCheckBox) component).getText()))
            {
                return (JCheckBox) component;
            }
            if (component instanceof Container)
            {
                try
                {
                    return checkboxWithText((Container) component, text);
                }
                catch (AssertionError ignored)
                {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No checkbox with text: " + text);
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

    private static JLabel findLabel(Component component, String text)
    {
        if (component instanceof JLabel
            && text.equals(((JLabel) component).getText()))
        {
            return (JLabel) component;
        }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents())
            {
                JLabel match = findLabel(child, text);
                if (match != null)
                {
                    return match;
                }
            }
        }
        return null;
    }

    private static int topLevelIndex(
        FateLockedPanel panel, Component component)
    {
        Container column = (Container) panel.getComponent(0);
        Component child = component;
        while (child.getParent() != column)
        {
            child = child.getParent();
        }
        Component[] children = column.getComponents();
        for (int i = 0; i < children.length; i++)
        {
            if (children[i] == child)
            {
                return i;
            }
        }
        return -1;
    }
}

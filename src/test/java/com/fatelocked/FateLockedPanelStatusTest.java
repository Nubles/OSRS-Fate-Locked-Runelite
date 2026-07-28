package com.fatelocked;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    public void assignsEverySettingToExactlyOneOwningSection()
    {
        FateLockedPanel panel = panel();

        assertSectionSettings(panel, "Current chunk", keys());
        assertSectionSettings(panel, "Guardian", keys("strictMode"));
        assertSectionSettings(panel, "Roll inbox", keys());
        assertSectionSettings(panel, "Run", keys());
        assertSectionSettings(panel, "Bundle",
            keys("autoReload", "reimportHotkey"));
        assertSectionSettings(panel, "Warnings", keys(
            "chatOnEnter", "warnOnLocked", "warnLockedBank", "flashOnLocked",
            "warnAccountMismatch", "tagLockedMenus", "tagLockedTeleports",
            "showHud", "showNearest", "showChunkContentBox", "useNotifier",
            "warnLockedSlayer", "warnOverTierGear", "showInfoBoxes", "rollNudges"));
        assertSectionSettings(panel, "Rendering", keys(
            "drawWorldMap", "drawScene", "drawMinimap",
            "highlightLockedBorders", "shadeNearbyLocked", "worldMapMarkers",
            "worldMapTooltip", "worldMapTooltipContent",
            "unlockedColor", "frontierColor", "lockedColor", "unauthoredColor"));
    }

    @Test
    public void strictToggleAppearsAboveItsIntroduction()
    {
        FateLockedPanel panel = panel();
        Container guardian = sectionContent(panel, "Guardian");
        Component toggle = panel.settingControlForTest("strictMode");
        Component introduction = labelStartingWith(guardian, "<html>Strict Mode");

        assertTrue(directChildIndex(guardian, toggle)
            < directChildIndex(guardian, introduction));
    }

    @Test
    public void configSuppliersInitializeTheOwnedControls()
    {
        Keybind hotkey = new Keybind(keyPressed(KeyEvent.VK_F));
        Color frontier = new Color(12, 34, 56, 78);
        when(config.strictMode()).thenReturn(true);
        when(config.autoReload()).thenReturn(false);
        when(config.showHud()).thenReturn(false);
        when(config.drawScene()).thenReturn(false);
        when(config.reimportHotkey()).thenReturn(hotkey);
        when(config.frontierColor()).thenReturn(frontier);

        FateLockedPanel panel = panel();

        assertTrue(settingCheckbox(panel, "strictMode").isSelected());
        assertFalse(settingCheckbox(panel, "autoReload").isSelected());
        assertFalse(settingCheckbox(panel, "showHud").isSelected());
        assertFalse(settingCheckbox(panel, "drawScene").isSelected());
        assertEquals(hotkey.toString(),
            ((JButton) panel.settingControlForTest("reimportHotkey")).getText());
        assertEquals(new Color(12, 34, 56),
            panel.settingControlForTest("frontierColor").getBackground());
        assertEquals("Show \"in this chunk\" box",
            settingCheckbox(panel, "showChunkContentBox").getText());
        assertEquals("Frontier color (Chunked)",
            ((JButton) panel.settingControlForTest("frontierColor")).getText());
    }

    @Test
    public void representativeBooleansPersistThroughTheirOwningSections()
        throws Exception
    {
        when(config.strictMode()).thenReturn(true);
        when(config.autoReload()).thenReturn(false);
        when(config.showHud()).thenReturn(false);
        when(config.drawScene()).thenReturn(false);
        FateLockedPanel panel = panel();

        SwingUtilities.invokeAndWait(() -> {
            settingCheckbox(panel, "strictMode").doClick();
            settingCheckbox(panel, "autoReload").doClick();
            settingCheckbox(panel, "showHud").doClick();
            settingCheckbox(panel, "drawScene").doClick();
        });

        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "strictMode", false);
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "autoReload", true);
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "showHud", true);
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "drawScene", true);
    }

    @Test
    public void bundleImportAndReloadCallbacksStayInBundleSection()
        throws Exception
    {
        FateLockedPanel panel = panel();
        java.util.List<String> imports = new java.util.ArrayList<>();
        AtomicInteger reloads = new AtomicInteger();
        panel.setCallbacks(imports::add, reloads::incrementAndGet, () -> { });
        Container bundle = sectionContent(panel, "Bundle");
        JTextArea paste = findTextArea(bundle);
        JButton importButton = buttonWithText(bundle, "Import pasted JSON");
        JButton reloadButton = buttonWithText(bundle, "Reload from file");

        SwingUtilities.invokeAndWait(() -> {
            paste.setText("  {\"run\":1}  ");
            importButton.doClick();
            reloadButton.doClick();
        });

        assertEquals(Arrays.asList("{\"run\":1}"), imports);
        assertEquals(1, reloads.get());
        assertTrue(isDescendant(bundle, importButton));
        assertTrue(isDescendant(bundle, reloadButton));
    }

    @Test
    public void guardianActionsStayInGuardianAndInvokeTheirCallbacks()
        throws Exception
    {
        FateLockedPanel panel = panel();
        AtomicInteger pauses = new AtomicInteger();
        AtomicInteger resumes = new AtomicInteger();
        AtomicInteger dismissals = new AtomicInteger();
        panel.setGuardianCallbacks(
            pauses::incrementAndGet,
            resumes::incrementAndGet,
            dismissals::incrementAndGet);
        Container guardian = sectionContent(panel, "Guardian");

        panel.updateStrictMode(true, false, 0);
        flushSwing();
        JButton pause = buttonWithText(
            guardian, "Pause Strict Mode for 60 seconds");
        SwingUtilities.invokeAndWait(pause::doClick);
        panel.updateStrictMode(true, true, 60);
        flushSwing();
        JButton resume = buttonWithText(
            guardian, "Resume Strict Mode \u00b7 60s");
        SwingUtilities.invokeAndWait(resume::doClick);
        panel.showStrictModeIntro();
        flushSwing();
        JButton dismiss = buttonWithText(guardian, "Got it");
        SwingUtilities.invokeAndWait(dismiss::doClick);

        assertEquals(1, pauses.get());
        assertEquals(1, resumes.get());
        assertEquals(1, dismissals.get());
        assertTrue(isDescendant(guardian, pause));
        assertTrue(isDescendant(guardian, dismiss));
    }

    @Test
    public void rollInboxLinkAndButtonStayInRollInbox()
    {
        FateLockedPanel panel = panel();
        panel.setRollInboxLink("https://tracker.example/app");
        Container inbox = sectionContent(panel, "Roll inbox");

        assertEquals(
            "https://tracker.example/app?open=roll-inbox",
            panel.rollInboxUrlForTest());
        JButton open = buttonWithText(inbox, "Open web Roll Inbox");
        assertTrue(isDescendant(inbox, open));
        assertEquals(1, open.getActionListeners().length);
        assertEquals(
            "Open the separate web Roll Inbox; local history is not transferred",
            open.getToolTipText());
        assertTrue(panel.hasTextForTest(
            "Local only — RuneLite does not upload gameplay data."));
    }

    @Test
    public void runValuesStillUpdateInsideRunSection() throws Exception
    {
        FateLockedPanel panel = panel();
        panel.update(bundleFixture(), null);
        flushSwing();
        Container run = sectionContent(panel, "Run");

        assertEquals("run-1", valueBesideLabel(run, "Run ID"));
        assertEquals("Nubles", valueBesideLabel(run, "Account"));
        assertEquals("0 \u00b7 O 0 \u00b7 C 0", valueBesideLabel(run, "Keys"));
        assertEquals("0", valueBesideLabel(run, "Fate"));
        assertEquals("\u2014", valueBesideLabel(run, "Goal"));
    }

    @Test
    public void sectionHeadersToggleIndependently()
    {
        FateLockedPanel panel = panel();
        CollapsiblePanelSection guardian = panel.sectionForTest("Guardian");
        CollapsiblePanelSection warnings = panel.sectionForTest("Warnings");

        warnings.headerForTest().doClick();

        assertTrue(warnings.isExpanded());
        assertTrue(guardian.isExpanded());
        warnings.headerForTest().doClick();
        assertFalse(warnings.isExpanded());
        assertTrue(guardian.isExpanded());
    }

    @Test
    public void allSectionsRemainUnderTheNorthAnchoredContentColumn()
    {
        FateLockedPanel panel = panel();
        BorderLayout layout = (BorderLayout) panel.getLayout();
        Component north = layout.getLayoutComponent(BorderLayout.NORTH);

        assertSame(panel.getComponent(0), north);
        for (String title : panel.sectionTitlesForTest())
        {
            assertTrue(isDescendant(
                (Container) north, panel.sectionForTest(title)));
        }
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
            "RuneLite retrieves rules from the Fate Locked relay. "
                + "Your IP address is visible to the relay, but RuneLite "
                + "does not upload gameplay data."));
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
    public void localRollInboxStatusDoesNotChangeConnectionState()
        throws Exception
    {
        FateLockedPanel panel = panel();
        panel.updateConnection(TrackerConnectionSnapshot.waiting());
        panel.updateRollInboxStatus(4, 2, 1, true);
        flushSwing();

        assertEquals("4", panel.localEventsTextForTest());
        assertEquals("2", panel.reviewTextForTest());
        assertEquals("1 active", panel.warningTextForTest());
        assertEquals("Waiting for tracker", panel.connectionTextForTest());
        assertTrue(panel.historyStatusVisibleForTest());
        assertEquals("Local history save failed",
            panel.historyStatusTextForTest());

        panel.updateRollInboxStatus(4, 2, 0, false);
        flushSwing();

        assertEquals("None", panel.warningTextForTest());
        assertFalse(panel.historyStatusVisibleForTest());
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
    public void rollInboxUrlContainsNoPairingData()
    {
        assertEquals(
            "https://tracker.example/app?open=roll-inbox",
            FateLockedPanel.rollInboxUrl(
                "https://tracker.example/app"));
    }

    private FateLockedPanel panel()
    {
        return new FateLockedPanel(config, configManager);
    }

    private static void flushSwing() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static LinkedHashSet<String> keys(String... keys)
    {
        return new LinkedHashSet<>(Arrays.asList(keys));
    }

    private static void assertSectionSettings(
        FateLockedPanel panel, String title, LinkedHashSet<String> expected)
    {
        assertEquals(expected, panel.sectionSettingKeysForTest(title));
        Container content = sectionContent(panel, title);
        for (String key : panel.settingKeysForTest())
        {
            assertEquals(
                title + " ownership for " + key,
                expected.contains(key),
                isDescendant(content, panel.settingControlForTest(key)));
        }
    }

    private static Container sectionContent(
        FateLockedPanel panel, String title)
    {
        return (Container) panel.sectionForTest(title).body().getComponent(0);
    }

    private static JCheckBox settingCheckbox(
        FateLockedPanel panel, String key)
    {
        return (JCheckBox) panel.settingControlForTest(key);
    }

    private static int directChildIndex(Container root, Component component)
    {
        Component child = component;
        while (child.getParent() != root)
        {
            child = child.getParent();
        }
        Component[] children = root.getComponents();
        for (int i = 0; i < children.length; i++)
        {
            if (children[i] == child)
            {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDescendant(
        Container ancestor, Component component)
    {
        Component current = component;
        while (current != null)
        {
            if (current == ancestor)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static JTextArea findTextArea(Container root)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JTextArea)
            {
                return (JTextArea) component;
            }
            if (component instanceof Container)
            {
                try
                {
                    return findTextArea((Container) component);
                }
                catch (AssertionError ignored)
                {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No text area found");
    }

    private static String valueBesideLabel(Container root, String label)
    {
        Component[] components = root.getComponents();
        for (int i = 0; i + 1 < components.length; i++)
        {
            if (components[i] instanceof JLabel
                && label.equals(((JLabel) components[i]).getText())
                && components[i + 1] instanceof JLabel)
            {
                return ((JLabel) components[i + 1]).getText();
            }
        }
        for (Component component : components)
        {
            if (component instanceof Container)
            {
                try
                {
                    return valueBesideLabel((Container) component, label);
                }
                catch (AssertionError ignored)
                {
                    // Continue searching sibling containers.
                }
            }
        }
        throw new AssertionError("No value beside label: " + label);
    }

    private FateLockedBundle bundleFixture() throws Exception
    {
        try (InputStream input = getClass().getClassLoader()
            .getResourceAsStream("bundles/v4-rules.json"))
        {
            assertNotNull(input);
            String json = new String(
                input.readAllBytes(), StandardCharsets.UTF_8);
            return FateLockedBundle.loadFromJson(new Gson(), json);
        }
    }

    private static KeyEvent keyPressed(int keyCode)
    {
        JButton source = new JButton();
        return new KeyEvent(source, KeyEvent.KEY_PRESSED, 0L, 0, keyCode,
            KeyEvent.CHAR_UNDEFINED)
        {
            @Override
            public int getExtendedKeyCode()
            {
                return getKeyCode();
            }
        };
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

package com.fatelocked;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedConfigBinderTest
{
    private ConfigManager configManager;
    private List<String> statuses;
    private FateLockedConfigBinder binder;

    @Before
    public void setUp()
    {
        configManager = mock(ConfigManager.class);
        statuses = new ArrayList<>();
        binder = new FateLockedConfigBinder(configManager, statuses::add);
    }

    @Test
    public void booleanControlPersistsUserChoiceAndRefreshesWithoutWritingAgain()
    {
        JCheckBox control = binder.booleanSetting(
            "showHud", "Show in-game HUD", () -> true);

        control.doClick();
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "showHud", false);

        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, "showHud", Boolean.class))
            .thenReturn(true);
        binder.refresh("showHud");

        assertTrue(control.isSelected());
        verify(configManager, times(1)).setConfiguration(
            FateLockedConfig.GROUP, "showHud", false);
    }

    @Test
    public void colourChoicePersistsSelectedColour()
    {
        Color chosen = new Color(16, 185, 129);
        FateLockedConfigBinder colorBinder = new FateLockedConfigBinder(
            configManager, statuses::add, (parent, title, initial) -> chosen);
        JComponent control = colorBinder.colorSetting(
            "unlockedColor", "Unlocked colour", () -> Color.BLUE);

        ((JButton) control).doClick();

        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "unlockedColor", chosen);
        assertSame(chosen, control.getBackground());
    }

    @Test
    public void escapeClearsKeybind()
    {
        JComponent control = binder.keybindSetting(
            "reimportHotkey", "Re-import hotkey", () -> new Keybind(
                keyPressed(new JButton(), KeyEvent.VK_F)));
        KeybindCaptureButton button = (KeybindCaptureButton) control;

        button.doClick();
        capture(button, keyPressed(button, KeyEvent.VK_ESCAPE));

        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "reimportHotkey", Keybind.NOT_SET);
    }

    @Test
    public void capturedKeyEventPersistsEquivalentKeybind()
    {
        JComponent control = binder.keybindSetting(
            "reimportHotkey", "Re-import hotkey", () -> Keybind.NOT_SET);
        KeybindCaptureButton button = (KeybindCaptureButton) control;
        KeyEvent event = keyPressed(button, KeyEvent.VK_K);

        button.doClick();
        capture(button, event);

        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "reimportHotkey", new Keybind(event));
    }

    @Test
    public void failedWriteRestoresLastConfirmedValueAndReportsStatus()
    {
        JCheckBox control = binder.booleanSetting(
            "showHud", "Show in-game HUD", () -> true);
        doThrow(new RuntimeException("no disk"))
            .when(configManager).setConfiguration(
                FateLockedConfig.GROUP, "showHud", false);

        control.doClick();

        assertTrue(control.isSelected());
        assertEquals(Arrays.asList("couldn't save setting"), statuses);
        verify(configManager, times(1)).setConfiguration(
            FateLockedConfig.GROUP, "showHud", false);
    }

    @Test
    public void keysContainOnlyControlsCreatedByCaller()
    {
        binder.booleanSetting("showHud", "Show in-game HUD", () -> true);
        binder.keybindSetting("reimportHotkey", "Re-import hotkey", () -> Keybind.NOT_SET);
        binder.colorSetting("unlockedColor", "Unlocked colour", () -> Color.BLUE);

        assertEquals(new LinkedHashSet<>(Arrays.asList(
            "showHud", "reimportHotkey", "unlockedColor")), binder.keys());
        assertNotNull(binder.keys());
        assertFalse(binder.keys().contains("pairingCode"));
    }

    private static void capture(KeybindCaptureButton button, KeyEvent event)
    {
        button.getKeyListeners()[0].keyPressed(event);
    }

    private static KeyEvent keyPressed(Component source, int keyCode)
    {
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
}

package com.fatelocked;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusEvent;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
        assertEquals(chosen, control.getBackground());
    }

    @Test
    public void translucentColoursUseOpaquePreviewsButPersistOriginalRgba()
    {
        Color initial = new Color(16, 185, 129, 90);
        Color chosen = new Color(239, 68, 68, 120);
        Color[] chooserInitial = new Color[1];
        FateLockedConfigBinder colorBinder = new FateLockedConfigBinder(
            configManager, statuses::add, (parent, title, supplied) -> {
                chooserInitial[0] = supplied;
                return chosen;
            });
        JButton control = (JButton) colorBinder.colorSetting(
            "unlockedColor", "Unlocked color", () -> initial);

        assertEquals(
            new Color(16, 185, 129), control.getBackground());
        control.doClick();

        assertSame(initial, chooserInitial[0]);
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, "unlockedColor", chosen);
        assertEquals(
            new Color(239, 68, 68), control.getBackground());
        assertEquals(255, control.getBackground().getAlpha());
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
        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, "showHud", Boolean.class)).thenReturn(false);

        control.doClick();

        assertTrue(control.isSelected());
        assertEquals(Arrays.asList("couldn't save setting"), statuses);
        verify(configManager, times(1)).setConfiguration(
            FateLockedConfig.GROUP, "showHud", false);
    }

    @Test
    public void capturedSpaceDoesNotReenterCaptureOrSaveTwice()
    {
        DispatchableKeybindCaptureButton button =
            new DispatchableKeybindCaptureButton(Keybind.NOT_SET, value ->
                configManager.setConfiguration(
                    FateLockedConfig.GROUP, "reimportHotkey", value));
        KeyEvent pressed = spaceKeyEvent(button, KeyEvent.KEY_PRESSED, 100L);
        KeyEvent released = spaceKeyEvent(button, KeyEvent.KEY_RELEASED, 101L);

        button.doClick();
        button.processKeyEventForTest(pressed);
        button.processKeyEventForTest(released);

        verify(configManager, times(1)).setConfiguration(
            FateLockedConfig.GROUP, "reimportHotkey", new Keybind(pressed));
        assertFalse("Press a key\u2026".equals(button.getText()));
    }

    @Test
    public void focusLossCancelsCaptureAndRestoresConfirmedDisplay()
    {
        KeybindCaptureButton button = (KeybindCaptureButton) binder.keybindSetting(
            "reimportHotkey", "Re-import hotkey", () -> Keybind.NOT_SET);

        button.doClick();
        focusLost(button);

        assertEquals(Keybind.NOT_SET.toString(), button.getText());
        verifyNoInteractions(configManager);
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
    private static void focusLost(KeybindCaptureButton button)
    {
        for (java.awt.event.FocusListener listener : button.getFocusListeners())
        {
            listener.focusLost(new FocusEvent(button, FocusEvent.FOCUS_LOST));
        }
    }

    private static KeyEvent spaceKeyEvent(Component source, int eventId, long when)
    {
        return new KeyEvent(source, eventId, when, 0, KeyEvent.VK_SPACE, ' ',
            KeyEvent.KEY_LOCATION_STANDARD)
        {
            @Override
            public int getExtendedKeyCode()
            {
                return getKeyCode();
            }
        };
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
    private static final class DispatchableKeybindCaptureButton
        extends KeybindCaptureButton
    {
        private DispatchableKeybindCaptureButton(Keybind keybind,
            java.util.function.Consumer<Keybind> captureListener)
        {
            super(keybind, captureListener);
        }

        private void processKeyEventForTest(KeyEvent event)
        {
            processKeyEvent(event);
        }
    }
}
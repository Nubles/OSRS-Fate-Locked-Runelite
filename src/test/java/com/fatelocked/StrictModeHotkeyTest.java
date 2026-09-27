package com.fatelocked;

import com.fatelocked.guardian.StrictModePause;
import com.fatelocked.guardian.StrictModeStatusView;
import net.runelite.api.Client;
import net.runelite.client.config.Keybind;
import net.runelite.client.util.HotkeyListener;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B16: an optional hotkey pauses Strict Mode for 60 seconds, through the
 * client thread as the banner's button does. It is not set by default, and
 * does nothing while Strict Mode is off.
 */
public class StrictModeHotkeyTest
{
    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final FateLockedPanel panel = mock(FateLockedPanel.class);

    @Before
    public void setUp() throws Exception
    {
        PluginTestSupport.runQueuedWorkInline(plugin);
        PluginTestSupport.set(plugin, "config", config);
        PluginTestSupport.set(plugin, "panel", panel);
        PluginTestSupport.set(plugin, "client", mock(Client.class));
    }

    @Test
    public void itIsNotSetByDefault()
    {
        assertEquals(Keybind.NOT_SET, new FateLockedConfig() { }.pauseStrictModeHotkey());
    }

    @Test
    public void pressingItPausesStrictModeForSixtySeconds() throws Exception
    {
        when(config.strictMode()).thenReturn(true);

        hotkey().hotkeyPressed();

        StrictModePause pause = (StrictModePause) PluginTestSupport.get(plugin, "strictPause");
        assertTrue(pause.isPaused());
        assertTrue(pause.remainingSeconds() > 55);
        ArgumentCaptor<StrictModeStatusView> shown = ArgumentCaptor.forClass(StrictModeStatusView.class);
        verify(panel).updateStrictMode(shown.capture());
        assertEquals(StrictModeStatusView.Tone.PAUSED, shown.getValue().getTone());
        assertEquals(StrictModeStatusView.Tone.PAUSED, plugin.getStrictModeStatus().getTone());
    }

    @Test
    public void itDoesNothingWhileStrictModeIsOff() throws Exception
    {
        when(config.strictMode()).thenReturn(false);

        hotkey().hotkeyPressed();

        assertFalse(((StrictModePause) PluginTestSupport.get(plugin, "strictPause")).isPaused());
    }

    @Test
    public void itReadsTheSetting() throws Exception
    {
        Keybind keybind = new Keybind(java.awt.event.KeyEvent.VK_F9, 0);
        when(config.pauseStrictModeHotkey()).thenReturn(keybind);
        java.lang.reflect.Field supplier = HotkeyListener.class.getDeclaredField("keybind");
        supplier.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<Keybind> read = (java.util.function.Supplier<Keybind>) supplier.get(hotkey());
        assertEquals(keybind, read.get());
    }

    private HotkeyListener hotkey() throws Exception
    {
        return (HotkeyListener) PluginTestSupport.get(plugin, "pauseStrictHotkey");
    }
}

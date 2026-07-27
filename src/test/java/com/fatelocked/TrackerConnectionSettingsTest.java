package com.fatelocked;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class TrackerConnectionSettingsTest
{
    private static final String PAIRING_CODE = "0123456789abcdef0123456789abcdef";
    private ConfigManager configManager;

    @Before
    public void setUp()
    {
        configManager = mock(ConfigManager.class);
    }

    @Test
    public void pairingIdentityUsesANonVisibleInternalKey()
    {
        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        settings.replacePairingCode(PAIRING_CODE);
        verify(configManager).setConfiguration(
            FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY, PAIRING_CODE);
    }

    @Test
    public void successfulUnifiedPairingClearsOnlyLegacyConnectionKeys()
    {
        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        settings.clearLegacySettings();
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
        verifyNoMoreInteractions(configManager);
    }

    @Test
    public void blankAndInvalidStoredPairingCodesAreUnpaired()
    {
        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY)).thenReturn("   ");
        assertEquals("", settings.pairingCode());
        assertFalse(settings.isPaired());
        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY))
            .thenReturn("0123456789abcdef0123456789abcdeg");
        assertEquals("", settings.pairingCode());
        assertFalse(settings.isPaired());
    }

    @Test
    public void validStoredPairingCodeIsPaired()
    {
        when(configManager.getConfiguration(
            FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY))
            .thenReturn(" " + PAIRING_CODE + " ");
        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        assertEquals(PAIRING_CODE, settings.pairingCode());
        assertTrue(settings.isPaired());
    }

    @Test
    public void tokensAreScopedByTypeAndPairingCode()
    {
        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        String otherCode = "fedcba9876543210fedcba9876543210";
        when(configManager.getConfiguration(FateLockedConfig.GROUP, "eventToken." + PAIRING_CODE))
            .thenReturn("first-token");
        assertEquals("first-token", settings.token("eventToken", PAIRING_CODE));
        settings.saveToken("runToken", otherCode, "second-token");
        verify(configManager).getConfiguration(FateLockedConfig.GROUP, "eventToken." + PAIRING_CODE);
        verify(configManager).setConfiguration(FateLockedConfig.GROUP,
            "runToken." + otherCode, "second-token");
    }
}

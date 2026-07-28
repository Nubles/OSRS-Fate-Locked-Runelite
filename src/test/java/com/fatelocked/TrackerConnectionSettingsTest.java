package com.fatelocked;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    public void successfulUnifiedPairingClearsLegacyConnectionKeysAndRelayTokens()
    {
        when(configManager.getConfigurationKeys(FateLockedConfig.GROUP + "."))
            .thenReturn(Arrays.asList(
                FateLockedConfig.GROUP + ".eventToken." + PAIRING_CODE,
                "stateToken." + PAIRING_CODE,
                FateLockedConfig.GROUP + ".suggestToken." + PAIRING_CODE,
                "ackToken." + PAIRING_CODE,
                FateLockedConfig.GROUP + ".trackerPairingCode",
                FateLockedConfig.GROUP + ".strictMode"));

        TrackerConnectionSettings settings = new TrackerConnectionSettings(configManager);
        settings.clearLegacySettings();

        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
        verify(configManager).unsetConfiguration(
            FateLockedConfig.GROUP, "eventToken." + PAIRING_CODE);
        verify(configManager).unsetConfiguration(
            FateLockedConfig.GROUP, "stateToken." + PAIRING_CODE);
        verify(configManager).unsetConfiguration(
            FateLockedConfig.GROUP, "suggestToken." + PAIRING_CODE);
        verify(configManager).unsetConfiguration(
            FateLockedConfig.GROUP, "ackToken." + PAIRING_CODE);
        verify(configManager, never()).unsetConfiguration(
            FateLockedConfig.GROUP, TrackerConnectionSettings.PAIRING_CODE_KEY);
        verify(configManager, never()).unsetConfiguration(
            FateLockedConfig.GROUP, "strictMode");
    }

    @Test
    public void legacyCleanupToleratesMissingConfigurationKeyEnumeration()
    {
        when(configManager.getConfigurationKeys(FateLockedConfig.GROUP + "."))
            .thenReturn(null);

        new TrackerConnectionSettings(configManager).clearLegacySettings();

        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
        verify(configManager).unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
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
}

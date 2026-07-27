package com.fatelocked;

import com.google.inject.Inject;
import net.runelite.client.config.ConfigManager;

final class TrackerConnectionSettings
{
    static final String RELAY_BASE_URL =
        "https://fate-relay.fatelocked.workers.dev";
    static final String PAIRING_CODE_KEY = "trackerPairingCode";
    private static final String CODE_PATTERN = "[0-9a-f]{32}";

    private final ConfigManager configManager;

    @Inject
    TrackerConnectionSettings(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    String pairingCode()
    {
        String value = configManager.getConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY);
        return value != null && value.trim().matches(CODE_PATTERN)
            ? value.trim() : "";
    }

    boolean isPaired()
    {
        return !pairingCode().isEmpty();
    }

    void replacePairingCode(String code)
    {
        if (code == null || !code.matches(CODE_PATTERN))
        {
            throw new IllegalArgumentException("Invalid pairing code");
        }
        configManager.setConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY, code);
    }

    void clearPairing()
    {
        configManager.unsetConfiguration(
            FateLockedConfig.GROUP, PAIRING_CODE_KEY);
    }

    void clearLegacySettings()
    {
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "onlineSync");
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "syncCode");
        configManager.unsetConfiguration(FateLockedConfig.GROUP, "relayUrl");
    }

    String token(String prefix, String code)
    {
        return configManager.getConfiguration(
            FateLockedConfig.GROUP, prefix + "." + code);
    }

    void saveToken(String prefix, String code, String token)
    {
        configManager.setConfiguration(
            FateLockedConfig.GROUP, prefix + "." + code, token);
    }
}

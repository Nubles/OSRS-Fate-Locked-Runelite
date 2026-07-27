package com.fatelocked;

import java.util.UUID;

final class PairingSupport
{
    private static final String TRACKER_URL =
        "https://nubles.github.io/OSRS-Fate-Locked/";

    private PairingSupport()
    {
    }

    static String newCode()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static String trackerPairingUrl(String code)
    {
        return TRACKER_URL + "#runelite-pair=" + code;
    }
}

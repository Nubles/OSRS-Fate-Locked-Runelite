package com.fatelocked;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    /**
     * Names a pairing in local files without writing the code: the first 16
     * hex digits of its SHA-256. Null when there is no pairing.
     */
    static String tag(String code)
    {
        if (code == null || code.trim().isEmpty())
        {
            return null;
        }
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(code.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++)
            {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException ex)
        {
            // Every Java runtime provides SHA-256.
            throw new IllegalStateException(ex);
        }
    }
}

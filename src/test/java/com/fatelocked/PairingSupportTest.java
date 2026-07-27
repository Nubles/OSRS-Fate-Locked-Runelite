package com.fatelocked;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PairingSupportTest
{
    @Test
    public void generatedCodeIsLowercaseHexWithThirtyTwoCharacters()
    {
        assertTrue(PairingSupport.newCode().matches("[0-9a-f]{32}"));
    }

    @Test
    public void trackerUrlCarriesThePairingRequestInTheHash()
    {
        assertEquals(
            "https://nubles.github.io/OSRS-Fate-Locked/#runelite-pair="
                + "0123456789abcdef0123456789abcdef",
            PairingSupport.trackerPairingUrl(
                "0123456789abcdef0123456789abcdef"));
    }
}

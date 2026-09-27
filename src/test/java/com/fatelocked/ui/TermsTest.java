package com.fatelocked.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fatelocked.rules.PermissionStatus;
import java.time.Duration;
import org.junit.Test;

public class TermsTest
{
    @Test
    public void aPlaceAndARowSayTheirStatusInTheWebAppsWords()
    {
        assertEquals("Unlocked", Terms.place(PermissionStatus.ALLOWED));
        assertEquals("Not ready", Terms.place(PermissionStatus.NOT_READY));
        assertEquals("Locked", Terms.place(PermissionStatus.LOCKED));
        assertEquals("Uncharted", Terms.place(PermissionStatus.UNKNOWN));

        assertEquals("Can do", Terms.row(PermissionStatus.ALLOWED));
        assertEquals("Not ready", Terms.row(PermissionStatus.NOT_READY));
        assertEquals("Locked", Terms.row(PermissionStatus.LOCKED));
        assertEquals("Needs checking", Terms.row(PermissionStatus.UNKNOWN));
    }

    @Test
    public void ritualsAreNamedAsTheWebAppNamesThem()
    {
        assertEquals("Ritual of Clarity", Terms.ritual("LUCK"));
        assertEquals("Ritual of Greed", Terms.ritual("GREED"));
        assertNull(Terms.ritual("NONE"));
        assertNull("an id this build doesn't know is never shown raw", Terms.ritual("CARTOGRAPHER"));
        assertNull(Terms.ritual(null));
    }

    @Test
    public void keysAndTheTagAreSpelledAsTheWebAppSpellsThem()
    {
        assertEquals("Omni-Keys", Terms.OMNI_KEYS);
        assertEquals("Chaos Keys", Terms.CHAOS_KEYS);
        assertEquals("Fate Points", Terms.FATE_POINTS);
        assertEquals(" (Locked)", Terms.LOCKED_TAG);
    }

    @Test
    public void agesReadNaturally()
    {
        assertEquals("just now", Copy.ago(Duration.ofSeconds(59)));
        assertEquals("just now", Copy.ago(Duration.ofSeconds(-5)));
        assertEquals("1 min ago", Copy.ago(Duration.ofSeconds(60)));
        assertEquals("59 min ago", Copy.ago(Duration.ofMinutes(59).plusSeconds(59)));
        assertEquals("1 h ago", Copy.ago(Duration.ofMinutes(60)));
        assertEquals("23 h ago", Copy.ago(Duration.ofHours(23).plusMinutes(59)));
        assertEquals("1 day ago", Copy.ago(Duration.ofHours(24)));
        assertEquals("2 days ago", Copy.ago(Duration.ofHours(48)));
    }

    @Test
    public void anIdShowsOnlyItsLastFourCharacters()
    {
        assertEquals("…a1b2", Copy.shortId("5f3c9d0e7a1b4c2d8e6f0a1b2c3da1b2"));
        assertEquals("a1b2", Copy.shortId("a1b2"));
        assertNull(Copy.shortId(""));
        assertNull(Copy.shortId(null));
    }

    @Test
    public void progressNamesItsUnit()
    {
        assertEquals("15 of 187 areas unlocked", Copy.unlocked(15, 187, "areas"));
    }
}

package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Names compare as Jagex compares them, and only names the relay takes are sent. */
public class BadgeNamesTest
{
    @Test
    public void spacesUnderscoresHyphensAndCaseAreAlike()
    {
        assertEquals("iron example", BadgeNames.normalise("Iron Example"));
        assertEquals("iron example", BadgeNames.normalise("iron_example"));
        assertEquals("iron example", BadgeNames.normalise("IRON-EXAMPLE"));
        assertEquals("iron example", BadgeNames.normalise("Iron Example"));
        assertEquals("iron example", BadgeNames.normalise("  Iron  _ Example "));
    }

    @Test
    public void theGamesTagsInANameAreIgnored()
    {
        assertEquals("iron example", BadgeNames.normalise("<img=2>Iron Example"));
        assertEquals("iron example", BadgeNames.normalise("<col=ff0000>Iron Example</col>"));
        assertEquals("", BadgeNames.normalise("<img=3>"));
    }

    @Test
    public void nothingIsEmpty()
    {
        assertEquals("", BadgeNames.normalise(null));
        assertEquals("", BadgeNames.normalise(""));
        assertEquals("", BadgeNames.normalise(" _- "));
    }

    @Test
    public void theRelayGetsTheNameAsWrittenWithPlainSpaces()
    {
        assertEquals("Iron Example", BadgeNames.shareable("Iron Example"));
        assertEquals("Iron-Ex_1", BadgeNames.shareable(" Iron-Ex_1 "));
        assertEquals("Iron Example", BadgeNames.shareable("<img=2>Iron Example"));
        assertEquals("abcdefghijkl", BadgeNames.shareable("abcdefghijkl"));
    }

    @Test
    public void namesTheRelayRefusesAreNotSent()
    {
        assertNull(BadgeNames.shareable(null));
        assertNull(BadgeNames.shareable("   "));
        assertNull(BadgeNames.shareable("abcdefghijklm"));
        assertNull(BadgeNames.shareable("Iron.Example"));
        assertNull(BadgeNames.shareable("Ironé"));
    }
}

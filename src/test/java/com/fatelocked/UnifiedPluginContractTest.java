package com.fatelocked;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnifiedPluginContractTest
{
    @Test
    public void pluginIdentityRemainsSingular()
    {
        PluginDescriptor descriptor =
            FateLockedPlugin.class.getAnnotation(PluginDescriptor.class);
        assertEquals("Fate Locked Ironman", descriptor.name());
        assertFalse(descriptor.description().toLowerCase()
            .contains("send"));
        // The plugin list says what players get, in their words (accuracy review, P-39).
        assertTrue(descriptor.description(), descriptor.description().contains("Roll inbox")
            && descriptor.description().contains("Strict Mode") && !descriptor.description().contains("app-authored"));
    }
}

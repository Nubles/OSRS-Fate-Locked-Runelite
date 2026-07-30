package com.fatelocked;

import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
    }
}

package com.fatelocked;

import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FateLockedConfigTest
{
    @Test
    public void strictModeDefaultsOff()
    {
        FateLockedConfig config = new FateLockedConfig() {};
        assertFalse(config.strictMode());
    }

    @Test
    public void configSurfaceHasOneStrictToggleAndNoTravelGuardianItem()
    {
        int strictModeItems = 0;
        for (Method method : FateLockedConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null) continue;
            String surface = (item.keyName() + " " + item.name() + " "
                + item.description()).toLowerCase(Locale.ROOT);
            String compact = surface.replaceAll("[^a-z0-9]", "");
            assertFalse(compact.contains("travelguardian"));
            assertFalse(surface.contains("travel")
                && surface.contains("guardian"));
            if ("strictMode".equals(item.keyName()))
            {
                strictModeItems++;
                assertEquals("Strict Mode", item.name());
            }
        }
        assertEquals(1, strictModeItems);
    }
}

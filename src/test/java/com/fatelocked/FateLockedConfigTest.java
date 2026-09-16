package com.fatelocked;

import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    public void configRetainsSettingsAndAddsDefaultOffNetworkConsent()
    {
        Map<String, ConfigItem> items = configItemsByKey();
        assertEquals(31, items.size());
        assertFalse(new FateLockedConfig() { }.trackerNetworkAccess());
        assertEquals(FateLockedConfig.NETWORK_WARNING,
            items.get(FateLockedConfig.NETWORK_ACCESS_KEY).warning());
        assertFalse(items.containsKey("onlineSync"));
        assertFalse(items.containsKey("syncCode"));
        assertFalse(items.containsKey("relayUrl"));
        assertEquals("Strict Mode", items.get("strictMode").name());
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

    private static Map<String, ConfigItem> configItemsByKey()
    {
        Map<String, ConfigItem> items = new LinkedHashMap<>();
        for (Method method : FateLockedConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null)
            {
                items.put(item.keyName(), item);
            }
        }
        return items;
    }
}

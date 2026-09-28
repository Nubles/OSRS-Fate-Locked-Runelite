package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

/** The owner's review, 28 Sept: the parts of Here left open are kept in the player's RuneLite profile. */
public class FateLockedHereFoldsTest
{
    private final ConfigManager configs = mock(ConfigManager.class);
    private final FateLockedPlugin plugin = new FateLockedPlugin();

    @Test
    public void whatsOpenIsSavedAndReadBack() throws Exception
    {
        PluginTestSupport.set(plugin, "configManager", configs);
        Method save = FateLockedPlugin.class.getDeclaredMethod("saveHereOpen", Set.class);
        save.setAccessible(true);

        save.invoke(plugin, new TreeSet<>(Arrays.asList("SKILLING", "SKILLING/Woodcutting")));
        verify(configs).setConfiguration(FateLockedConfig.GROUP, FateLockedPlugin.HERE_OPEN,
            "SKILLING,SKILLING/Woodcutting");
        save.invoke(plugin, Collections.emptySet());
        verify(configs).unsetConfiguration(FateLockedConfig.GROUP, FateLockedPlugin.HERE_OPEN);

        when(configs.getConfiguration(FateLockedConfig.GROUP, FateLockedPlugin.HERE_OPEN))
            .thenReturn("SKILLING, ,BANKS");
        assertEquals(new TreeSet<>(Arrays.asList("BANKS", "SKILLING")), plugin.hereOpen());
        when(configs.getConfiguration(FateLockedConfig.GROUP, FateLockedPlugin.HERE_OPEN)).thenReturn(null);
        assertEquals(Collections.emptySet(), plugin.hereOpen());
        when(configs.getConfiguration(FateLockedConfig.GROUP, FateLockedPlugin.HERE_OPEN))
            .thenThrow(new IllegalStateException("unreadable settings"));
        assertEquals("an unreadable profile opens nothing", Collections.emptySet(), plugin.hereOpen());
    }
}

package com.fatelocked;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * Hands a spot the Here card shows the way to over to the Shortest Path plugin, from the
 * Plugin Hub, when the player runs it (the owner's review, 28 Sept): it draws the walk there,
 * round walls and through doors. It is asked with RuneLite's plugin messages, in the same
 * words Quest Helper uses; nothing goes anywhere else. Without it, nothing is sent.
 */
final class ShortestPathHandOff
{
    static final String NAMESPACE = "shortestpath";
    static final String PATH = "path";
    static final String CLEAR = "clear";
    static final String START = "start";
    static final String TARGET = "target";
    /** The name it goes by in RuneLite's plugin list. */
    static final String NAME = "Shortest Path";

    private final PluginManager plugins;
    private final EventBus eventBus;

    ShortestPathHandOff(PluginManager plugins, EventBus eventBus)
    {
        this.plugins = plugins;
        this.eventBus = eventBus;
    }

    /** Whether the player has Shortest Path running now. */
    boolean running()
    {
        if (plugins == null)
        {
            return false;
        }
        for (Plugin plugin : plugins.getPlugins())
        {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null && NAME.equals(descriptor.name()) && plugins.isPluginActive(plugin))
            {
                return true;
            }
        }
        return false;
    }

    /** Ask it for the way from one tile to another: true when it was asked, as it runs. */
    boolean route(WorldPoint from, WorldPoint to)
    {
        if (from == null || to == null || eventBus == null || !running())
        {
            return false;
        }
        Map<String, Object> data = new HashMap<>();
        data.put(START, from);
        data.put(TARGET, to);
        eventBus.post(new PluginMessage(NAMESPACE, PATH, data));
        return true;
    }

    /** Take down the way it was asked for. */
    void clear()
    {
        if (eventBus != null)
        {
            eventBus.post(new PluginMessage(NAMESPACE, CLEAR));
        }
    }
}

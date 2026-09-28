package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * The owner's review, 28 Sept: with Shortest Path running, the way to a spot seen before is
 * handed to it in the words Quest Helper uses; without it, nothing is sent.
 */
public class ShortestPathHandOffTest
{
    private static final WorldPoint FROM = new WorldPoint(3205, 3205, 0);
    private static final WorldPoint TO = new WorldPoint(3210, 3200, 0);

    private final PluginManager plugins = mock(PluginManager.class);
    private final EventBus eventBus = mock(EventBus.class);
    private final Plugin shortestPath = new ShortestPath();
    private final Plugin another = new Another();
    private final ShortestPathHandOff handOff = new ShortestPathHandOff(plugins, eventBus);

    @PluginDescriptor(name = "Shortest Path")
    private static final class ShortestPath extends Plugin
    {
    }

    @PluginDescriptor(name = "Quest Helper")
    private static final class Another extends Plugin
    {
    }

    @Before
    public void setUp()
    {
        when(plugins.getPlugins()).thenReturn(Arrays.asList(another, shortestPath));
    }

    @Test
    public void runningItIsAskedForTheWay()
    {
        when(plugins.isPluginActive(shortestPath)).thenReturn(true);

        assertTrue(handOff.running());
        assertTrue(handOff.route(FROM, TO));

        ArgumentCaptor<PluginMessage> sent = ArgumentCaptor.forClass(PluginMessage.class);
        verify(eventBus).post(sent.capture());
        assertEquals("shortestpath", sent.getValue().getNamespace());
        assertEquals("path", sent.getValue().getName());
        assertEquals(FROM, sent.getValue().getData().get("start"));
        assertEquals(TO, sent.getValue().getData().get("target"));
        assertEquals(2, sent.getValue().getData().size());
    }

    /** Installed but off, or not there at all: nothing is sent. */
    @Test
    public void notRunningNothingIsSent()
    {
        when(plugins.isPluginActive(another)).thenReturn(true);

        assertFalse(handOff.running());
        assertFalse(handOff.route(FROM, TO));
        verify(eventBus, never()).post(any());

        assertFalse(new ShortestPathHandOff(null, eventBus).route(FROM, TO));
    }

    /** No way from inside an instance or a boat, which have no place in the world. */
    @Test
    public void noWayWithoutBothEnds()
    {
        when(plugins.isPluginActive(shortestPath)).thenReturn(true);

        assertFalse(handOff.route(null, TO));
        assertFalse(handOff.route(FROM, null));
        verify(eventBus, never()).post(any());
    }

    @Test
    public void clearingTakesTheWayDown()
    {
        handOff.clear();

        ArgumentCaptor<PluginMessage> sent = ArgumentCaptor.forClass(PluginMessage.class);
        verify(eventBus).post(sent.capture());
        assertEquals("shortestpath", sent.getValue().getNamespace());
        assertEquals("clear", sent.getValue().getName());
        assertTrue(sent.getValue().getData() == null || sent.getValue().getData().isEmpty());
    }
}

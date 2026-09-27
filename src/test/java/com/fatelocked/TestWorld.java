package com.fatelocked;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Puts actors on a plain top-level scene the way ChunkLocator reads them
 * (B14): through the actor's world view and local point. The scene is based
 * at 0,0, so a local point is simply the world tile times 128.
 */
final class TestWorld
{
    private TestWorld()
    {
    }

    /** The client's top-level world view, made on first use. */
    static WorldView topLevel(Client client)
    {
        WorldView existing = client.getTopLevelWorldView();
        if (existing != null) return existing;
        WorldView view = mock(WorldView.class);
        Scene scene = mock(Scene.class);
        when(view.isTopLevel()).thenReturn(true);
        when(view.getId()).thenReturn(WorldView.TOPLEVEL);
        when(view.getScene()).thenReturn(scene);
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(client.getWorldView(WorldView.TOPLEVEL)).thenReturn(view);
        return view;
    }

    /** Stand an actor (the player, an NPC) on a world tile. */
    static void standAt(Client client, Actor actor, WorldPoint tile)
    {
        WorldView view = topLevel(client);
        LocalPoint point = new LocalPoint(tile.getX() * 128 + 64, tile.getY() * 128 + 64, WorldView.TOPLEVEL);
        when(actor.getWorldView()).thenReturn(view);
        when(actor.getLocalLocation()).thenReturn(point);
    }
}

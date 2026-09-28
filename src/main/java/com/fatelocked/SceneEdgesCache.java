package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import net.runelite.api.WorldView;

/**
 * The loaded scene as the rules draw it, worked out again only when a scene loads, or the
 * plane, the base or the rules change (A9). The game view and the minimap draw from one.
 * Client thread.
 */
final class SceneEdgesCache
{
    private DecisionService decisions;
    private int generation = -1;
    private int plane = -1;
    private int baseX;
    private int baseY;
    private SceneEdges scene = SceneEdges.NONE;

    /** The scene for these rules, on this plane of this load of the scene. */
    SceneEdges get(DecisionService rules, WorldView view, int onPlane, int load, ChunkLocator locator)
    {
        if (rules != decisions || load != generation || onPlane != plane
            || view.getBaseX() != baseX || view.getBaseY() != baseY)
        {
            decisions = rules;
            generation = load;
            plane = onPlane;
            baseX = view.getBaseX();
            baseY = view.getBaseY();
            scene = SceneEdges.of(zones(rules, locator, view.getSizeX() / SceneEdges.ZONE,
                view.getSizeY() / SceneEdges.ZONE));
        }
        return scene;
    }

    /** Each zone of the scene, as the rules tint the chunk it is a copy of. */
    private static SceneEdges.Zone[][] zones(DecisionService rules, ChunkLocator locator, int width, int height)
    {
        SceneEdges.Zone[][] zones = new SceneEdges.Zone[width][height];
        for (int zx = 0; zx < width; zx++)
        {
            for (int zy = 0; zy < height; zy++)
            {
                CanonicalChunk chunk = locator.sceneZone(zx, zy);
                zones[zx][zy] = new SceneEdges.Zone(
                    chunk == null ? TintPolicy.Tint.UNKNOWN : TintPolicy.at(rules, chunk), chunk);
            }
        }
        return zones;
    }
}

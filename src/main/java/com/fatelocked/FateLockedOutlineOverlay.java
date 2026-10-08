package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Trust;
import com.fatelocked.sidebar.LockedThings;
import com.fatelocked.ui.Palette;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Things outlined in the game view (the owner's calls, 8 Oct): a bank booth, shop keeper,
 * skilling spot or monster near the player, red when the rules lock it, orange when a skill tier
 * doesn't open it yet, green when it's open, and nothing written over it. Only things the player
 * can click are outlined: a tree with no Chop option is scenery, and a monster needs Attack. What
 * counts is the tracker's own rows ({@link LockedThings}), so the outlines say what the Here card
 * and the (Locked) tag say. Land that is locked as a whole is left to its borders and shade.
 * Advice only: nothing is blocked.
 *
 * <p>A monster keeps the most open look it has had while it's in view, so one walking between
 * chunks whose rows differ doesn't flicker. Outline locked things turns all of it off; a switch
 * for each kind sits under it. What to outline is worked out once a game tick, within a few tiles
 * of the player, never every frame.
 */
public class FateLockedOutlineOverlay extends Overlay
{
    /** How far from the player, in tiles, things are looked at. */
    static final int RADIUS = 12;
    private static final int OUTLINE_WIDTH = 2;
    private static final int FEATHER = 2;
    private static final int ZONE_TILES = 8;

    /** One thing to outline, and how. */
    @Value
    static class Outlined
    {
        /** The NPC, or null for an object. */
        NPC npc;
        TileObject object;
        LockedThings.Look look;
    }

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;
    private final ModelOutlineRenderer outlines;

    private int scannedTick = -1;
    private List<Outlined> found = Collections.emptyList();
    /** The look each monster in view has had, for the rules it was seen under. */
    private Map<NPC, LockedThings.Thing> npcLooks = new IdentityHashMap<>();
    private DecisionService npcLooksFor;

    @Inject
    FateLockedOutlineOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config,
        ModelOutlineRenderer outlines)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.outlines = outlines;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.outlineLocked())
        {
            found = Collections.emptyList();
            npcLooks = new IdentityHashMap<>();
            return null;
        }
        int tick = client.getTickCount();
        if (tick != scannedTick)
        {
            scannedTick = tick;
            found = scan();
        }
        Palette palette = plugin.palette();
        for (Outlined thing : found)
        {
            Color colour = palette.text(tone(thing.getLook()));
            try
            {
                if (thing.getNpc() != null)
                {
                    outlines.drawOutline(thing.getNpc(), OUTLINE_WIDTH, colour, FEATHER);
                }
                else
                {
                    outlines.drawOutline(thing.getObject(), OUTLINE_WIDTH, colour, FEATHER);
                }
            }
            catch (RuntimeException gone)
            {
                // Seen in game (8 Oct): an object can lose its model between the tick's scan and
                // this frame, and the outline renderer then throws. Skip it until the next scan.
            }
        }
        return null;
    }

    static Palette.Tone tone(LockedThings.Look look)
    {
        switch (look)
        {
            case LOCKED:
                return Palette.Tone.BAD;
            case TIER:
                return Palette.Tone.PENDING;
            default:
                return Palette.Tone.GOOD;
        }
    }

    /**
     * Whether the game offers something to click that the row is about: a monster its Attack,
     * anything else any option at all. Scenery that shares a name, such as a tree with no Chop
     * down, offers none.
     */
    static boolean clickable(LockedThings.Kind kind, boolean npc, String[] options)
    {
        if (options == null) return false;
        for (String option : options)
        {
            if (option == null || option.isEmpty()) continue;
            if (!npc || kind != LockedThings.Kind.MONSTERS || "attack".equalsIgnoreCase(option)) return true;
        }
        return false;
    }

    /** The more open of two looks for one monster, so it keeps the best one it has had. */
    static LockedThings.Thing steadier(LockedThings.Thing before, LockedThings.Thing now)
    {
        if (now == null || now.getLook() == null) return before;
        if (before == null || before.getLook() == null) return now;
        return now.getLook().ordinal() <= before.getLook().ordinal() ? now : before;
    }

    /** Whether the settings outline a thing of this kind that looks like this. */
    static boolean wanted(FateLockedConfig config, LockedThings.Thing thing)
    {
        if (thing == null || thing.getLook() == null) return false;
        if (thing.getLook() == LockedThings.Look.OPEN && !config.outlineOpen()) return false;
        switch (thing.getKind())
        {
            case BANKS_AND_SHOPS:
                return config.outlineBanksAndShops();
            case SKILLING:
                return config.outlineSkilling();
            case MONSTERS:
                return config.outlineMonsters();
            default:
                return false;
        }
    }

    /** What to outline near the player now; nothing on another character or with no rules. */
    private List<Outlined> scan()
    {
        DecisionService decisions = plugin.decisions();
        WorldView view = client.getTopLevelWorldView();
        Player player = client.getLocalPlayer();
        if (decisions.trust() != Trust.TRUSTED || view == null || player == null || player.getWorldView() != view)
        {
            return Collections.emptyList();
        }
        LocalPoint me = player.getLocalLocation();
        if (me == null) return Collections.emptyList();
        ChunkLocator locator = plugin.chunkLocator();
        List<Outlined> next = new ArrayList<>();
        Map<NPC, LockedThings.Thing> looks = new IdentityHashMap<>();
        Map<NPC, LockedThings.Thing> before = npcLooksFor == decisions ? npcLooks : Collections.emptyMap();

        if (view.npcs() != null)
        {
            for (NPC npc : view.npcs())
            {
                if (npc == null || npc.getLocalLocation() == null
                    || npc.getLocalLocation().distanceTo(me) > RADIUS * 128)
                {
                    continue;
                }
                NPCComposition shown = npc.getTransformedComposition();
                String name = shown != null ? shown.getName() : npc.getName();
                String[] options = shown != null ? shown.getActions() : null;
                LockedThings.Thing row = thing(decisions, locator.actor(npc), name, options);
                if (row != null && !clickable(row.getKind(), true, options)) row = null;
                LockedThings.Thing thing = steadier(before.get(npc), row);
                if (thing == null) continue;
                looks.put(npc, thing);
                if (wanted(config, thing))
                {
                    next.add(new Outlined(npc, null, thing.getLook()));
                }
            }
        }
        npcLooks = looks;
        npcLooksFor = decisions;

        Scene scene = view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        int plane = view.getPlane();
        if (tiles == null || plane < 0 || plane >= tiles.length) return next;
        Tile[][] floor = tiles[plane];
        Map<Integer, CanonicalChunk> zones = new HashMap<>();
        Set<TileObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int sx = me.getSceneX();
        int sy = me.getSceneY();
        for (int x = Math.max(0, sx - RADIUS); x <= sx + RADIUS && x < floor.length; x++)
        {
            for (int y = Math.max(0, sy - RADIUS); y <= sy + RADIUS && y < floor[x].length; y++)
            {
                Tile tile = floor[x][y];
                if (tile == null) continue;
                int zoneX = x / ZONE_TILES;
                int zoneY = y / ZONE_TILES;
                CanonicalChunk chunk = zones.computeIfAbsent(zoneX * 1024 + zoneY,
                    key -> locator.sceneZone(zoneX, zoneY));
                if (chunk == null) continue;
                for (TileObject object : SceneSearch.objectsOn(tile))
                {
                    if (object == null || !seen.add(object)) continue;
                    ObjectComposition shown = SceneSearch.shown(client, object);
                    if (shown == null) continue;
                    LockedThings.Thing thing = thing(decisions, chunk, shown.getName(), shown.getActions());
                    if (wanted(config, thing) && clickable(thing.getKind(), false, shown.getActions()))
                    {
                        next.add(new Outlined(null, object, thing.getLook()));
                    }
                }
            }
        }
        return next;
    }

    /** The row for a thing in a chunk that isn't locked as a whole; null otherwise. */
    private LockedThings.Thing thing(DecisionService decisions, CanonicalChunk chunk, String name, String[] options)
    {
        if (chunk == null || name == null || "null".equals(name)) return null;
        LockedThings things = plugin.lockedThings(chunk, decisions);
        return things.placeLocked() ? null : things.find(name, options);
    }
}

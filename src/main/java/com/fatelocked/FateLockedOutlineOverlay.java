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
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Locked things outlined in the game view (the owner's call, 8 Oct): a bank booth, shop keeper,
 * skilling spot or monster near the player that the rules lock, in the locked colour, or that a
 * skill tier doesn't open yet, in the not-ready colour, with a few words saying why. Open ones
 * too, in the unlocked colour, if the player asks. What counts is the tracker's own rows
 * ({@link LockedThings}), so the outlines say what the Here card and the (Locked) tag say. Land
 * that is locked as a whole is left to its borders and shade. Advice only: nothing is blocked.
 *
 * <p>Outline locked things turns all of it off; a switch for each kind sits under it. What to
 * outline is worked out once a game tick, within a few tiles of the player, never every frame.
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
        /** Beside it; null for an open one. */
        String label;
    }

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;
    private final ModelOutlineRenderer outlines;

    private int scannedTick = -1;
    private List<Outlined> found = Collections.emptyList();

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
            if (thing.getNpc() != null)
            {
                outlines.drawOutline(thing.getNpc(), OUTLINE_WIDTH, colour, FEATHER);
                if (thing.getLabel() != null)
                {
                    Point at = thing.getNpc().getCanvasTextLocation(graphics, thing.getLabel(),
                        thing.getNpc().getLogicalHeight() + 40);
                    if (at != null) OverlayUtil.renderTextLocation(graphics, at, thing.getLabel(), colour);
                }
            }
            else
            {
                outlines.drawOutline(thing.getObject(), OUTLINE_WIDTH, colour, FEATHER);
                if (thing.getLabel() != null)
                {
                    Point at = thing.getObject().getCanvasTextLocation(graphics, thing.getLabel(), 0);
                    if (at != null) OverlayUtil.renderTextLocation(graphics, at, thing.getLabel(), colour);
                }
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
                LockedThings.Thing thing = thing(decisions, locator.actor(npc), name,
                    shown != null ? shown.getActions() : null);
                if (wanted(config, thing))
                {
                    next.add(new Outlined(npc, null, thing.getLook(), thing.getLabel()));
                }
            }
        }

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
                    if (wanted(config, thing))
                    {
                        next.add(new Outlined(null, object, thing.getLook(), thing.getLabel()));
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

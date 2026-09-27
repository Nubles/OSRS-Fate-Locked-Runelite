package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fatelocked.SceneEdges.Kind;
import com.fatelocked.SceneEdges.Run;
import com.fatelocked.SceneEdges.Zone;
import com.fatelocked.TintPolicy.Tint;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.Test;

/** U3: where the scene's chunk lines run, worked out from its 8-tile zones. */
public class SceneEdgesTest
{
    /** 104 tiles across, as RuneLite loads the scene. */
    private static final int ZONES = 13;
    /** A scene whose chunk lines fall on its tiles 64 across and up. */
    private static final int LINE = 64;

    @Test
    public void aLockedChunkBesideAnUnlockedOneIsOneLockedRunOnTheChunkLine()
    {
        List<Run> runs = SceneEdges.of(scene((zx, zy) -> new Zone(
            zx < 8 ? Tint.UNLOCKED : Tint.LOCKED, surface(zx, zy))));

        assertEquals(Arrays.asList(
            new Run(true, LINE, 0, 104, Kind.LOCKED, 1),
            new Run(false, LINE, 0, 104, Kind.PLAIN, 0)), runs);
    }

    @Test
    public void lockedBesideLockedIsPlainAndAnUndecidedPlaceDrawsNothing()
    {
        assertEquals(Arrays.asList(
                new Run(true, LINE, 0, 104, Kind.PLAIN, 0),
                new Run(false, LINE, 0, 104, Kind.PLAIN, 0)),
            SceneEdges.of(scene((zx, zy) -> new Zone(Tint.LOCKED, surface(zx, zy)))));

        assertEquals(Arrays.asList(new Run(false, LINE, 0, 64, Kind.PLAIN, 0)),
            SceneEdges.of(scene((zx, zy) -> zx < 8 ? new Zone(Tint.UNLOCKED, surface(zx, zy))
                : new Zone(Tint.UNKNOWN, null))));
    }

    /** An instance is copied zone by zone, so one locked zone is edged on all four sides. */
    @Test
    public void anInstanceBreaksAtItsZones()
    {
        CanonicalChunk tomb = new CanonicalChunk(39, 53);
        List<Run> runs = SceneEdges.of(scene((zx, zy) -> zx == 5 && zy == 5
            ? new Zone(Tint.LOCKED, tomb) : new Zone(Tint.UNLOCKED, new CanonicalChunk(50, 50))));

        assertEquals(Arrays.asList(
            new Run(true, 40, 40, 48, Kind.LOCKED, 1),
            new Run(true, 48, 40, 48, Kind.LOCKED, -1),
            new Run(false, 40, 40, 48, Kind.LOCKED, 1),
            new Run(false, 48, 40, 48, Kind.LOCKED, -1)), runs);
    }

    @Test
    public void theScenesOwnBorderIsNeverAnEdge()
    {
        assertTrue(SceneEdges.of(scene((zx, zy) -> new Zone(Tint.UNLOCKED, new CanonicalChunk(50, 50)))).isEmpty());
        assertTrue(SceneEdges.of(scene((zx, zy) -> new Zone(Tint.LOCKED, new CanonicalChunk(46, 52)))).isEmpty());
    }

    @Test
    public void aRunBreaksWhereTheLockedSideChanges()
    {
        List<Run> runs = SceneEdges.of(scene((zx, zy) ->
            new Zone((zx < 8) == (zy < 8) ? Tint.UNLOCKED : Tint.LOCKED, surface(zx, zy))));

        assertEquals(Arrays.asList(
            new Run(true, LINE, 0, 64, Kind.LOCKED, 1),
            new Run(true, LINE, 64, 104, Kind.LOCKED, -1),
            new Run(false, LINE, 0, 64, Kind.LOCKED, 1),
            new Run(false, LINE, 64, 104, Kind.LOCKED, -1)), runs);
    }

    /** The surface chunk a zone of this scene lies in: four chunks, split 64 tiles in. */
    private static CanonicalChunk surface(int zx, int zy)
    {
        return new CanonicalChunk(50 + zx / 8, 50 + zy / 8);
    }

    private static Zone[][] scene(BiFunction<Integer, Integer, Zone> zone)
    {
        Zone[][] zones = new Zone[ZONES][ZONES];
        for (int zx = 0; zx < ZONES; zx++)
        {
            for (int zy = 0; zy < ZONES; zy++)
            {
                zones[zx][zy] = zone.apply(zx, zy);
            }
        }
        return zones;
    }
}

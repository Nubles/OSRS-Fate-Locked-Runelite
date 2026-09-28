package com.fatelocked;

import com.fatelocked.storage.LocalFileMerge;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Where the player has seen what the Here card can point at (the owner's review, 28 Sept), so
 * a click can show the way to one that is out of sight: for each chunk and row, the tiles it
 * was seen on. It is kept in the plugin's own folder on this computer, in {@link #FILE}, and
 * never sent anywhere. A row keeps the {@link #PER_ROW} spots found last, and a spot within
 * {@link #SAME_SPOT} tiles of one it has is the same one, as a shopkeeper wanders the shop.
 */
final class SpotMemory
{
    static final String FILE = "spots.json";
    static final int PER_ROW = 8;
    static final int SAME_SPOT = 3;

    /** A tile in the world. */
    @Value
    static class Spot
    {
        int x;
        int y;
        int plane;

        /** Whether another spot is on this one's floor, within {@link #SAME_SPOT} tiles of it. */
        boolean near(Spot other)
        {
            return plane == other.plane && Math.max(Math.abs(x - other.x), Math.abs(y - other.y)) <= SAME_SPOT;
        }
    }

    /** Each row's spots, the first found first, by {@link #key}. */
    private Map<String, List<Spot>> rows = new HashMap<>();
    /** Spots found empty since the last save, so the file loses them too. */
    private final Map<String, List<Spot>> gone = new HashMap<>();
    private boolean changed;

    static String key(CanonicalChunk chunk, String category, String row)
    {
        return chunk.getCx() + "," + chunk.getCy() + "|" + category + "|" + row;
    }

    /** One of a chunk's rows seen at a spot: true when the spot is new. */
    synchronized boolean see(CanonicalChunk chunk, String category, String row, Spot spot)
    {
        String key = key(chunk, category, row);
        if (!add(rows, key, spot))
        {
            return false;
        }
        List<Spot> emptied = gone.get(key);
        if (emptied != null)
        {
            emptied.removeIf(spot::near);
        }
        changed = true;
        return true;
    }

    /** The spot seen nearest a tile, on its floor first; null when none has been seen. */
    synchronized Spot nearest(CanonicalChunk chunk, String category, String row, Spot from)
    {
        List<Spot> spots = chunk == null || from == null ? null : rows.get(key(chunk, category, row));
        Spot best = null;
        for (Spot spot : spots == null ? new ArrayList<Spot>() : spots)
        {
            if (best == null || nearer(spot, best, from))
            {
                best = spot;
            }
        }
        return best;
    }

    /** Nothing at a spot now: it's forgotten, and at the next save the file forgets it too. */
    synchronized void forget(CanonicalChunk chunk, String category, String row, Spot spot)
    {
        String key = key(chunk, category, row);
        List<Spot> spots = rows.get(key);
        if (spots != null)
        {
            spots.removeIf(spot::near);
        }
        gone.computeIfAbsent(key, k -> new ArrayList<>()).add(spot);
        changed = true;
    }

    /** Whether anything has changed since the last save. */
    synchronized boolean changed()
    {
        return changed;
    }

    /** Add what the file holds to what's known. A damaged file is set aside, as the others are. */
    void load(Gson gson, Path path) throws IOException
    {
        LocalFileMerge.update(path, current -> {
            Map<String, List<Spot>> stored = read(gson, current, path);
            synchronized (this)
            {
                for (Map.Entry<String, List<Spot>> row : stored.entrySet())
                {
                    for (Spot spot : row.getValue())
                    {
                        if (!isGone(row.getKey(), spot))
                        {
                            add(rows, row.getKey(), spot);
                        }
                    }
                }
            }
            return null;
        });
    }

    /**
     * Write what's known into the file as it is now, since another RuneLite on this computer
     * may have added to it, and keep what that adds too.
     */
    void save(Gson gson, Path path) throws IOException
    {
        LocalFileMerge.update(path, current -> {
            Map<String, List<Spot>> merged = read(gson, current, path);
            synchronized (this)
            {
                for (Map.Entry<String, List<Spot>> row : rows.entrySet())
                {
                    for (Spot spot : row.getValue())
                    {
                        add(merged, row.getKey(), spot);
                    }
                }
                for (Map.Entry<String, List<Spot>> row : gone.entrySet())
                {
                    List<Spot> spots = merged.get(row.getKey());
                    for (Spot spot : row.getValue())
                    {
                        if (spots != null)
                        {
                            spots.removeIf(spot::near);
                        }
                    }
                }
                merged.values().removeIf(List::isEmpty);
                rows = merged;
                gone.clear();
                changed = false;
                return gson.toJson(stored(merged)).getBytes(StandardCharsets.UTF_8);
            }
        });
    }

    private boolean isGone(String key, Spot spot)
    {
        List<Spot> emptied = gone.get(key);
        return emptied != null && emptied.stream().anyMatch(spot::near);
    }

    /** Add a spot to a row unless it has one there: true when added. Past the limit, the first found goes. */
    private static boolean add(Map<String, List<Spot>> rows, String key, Spot spot)
    {
        List<Spot> spots = rows.computeIfAbsent(key, k -> new ArrayList<>());
        for (Spot known : spots)
        {
            if (known.near(spot))
            {
                return false;
            }
        }
        spots.add(spot);
        if (spots.size() > PER_ROW)
        {
            spots.remove(0);
        }
        return true;
    }

    /** On the tile's floor before another, then nearer. */
    private static boolean nearer(Spot spot, Spot best, Spot from)
    {
        boolean onFloor = spot.getPlane() == from.getPlane();
        if (onFloor != (best.getPlane() == from.getPlane()))
        {
            return onFloor;
        }
        return distance(spot, from) < distance(best, from);
    }

    private static long distance(Spot a, Spot b)
    {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        return dx * dx + dy * dy;
    }

    /** The file's rows; none when there is no file, or it is damaged, and then it's set aside. */
    private static Map<String, List<Spot>> read(Gson gson, byte[] bytes, Path path) throws IOException
    {
        Map<String, List<Spot>> rows = new HashMap<>();
        if (bytes == null)
        {
            return rows;
        }
        try
        {
            Stored stored = gson.fromJson(new String(bytes, StandardCharsets.UTF_8), Stored.class);
            if (stored == null || stored.rows == null)
            {
                throw new IllegalStateException("no rows");
            }
            for (Map.Entry<String, List<int[]>> row : stored.rows.entrySet())
            {
                for (int[] tile : row.getValue() == null ? new ArrayList<int[]>() : row.getValue())
                {
                    if (row.getKey() != null && tile != null && tile.length == 3)
                    {
                        add(rows, row.getKey(), new Spot(tile[0], tile[1], tile[2]));
                    }
                }
            }
            return rows;
        }
        catch (RuntimeException damaged)
        {
            LocalFileMerge.moveAside(path);
            return new HashMap<>();
        }
    }

    private static Stored stored(Map<String, List<Spot>> rows)
    {
        Stored stored = new Stored();
        stored.rows = new HashMap<>();
        for (Map.Entry<String, List<Spot>> row : rows.entrySet())
        {
            List<int[]> tiles = new ArrayList<>();
            for (Spot spot : row.getValue())
            {
                tiles.add(new int[] {spot.getX(), spot.getY(), spot.getPlane()});
            }
            stored.rows.put(row.getKey(), tiles);
        }
        return stored;
    }

    /** The file: each row's spots as x, y and floor. */
    private static final class Stored
    {
        int version = 1;
        Map<String, List<int[]>> rows;
    }
}

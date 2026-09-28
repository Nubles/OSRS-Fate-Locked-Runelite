package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;

/** U18: world map tiles placed with RuneLite's own maths, so what's drawn lines up with the pins. */
public class WorldMapProjectionTest
{
    private static final float[] ZOOMS = {1.5f, 2f, 3f, 4f, 6f, 8f};
    /** Odd and even sizes, anywhere on the canvas. */
    private static final Rectangle[] MAPS = {
        new Rectangle(35, 60, 700, 470), new Rectangle(8, 11, 701, 471), new Rectangle(0, 0, 517, 333)};
    private static final int[][] CENTRES = {{3200, 3200}, {3201, 3203}, {2999, 3456}};

    /** Exactly where RuneLite puts a pin, and inside its tile, or on the tile's line at a fractional zoom. */
    @Test
    public void everyPinIsWhereRuneLitePutsItOnItsTile()
    {
        for (float zoom : ZOOMS)
        {
            for (Rectangle map : MAPS)
            {
                for (int[] centre : CENTRES)
                {
                    WorldMapProjection projection = new WorldMapProjection(map, zoom, centre[0], centre[1]);
                    for (int tx = centre[0] - 70; tx <= centre[0] + 70; tx += 3)
                    {
                        for (int ty = centre[1] - 70; ty <= centre[1] + 70; ty += 3)
                        {
                            String where = zoom + " " + map + " tile " + tx + "," + ty;
                            Point pin = projection.pin(tx, ty);
                            assertEquals(where, runelite(map, zoom, centre[0], centre[1], tx, ty), pin);
                            assertTrue(where, projection.lineX(tx) <= pin.x && pin.x <= projection.lineX(tx + 1));
                            assertTrue(where, projection.lineY(ty + 1) <= pin.y && pin.y <= projection.lineY(ty));
                        }
                    }
                }
            }
        }
    }

    /** Hovering: the tile under a pixel is the one whose lines hold it, all across the map. */
    @Test
    public void theTileUnderAPixelIsTheOneWhoseLinesHoldIt()
    {
        for (float zoom : ZOOMS)
        {
            for (Rectangle map : MAPS)
            {
                WorldMapProjection projection = new WorldMapProjection(map, zoom, 3201, 3203);
                for (int x = map.x; x < map.x + map.width; x++)
                {
                    int tile = projection.tileX(x);
                    assertTrue(zoom + " x " + x, projection.lineX(tile) <= x && x < projection.lineX(tile + 1));
                }
                for (int y = map.y; y < map.y + map.height; y++)
                {
                    int tile = projection.tileY(y);
                    assertTrue(zoom + " y " + y, projection.lineY(tile + 1) <= y && y < projection.lineY(tile));
                }
            }
        }
    }

    @Test
    public void theChunksInViewCoverTheMap()
    {
        for (float zoom : ZOOMS)
        {
            for (Rectangle map : MAPS)
            {
                WorldMapProjection projection = new WorldMapProjection(map, zoom, 2999, 3456);
                int left = projection.tileX(map.x) >> 6;
                int right = projection.tileX(map.x + map.width - 1) >> 6;
                int top = projection.tileY(map.y) >> 6;
                int bottom = projection.tileY(map.y + map.height - 1) >> 6;
                String where = zoom + " " + map;
                assertTrue(where, projection.westChunk() <= left && right <= projection.eastChunk());
                assertTrue(where, projection.southChunk() <= bottom && top <= projection.northChunk());
            }
        }
    }

    /** RuneLite 1.12.39's WorldMapOverlay.mapWorldPointToGraphicsPoint, transcribed from its bytecode. */
    private static Point runelite(Rectangle worldMapRect, float pixelsPerTile, int positionX, int positionY,
        int worldX, int worldY)
    {
        int widthInTiles = (int) Math.ceil(worldMapRect.getWidth() / pixelsPerTile);
        int heightInTiles = (int) Math.ceil(worldMapRect.getHeight() / pixelsPerTile);
        int yTileMax = positionY - heightInTiles / 2;
        int yTileOffset = (yTileMax - worldY - 1) * -1;
        int xTileOffset = worldX + widthInTiles / 2 - positionX;
        int xGraphDiff = (int) (xTileOffset * pixelsPerTile);
        int yGraphDiff = (int) (yTileOffset * pixelsPerTile);
        yGraphDiff -= pixelsPerTile - Math.ceil(pixelsPerTile / 2);
        xGraphDiff += pixelsPerTile - Math.ceil(pixelsPerTile / 2);
        yGraphDiff = worldMapRect.height - yGraphDiff;
        yGraphDiff += (int) worldMapRect.getY();
        xGraphDiff += (int) worldMapRect.getX();
        return new Point(xGraphDiff, yGraphDiff);
    }
}

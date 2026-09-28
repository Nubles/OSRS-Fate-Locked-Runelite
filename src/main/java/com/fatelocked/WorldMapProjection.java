package com.fatelocked;

import java.awt.Point;
import java.awt.Rectangle;

/**
 * Where world tiles fall on the world map (U18), in RuneLite's own integer maths, so what this
 * plugin draws lines up with every pin on the map, its own included. The old float maths could
 * sit up to a tile off them.
 *
 * <p>RuneLite's {@code WorldMapOverlay.mapWorldPointToGraphicsPoint} counts whole tiles across
 * the map, rounds each offset down to a pixel, then moves a pin to the middle of its tile.
 * Tile lines here are the same offsets before that move, so every pin sits inside its tile.
 */
final class WorldMapProjection
{
    private final int mapX;
    private final int mapY;
    private final int mapHeight;
    private final float zoom;
    private final int widthInTiles;
    private final int heightInTiles;
    /** The tile column at the map's west edge. */
    private final int west;
    /** The tile row at the map's south edge. */
    private final int south;

    /** The map's view: its widget's bounds, pixels a tile, and the tile at its centre. */
    WorldMapProjection(Rectangle map, float zoom, int centreX, int centreY)
    {
        mapX = map.x;
        mapY = map.y;
        mapHeight = map.height;
        this.zoom = zoom;
        widthInTiles = (int) Math.ceil(map.getWidth() / zoom);
        heightInTiles = (int) Math.ceil(map.getHeight() / zoom);
        west = centreX - widthInTiles / 2;
        south = centreY - heightInTiles / 2;
    }

    /** The canvas x of the line on the west side of this tile column. */
    int lineX(int tileX)
    {
        return mapX + (int) ((float) (tileX - west) * zoom);
    }

    /** The canvas y of the line on the south side of this tile row; the canvas's y grows down. */
    int lineY(int tileY)
    {
        return mapY + mapHeight - (int) ((float) (tileY - south) * zoom);
    }

    /** Where RuneLite places a pin on this tile: in its middle. */
    Point pin(int tileX, int tileY)
    {
        int x = (int) ((float) (tileX - west) * zoom);
        int y = (int) ((float) (tileY - south + 1) * zoom);
        double middle = zoom - Math.ceil(zoom / 2);
        y = (int) (y - middle);
        x = (int) (x + middle);
        return new Point(mapX + x, mapY + mapHeight - y);
    }

    /** The tile column under this canvas x. */
    int tileX(int canvasX)
    {
        int tile = west + (int) Math.floor((canvasX - mapX) / zoom);
        while (canvasX < lineX(tile))
        {
            tile--;
        }
        while (canvasX >= lineX(tile + 1))
        {
            tile++;
        }
        return tile;
    }

    /** The tile row under this canvas y. */
    int tileY(int canvasY)
    {
        int tile = south + (int) Math.floor((mapY + mapHeight - canvasY) / zoom);
        while (canvasY >= lineY(tile))
        {
            tile--;
        }
        while (canvasY < lineY(tile + 1))
        {
            tile++;
        }
        return tile;
    }

    /** The westmost chunk column on the map. */
    int westChunk()
    {
        return Math.floorDiv(west, 64);
    }

    /** The eastmost chunk column on the map. */
    int eastChunk()
    {
        return Math.floorDiv(west + widthInTiles, 64);
    }

    /** The southmost chunk row on the map. */
    int southChunk()
    {
        return Math.floorDiv(south, 64);
    }

    /** The northmost chunk row on the map. */
    int northChunk()
    {
        return Math.floorDiv(south + heightInTiles, 64);
    }
}

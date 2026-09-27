package com.fatelocked.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * OSRS art from the game cache, through RuneLite's sprite and item managers. Each
 * piece is loaded once and kept; callers waiting for the same piece are all answered
 * when it arrives. Swing thread only.
 */
public final class GameArt implements IconSource
{
    private final SpriteManager sprites;
    private final ItemManager items;
    private final Map<Art, BufferedImage> loaded = new EnumMap<>(Art.class);
    private final Map<Art, List<Consumer<BufferedImage>>> waiting = new EnumMap<>(Art.class);

    public GameArt(SpriteManager sprites, ItemManager items)
    {
        this.sprites = sprites;
        this.items = items;
    }

    @Override
    public void load(Art art, Consumer<BufferedImage> into)
    {
        BufferedImage image = loaded.get(art);
        if (image != null)
        {
            into.accept(image);
            return;
        }
        List<Consumer<BufferedImage>> queue = waiting.get(art);
        if (queue != null)
        {
            queue.add(into);
            return;
        }
        queue = new ArrayList<>();
        queue.add(into);
        waiting.put(art, queue);
        if (art.kind() == Art.Kind.SPRITE)
        {
            // Called back on the client thread, once the cache is ready.
            sprites.getSpriteAsync(art.id(), art.detail(), sprite -> deliver(art, sprite));
        }
        else
        {
            AsyncBufferedImage item = items.getImage(art.id(), art.detail(), false);
            item.onLoaded(() -> deliver(art, item));
        }
    }

    private void deliver(Art art, BufferedImage image)
    {
        SwingUtilities.invokeLater(() -> {
            List<Consumer<BufferedImage>> queue = waiting.remove(art);
            if (image == null)
            {
                return;
            }
            loaded.put(art, image);
            if (queue != null)
            {
                for (Consumer<BufferedImage> into : queue)
                {
                    into.accept(image);
                }
            }
        });
    }
}

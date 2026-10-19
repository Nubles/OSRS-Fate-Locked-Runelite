package com.fatelocked.badges;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;

/**
 * The five keys, one a tier: iron, bronze, silver, gold and crystal. They're registered with
 * RuneLite's ChatIconManager once for the plugin's life, since it keeps every icon it is given.
 */
public final class BadgeIcons
{
    /** Each tier's key, in tier order. */
    static final String[] FILES = {
        "key_iron.png", "key_bronze.png", "key_silver.png", "key_gold.png", "key_crystal.png"
    };

    private final BufferedImage[] images = new BufferedImage[BadgeTier.COUNT];
    private final int[] iconIds = new int[BadgeTier.COUNT];
    private ChatIconManager registeredWith;

    public BadgeIcons()
    {
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            images[tier] = ImageUtil.loadImageResource(BadgeIcons.class, FILES[tier]);
        }
        Arrays.fill(iconIds, -1);
    }

    /** The tier's key; null for no tier. */
    public BufferedImage image(int tier)
    {
        return BadgeTier.valid(tier) ? images[tier] : null;
    }

    /** Hand the keys to the chat icon manager, once: again does nothing. */
    public synchronized void register(ChatIconManager chatIcons)
    {
        if (chatIcons == null || chatIcons == registeredWith)
        {
            return;
        }
        for (int tier = 0; tier < BadgeTier.COUNT; tier++)
        {
            iconIds[tier] = chatIcons.registerChatIcon(images[tier]);
        }
        registeredWith = chatIcons;
    }

    /** The tier's key as an index into the game's chat icons; -1 until the game has it. */
    public synchronized int chatIndex(int tier)
    {
        if (registeredWith == null || !BadgeTier.valid(tier) || iconIds[tier] < 0)
        {
            return -1;
        }
        return registeredWith.chatIconIndex(iconIds[tier]);
    }
}

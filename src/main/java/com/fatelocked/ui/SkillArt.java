package com.fatelocked.ui;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.util.ImageUtil;

/**
 * The game's small skill icons, as RuneLite ships them with the client, by the skill's
 * name. They need no game cache, so they are there from the start, and at 16 px or less
 * they are drawn 1:1 beside a line.
 */
public final class SkillArt
{
    private static final Map<String, Optional<BufferedImage>> LOADED = new ConcurrentHashMap<>();

    private SkillArt()
    {
    }

    /** The skill's icon, or null for a name that isn't a skill. */
    public static BufferedImage icon(String skill)
    {
        if (skill == null)
        {
            return null;
        }
        return LOADED.computeIfAbsent(skill.trim().toLowerCase(Locale.ROOT), SkillArt::load).orElse(null);
    }

    private static Optional<BufferedImage> load(String name)
    {
        String path = "/skill_icons_small/" + name + ".png";
        if (!name.matches("[a-z]+") || SkillIconManager.class.getResource(path) == null)
        {
            return Optional.empty();
        }
        return Optional.of(ImageUtil.loadImageResource(SkillIconManager.class, path));
    }
}

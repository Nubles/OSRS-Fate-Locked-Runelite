package com.fatelocked;

import com.fatelocked.ui.Palette;
import com.fatelocked.ui.TierMarks;
import com.fatelocked.ui.Type;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Fate in the game's Skills tab (plugin vision idea 2): each skill gets its tier numeral in
 * the corner, the tracker's I to X in its gem's colour, orange when the player has levelled
 * past what the tier opens. A skill with no Key yet is dimmed with a small padlock. Skills are
 * found by their "View ... guide" option, so a new skill or a moved tab needs no change here.
 */
public class FateLockedSkillTierOverlay extends Overlay
{
    /** The Skills tab's interface (gameval InterfaceID.STATS). */
    static final int SKILLS_GROUP = 320;
    /** More than the tab's children, so every skill is looked at. */
    private static final int MAX_CHILDREN = 64;
    private static final Pattern GUIDE = Pattern.compile("^View (.+) guide$");
    private static final BasicStroke SHACKLE = new BasicStroke(1.5f);

    private final Client client;
    private final FateLockedPlugin plugin;
    private final FateLockedConfig config;

    @Inject
    FateLockedSkillTierOverlay(Client client, FateLockedPlugin plugin, FateLockedConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.skillTierMarks()) return null;
        Map<String, Integer> tiers = plugin.decisions().skillTiers();
        if (tiers.isEmpty()) return null;
        for (int child = 0; child < MAX_CHILDREN; child++)
        {
            Widget widget = client.getWidget(SKILLS_GROUP, child);
            if (widget == null || widget.isHidden()) continue;
            String skill = skillOf(widget.getActions());
            if (skill == null) continue;
            Integer tier = tierOf(tiers, skill);
            Rectangle bounds = widget.getBounds();
            if (tier == null || bounds == null || bounds.width <= 0) continue;
            if (tier <= 0)
            {
                drawLocked(graphics, bounds);
                continue;
            }
            int level = realLevel(skill);
            String numeral = TierMarks.roman(tier);
            TierMarks.shadowed(graphics, numeral, bounds.x + 3, bounds.y + bounds.height - 2, Type.small(),
                level > TierMarks.levelCap(tier) ? TierMarks.PAST_TIER : TierMarks.gem(tier));
        }
        return null;
    }

    /** The skill a Skills tab widget stands for, from its "View Attack guide" option; null for anything else. */
    static String skillOf(String[] actions)
    {
        if (actions == null) return null;
        for (String action : actions)
        {
            if (action == null) continue;
            Matcher match = GUIDE.matcher(Text.removeTags(action).trim());
            if (match.matches()) return match.group(1);
        }
        return null;
    }

    /** The run's tier for a skill, matching the tracker's name whatever its case; null when it isn't one. */
    static Integer tierOf(Map<String, Integer> tiers, String skill)
    {
        for (Map.Entry<String, Integer> entry : tiers.entrySet())
        {
            if (entry.getKey().equalsIgnoreCase(skill)) return entry.getValue() == null ? 0 : entry.getValue();
        }
        return null;
    }

    private int realLevel(String skill)
    {
        try
        {
            return client.getRealSkillLevel(Skill.valueOf(skill.toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException unknown)
        {
            return 0;
        }
    }

    /** No Key yet: the cell darkened, with a small padlock in its corner. */
    private static void drawLocked(Graphics2D graphics, Rectangle bounds)
    {
        graphics.setColor(Palette.SKILL_LOCKED_DIM);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        int x = bounds.x + 4;
        int y = bounds.y + bounds.height - 10;
        graphics.setColor(Color.BLACK);
        graphics.fillRect(x, y + 3, 9, 7);
        graphics.setColor(Palette.SKILL_PADLOCK);
        graphics.fillRect(x + 1, y + 4, 7, 5);
        graphics.setStroke(SHACKLE);
        graphics.drawArc(x + 2, y - 1, 5, 7, 0, 180);
    }
}

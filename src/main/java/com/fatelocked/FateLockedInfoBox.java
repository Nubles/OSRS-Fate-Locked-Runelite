package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Art;
import com.fatelocked.ui.Palette;
import com.fatelocked.ui.Terms;
import java.awt.Color;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * One of the plugin's three infoboxes (A15, E7), shown when the setting is on. Each has its
 * own name, so RuneLite moves it and saves its place on its own; its art is the sidebar's; its
 * words are the web app's. A box with nothing to count, before rules load or on another
 * character, isn't drawn.
 */
final class FateLockedInfoBox extends InfoBox
{
    /** What a box counts. Its name is pinned: RuneLite saves each box's place under it. */
    enum Kind
    {
        KEYS("FateLocked_Keys", Art.KEYS, Palette.Tone.FRONTIER),
        FATE_POINTS("FateLocked_FatePoints", Art.FATE_POINTS, null),
        UNLOCKED("FateLocked_Unlocked", Art.PLACE, Palette.Tone.GOOD);

        private final String boxName;
        private final Art art;
        /** The count's tone; null for plain white. */
        private final Palette.Tone tone;

        Kind(String boxName, Art art, Palette.Tone tone)
        {
            this.boxName = boxName;
            this.art = art;
            this.tone = tone;
        }

        String boxName()
        {
            return boxName;
        }

        Art art()
        {
            return art;
        }
    }

    private final Kind kind;
    private final FateLockedPlugin plugin;

    /** A box whose art is set once it loads. */
    FateLockedInfoBox(Kind kind, FateLockedPlugin plugin)
    {
        super(null, plugin);
        this.kind = kind;
        this.plugin = plugin;
    }

    Kind kind()
    {
        return kind;
    }

    @Override
    public String getName()
    {
        return kind.boxName;
    }

    @Override
    public boolean render()
    {
        return getImage() != null && getText() != null;
    }

    @Override
    public String getText()
    {
        DecisionService decisions = plugin.decisions();
        return text(kind, run(decisions), decisions.progress());
    }

    @Override
    public Color getTextColor()
    {
        return kind.tone == null ? Palette.TITLE : plugin.palette().text(kind.tone);
    }

    @Override
    public String getTooltip()
    {
        DecisionService decisions = plugin.decisions();
        return tooltip(kind, run(decisions), decisions.progress());
    }

    /** The run's Keys and Fate Points, for the rules' own character only. */
    private FateLockedBundle.RunState run(DecisionService decisions)
    {
        return decisions.trust() == Trust.TRUSTED ? plugin.getBundle().getState() : null;
    }

    /** The box's count, or null when there is none to show. */
    static String text(Kind kind, FateLockedBundle.RunState run, Progress progress)
    {
        switch (kind)
        {
            case KEYS:
                return run == null ? null : String.valueOf(run.getKeys());
            case FATE_POINTS:
                return run == null ? null : String.valueOf(run.getFatePoints());
            default:
                return ProgressText.infoBoxText(progress);
        }
    }

    /** The box's tooltip, in the web app's words; Omni-Keys and Chaos Keys only when there are some. */
    static String tooltip(Kind kind, FateLockedBundle.RunState run, Progress progress)
    {
        switch (kind)
        {
            case KEYS:
                if (run == null) return null;
                StringBuilder tip = new StringBuilder(Terms.KEYS).append(": ").append(run.getKeys());
                if (run.getSpecialKeys() > 0)
                {
                    tip.append("</br>").append(Terms.OMNI_KEYS).append(": ").append(run.getSpecialKeys());
                }
                if (run.getChaosKeys() > 0)
                {
                    tip.append("</br>").append(Terms.CHAOS_KEYS).append(": ").append(run.getChaosKeys());
                }
                return tip.toString();
            case FATE_POINTS:
                return run == null ? null : Terms.FATE_POINTS + ": " + run.getFatePoints();
            default:
                return ProgressText.infoBoxTooltip(progress);
        }
    }
}

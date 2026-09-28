package com.fatelocked;

import com.fatelocked.ui.Palette;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * The in-game HUD (E6), drawing the plugin's {@link HudModel} on RuneLite's own panel: labels
 * plain, values in the palette's tones, headings in the accent. The model is worked out when
 * anything changes, and the panel is built again only when it or the palette does.
 */
public class FateLockedHudOverlay extends OverlayPanel
{
    private static final int WIDTH = 165;
    /** Detailed lists what the place holds, which needs more room. */
    private static final int DETAILED_WIDTH = 210;

    private final FateLockedPlugin plugin;
    // What the panel shows now.
    private HudModel shown;
    private Palette shownIn;

    @Inject
    FateLockedHudOverlay(FateLockedPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setResizable(false);
        setClearChildren(false);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        HudModel model = plugin.hudModel();
        if (model.getLines().isEmpty()) return null;
        Palette palette = plugin.palette();
        if (model != shown || palette != shownIn)
        {
            build(model, palette);
            shown = model;
            shownIn = palette;
        }
        return super.render(graphics);
    }

    private void build(HudModel model, Palette palette)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(model.isDetailed() ? DETAILED_WIDTH : WIDTH, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Fate Locked")
            .color(Palette.ACCENT)
            .build());
        for (HudModel.Line line : model.getLines())
        {
            LineComponent.LineComponentBuilder component = LineComponent.builder().left(line.getLabel());
            if (line.getValue() != null)
            {
                component.leftColor(Palette.TITLE)
                    .right(line.getValue())
                    .rightColor(line.getTone() == null ? Palette.TITLE : palette.text(line.getTone()));
            }
            else
            {
                component.leftColor(line.getTone() == null ? Palette.ACCENT : palette.text(line.getTone()));
            }
            panelComponent.getChildren().add(component.build());
        }
    }
}

package com.fatelocked;

import com.fatelocked.guardian.travel.TravelBlockNotice;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.ui.Palette;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A transient, non-gameplay explanation for a Strict Mode travel block, on RuneLite's
 * standard overlay panel: the block in the palette's locked colour, the nearest legal
 * option in its good colour, and the pause button in the accent.
 */
public class FateLockedTravelBlockOverlay extends Overlay implements MouseListener
{
    /** The banner's button, which names Strict Mode (B15). */
    static final String PAUSE_LABEL = "Pause Strict Mode for 60s";
    private static final int PADDING = 10;
    private static final int LINE_GAP = 5;
    private static final int BUTTON_HORIZONTAL_PADDING = 8;
    private static final int BUTTON_VERTICAL_PADDING = 5;

    private final Object interactionLock = new Object();
    private final TravelBlockNoticeStore noticeStore;
    private final BooleanSupplier strictModeEnabled;
    private final BooleanSupplier strictModePaused;
    private volatile InteractionState interactionState;
    private volatile Supplier<Palette> palette = Palette::defaults;
    private final BackgroundComponent background = new BackgroundComponent();

    public FateLockedTravelBlockOverlay(
        TravelBlockNoticeStore noticeStore,
        BooleanSupplier strictModeEnabled,
        BooleanSupplier strictModePaused,
        Runnable pauseGuardian)
    {
        if (noticeStore == null || strictModeEnabled == null || strictModePaused == null)
        {
            throw new IllegalArgumentException("notice store and Strict Mode state are required");
        }
        if (pauseGuardian == null)
        {
            throw new IllegalArgumentException("pause callback is required");
        }
        this.noticeStore = noticeStore;
        this.strictModeEnabled = strictModeEnabled;
        this.strictModePaused = strictModePaused;
        interactionState = InteractionState.hidden(pauseGuardian);
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setResizable(false);
    }

    public void setPauseGuardian(Runnable pauseGuardian)
    {
        if (pauseGuardian == null)
        {
            throw new IllegalArgumentException("pause callback is required");
        }
        synchronized (interactionLock)
        {
            interactionState = interactionState.withPauseGuardian(pauseGuardian);
        }
    }

    /** Where the banner's colours come from: the plugin's palette. */
    public void setPalette(Supplier<Palette> palette)
    {
        if (palette == null)
        {
            throw new IllegalArgumentException("a palette is required");
        }
        this.palette = palette;
    }

    public Rectangle getPauseButtonBounds()
    {
        Rectangle bounds = interactionState.localButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!strictModeEnabled.getAsBoolean() || strictModePaused.getAsBoolean())
        {
            clearInteractionState();
            return null;
        }

        Optional<TravelBlockNotice> current = noticeStore.current();
        if (!current.isPresent())
        {
            clearInteractionState();
            return null;
        }

        TravelBlockNotice notice = current.get();
        String alternative = notice.getAlternative();
        if (alternative != null && alternative.trim().isEmpty())
        {
            alternative = null;
        }
        String alternativeLine = alternative == null ? null
            : "Nearest legal option: " + alternative;
        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int buttonWidth = metrics.stringWidth(PAUSE_LABEL) + BUTTON_HORIZONTAL_PADDING * 2;
        int width = Math.max(buttonWidth, Math.max(metrics.stringWidth(notice.getHeadline()),
            Math.max(metrics.stringWidth(notice.getReason()),
                alternativeLine == null ? 0 : metrics.stringWidth(alternativeLine))))
            + PADDING * 2;
        int textLines = alternativeLine == null ? 2 : 3;
        int buttonHeight = lineHeight + BUTTON_VERTICAL_PADDING * 2;
        int height = PADDING * 2 + textLines * lineHeight
            + (textLines - 1) * LINE_GAP + LINE_GAP + buttonHeight;

        Palette colours = palette.get();
        background.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        background.setRectangle(new Rectangle(0, 0, width, height));
        background.render(graphics);

        int baseline = PADDING + metrics.getAscent();
        graphics.setColor(colours.text(Palette.Tone.BAD));
        graphics.drawString(notice.getHeadline(), PADDING, baseline);
        baseline += lineHeight + LINE_GAP;
        graphics.setColor(Color.WHITE);
        graphics.drawString(notice.getReason(), PADDING, baseline);
        if (alternativeLine != null)
        {
            baseline += lineHeight + LINE_GAP;
            graphics.setColor(colours.text(Palette.Tone.GOOD));
            graphics.drawString(alternativeLine, PADDING, baseline);
        }

        int buttonY = height - PADDING - buttonHeight;
        Rectangle localButtonBounds = new Rectangle(PADDING, buttonY, buttonWidth, buttonHeight);
        Rectangle canvasButtonBounds = new Rectangle(localButtonBounds);
        Rectangle overlayBounds = getBounds();
        canvasButtonBounds.translate(overlayBounds.x, overlayBounds.y);
        publishInteractionState(localButtonBounds, canvasButtonBounds);
        graphics.setColor(Palette.ACCENT);
        graphics.fillRect(localButtonBounds.x, localButtonBounds.y,
            localButtonBounds.width, localButtonBounds.height);
        graphics.setColor(Palette.ON_ACCENT);
        graphics.drawString(PAUSE_LABEL, localButtonBounds.x + BUTTON_HORIZONTAL_PADDING,
            localButtonBounds.y + BUTTON_VERTICAL_PADDING + metrics.getAscent());
        return new Dimension(width, height);
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event)
    {
        Runnable pause = null;
        synchronized (interactionLock)
        {
            InteractionState snapshot = interactionState;
            if (event.getButton() == MouseEvent.BUTTON1
                && strictModeEnabled.getAsBoolean()
                && !strictModePaused.getAsBoolean()
                && noticeStore.current().isPresent()
                && snapshot.canvasButtonBounds != null
                && snapshot.canvasButtonBounds.contains(event.getPoint()))
            {
                pause = snapshot.pauseGuardian;
            }
        }
        if (pause != null)
        {
            pause.run();
            return null;
        }
        return event;
    }

    @Override public MouseEvent mouseClicked(MouseEvent event) { return event; }
    @Override public MouseEvent mouseReleased(MouseEvent event) { return event; }
    @Override public MouseEvent mouseEntered(MouseEvent event) { return event; }
    @Override public MouseEvent mouseExited(MouseEvent event) { return event; }
    @Override public MouseEvent mouseDragged(MouseEvent event) { return event; }
    @Override public MouseEvent mouseMoved(MouseEvent event) { return event; }

    private void clearInteractionState()
    {
        synchronized (interactionLock)
        {
            interactionState = InteractionState.hidden(interactionState.pauseGuardian);
        }
    }

    private void publishInteractionState(Rectangle localButtonBounds, Rectangle canvasButtonBounds)
    {
        synchronized (interactionLock)
        {
            interactionState = new InteractionState(localButtonBounds, canvasButtonBounds,
                interactionState.pauseGuardian);
        }
    }

    private static final class InteractionState
    {
        private final Rectangle localButtonBounds;
        private final Rectangle canvasButtonBounds;
        private final Runnable pauseGuardian;

        private InteractionState(
            Rectangle localButtonBounds, Rectangle canvasButtonBounds, Runnable pauseGuardian)
        {
            this.localButtonBounds = localButtonBounds == null ? null : new Rectangle(localButtonBounds);
            this.canvasButtonBounds = canvasButtonBounds == null ? null : new Rectangle(canvasButtonBounds);
            this.pauseGuardian = pauseGuardian;
        }

        private static InteractionState hidden(Runnable pauseGuardian)
        {
            return new InteractionState(null, null, pauseGuardian);
        }

        private InteractionState withPauseGuardian(Runnable pauseGuardian)
        {
            return new InteractionState(localButtonBounds, canvasButtonBounds, pauseGuardian);
        }
    }
}

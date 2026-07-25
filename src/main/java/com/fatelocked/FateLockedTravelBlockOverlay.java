package com.fatelocked;

import com.fatelocked.guardian.travel.TravelBlockNotice;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import net.runelite.client.input.MouseListener;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** A transient, non-gameplay explanation for a Strict Mode travel block. */
public class FateLockedTravelBlockOverlay extends Overlay implements MouseListener
{
    private static final Color PANEL = new Color(17, 24, 39, 235);
    private static final Color HEADLINE = new Color(239, 68, 68);
    private static final Color TEXT = new Color(255, 255, 255);
    private static final Color AMBER = new Color(245, 158, 11);
    private static final String PAUSE_LABEL = "Pause Guardian for 60s";
    private static final int PADDING = 10;
    private static final int LINE_GAP = 5;
    private static final int BUTTON_HORIZONTAL_PADDING = 8;
    private static final int BUTTON_VERTICAL_PADDING = 5;

    private final Object interactionLock = new Object();
    private final TravelBlockNoticeStore noticeStore;
    private final BooleanSupplier strictModeEnabled;
    private final BooleanSupplier strictModePaused;
    private volatile InteractionState interactionState;

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

        graphics.setColor(PANEL);
        graphics.fillRect(0, 0, width, height);

        int baseline = PADDING + metrics.getAscent();
        graphics.setColor(HEADLINE);
        graphics.drawString(notice.getHeadline(), PADDING, baseline);
        baseline += lineHeight + LINE_GAP;
        graphics.setColor(TEXT);
        graphics.drawString(notice.getReason(), PADDING, baseline);
        if (alternativeLine != null)
        {
            baseline += lineHeight + LINE_GAP;
            graphics.setColor(AMBER);
            graphics.drawString(alternativeLine, PADDING, baseline);
        }

        int buttonY = height - PADDING - buttonHeight;
        Rectangle localButtonBounds = new Rectangle(PADDING, buttonY, buttonWidth, buttonHeight);
        Rectangle canvasButtonBounds = new Rectangle(localButtonBounds);
        Rectangle overlayBounds = getBounds();
        canvasButtonBounds.translate(overlayBounds.x, overlayBounds.y);
        publishInteractionState(localButtonBounds, canvasButtonBounds);
        graphics.setColor(AMBER);
        graphics.fillRect(localButtonBounds.x, localButtonBounds.y,
            localButtonBounds.width, localButtonBounds.height);
        graphics.setColor(PANEL);
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

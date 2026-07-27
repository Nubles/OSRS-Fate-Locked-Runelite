package com.fatelocked;

import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class FateLockedTravelBlockOverlayTest
{
    @Test
    public void rendersOnlyForAnActiveNoticeWhileStrictModeIsEnabledAndUnpaused()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicBoolean paused = new AtomicBoolean(false);
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, enabled::get, paused::get, () -> {});

        assertEquals(null, render(overlay));

        store.show("boat:port", "Travel blocked - Boat", "Port is locked", "Varrock tablet");
        Dimension rendered = render(overlay);
        assertNotNull(rendered);
        assertNotNull(overlay.getPauseButtonBounds());

        enabled.set(false);
        assertEquals(null, render(overlay));
        enabled.set(true);
        paused.set(true);
        assertEquals(null, render(overlay));
    }

    @Test
    public void onlyLeftPressInsidePauseButtonInvokesTheSuppliedCallbackAndConsumesTheEvent()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        AtomicInteger pauses = new AtomicInteger();
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, () -> true, () -> false, pauses::incrementAndGet);
        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        render(overlay);

        Rectangle button = overlay.getPauseButtonBounds();
        MouseEvent inside = mouse(MouseEvent.MOUSE_PRESSED,
            button.x + button.width / 2, button.y + button.height / 2, MouseEvent.BUTTON1);
        MouseEvent outside = mouse(MouseEvent.MOUSE_PRESSED,
            button.x - 1, button.y - 1, MouseEvent.BUTTON1);
        MouseEvent rightInside = mouse(MouseEvent.MOUSE_PRESSED,
            button.x + button.width / 2, button.y + button.height / 2, MouseEvent.BUTTON3);

        assertNull(overlay.mousePressed(inside));
        assertEquals(1, pauses.get());
        assertSame(outside, overlay.mousePressed(outside));
        assertSame(rightInside, overlay.mousePressed(rightInside));
        assertEquals(1, pauses.get());
    }

    @Test
    public void pausesForTheVisibleCanvasRelativeButtonAndNotItsOldLocalCoordinates()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        AtomicInteger pauses = new AtomicInteger();
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, () -> true, () -> false, pauses::incrementAndGet);
        overlay.setBounds(new Rectangle(120, 70, 0, 0));
        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        render(overlay);

        Rectangle localButton = overlay.getPauseButtonBounds();
        MouseEvent visibleButton = mouse(MouseEvent.MOUSE_PRESSED,
            120 + localButton.x + localButton.width / 2,
            70 + localButton.y + localButton.height / 2, MouseEvent.BUTTON1);
        MouseEvent oldLocalCoordinates = mouse(MouseEvent.MOUSE_PRESSED,
            localButton.x + localButton.width / 2,
            localButton.y + localButton.height / 2, MouseEvent.BUTTON1);

        assertNull(overlay.mousePressed(visibleButton));
        assertEquals(1, pauses.get());
        assertSame(oldLocalCoordinates, overlay.mousePressed(oldLocalCoordinates));
        assertEquals(1, pauses.get());
    }

    @Test
    public void passesThroughWhenStrictModeIsDisabledAfterRendering()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicInteger pauses = new AtomicInteger();
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, enabled::get, () -> false, pauses::incrementAndGet);
        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        render(overlay);
        MouseEvent click = insideButton(overlay);

        enabled.set(false);

        assertSame(click, overlay.mousePressed(click));
        assertEquals(0, pauses.get());
    }

    @Test
    public void passesThroughWhenStrictModeIsPausedAfterRendering()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        AtomicBoolean paused = new AtomicBoolean(false);
        AtomicInteger pauses = new AtomicInteger();
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, () -> true, paused::get, pauses::incrementAndGet);
        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        render(overlay);
        MouseEvent click = insideButton(overlay);

        paused.set(true);

        assertSame(click, overlay.mousePressed(click));
        assertEquals(0, pauses.get());
    }

    @Test
    public void passesThroughWhenTheNoticeExpiresAfterRendering()
    {
        MutableClock clock = new MutableClock();
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(clock);
        AtomicInteger pauses = new AtomicInteger();
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, () -> true, () -> false, pauses::incrementAndGet);
        store.show("walk:locked", "Travel blocked - Walk here", "Locked", null);
        render(overlay);
        MouseEvent click = insideButton(overlay);

        clock.advance(Duration.ofSeconds(4));

        assertSame(click, overlay.mousePressed(click));
        assertEquals(0, pauses.get());
    }

    @Test
    public void nonPressMouseEventsAlwaysPassThroughUnchanged()
    {
        TravelBlockNoticeStore store = new TravelBlockNoticeStore(
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC));
        FateLockedTravelBlockOverlay overlay = new FateLockedTravelBlockOverlay(
            store, () -> true, () -> false, () -> {});
        MouseEvent event = mouse(MouseEvent.MOUSE_MOVED, 4, 5, MouseEvent.NOBUTTON);

        assertSame(event, overlay.mouseClicked(event));
        assertSame(event, overlay.mouseReleased(event));
        assertSame(event, overlay.mouseEntered(event));
        assertSame(event, overlay.mouseExited(event));
        assertSame(event, overlay.mouseDragged(event));
        assertSame(event, overlay.mouseMoved(event));
    }

    private static Dimension render(FateLockedTravelBlockOverlay overlay)
    {
        BufferedImage image = new BufferedImage(500, 250, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            return overlay.render(graphics);
        }
        finally
        {
            graphics.dispose();
        }
    }

    private static MouseEvent mouse(int id, int x, int y, int button)
    {
        return new MouseEvent(new Canvas(), id, 0L, 0, x, y, 1, false, button);
    }

    private static MouseEvent insideButton(FateLockedTravelBlockOverlay overlay)
    {
        Rectangle button = overlay.getPauseButtonBounds();
        Rectangle overlayBounds = overlay.getBounds();
        return mouse(MouseEvent.MOUSE_PRESSED,
            overlayBounds.x + button.x + button.width / 2,
            overlayBounds.y + button.y + button.height / 2, MouseEvent.BUTTON1);
    }

    private static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse("2026-07-24T10:00:00Z");

        void advance(Duration duration)
        {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

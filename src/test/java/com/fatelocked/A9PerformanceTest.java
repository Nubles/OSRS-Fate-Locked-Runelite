package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.awt.Rectangle;
import java.awt.Shape;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Proxy;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import org.junit.Test;

/**
 * A9, F2: what runs in every frame, or for every option of every menu, makes no garbage: the world
 * map's loop over a golden run, and the menu tag passing over options it could never tag. Counted
 * with the JVM's own tally of the bytes a thread allocates.
 */
public class A9PerformanceTest
{
    private static final int WARM_UP = 100_000;
    private static final int ROUNDS = 100_000;

    @Test
    public void theWorldMapLoopMakesNoGarbage() throws Exception
    {
        FateLockedBundle mid = FateLockedBundle.loadFromJson(new Gson(),
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz")));
        WorldMapModel model = WorldMapModel.of(
            DecisionService.create(RulesSnapshot.of(mid), "iron example", "iron example"));
        Rectangle map = new Rectangle(0, 0, 700, 470);
        WorldMapProjection projection = new WorldMapProjection(map, 1.5f, 3200, 3200);
        Shape clip = WorldMapClip.of(map, null, null);
        Shape outline = FateLockedWorldMapOverlay.outlinePath(model, projection);
        Shape grid = FateLockedWorldMapOverlay.gridPath(model, projection);
        Palette palette = Palette.defaults();
        NoopGraphics graphics = new NoopGraphics();

        FateLockedWorldMapOverlay.draw(graphics, model, projection, palette, clip, outline, grid);
        assertTrue("the loop fills the locked land in view", graphics.fills > 0);
        assertEquals("and draws the grid, then the outline over its underlay", 3, graphics.draws);

        double bytes = perRound(() -> FateLockedWorldMapOverlay.draw(graphics, model, projection, palette, clip, outline,
            grid));
        assertTrue(bytes + " bytes a frame", bytes < 1);
    }

    @Test
    public void theMenuTagPassesOverOptionsWithoutGarbage()
    {
        FateLockedPlugin plugin = new FateLockedPlugin();
        for (MenuAction type : new MenuAction[] {MenuAction.WALK, MenuAction.CANCEL, MenuAction.EXAMINE_NPC,
            MenuAction.EXAMINE_OBJECT, MenuAction.EXAMINE_ITEM, MenuAction.PLAYER_FIRST_OPTION})
        {
            MenuEntryAdded event = new MenuEntryAdded(entryOf(type));
            double bytes = perRound(() -> plugin.onMenuEntryAdded(event));
            assertTrue(type + ": " + bytes + " bytes an option", bytes < 1);
        }
    }

    /**
     * The bytes this thread allocates a round of the work, on average, once the JIT has had a warm-up.
     * Anything made every round is 16 bytes or more; under one byte a round is the tally's own cost
     * and the odd one-off.
     */
    private static double perRound(Runnable work)
    {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) bean;
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled());
        long id = Thread.currentThread().getId();
        for (int i = 0; i < WARM_UP; i++)
        {
            work.run();
        }
        long before = threads.getThreadAllocatedBytes(id);
        for (int i = 0; i < ROUNDS; i++)
        {
            work.run();
        }
        return (threads.getThreadAllocatedBytes(id) - before) / (double) ROUNDS;
    }

    /**
     * A menu entry of this type, as a proxy that answers without making anything: a mock would
     * record every call, which is garbage of its own.
     */
    private static MenuEntry entryOf(MenuAction type)
    {
        return (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[] {MenuEntry.class},
            (proxy, method, args) -> "getType".equals(method.getName()) ? type : null);
    }
}

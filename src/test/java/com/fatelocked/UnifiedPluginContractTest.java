package com.fatelocked;

import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.NavigationButton;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class UnifiedPluginContractTest
{
    @Test
    public void pluginIdentityRemainsSingular()
    {
        PluginDescriptor descriptor =
            FateLockedPlugin.class.getAnnotation(PluginDescriptor.class);
        assertEquals("Fate Locked Ironman", descriptor.name());
    }

    @Test
    public void reconnectAndGuardianCallbacksCanCoexist() throws Exception
    {
        Harness harness = new Harness();
        FateLockedPanel panel = harness.panel();

        SwingUtilities.invokeAndWait(() -> {
            panel.connectButtonForTest().doClick();
            panel.guardianPauseButtonForTest().doClick();
        });

        assertEquals(1, harness.connectCalls());
        assertEquals(1, harness.pauseCalls());
    }

    @Test
    public void startupNavigationOwnsConnectAndGuardianInTheSamePanel()
        throws Exception
    {
        Harness harness = new Harness();

        NavigationButton navigation = harness.navigation();

        assertSame(harness.panel(), navigation.getPanel());
        assertNotNull(harness.panel().connectButtonForTest());
        assertNotNull(harness.panel().sectionForTest("Guardian"));
        assertNotNull(harness.panel().guardianPauseButtonForTest());
    }

    private static final class Harness
    {
        private final FateLockedPanel panel = new FateLockedPanel();
        private final AtomicInteger connectCalls = new AtomicInteger();
        private final AtomicInteger pauseCalls = new AtomicInteger();
        private final NavigationButton navigation;

        private Harness() throws Exception
        {
            FateLockedPlugin.wirePanelActions(
                panel, ignored -> { }, () -> { },
                connectCalls::incrementAndGet);
            panel.setGuardianCallbacks(
                pauseCalls::incrementAndGet, () -> { }, () -> { });
            navigation = FateLockedPlugin.buildNavigationButton(panel);
        }

        private FateLockedPanel panel()
        {
            return panel;
        }

        private NavigationButton navigation()
        {
            return navigation;
        }

        private int connectCalls()
        {
            return connectCalls.get();
        }

        private int pauseCalls()
        {
            return pauseCalls.get();
        }
    }
}

package com.fatelocked;

import com.fatelocked.guardian.StrictModeAuditEntry;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.StrictModePause;
import com.fatelocked.guardian.StrictModeReadiness;
import com.fatelocked.guardian.travel.IntentClassifier;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one Strict Mode gate (G14), through the plugin. For every mix of the
 * facts Strict Mode depends on, a click is consumed only when the readiness
 * the sidebar shows is ACTIVE and the click is exactly matched travel the
 * rules prove locked. When paused, that trip is let through and recorded.
 * Nothing else is ever consumed or recorded.
 */
@RunWith(Parameterized.class)
public class StrictModeGateTest
{
    enum Rules { BOUND, UNBOUND, NONE, LEGACY }

    enum Playing { OWNER, SOMEONE_ELSE, NOBODY }

    @Parameterized.Parameters(name = "{0}")
    public static List<Object[]> facts()
    {
        List<Object[]> cases = new ArrayList<>();
        for (boolean enabled : new boolean[] { true, false })
        {
            for (boolean paused : new boolean[] { false, true })
            {
                for (Rules rules : Rules.values())
                {
                    for (Playing playing : Playing.values())
                    {
                        for (boolean fresh : new boolean[] { true, false })
                        {
                            String name = (enabled ? "on" : "off") + (paused ? " paused" : "")
                                + ", " + rules + " rules, " + playing + " playing, "
                                + (fresh ? "fresh" : "stale");
                            cases.add(new Object[] { name, enabled, paused, rules, playing, fresh });
                        }
                    }
                }
            }
        }
        return cases;
    }

    private final boolean enabled;
    private final boolean paused;
    private final Rules rules;
    private final Playing playing;
    private final boolean fresh;

    public StrictModeGateTest(
        String name, boolean enabled, boolean paused, Rules rules, Playing playing, boolean fresh)
    {
        this.enabled = enabled;
        this.paused = paused;
        this.rules = rules;
        this.playing = playing;
        this.fresh = fresh;
    }

    @Test
    public void onlyAnActiveGateConsumesAndOnlyAProvenLock() throws Exception
    {
        Harness harness = new Harness();
        StrictModeReadiness.State state = harness.plugin.strictModeReadiness().getState();
        assertEquals("the sidebar's readiness", expectedState(), state);

        MenuOptionClicked teleport = TravelClicks.cast("Falador Teleport");
        MenuOptionClicked dialogue = TravelClicks.click(MenuAction.WIDGET_CONTINUE, "Continue", "");
        MenuOptionClicked wield = TravelClicks.item(4151, "Wield", "Abyssal whip");
        harness.plugin.onMenuOptionClicked(teleport);
        harness.plugin.onMenuOptionClicked(dialogue);
        harness.plugin.onMenuOptionClicked(wield);

        boolean active = state == StrictModeReadiness.State.ACTIVE;
        verify(teleport, times(active ? 1 : 0)).consume();
        verify(dialogue, never()).consume();
        verify(wield, never()).consume();
        assertEquals(active ? List.of("BLOCKED")
                : state == StrictModeReadiness.State.PAUSED ? List.of("ALLOWED_PAUSED")
                : Collections.emptyList(),
            harness.auditOutcomes());
        assertEquals(active ? 1 : 0, harness.chat.size());
    }

    /** Worked out from the facts alone, so a gate that never opens can't pass. */
    private StrictModeReadiness.State expectedState()
    {
        if (!enabled) return StrictModeReadiness.State.OFF;
        if (rules != Rules.BOUND || playing != Playing.OWNER || !fresh)
        {
            return StrictModeReadiness.State.INACTIVE;
        }
        return paused ? StrictModeReadiness.State.PAUSED : StrictModeReadiness.State.ACTIVE;
    }

    private static String fixture(String name) throws Exception
    {
        try (InputStream in = StrictModeGateTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private final class Harness
    {
        private final FateLockedPlugin plugin = new FateLockedPlugin();
        private final List<String> chat = new ArrayList<>();
        private final List<StrictModeAuditEntry> audit = new ArrayList<>();

        private Harness() throws Exception
        {
            FateLockedConfig config = mock(FateLockedConfig.class);
            when(config.strictMode()).thenReturn(enabled);
            set("config", config);

            Client client = mock(Client.class);
            if (playing != Playing.NOBODY)
            {
                Player player = mock(Player.class);
                when(player.getName()).thenReturn(playing == Playing.OWNER ? "Nubles" : "Zezima");
                TestWorld.standAt(client, player, new WorldPoint(49 << 6, 50 << 6, 0));
                when(client.getLocalPlayer()).thenReturn(player);
            }
            set("client", client);

            // The fixture's travel table locks Falador Teleport: a proven lock.
            String locked = fixture("bundles/v4-travel.json");
            Gson gson = new Gson();
            switch (rules)
            {
                case BOUND:
                    set("active", new ActiveRules(FateLockedBundle.loadFromJson(gson, locked),
                        FateLockedPlugin.RulesSource.RELAY));
                    break;
                case UNBOUND:
                    set("active", new ActiveRules(FateLockedBundle.loadFromJson(gson,
                        locked.replace("\"account\": \"Nubles\",", "")), FateLockedPlugin.RulesSource.RELAY));
                    break;
                case LEGACY:
                    set("active", new ActiveRules(FateLockedBundle.loadFromJson(gson,
                        fixture("bundles/v3-standard.json")), FateLockedPlugin.RulesSource.RELAY));
                    break;
                default:
                    break;
            }

            // The relay keeps tracker rules fresh while it confirms them.
            TrackerConnectionSettings settings = mock(TrackerConnectionSettings.class);
            when(settings.isPaired()).thenReturn(true);
            when(settings.networkAccessAllowed()).thenReturn(true);
            TrackerConnectionController controller = mock(TrackerConnectionController.class);
            Instant lastSync = fresh ? Instant.now() : Instant.now().minus(Duration.ofMinutes(20));
            when(controller.snapshot()).thenReturn(TrackerConnectionSnapshot.connected(lastSync, "1"));
            set("connectionSettings", settings);
            set("connectionController", controller);

            if (paused) ((StrictModePause) get("strictPause")).pauseFor(Duration.ofSeconds(60));

            TravelAlternativeFinder finder = mock(TravelAlternativeFinder.class);
            when(finder.find(any(), any(), any())).thenReturn(Optional.empty());
            set("travelGuardianShell", new TravelGuardianPluginShell(
                new TravelGuardianCoordinator(
                    new IntentClassifier(),
                    new TravelRuleEvaluator(),
                    finder,
                    new TravelBlockNoticeStore(Clock.systemUTC()),
                    new StrictModeClickHandler(new StrictModeGuard())),
                mock(TravelAvailability.class),
                chat::add,
                audit::add,
                (stage, error) -> { throw new AssertionError(stage, error); },
                Clock.systemUTC()));

            // As every tick does: the decision service for these rules and this character.
            Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
            refresh.setAccessible(true);
            refresh.invoke(plugin);
        }

        private List<String> auditOutcomes()
        {
            List<String> outcomes = new ArrayList<>();
            for (StrictModeAuditEntry entry : audit) outcomes.add(entry.getOutcome());
            return outcomes;
        }

        private void set(String name, Object value) throws Exception
        {
            field(name).set(plugin, value);
        }

        private Object get(String name) throws Exception
        {
            return field(name).get(plugin);
        }

        private Field field(String name) throws Exception
        {
            Field field = FateLockedPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}

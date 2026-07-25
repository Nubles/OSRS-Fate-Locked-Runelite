package com.fatelocked;

import com.fatelocked.guardian.GuardedActionFactory;
import com.fatelocked.guardian.StrictModeClickHandler;
import com.fatelocked.guardian.StrictModeGuard;
import com.fatelocked.guardian.travel.TravelActionResolver;
import com.fatelocked.guardian.travel.TravelAlternativeFinder;
import com.fatelocked.guardian.travel.TravelAvailability;
import com.fatelocked.guardian.travel.TravelBlockNoticeStore;
import com.fatelocked.guardian.travel.TravelGuardianCoordinator;
import com.fatelocked.guardian.travel.TravelRuleEvaluator;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FateLockedPluginTravelAccountBindingTest
{
    @Test
    public void missingRulesAccountCannotEnforceOrSuggest() throws Exception
    {
        assertUnboundFailsOpen(lockedBundle().replace(
            "\"account\": \"Nubles\",", ""), true, "Nubles");
    }

    @Test
    public void blankRulesAccountCannotEnforceOrSuggest() throws Exception
    {
        assertUnboundFailsOpen(lockedBundle().replace(
            "\"account\": \"Nubles\"", "\"account\": \"   \""),
            true, "Nubles");
    }

    @Test
    public void absentLocalPlayerCannotEnforceOrSuggest() throws Exception
    {
        assertUnboundFailsOpen(lockedBundle(), false, null);
    }

    @Test
    public void wrongLocalPlayerCannotEnforceOrSuggest() throws Exception
    {
        assertUnboundFailsOpen(lockedBundle(), true, "Other Player");
    }

    @Test
    public void unboundRulesWithAbsentPlayerRetainGenericEquipmentEnforcement()
        throws Exception
    {
        Harness harness = new Harness(lockedBundle().replace(
            "\"account\": \"Nubles\"," , ""), false, null);
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Wield");
        when(entry.getTarget()).thenReturn("Abyssal whip");
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        when(entry.getItemId()).thenReturn(4151);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);

        harness.plugin.onMenuOptionClicked(click);

        verify(click).consume();
    }
    @Test
    public void normalizedMatchingAccountCanEnforceAndLookUpAlternatives()
        throws Exception
    {
        Harness harness = new Harness(lockedBundle(), true, "nUbLeS");
        MenuOptionClicked click = namedTeleportClick();

        harness.plugin.onMenuOptionClicked(click);

        verify(click).consume();
        verify(harness.finder).find(any(), any(), any());
    }

    private static void assertUnboundFailsOpen(
        String json, boolean playerPresent, String playerName) throws Exception
    {
        Harness harness = new Harness(json, playerPresent, playerName);
        MenuOptionClicked click = namedTeleportClick();

        harness.plugin.onMenuOptionClicked(click);

        verify(click, never()).consume();
        verify(harness.finder, never()).find(any(), any(), any());
        assertFalse(harness.noticeStore.current().isPresent());
    }

    private static MenuOptionClicked namedTeleportClick()
    {
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getOption()).thenReturn("Teleport");
        when(entry.getTarget()).thenReturn("Lumbridge");
        when(entry.getType()).thenReturn(MenuAction.UNKNOWN);
        MenuOptionClicked click = mock(MenuOptionClicked.class);
        when(click.getMenuEntry()).thenReturn(entry);
        return click;
    }

    private static String lockedBundle() throws Exception
    {
        return fixtureText("bundles/v4-rules.json")
            .replace("\"entry\": \"ALLOWED\"", "\"entry\": \"LOCKED\"");
    }

    private static String fixtureText(String name) throws Exception
    {
        try (InputStream in =
            FateLockedPluginTravelAccountBindingTest.class.getClassLoader()
                .getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void setField(Object target, String name, Object value)
        throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Harness
    {
        private final FateLockedPlugin plugin = new FateLockedPlugin();
        private final TravelAlternativeFinder finder =
            mock(TravelAlternativeFinder.class);
        private final TravelBlockNoticeStore noticeStore =
            new TravelBlockNoticeStore(Clock.systemUTC());

        private Harness(
            String json, boolean playerPresent, String playerName)
            throws Exception
        {
            Gson gson = new Gson();
            FateLockedBundle bundle = FateLockedBundle.loadFromJson(gson, json);
            FateLockedConfig config = mock(FateLockedConfig.class);
            when(config.strictMode()).thenReturn(true);
            when(config.onlineSync()).thenReturn(true);

            Client client = mock(Client.class);
            if (playerPresent)
            {
                Player player = mock(Player.class);
                when(client.getLocalPlayer()).thenReturn(player);
                when(player.getName()).thenReturn(playerName);
                when(player.getWorldLocation()).thenReturn(
                    new WorldPoint(49 << 6, 50 << 6, 0));
            }

            TravelAvailability availability = mock(TravelAvailability.class);
            when(finder.find(any(), any(), any())).thenReturn(Optional.empty());
            TravelGuardianCoordinator coordinator =
                new TravelGuardianCoordinator(
                    new TravelActionResolver(),
                    new TravelRuleEvaluator(),
                    finder,
                    noticeStore,
                    new StrictModeClickHandler(new StrictModeGuard()));
            GuardedActionFactory genericFactory = new GuardedActionFactory();
            StrictModeClickHandler genericClickHandler =
                new StrictModeClickHandler(new StrictModeGuard());
            TravelGuardianPluginShell shell = new TravelGuardianPluginShell(
                coordinator,
                availability,
                message -> { },
                entry -> { },
                (event, context) -> genericClickHandler.handle(
                    event, genericFactory.from(event.getMenuEntry(), client), context),
                (stage, error) -> { },
                Clock.systemUTC());

            setField(plugin, "bundle", bundle);
            setField(plugin, "config", config);
            setField(plugin, "client", client);
            setField(plugin, "lastTrackerSync", Instant.now());
            setField(plugin, "travelGuardianShell", shell);
        }
    }
}
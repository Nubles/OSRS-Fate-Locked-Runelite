package com.fatelocked;

import com.fatelocked.detectors.SkillLevelDetector;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RuneLite reports LOGGED_IN after every loading screen as well as after a
 * login. Only a login, a hop, a reconnect or a different account may reset
 * the once-per-login warnings and the level and diary baselines; a teleport
 * must not repeat the account warning or swallow the next level-up.
 */
public class FateLockedLoginSessionTest
{
    private static final long MAIN_ACCOUNT = 7L;
    private static final long OTHER_ACCOUNT = 8L;
    private static final CanonicalChunk LUMBRIDGE = new CanonicalChunk(50, 50);
    private static final int WHIP = 4151;

    private final Client client = mock(Client.class);
    private FateLockedPlugin plugin;

    @Before
    public void logIn() throws Exception
    {
        plugin = new FateLockedPlugin();
        set("client", client);
        when(client.getAccountHash()).thenReturn(MAIN_ACCOUNT);
        fire(GameState.LOGIN_SCREEN, GameState.LOGGING_IN, GameState.LOGGED_IN);
    }

    @Test
    public void loadingScreensKeepTheSession() throws Exception
    {
        play();
        for (int teleport = 0; teleport < 3; teleport++)
        {
            fire(GameState.LOADING, GameState.LOGGED_IN);
        }

        assertWarningsKept();
        assertFalse("diary reading kept", (Boolean) get("diaryReadingDue"));
        assertTrue("a level-up after a teleport still counts",
            detector().detect("Attack", 51).isPresent());
    }

    @Test
    public void aHopOrReconnectResetsBaselinesButNotWarnings() throws Exception
    {
        for (GameState interruption : new GameState[] {
            GameState.HOPPING, GameState.CONNECTION_LOST })
        {
            play();
            fire(interruption, GameState.LOADING, GameState.LOGGED_IN);

            assertWarningsKept();
            assertTrue(interruption + " re-reads diaries",
                (Boolean) get("diaryReadingDue"));
            // The client re-sends every skill, which must not read as a level-up.
            assertFalse(interruption + " re-baselines skills",
                detector().detect("Attack", 51).isPresent());
        }
    }

    @Test
    public void loggingOutStartsTheNextLoginAfresh() throws Exception
    {
        play();

        fire(GameState.LOGIN_SCREEN);
        assertWarningsForgotten();

        fire(GameState.LOGGING_IN, GameState.LOGGED_IN);
        assertTrue((Boolean) get("diaryReadingDue"));
        assertFalse(detector().detect("Attack", 51).isPresent());
    }

    @Test
    public void aDifferentAccountStartsAfresh() throws Exception
    {
        play();
        when(client.getAccountHash()).thenReturn(OTHER_ACCOUNT);

        fire(GameState.LOADING, GameState.LOGGED_IN);

        assertWarningsForgotten();
        assertTrue((Boolean) get("diaryReadingDue"));
        assertFalse(detector().detect("Attack", 51).isPresent());
    }

    @Test
    public void turningThePluginOnStartsCleanAndAdoptsTheLogin() throws Exception
    {
        play();
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

        startSessionTracking();
        assertWarningsForgotten();
        assertTrue((Boolean) get("diaryReadingDue"));
        assertFalse(detector().detect("Attack", 50).isPresent());

        // Already logged in, so its next loading screen is not a new login.
        play();
        fire(GameState.LOADING, GameState.LOGGED_IN);
        assertWarningsKept();
        assertTrue(detector().detect("Attack", 51).isPresent());
    }

    /** What a few minutes of play leaves behind. */
    private void play() throws Exception
    {
        detector().clear();
        detector().detect("Attack", 50);
        set("diaryReadingDue", false);
        set("lastAccountWarned", "zezima");
        set("lastChunk", LUMBRIDGE);
        warnedOverTier().clear();
        warnedOverTier().add(WHIP);
    }

    private void assertWarningsKept() throws Exception
    {
        assertEquals("account warning not repeated", "zezima", get("lastAccountWarned"));
        assertEquals("chunk not re-announced", LUMBRIDGE, get("lastChunk"));
        assertTrue("gear warning not repeated", warnedOverTier().contains(WHIP));
    }

    private void assertWarningsForgotten() throws Exception
    {
        assertNull(get("lastAccountWarned"));
        assertNull(get("lastChunk"));
        assertTrue(warnedOverTier().isEmpty());
    }

    private void fire(GameState... states)
    {
        for (GameState state : states)
        {
            GameStateChanged event = new GameStateChanged();
            event.setGameState(state);
            plugin.onGameStateChanged(event);
        }
    }

    private void startSessionTracking() throws Exception
    {
        Method method = FateLockedPlugin.class.getDeclaredMethod("startSessionTracking");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private SkillLevelDetector detector() throws Exception
    {
        return (SkillLevelDetector) get("skillLevelDetector");
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> warnedOverTier() throws Exception
    {
        return (Set<Integer>) get("warnedOverTier");
    }

    private Object get(String name) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin);
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}

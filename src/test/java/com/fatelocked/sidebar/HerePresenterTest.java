package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.Trust;
import com.google.gson.Gson;
import java.util.Optional;
import org.junit.Test;

public class HerePresenterTest
{
    @Test
    public void aVariantOfOneThingIsNamedInBrackets()
    {
        assertEquals("Culinaromancer's Chest (Food)", HerePresenter.rowName("Culinaromancer's Chest#Food"));
        assertEquals("Battle Runes (Pre-miniquest)", HerePresenter.rowName("Battle Runes#Pre-miniquest"));
        assertEquals("Fishing spot", HerePresenter.rowName("Fishing spot"));
        assertEquals("a name that starts with one is kept", "#1", HerePresenter.rowName("#1"));
        assertEquals("Chest#", HerePresenter.rowName("Chest#"));
    }

    @Test
    public void anUnlockedPlaceKeepsARowsOwnLocationLock()
    {
        ChunkPermissionSnapshot snapshot = new Gson().fromJson("{\"name\":\"Somewhere\",\"entry\":\"ALLOWED\","
            + "\"categories\":{\"SHOPS\":[{\"name\":\"Upstairs shop\",\"status\":\"LOCKED\","
            + "\"detail\":\"Location locked\"}]}}", ChunkPermissionSnapshot.class);
        DecisionService decisions = mock(DecisionService.class);
        when(decisions.trust()).thenReturn(Trust.TRUSTED);
        when(decisions.chunk(any())).thenReturn(
            new Decision(PermissionStatus.ALLOWED, "Somewhere", null, Decision.Source.CHUNK));
        when(decisions.details(any())).thenReturn(Optional.of(snapshot));
        when(decisions.chunkName(any())).thenReturn("Somewhere");

        HereModel here = new HerePresenter().present(decisions, new CanonicalChunk(1, 2));
        assertEquals("Location locked", here.getGroups().get(0).getRows().get(0).getReason());
    }

    @Test
    public void aLockedPlaceDropsOnlyTheLocationLockFromARowsReason()
    {
        assertEquals("Woodcutting 1/15 · cap 0",
            HerePresenter.rowReason("Woodcutting 1/15 · cap 0; Location locked", true));
        assertNull(HerePresenter.rowReason("Location locked", true));
        assertNull(HerePresenter.rowReason("  ", true));
        assertEquals("Needs Sailing; Goblin Diplomacy",
            HerePresenter.rowReason("Needs Sailing; location locked; Goblin Diplomacy", true));
        assertEquals("an unlocked place keeps a row's own location lock", "Location locked",
            HerePresenter.rowReason("Location locked", false));
    }
}

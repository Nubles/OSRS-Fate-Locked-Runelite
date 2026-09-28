package com.fatelocked.sidebar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fatelocked.CanonicalChunk;
import com.fatelocked.rules.ChunkPermissionSnapshot;
import com.fatelocked.rules.Decision;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.Trust;
import com.fatelocked.ui.Palette;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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

    /**
     * The owner's review, 28 Sept: Skilling by skill, in the order of their names. Each notes the
     * player's level and cap, each row keeps the level it needs, and rows without a skill come last.
     */
    @Test
    public void skillingIsSplitBySkill()
    {
        HereModel.Row rope = new HereModel.Row("Rope swing", "Can do", Palette.Tone.GOOD, null);
        List<HereModel.Row> rows = Arrays.asList(
            new HereModel.Row("Yew tree", "Not ready", Palette.Tone.PENDING, "Woodcutting 15/60 · cap 20"),
            new HereModel.Row("Fishing spot", "Can do", Palette.Tone.GOOD,
                "Fishing 5/1 · cap 10; The Lost Tribe started"),
            new HereModel.Row("Tree", "Can do", Palette.Tone.GOOD, "Woodcutting 15/1 · cap 20"),
            rope);

        List<HereModel.Subgroup> skills = HerePresenter.bySkill("SKILLING", rows);

        assertEquals(Arrays.asList("Fishing", "Woodcutting", "Other"),
            skills.stream().map(HereModel.Subgroup::getTitle).collect(Collectors.toList()));
        HereModel.Subgroup fishing = skills.get(0);
        assertEquals("Level 5 · cap 10", fishing.getNote());
        assertEquals("Level 1; The Lost Tribe started", fishing.getRows().get(0).getReason());
        HereModel.Subgroup woodcutting = skills.get(1);
        assertEquals("SKILLING/Woodcutting", woodcutting.getKey());
        assertEquals("Woodcutting", woodcutting.getSkill());
        assertEquals("Level 15 · cap 20", woodcutting.getNote());
        assertEquals(Arrays.asList("Level 60", "Level 1"),
            woodcutting.getRows().stream().map(HereModel.Row::getReason).collect(Collectors.toList()));
        HereModel.Subgroup other = skills.get(2);
        assertEquals("SKILLING/Other", other.getKey());
        assertNull(other.getSkill());
        assertNull(other.getNote());
        assertEquals(Collections.singletonList(rope), other.getRows());

        assertTrue("no skill at all: nothing to split", HerePresenter.bySkill("SKILLING",
            Collections.singletonList(rope)).isEmpty());
    }

    /** A closed line's summary: each status the rows have, in the card's order, the others left out. */
    @Test
    public void rowsAreTalliedByStatus()
    {
        List<HereModel.Row> rows = Arrays.asList(
            new HereModel.Row("A", "Can do", Palette.Tone.GOOD, null),
            new HereModel.Row("B", "Locked", Palette.Tone.BAD, null),
            new HereModel.Row("C", "Can do", Palette.Tone.GOOD, null),
            new HereModel.Row("D", null, Palette.Tone.NEUTRAL, null));
        assertEquals(Arrays.asList(new HereModel.Count(2, "can do", Palette.Tone.GOOD),
            new HereModel.Count(1, "locked", Palette.Tone.BAD)), new HereModel.Group("SHOPS", "Shops", rows).tally());
        assertEquals(Collections.singletonList(new HereModel.Count(1, "not ready", Palette.Tone.PENDING)),
            HereModel.tally(Collections.singletonList(new HereModel.Row("E", "Not ready", Palette.Tone.PENDING,
                null))));
    }
}

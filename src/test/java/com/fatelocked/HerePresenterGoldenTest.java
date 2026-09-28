package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.fatelocked.ui.Palette.Tone;
import com.google.gson.Gson;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/** The Here card from the golden runs (C3; U13, U16, U21). */
public class HerePresenterGoldenTest
{
    private static final Gson GSON = new Gson();
    private static final HerePresenter PRESENTER = new HerePresenter();

    @Test
    public void everyRowKeepsItsReasonAndSaysItsStatusInAWord() throws Exception
    {
        HereModel here = here("vanilla-mid", "iron example", 50, 50);
        assertEquals("Lumbridge Castle", here.getPlace());
        assertEquals("Unlocked", here.getWord());
        assertEquals(Tone.GOOD, here.getTone());
        assertNull("an unlocked place needs no reason", here.getReason());
        assertEquals("Lumbridge · Misthalin · 50, 50", here.getWhere());
        // Six rows the tracker leaves undecided, the caves' monsters, count as not ready: logged out,
        // the game can't say The Lost Tribe is started.
        assertEquals(List.of(14, 26, 35),
            here.getCounts().stream().map(HereModel.Count::getValue).collect(Collectors.toList()));
        assertEquals(List.of("Can do", "Not ready", "Locked"),
            here.getCounts().stream().map(HereModel.Count::getLabel).collect(Collectors.toList()));

        HereModel.Group skilling = group(here, "SKILLING");
        HereModel.Row deadTree = skilling.getRows().get(0);
        assertEquals("Dead tree", deadTree.getName());
        assertEquals("Not ready", deadTree.getWord());
        assertEquals("Woodcutting 1/1 · cap 0", deadTree.getReason());

        // U13: quests, banks and shops kept no reason before Stage 3.
        HereModel.Row shop = group(here, "SHOPS").getRows().get(0);
        assertEquals("Locked", shop.getWord());
        assertNotNull(shop.getName() + " says why", shop.getReason());
        assertTrue(group(here, "QUESTS").getRows().stream().anyMatch(row -> row.getReason() != null));
        HereModel.Row guard = group(here, "COMBAT").getRows().stream()
            .filter(row -> row.getName().equals("Cave goblin guard")).findFirst().orElseThrow(AssertionError::new);
        assertEquals("Not ready", guard.getWord());
        assertEquals("The Lost Tribe started", guard.getReason());
    }

    /** The owner's review, 28 Sept: no row of any golden, anywhere, is left without a status. */
    @Test
    public void everyRowHasAStatus() throws Exception
    {
        for (Object[] scenario : GoldenBundleContractTest.scenarios())
        {
            String id = (String) scenario[0];
            DecisionService playing = decisions(id, "iron example");
            for (CanonicalChunk chunk : playing.mappedChunks())
            {
                for (HereModel.Group group : PRESENTER.present(playing, chunk).getGroups())
                {
                    for (HereModel.Row row : group.getRows())
                    {
                        assertTrue(id + " " + chunk + " " + row.getName() + ": " + row.getWord(),
                            List.of("Can do", "Not ready", "Locked").contains(row.getWord()));
                    }
                }
            }
        }
    }

    /** Skilling splits by skill, each with the level and cap the tracker gives; rows keep the level they need. */
    @Test
    public void skillingSplitsBySkill() throws Exception
    {
        HereModel.Group skilling = group(here("vanilla-mid", "iron example", 50, 50), "SKILLING");
        List<String> skills = skilling.getSubgroups().stream().map(HereModel.Subgroup::getTitle)
            .collect(Collectors.toList());
        assertEquals(List.of("Fishing", "Mining", "Woodcutting"), skills);
        HereModel.Subgroup woodcutting = skilling.getSubgroups().get(2);
        assertEquals("SKILLING/Woodcutting", woodcutting.getKey());
        assertEquals("Woodcutting", woodcutting.getSkill());
        assertEquals("Level 1 · cap 0", woodcutting.getNote());
        HereModel.Row yew = woodcutting.getRows().stream().filter(row -> row.getName().equals("Yew tree")).findFirst()
            .orElseThrow(AssertionError::new);
        assertEquals("Level 60", yew.getReason());
        assertEquals("every row, in one skill or another", skilling.getRows().size(),
            skilling.getSubgroups().stream().mapToInt(subgroup -> subgroup.getRows().size()).sum());
    }

    @Test
    public void aLockedPlaceSaysWhyOnceAndItsRowsDontRepeatIt() throws Exception
    {
        HereModel here = here("vanilla-fresh", "iron example", 46, 52);
        assertEquals("West Falador", here.getPlace());
        assertEquals("Locked", here.getWord());
        assertEquals(Tone.BAD, here.getTone());
        assertEquals("Unlock Falador", here.getReason());
        for (HereModel.Group group : here.getGroups())
        {
            for (HereModel.Row row : group.getRows())
            {
                assertTrue(row.getName() + ": " + row.getReason(),
                    row.getReason() == null || !row.getReason().contains("Location locked"));
            }
        }
        assertEquals("Woodcutting 1/15 · cap 0", group(here, "SKILLING").getRows().get(0).getReason());
    }

    @Test
    public void anInteriorIsNamedByItsPlace() throws Exception
    {
        HereModel here = here("vanilla-interiors", "iron example", 18, 143);
        assertEquals("Kurask Lair", here.getPlace());
        assertEquals("Locked", here.getWord());
        assertEquals("18, 143", here.getWhere());
    }

    @Test
    public void theSeaIsNamedAsTheTrackersPlacesNameIt() throws Exception
    {
        HereModel here = here("vanilla-mid", "iron example", 40, 41);
        assertEquals("Ocean", here.getPlace());
        assertEquals("Locked", here.getWord());
    }

    @Test
    public void anotherCharacterSeesThePlaceButNothingChecked() throws Exception
    {
        HereModel here = here("vanilla-mid", "zezima", 50, 50);
        assertEquals("Lumbridge Castle", here.getPlace());
        assertNull(here.getWord());
        assertEquals(Tone.NEUTRAL, here.getTone());
        assertTrue(here.getReason(), here.getReason().contains("another character"));
        assertTrue(here.getGroups().isEmpty());
        assertTrue(here.getCounts().isEmpty());
    }

    @Test
    public void loggedOutThereIsNoPlace() throws Exception
    {
        DecisionService decisions = decisions("vanilla-mid", "iron example");
        HereModel here = PRESENTER.present(decisions, null);
        assertNull(here.getPlace());
        assertTrue(here.getMessage(), here.getMessage().startsWith("Log in"));
    }

    @Test
    public void aGroupShowsFiveRowsUntilOpened() throws Exception
    {
        HereModel.Group shops = group(here("vanilla-mid", "iron example", 50, 50), "SHOPS");
        assertEquals(21, shops.getRows().size());
        assertEquals(HereModel.ROWS_SHOWN, shops.shown(false).size());
        assertEquals(16, shops.hidden());
        assertEquals(21, shops.shown(true).size());
    }

    @Test
    public void rowNamesShowTheirVariantInBrackets() throws Exception
    {
        List<String> names = group(here("vanilla-mid", "iron example", 50, 50), "SHOPS").getRows().stream()
            .map(HereModel.Row::getName).collect(Collectors.toList());
        assertTrue(names.toString(), names.contains("Culinaromancer's Chest (Food)"));
        assertTrue(names.stream().noneMatch(name -> name.contains("#")));
    }

    /**
     * What the card could point at in a place, for remembering where each is seen: the rows of
     * the categories it points in, named as it shows them; none for another character.
     */
    @Test
    public void theCardKnowsWhatItCouldPointAt() throws Exception
    {
        CanonicalChunk lumbridge = new CanonicalChunk(50, 50);
        List<String> rows = HerePresenter.pointable(decisions("vanilla-mid", "iron example"), lumbridge).stream()
            .map(target -> target.getCategory() + ": " + target.getLabel()).collect(Collectors.toList());

        assertTrue(rows.toString(), rows.contains("SKILLING: Dead tree"));
        assertTrue(rows.contains("COMBAT: Cave goblin guard"));
        assertTrue(rows.contains("SHOPS: Culinaromancer's Chest (Food)"));
        assertEquals("every Skilling row, pointable or not by status",
            group(here("vanilla-mid", "iron example", 50, 50), "SKILLING").getRows().size(),
            rows.stream().filter(row -> row.startsWith("SKILLING: ")).count());
        assertTrue("no quest or journey is one thing", rows.stream()
            .noneMatch(row -> row.startsWith("QUESTS") || row.startsWith("TRAVEL")));
        assertTrue(HerePresenter.pointable(decisions("vanilla-mid", "someone else"), lumbridge).isEmpty());
        assertTrue(HerePresenter.pointable(decisions("vanilla-mid", "iron example"), null).isEmpty());
    }

    /** An older export lists what's in a place without statuses; the card can still point at it. */
    @Test
    public void anOlderExportsContentCanBePointedAt()
    {
        CanonicalChunk lumbridge = new CanonicalChunk(50, 50);
        DecisionService older = org.mockito.Mockito.mock(DecisionService.class);
        org.mockito.Mockito.when(older.trust()).thenReturn(com.fatelocked.rules.Trust.TRUSTED);
        org.mockito.Mockito.when(older.details(lumbridge)).thenReturn(java.util.Optional.empty());
        java.util.Map<String, List<String>> content = new java.util.LinkedHashMap<>();
        content.put("mon", List.of("Goblin"));
        content.put("shop", List.of("Bob's Brilliant Axes"));
        content.put("farm", List.of("Herb patch"));
        content.put("poi", List.of("Fountain"));
        org.mockito.Mockito.when(older.legacyContent(lumbridge)).thenReturn(content);

        List<String> rows = HerePresenter.pointable(older, lumbridge).stream()
            .map(target -> target.getCategory() + ": " + target.getLabel()).collect(Collectors.toList());

        assertEquals(List.of("COMBAT: Goblin", "SHOPS: Bob's Brilliant Axes", "FARMING: Herb patch",
            "ACTIVITIES: Fountain"), rows);
    }

    private static HereModel.Group group(HereModel here, String category)
    {
        return here.getGroups().stream().filter(group -> group.getCategory().equals(category)).findFirst()
            .orElseThrow(() -> new AssertionError("no " + category));
    }

    private static HereModel here(String scenario, String player, int x, int y) throws Exception
    {
        return PRESENTER.present(decisions(scenario, player), new CanonicalChunk(x, y));
    }

    private static DecisionService decisions(String scenario, String player) throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(scenario + ".bundle.json.gz"));
        return DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(GSON, json)), "iron example",
            player);
    }
}

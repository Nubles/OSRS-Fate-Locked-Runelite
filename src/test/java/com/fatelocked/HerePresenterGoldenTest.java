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
        assertEquals(List.of(14, 20, 35),
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
        assertTrue("combat rows say Needs checking, not a cross",
            group(here, "COMBAT").getRows().stream().anyMatch(row -> "Needs checking".equals(row.getWord())));
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

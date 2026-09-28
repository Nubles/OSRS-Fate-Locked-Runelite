package com.fatelocked;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.RulesSnapshot;
import com.fatelocked.sidebar.GameFacts;
import com.fatelocked.sidebar.HereModel;
import com.fatelocked.sidebar.HerePresenter;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;

/** The owner's review, 28 Sept: what the game says, read for the rows the tracker leaves undecided. */
public class FateLockedGameFactsTest
{
    /** Lumbridge Castle, whose caves' monsters the tracker leaves undecided. */
    private static final CanonicalChunk CASTLE = new CanonicalChunk(50, 50);

    private final Client client = mock(Client.class);
    private final ItemManager items = mock(ItemManager.class);
    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private DecisionService rules;

    @Before
    public void setUp() throws Exception
    {
        String json = GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes("vanilla-mid.bundle.json.gz"));
        rules = DecisionService.create(RulesSnapshot.of(FateLockedBundle.loadFromJson(new Gson(), json)),
            "iron example", "iron example");
        PluginTestSupport.set(plugin, "client", client);
        PluginTestSupport.set(plugin, "itemManager", items);
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTickCount()).thenReturn(100);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        when(client.getVarpValue(VarPlayerID.QP)).thenReturn(41);
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(30);
        // The Lost Tribe is under way; every other quest is still to start.
        int[] stack = new int[1];
        doAnswer(ask -> {
            stack[0] = (Integer) ask.getArguments()[1] == Quest.THE_LOST_TRIBE.getId() ? 0 : 1;
            return null;
        }).when(client).runScript(any(), any());
        when(client.getIntStack()).thenReturn(stack);
        ItemContainer inventory = mock(ItemContainer.class);
        when(inventory.getItems()).thenReturn(new Item[] {new Item(ItemID.CANDLE_LANTERN_LIT, 1),
            new Item(ItemID.EDGEVILLEDUNGEONKEY, 1), new Item(-1, 0)});
        when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
        name(ItemID.CANDLE_LANTERN_LIT, "Candle lantern");
        name(ItemID.EDGEVILLEDUNGEONKEY, "Brass key");
    }

    @Test
    public void theGameIsAskedOnlyAboutWhatUndecidedRowsNeed()
    {
        GameFacts facts = plugin.gameFacts(rules, CASTLE);

        assertEquals(Boolean.TRUE, facts.getMembers());
        assertEquals(41, facts.getQuestPoints());
        assertEquals(Integer.valueOf(30), facts.getLevels().get(Skill.MINING));
        assertEquals("only the quests its rows name", EnumSet.of(Quest.THE_LOST_TRIBE, Quest.DEATH_TO_THE_DORGESHUUN),
            facts.getQuests().keySet());
        assertEquals(QuestState.IN_PROGRESS, facts.getQuests().get(Quest.THE_LOST_TRIBE));
        assertEquals(QuestState.NOT_STARTED, facts.getQuests().get(Quest.DEATH_TO_THE_DORGESHUUN));
        assertEquals(new HashSet<>(Arrays.asList("candle lantern", "brass key")), facts.getCarried());
        assertEquals(Boolean.TRUE, facts.getLight());

        assertSame("read once a tick", facts, plugin.gameFacts(rules, CASTLE));
        verify(client, times(2)).runScript(any(), any());
    }

    /** The caves' guard only needs The Lost Tribe started: now it can be done; the frogs need more. */
    @Test
    public void theHereCardDecidesByThem()
    {
        HereModel here = new HerePresenter().present(rules, CASTLE, plugin.gameFacts(rules, CASTLE));
        HereModel.Group combat = here.getGroups().stream().filter(group -> group.getCategory().equals("COMBAT"))
            .findFirst().orElseThrow(AssertionError::new);
        assertEquals("Can do", row(combat, "Cave goblin guard").getWord());
        HereModel.Row frog = row(combat, "Big frog");
        assertEquals("Not ready", frog.getWord());
        assertEquals("Death to the Dorgeshuun", frog.getReason());
    }

    @Test
    public void nothingIsReadWhereNoRowNeedsIt()
    {
        assertSame(GameFacts.NONE, plugin.gameFacts(rules, new CanonicalChunk(46, 52)));
        assertSame("logged out", GameFacts.NONE, plugin.gameFacts(rules, null));
        when(client.isClientThread()).thenReturn(false);
        assertSame("off the client thread", GameFacts.NONE, plugin.gameFacts(rules, CASTLE));
        verify(client, never()).runScript(any(), any());
        verify(items, never()).getItemComposition(anyInt());
    }

    private void name(int id, String name)
    {
        ItemComposition composition = mock(ItemComposition.class);
        when(composition.getName()).thenReturn(name);
        when(items.getItemComposition(id)).thenReturn(composition);
    }

    private static HereModel.Row row(HereModel.Group group, String name)
    {
        return group.getRows().stream().filter(row -> row.getName().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no " + name));
    }
}

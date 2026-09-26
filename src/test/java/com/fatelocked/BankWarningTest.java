package com.fatelocked;

import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.PermissionStatus;
import com.fatelocked.rules.RulesSnapshot;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B3: the "roll it under Banks" warning when a bank opens. It comes from the
 * decision service, only for a bank the rules lock that isn't rolled: in
 * custom-none-banks-on, Lumbridge Castle's bank warns and the rolled Al
 * Kharid bank (13105) never does, though its row is locked with its area.
 */
public class BankWarningTest
{
    private static final Gson GSON = new Gson();
    private static final int BANK = 12;
    private static final int DEPOSIT_BOX = 192;
    /** Bank 12850, never rolled in custom-none-banks-on. */
    private static final CanonicalChunk LUMBRIDGE_CASTLE = new CanonicalChunk(50, 50);
    /** Bank 13105, rolled in custom-none-banks-on. */
    private static final CanonicalChunk AL_KHARID_PALACE = new CanonicalChunk(51, 49);
    /** Bank 11828, rolled in the v3 fixture. */
    private static final CanonicalChunk FALADOR = new CanonicalChunk(46, 52);

    private final FateLockedPlugin plugin = new FateLockedPlugin();
    private final FateLockedConfig config = mock(FateLockedConfig.class);
    private final Client client = mock(Client.class);
    private final ChatMessageManager chat = mock(ChatMessageManager.class);
    private final Player player = mock(Player.class);

    @Before
    public void setUp() throws Exception
    {
        when(config.warnLockedBank()).thenReturn(true);
        set("config", config);
        set("client", client);
        set("chatMessageManager", chat);
        set("notifier", mock(Notifier.class));
    }

    @Test
    public void aLockedBankThatIsNotRolledWarns() throws Exception
    {
        playing(golden("custom-none-banks-on"), "Iron Example");

        List<String> lines = openBankAt(LUMBRIDGE_CASTLE, BANK);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("Lumbridge Castle"));
        assertTrue(lines.get(0), lines.get(0).contains("roll it under Banks"));
        assertEquals("a deposit box too", 1, openBankAt(LUMBRIDGE_CASTLE, DEPOSIT_BOX).size());
    }

    @Test
    public void aRolledBankNeverWarnsEvenInALockedArea() throws Exception
    {
        FateLockedBundle rules = golden("custom-none-banks-on");
        JsonObject expected = GoldenBundleContractTest.json("custom-none-banks-on.expect.json");
        assertTrue("13105 is rolled", expected.getAsJsonObject("banks").get("13105").getAsBoolean());
        DecisionService decisions = DecisionService.create(RulesSnapshot.of(rules), "iron example", "iron example");
        assertEquals("its row is locked with its area",
            PermissionStatus.LOCKED, decisions.bankAt(AL_KHARID_PALACE).getStatus());
        playing(rules, "Iron Example");

        assertEquals(List.of(), openBankAt(AL_KHARID_PALACE, BANK));
    }

    /**
     * Keldagrim's bank is inside the city, where the rules list no bank yet
     * (R5, fixed by the tracker's bank table in E4): no row, no warning.
     */
    @Test
    public void noWarningWhereTheRulesListNoBank() throws Exception
    {
        FateLockedBundle rules = golden("custom-none-banks-on");
        CanonicalChunk keldagrim = new CanonicalChunk(44, 159);
        DecisionService decisions = DecisionService.create(RulesSnapshot.of(rules), "iron example", "iron example");
        assertEquals(PermissionStatus.UNKNOWN, decisions.bankAt(keldagrim).getStatus());
        assertEquals("not rolled", PermissionStatus.LOCKED, decisions.bankRoll(keldagrim).getStatus());
        playing(rules, "Iron Example");

        assertEquals(List.of(), openBankAt(keldagrim, BANK));
    }

    @Test
    public void banksOffNeverWarns() throws Exception
    {
        FateLockedBundle rules = golden("custom-lumbridge-banks-off");
        DecisionService decisions = DecisionService.create(RulesSnapshot.of(rules), "iron example", "iron example");
        assertEquals(PermissionStatus.LOCKED, decisions.bankAt(AL_KHARID_PALACE).getStatus());
        playing(rules, "Iron Example");

        assertEquals(List.of(), openBankAt(AL_KHARID_PALACE, BANK));
        assertEquals(List.of(), openBankAt(LUMBRIDGE_CASTLE, BANK));
    }

    @Test
    public void anotherCharacterOrTheSettingOffGetsNoWarning() throws Exception
    {
        playing(golden("custom-none-banks-on"), "Someone Else");
        assertEquals(List.of(), openBankAt(LUMBRIDGE_CASTLE, BANK));

        playing(golden("custom-none-banks-on"), "Iron Example");
        when(config.warnLockedBank()).thenReturn(false);
        assertEquals(List.of(), openBankAt(LUMBRIDGE_CASTLE, BANK));
    }

    @Test
    public void anOlderExportWarnsAsBeforeForItsCharacter() throws Exception
    {
        FateLockedBundle legacy = FateLockedBundle.loadFromJson(GSON, text("bundles/v3-standard.json"));
        playing(legacy, "Nubles");

        List<String> lines = openBankAt(LUMBRIDGE_CASTLE, BANK);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).contains("Lumbridge · Misthalin bank"));
        assertEquals("rolled", List.of(), openBankAt(FALADOR, BANK));

        playing(legacy, "Zezima");
        assertEquals(List.of(), openBankAt(LUMBRIDGE_CASTLE, BANK));
    }

    /** Open a bank interface standing in a chunk; the chat lines it queued. */
    private List<String> openBankAt(CanonicalChunk chunk, int group)
    {
        clearInvocations(chat);
        TestWorld.standAt(client, player, new WorldPoint((chunk.getCx() << 6) + 30, (chunk.getCy() << 6) + 30, 0));
        WidgetLoaded loaded = new WidgetLoaded();
        loaded.setGroupId(group);
        plugin.onWidgetLoaded(loaded);
        ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chat, atLeast(0)).queue(queued.capture());
        List<String> lines = new ArrayList<>();
        for (QueuedMessage message : queued.getAllValues()) lines.add(message.getRuneLiteFormattedMessage());
        return lines;
    }

    /** The rules in force, for a character logged in, as the plugin refreshes them. */
    private void playing(FateLockedBundle rules, String name) throws Exception
    {
        set("active", new ActiveRules(rules, FateLockedPlugin.RulesSource.RELAY));
        when(player.getName()).thenReturn(name);
        when(client.getLocalPlayer()).thenReturn(player);
        Method refresh = FateLockedPlugin.class.getDeclaredMethod("refreshDecisions");
        refresh.setAccessible(true);
        refresh.invoke(plugin);
    }

    private static FateLockedBundle golden(String id) throws Exception
    {
        return FateLockedBundle.loadFromJson(GSON,
            GoldenBundleContractTest.gunzip(GoldenBundleContractTest.bytes(id + ".bundle.json.gz")));
    }

    private static String text(String name) throws Exception
    {
        try (InputStream in = BankWarningTest.class.getClassLoader().getResourceAsStream(name))
        {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void set(String name, Object value) throws Exception
    {
        Field field = FateLockedPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}

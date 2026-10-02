package com.fatelocked;

import com.fatelocked.detection.DiaryTiers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For the owner's review in RuneLite (Stage 4, C5), never shipped: it lives with the tests, and the
 * dev launcher loads it only when asked. It writes down what the game says that RuneLite's detectors
 * read, so real lines can join the tests: game messages, notification popups, the collection log's
 * notification setting, and the diary and Slayer values as they change. They go to
 * .runelite/fate-locked-review-signals.log on this computer, and nowhere else.
 */
@PluginDescriptor(
    name = "Fate Locked review recorder",
    description = "Writes the game's messages, popups, diary and Slayer values to a file, for the review"
)
public class ReviewRecorderPlugin extends Plugin
{
    static final String FILE = "fate-locked-review-signals.log";
    private static final Logger log = LoggerFactory.getLogger(ReviewRecorderPlugin.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** The Slayer values the detector reads, by name. */
    private static final Map<Integer, String> SLAYER_VARBITS = named(
        VarbitID.SLAYER_MASTER, "master",
        VarbitID.SLAYER_TASKS_COMPLETED, "streak",
        VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, "wildernessStreak",
        VarbitID.SLAYER_TARGET_BOSSID, "bossId",
        VarbitID.SLAYER_MODIFIER_ID, "modifierId",
        VarbitID.SLAYER_MODIFIER_VALUE, "modifierValue",
        VarbitID.SLAYER_MODIFIER_NEGATIVE, "modifierNegative");
    private static final Map<Integer, String> SLAYER_VARPS = named(
        VarPlayerID.SLAYER_COUNT, "amount",
        VarPlayerID.SLAYER_TARGET, "target",
        VarPlayerID.SLAYER_COUNT_ORIGINAL, "assigned",
        VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, "mortimerStreak");

    @Inject
    private Client client;

    private boolean notificationStarted;
    private boolean slayerChanged;

    @Override
    protected void startUp()
    {
        write("recorder started");
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev)
    {
        if (ev.getType() == ChatMessageType.GAMEMESSAGE || ev.getType() == ChatMessageType.SPAM)
        {
            write("chat " + ev.getType() + " " + ev.getMessage());
        }
    }

    /** The notification popup, read as the plugin reads it: its title and text once shown. */
    @Subscribe
    public void onScriptPreFired(ScriptPreFired ev)
    {
        if (ev.getScriptId() == ScriptID.NOTIFICATION_START)
        {
            notificationStarted = true;
        }
        else if (ev.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
        {
            notificationStarted = false;
            write("popup " + client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE) + " | "
                + client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN));
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged ev)
    {
        int varbit = ev.getVarbitId();
        String tier = DiaryTiers.TIER_IDS.get(varbit);
        if (tier != null)
        {
            write("diary " + tier + " (varbit " + varbit + ") = " + ev.getValue());
        }
        else if (varbit == VarbitID.OPTION_COLLECTION_NEW_ITEM)
        {
            write("collection log notification setting = " + ev.getValue());
        }
        if (SLAYER_VARBITS.containsKey(varbit) || varbit == -1 && SLAYER_VARPS.containsKey(ev.getVarpId()))
        {
            slayerChanged = true;
        }
    }

    /** Every Slayer value together, once a tick's changes are in. */
    @Subscribe
    public void onGameTick(GameTick ev)
    {
        if (!slayerChanged) return;
        slayerChanged = false;
        StringBuilder line = new StringBuilder("slayer");
        for (Map.Entry<Integer, String> value : SLAYER_VARPS.entrySet())
        {
            line.append(' ').append(value.getValue()).append('=').append(client.getVarpValue(value.getKey()));
        }
        for (Map.Entry<Integer, String> value : SLAYER_VARBITS.entrySet())
        {
            line.append(' ').append(value.getValue()).append('=').append(client.getVarbitValue(value.getKey()));
        }
        write(line.toString());
    }

    private static synchronized void write(String line)
    {
        Path file = RuneLite.RUNELITE_DIR.toPath().resolve(FILE);
        try
        {
            Files.write(file, Collections.singletonList(LocalDateTime.now().format(TIME) + " " + line),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException ex)
        {
            log.warn("Could not write the review recording", ex);
        }
    }

    private static Map<Integer, String> named(Object... idsAndNames)
    {
        Map<Integer, String> named = new LinkedHashMap<>();
        for (int i = 0; i < idsAndNames.length; i += 2)
        {
            named.put((Integer) idsAndNames[i], (String) idsAndNames[i + 1]);
        }
        return named;
    }
}

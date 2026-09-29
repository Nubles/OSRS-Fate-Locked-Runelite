package com.fatelocked;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with this plugin loaded from source, for the checks in
 * docs/in-game-release-checklist.md. Not a unit test: run it with
 * {@code gradle runClient}, or run {@code main} from an IDE with the VM
 * option {@code -ea}. With {@code -Dfatelocked.reviewRecorder=true} it also
 * loads {@link ReviewRecorderPlugin}, for an owner's review.
 */
public class FateLockedPluginDevLauncher
{
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception
    {
        if (Boolean.getBoolean("fatelocked.reviewRecorder"))
        {
            ExternalPluginManager.loadBuiltin(FateLockedPlugin.class, ReviewRecorderPlugin.class);
        }
        else
        {
            ExternalPluginManager.loadBuiltin(FateLockedPlugin.class);
        }
        RuneLite.main(args);
    }
}

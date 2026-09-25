package com.fatelocked;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts RuneLite with this plugin loaded from source, for the checks in
 * docs/in-game-release-checklist.md. Not a unit test: run it with
 * {@code gradle runClient}, or run {@code main} from an IDE with the VM
 * option {@code -ea}.
 */
public class FateLockedPluginDevLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FateLockedPlugin.class);
        RuneLite.main(args);
    }
}

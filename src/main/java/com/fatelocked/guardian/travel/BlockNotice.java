package com.fatelocked.guardian.travel;

import lombok.Value;

/**
 * What Strict Mode says when it blocks a trip (B15, G10): the banner's
 * headline, reason and suggestion, and the chat line, which names Strict
 * Mode and says how to pause it.
 */
@Value
public class BlockNotice
{
    /** "Strict Mode blocked Varrock Teleport". */
    String headline;
    /** Why, in the tracker's words: "Varrock is locked". */
    String reason;
    /** Another way there the player can use, or null. */
    String alternative;
    /** The chat line, without the plugin's "[Fate Locked]" prefix. */
    String chatLine;
}

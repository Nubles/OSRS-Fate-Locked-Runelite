package com.fatelocked;

import net.runelite.client.util.Text;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which OSRS account a tracker profile is for. This is the one place that
 * reads it, and it compares names the way the tracker's normalizeAccountName
 * does (trim, collapse whitespace, lower-case), so the rules, Strict Mode,
 * the HUD and the account warning can never disagree.
 */
final class AccountBinding
{
    private static final Pattern WHITESPACE =
        Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private AccountBinding()
    {
    }

    /**
     * The account the profile is for: the rules' account. Older bundles
     * without rules fall back to the run state's linked account. Null when
     * the profile is not bound to one.
     */
    static String boundAccount(FateLockedBundle bundle)
    {
        if (bundle == null)
        {
            return null;
        }
        String bound = bundle.getRules() != null
            ? bundle.getRules().getAccount()
            : bundle.getState() == null ? null : bundle.getState().getLinkedAccount();
        return normalize(bound).isEmpty() ? null : bound.trim();
    }

    /**
     * The tracker's normalizeAccountName, after RuneLite's own clean-up of
     * a name (icon tags, non-breaking spaces).
     */
    static String normalize(String name)
    {
        if (name == null)
        {
            return "";
        }
        return WHITESPACE.matcher(Text.sanitize(name)).replaceAll(" ")
            .trim().toLowerCase(Locale.ROOT);
    }

    /** Whether a logged-in name is the bound account. */
    static boolean sameAccount(String bound, String player)
    {
        String account = normalize(bound);
        return !account.isEmpty() && account.equals(normalize(player));
    }
}

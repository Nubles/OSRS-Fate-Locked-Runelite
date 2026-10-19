package com.fatelocked.badges;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.ChatMessageType;
import org.junit.Test;

/** A badge goes before the name, after the icons the game put there, and only once. */
public class BadgeChatTest
{
    private static final String KEY = "<img=60>";

    @Test
    public void theKeyGoesBeforeAPlainName()
    {
        assertEquals("<img=60>Iron Example", BadgeChat.withBadge("Iron Example", KEY));
    }

    @Test
    public void theGamesIconsStayFirst()
    {
        // An ironman helmet, then a rank: the key goes after both.
        assertEquals("<img=2><img=60>Iron Example", BadgeChat.withBadge("<img=2>Iron Example", KEY));
        assertEquals("<img=2><img=41><img=60>Iron Example",
            BadgeChat.withBadge("<img=2><img=41>Iron Example", KEY));
        // A colour tag isn't an icon.
        assertEquals("<img=60><col=ffffff>Iron Example</col>",
            BadgeChat.withBadge("<col=ffffff>Iron Example</col>", KEY));
    }

    @Test
    public void aRebuiltLineGetsNoSecondKey()
    {
        String once = BadgeChat.withBadge("<img=2>Iron Example", KEY);
        assertEquals(once, BadgeChat.withBadge(once, KEY));
        // <img=6> is not <img=60>.
        assertEquals("<img=6><img=60>Iron Example", BadgeChat.withBadge("<img=6>Iron Example", KEY));
    }

    @Test
    public void nothingToBadgeIsLeftAlone()
    {
        assertNull(BadgeChat.withBadge(null, KEY));
        assertEquals("", BadgeChat.withBadge("", KEY));
        assertEquals("Iron Example", BadgeChat.withBadge("Iron Example", null));
        // A broken tag is not an icon to keep first.
        assertEquals("<img=60><img=2", BadgeChat.withBadge("<img=2", KEY));
    }

    @Test
    public void theTagNamesTheChatIcon()
    {
        assertEquals("<img=60>", BadgeChat.tag(60));
        assertEquals("<img=0>", BadgeChat.tag(0));
        assertNull(BadgeChat.tag(-1));
    }

    @Test
    public void playersMessagesAreBadgedAndTheGamesAreNot()
    {
        for (ChatMessageType type : new ChatMessageType[] {ChatMessageType.PUBLICCHAT, ChatMessageType.MODCHAT,
            ChatMessageType.PRIVATECHAT, ChatMessageType.MODPRIVATECHAT, ChatMessageType.PRIVATECHATOUT,
            ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
            ChatMessageType.CLAN_GIM_CHAT})
        {
            assertTrue(type.name(), BadgeChat.badged(type));
        }
        for (ChatMessageType type : new ChatMessageType[] {ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM,
            ChatMessageType.CLAN_MESSAGE, ChatMessageType.CLAN_GUEST_MESSAGE, ChatMessageType.LOGINLOGOUTNOTIFICATION,
            ChatMessageType.BROADCAST, ChatMessageType.TRADEREQ, ChatMessageType.CONSOLE})
        {
            assertFalse(type.name(), BadgeChat.badged(type));
        }
        assertFalse(BadgeChat.badged(null));
    }
}

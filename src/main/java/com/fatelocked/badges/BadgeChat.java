package com.fatelocked.badges;

import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.ChatMessageType;

/** Where a badge goes in chat: before the sender's name, after the icons the game already put there. */
public final class BadgeChat
{
    /** Public, private both ways, friends chat, clan, guest clan and group ironman chat. */
    static final Set<ChatMessageType> BADGED = EnumSet.of(
        ChatMessageType.PUBLICCHAT,
        ChatMessageType.MODCHAT,
        ChatMessageType.PRIVATECHAT,
        ChatMessageType.MODPRIVATECHAT,
        ChatMessageType.PRIVATECHATOUT,
        ChatMessageType.FRIENDSCHAT,
        ChatMessageType.CLAN_CHAT,
        ChatMessageType.CLAN_GUEST_CHAT,
        ChatMessageType.CLAN_GIM_CHAT);

    private BadgeChat()
    {
    }

    /** Whether a message of this kind names a player whose badge shows. */
    public static boolean badged(ChatMessageType type)
    {
        return type != null && BADGED.contains(type);
    }

    /** The chat tag for chat icon index; null for none. */
    public static String tag(int chatIconIndex)
    {
        return chatIconIndex < 0 ? null : "<img=" + chatIconIndex + ">";
    }

    /**
     * The name with the badge's tag after any icons it starts with (an ironman helmet, a mod
     * crown, a rank), so theirs stay first. Unchanged when it carries the tag already, so a
     * rebuilt chatbox never shows two.
     */
    public static String withBadge(String name, String tag)
    {
        if (name == null || name.isEmpty() || tag == null || name.contains(tag))
        {
            return name;
        }
        int at = 0;
        while (name.startsWith("<img=", at))
        {
            int end = name.indexOf('>', at);
            if (end < 0)
            {
                break;
            }
            at = end + 1;
        }
        return name.substring(0, at) + tag + name.substring(at);
    }
}

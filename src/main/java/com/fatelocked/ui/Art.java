package com.fatelocked.ui;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;

/**
 * The OSRS art the plugin draws with, by what it stands for: interface sprites from
 * the game cache, and item images. Rows use art of 16 px or less, drawn 1:1; larger
 * art goes in headers and tiles.
 */
public enum Art
{
    /** The plugin's mark, as on the web app: the crystal key. */
    MARK(Kind.ITEM, ItemID.CRYSTAL_KEY, 1),
    /** The world map globe, for a place. */
    PLACE(Kind.SPRITE, SpriteID.WorldmapIcon.PLANET, 0),
    STRICT_MODE(Kind.SPRITE, SpriteID.Magicon2.TELE_BLOCK, 0),
    /** The game's own greyed Tele Block, for Strict Mode off or paused. */
    STRICT_MODE_OFF(Kind.SPRITE, SpriteID.Magicoff2.TELE_BLOCK, 0),
    PADLOCK(Kind.SPRITE, SpriteID.Bankbuttons.PLACEHOLDERS_LOCK, 0),

    SKILLING(Kind.SPRITE, SpriteID.SideIcons.STATS, 0),
    BANKS(Kind.SPRITE, SpriteID.Mapfunction.BANK, 0),
    SHOPS(Kind.SPRITE, SpriteID.Mapfunction.GENERAL_STORE, 0),
    QUESTS(Kind.SPRITE, SpriteID.Mapfunction.QUEST_START, 0),
    COMBAT(Kind.SPRITE, SpriteID.SideIcons.COMBAT, 0),
    TRAVEL(Kind.SPRITE, SpriteID.Mapfunction.TRANSPORTATION, 0),
    FARMING(Kind.SPRITE, SpriteID.Mapfunction.FARMING_PATCH, 0),
    ACTIVITIES(Kind.SPRITE, SpriteID.Mapfunction.MINIGAME, 0),

    // What the Roll inbox lists (Stage 4); levels and Slayer tasks take their skill's icon.
    /** A combat task: the game's sword for a hard task, as its Combat Achievements tab draws it. */
    COMBAT_TASK(Kind.SPRITE, SpriteID.CaTierSwordsSmall._2, 0),
    COLLECTION_LOG(Kind.ITEM, ItemID.COLLECTION_LOG, 1),
    /** A clue scroll, as every tier's looks. */
    CLUE(Kind.ITEM, ItemID.TRAIL_CLUE_EASY_SIMPLE001, 1),
    RAID(Kind.SPRITE, SpriteID.Mapfunction.RAIDS_LOBBY, 0),
    /** The quest journal's achievement diary tab. */
    DIARY(Kind.SPRITE, SpriteID.AchievementDiaryIcons.GREEN_ACHIEVEMENT_DIARIES, 0),

    KEYS(Kind.ITEM, ItemID.CRYSTAL_KEY, 1),
    OMNI_KEYS(Kind.ITEM, ItemID.PRIF_CRYSTAL_KEY, 1),
    CHAOS_KEYS(Kind.ITEM, ItemID.KONAR_KEY, 1),
    /** A big stack of stardust, as the web app shows Fate Points. */
    FATE_POINTS(Kind.ITEM, ItemID.STAR_DUST, 175);

    public enum Kind
    {
        SPRITE,
        ITEM
    }

    private final Kind kind;
    private final int id;
    /** The sprite's frame, or the item stack to draw. */
    private final int detail;

    Art(Kind kind, int id, int detail)
    {
        this.kind = kind;
        this.id = id;
        this.detail = detail;
    }

    public Kind kind()
    {
        return kind;
    }

    /** The sprite archive, or the item id. */
    public int id()
    {
        return id;
    }

    /** The sprite's frame, for a sprite; the stack size to draw, for an item. */
    public int detail()
    {
        return detail;
    }

    /** The art for a content category, or null. */
    public static Art forCategory(String category)
    {
        switch (category)
        {
            case "SKILLING":
                return SKILLING;
            case "BANKS":
                return BANKS;
            case "SHOPS":
                return SHOPS;
            case "QUESTS":
                return QUESTS;
            case "COMBAT":
                return COMBAT;
            case "TRAVEL":
                return TRAVEL;
            case "FARMING":
                return FARMING;
            case "ACTIVITIES":
                return ACTIVITIES;
            default:
                return null;
        }
    }
}

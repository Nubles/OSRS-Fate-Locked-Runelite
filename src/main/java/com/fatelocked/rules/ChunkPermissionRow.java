package com.fatelocked.rules;

import lombok.Getter;

@Getter
public final class ChunkPermissionRow
{
    private String key;
    private String name;
    private PermissionStatus status;
    private String detail;
    private String targetKind;
    /**
     * True on a minigame's ACTIVITIES row, which names a place rather than a thing the game
     * shows; absent on every other row and in exports from before the field.
     */
    private Boolean minigame;

    /** Whether this is a minigame's row. */
    public boolean isMinigame()
    {
        return Boolean.TRUE.equals(minigame);
    }

    ChunkPermissionRow normalized()
    {
        ChunkPermissionRow copy = new ChunkPermissionRow();
        copy.key = key == null ? "" : key;
        copy.name = name == null ? "" : name;
        copy.status = status == null ? PermissionStatus.UNKNOWN : status;
        copy.detail = detail;
        copy.targetKind = targetKind;
        copy.minigame = minigame;
        return copy;
    }
}

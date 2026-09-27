package com.fatelocked;

import lombok.Value;

/**
 * Where the player stands, for overlays that draw on the scene (B14): the
 * chunk the rules judge them by, and the chunk and plane of the loaded
 * top-level scene under them. They differ inside an instance, whose scene
 * is a copy of somewhere else, and on a boat, which floats on the sea.
 */
@Value
public class Located
{
    /** The rules chunk; null when it can't be known (an instance zone with no template). */
    CanonicalChunk rules;
    /** The chunk of the top-level scene, in its own coordinates, for drawing. */
    CanonicalChunk scene;
    int plane;
    /** The player's tile in the top-level scene, counted from its base. */
    int sceneX;
    int sceneY;
}

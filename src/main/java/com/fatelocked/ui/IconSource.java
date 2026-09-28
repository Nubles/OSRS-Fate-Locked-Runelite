package com.fatelocked.ui;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Where the sidebar gets its OSRS art. In the client it comes from the game cache,
 * which is ready only from the login screen, so art arrives late and views show
 * nothing in its place until it does.
 */
public interface IconSource
{
    /** No art at all: views draw without it. */
    IconSource NONE = (art, into) -> { };

    /** Hands the art to {@code into} on the Swing thread, now or once it has loaded. */
    void load(Art art, Consumer<BufferedImage> into);
}

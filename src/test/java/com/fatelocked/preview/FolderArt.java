package com.fatelocked.preview;

import com.fatelocked.ui.Art;
import com.fatelocked.ui.IconSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * OSRS art for previews, from a folder of PNGs named after {@link Art} constants
 * ({@code STRICT_MODE.png}), since the game cache isn't there without a client. The
 * folder holds the same art as the game: the OSRS wiki's copies or a sprite dump.
 * Missing files draw nothing, as art that hasn't loaded does in the client.
 */
public final class FolderArt implements IconSource
{
    private final Path folder;

    public FolderArt(Path folder)
    {
        this.folder = folder;
    }

    @Override
    public void load(Art art, Consumer<BufferedImage> into)
    {
        Path file = folder.resolve(art.name() + ".png");
        if (!Files.isRegularFile(file))
        {
            return;
        }
        try
        {
            into.accept(ImageIO.read(file.toFile()));
        }
        catch (IOException e)
        {
            throw new IllegalStateException("unreadable preview art " + file, e);
        }
    }
}

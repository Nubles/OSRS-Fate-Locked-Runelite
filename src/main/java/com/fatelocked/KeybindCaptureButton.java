package com.fatelocked;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.JButton;
import net.runelite.client.config.Keybind;

final class KeybindCaptureButton extends JButton
{
    private Consumer<Keybind> captureListener;
    private Keybind keybind;
    private boolean capturing;

    KeybindCaptureButton(Keybind keybind, Consumer<Keybind> captureListener)
    {
        this.captureListener = captureListener;
        setKeybind(keybind);
        addActionListener(event -> beginCapture());
        addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent event)
            {
                cancelCapture();
            }
        });
        addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent event)
            {
                capture(event);
            }
        });
    }

    void setCaptureListener(Consumer<Keybind> captureListener)
    {
        this.captureListener = captureListener;
    }

    void setKeybind(Keybind keybind)
    {
        this.keybind = keybind == null ? Keybind.NOT_SET : keybind;
        setText(this.keybind.toString());
    }

    private void beginCapture()
    {
        capturing = true;
        setText("Press a key…");
        requestFocusInWindow();
    }

    private void cancelCapture()
    {
        if (capturing)
        {
            capturing = false;
            setKeybind(keybind);
        }
    }

    private void capture(KeyEvent event)
    {
        if (!capturing)
        {
            return;
        }

        event.consume();
        capturing = false;
        Keybind captured = event.getKeyCode() == KeyEvent.VK_ESCAPE
            ? Keybind.NOT_SET : new Keybind(event);
        setKeybind(captured);
        captureListener.accept(captured);
    }
}

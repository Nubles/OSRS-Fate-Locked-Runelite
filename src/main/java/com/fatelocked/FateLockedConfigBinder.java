package com.fatelocked;

import java.awt.Color;
import java.awt.Component;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;

final class FateLockedConfigBinder
{
    interface ColorChooser
    {
        Color choose(Component parent, String title, Color initialColor);
    }

    private final ConfigManager configManager;
    private final Consumer<String> statusSink;
    private final ColorChooser colorChooser;
    private final Map<String, Runnable> refreshers = new LinkedHashMap<>();

    FateLockedConfigBinder(ConfigManager configManager, Consumer<String> statusSink)
    {
        this(configManager, statusSink, JColorChooser::showDialog);
    }

    FateLockedConfigBinder(ConfigManager configManager, Consumer<String> statusSink,
        ColorChooser colorChooser)
    {
        this.configManager = configManager;
        this.statusSink = statusSink;
        this.colorChooser = colorChooser;
    }

    JCheckBox booleanSetting(String key, String label, BooleanSupplier current)
    {
        final boolean[] confirmed = {current.getAsBoolean()};
        JCheckBox control = new JCheckBox(label, confirmed[0]);
        control.addActionListener(event -> {
            boolean selected = control.isSelected();
            save(key, selected, () -> confirmed[0] = selected,
                () -> control.setSelected(confirmed[0]));
        });
        refreshers.put(key, () -> {
            Boolean value = configManager.getConfiguration(
                FateLockedConfig.GROUP, key, Boolean.class);
            if (value != null)
            {
                confirmed[0] = value;
                control.setSelected(value);
            }
        });
        return control;
    }

    JComponent keybindSetting(String key, String label, Supplier<Keybind> current)
    {
        final Keybind[] confirmed = {keybindOrNotSet(current.get())};
        KeybindCaptureButton control = new KeybindCaptureButton(confirmed[0], value -> { });
        control.setToolTipText(label);
        control.setCaptureListener(value -> save(key, value,
            () -> confirmed[0] = value,
            () -> control.setKeybind(confirmed[0])));
        refreshers.put(key, () -> {
            Keybind value = configManager.getConfiguration(
                FateLockedConfig.GROUP, key, Keybind.class);
            if (value != null)
            {
                confirmed[0] = value;
                control.setKeybind(value);
            }
        });
        return control;
    }

    JComponent colorSetting(String key, String label, Supplier<Color> current)
    {
        final Color[] confirmed = {current.get()};
        JButton control = new JButton(label);
        applyColor(control, confirmed[0]);
        control.addActionListener(event -> {
            Color selected = colorChooser.choose(control, label, confirmed[0]);
            if (selected != null)
            {
                save(key, selected, () -> {
                    confirmed[0] = selected;
                    applyColor(control, selected);
                }, () -> applyColor(control, confirmed[0]));
            }
        });
        refreshers.put(key, () -> {
            Color value = configManager.getConfiguration(
                FateLockedConfig.GROUP, key, Color.class);
            if (value != null)
            {
                confirmed[0] = value;
                applyColor(control, value);
            }
        });
        return control;
    }

    void refresh(String key)
    {
        Runnable refresher = refreshers.get(key);
        if (refresher != null)
        {
            refresher.run();
        }
    }

    Set<String> keys()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(refreshers.keySet()));
    }

    private void save(String key, Object value, Runnable confirmed, Runnable rollback)
    {
        try
        {
            configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
            confirmed.run();
        }
        catch (RuntimeException error)
        {
            refresh(key);
            rollback.run();
            statusSink.accept("couldn't save setting");
        }
    }

    private static void applyColor(JButton control, Color color)
    {
        control.setBackground(color);
    }

    private static Keybind keybindOrNotSet(Keybind keybind)
    {
        return keybind == null ? Keybind.NOT_SET : keybind;
    }
}

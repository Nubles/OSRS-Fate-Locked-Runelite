package com.fatelocked;

import com.fatelocked.badges.BadgeIcons;
import com.fatelocked.badges.BadgeRelay;
import com.fatelocked.badges.BadgeService;
import com.fatelocked.rules.DecisionService;
import com.fatelocked.rules.Progress;
import com.google.gson.Gson;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;

/**
 * Connects a start's {@link BadgeService} to the plugin: its settings, the pairing (once its
 * profile is on the relay, as the tracker connection says) and online sync's consent, the run's bound character and progress, and the fixed relay.
 */
final class BadgeWiring
{
    private BadgeWiring()
    {
    }

    static BadgeService create(
        OkHttpClient http,
        Gson gson,
        TrackerConnectionSettings connection,
        Supplier<String> profileCode,
        FateLockedConfig config,
        ConfigManager configManager,
        Client client,
        BadgeIcons icons,
        Supplier<FateLockedBundle> bundle,
        Supplier<String> loggedInName,
        Supplier<DecisionService> decisions,
        Consumer<Runnable> onClient)
    {
        BadgeService.Settings settings = new BadgeService.Settings()
        {
            @Override
            public boolean sync()
            {
                return connection.networkAccessAllowed();
            }

            @Override
            public boolean showInChat()
            {
                return config.badgesInChat();
            }

            @Override
            public boolean showAboveHeads()
            {
                return config.badgesAboveHeads();
            }

            @Override
            public boolean share()
            {
                return config.shareBadge();
            }

            @Override
            public String pairingCode()
            {
                String code = profileCode.get();
                return code == null ? "" : code;
            }
        };
        BadgeService.Store store = new BadgeService.Store()
        {
            @Override
            public String get(String key)
            {
                return configManager.getConfiguration(FateLockedConfig.GROUP, key);
            }

            @Override
            public void set(String key, String value)
            {
                configManager.setConfiguration(FateLockedConfig.GROUP, key, value);
            }

            @Override
            public void unset(String key)
            {
                configManager.unsetConfiguration(FateLockedConfig.GROUP, key);
            }
        };
        BadgeService.Run run = new BadgeService.Run()
        {
            @Override
            public String boundName()
            {
                String name = loggedInName.get();
                return AccountBinding.sameAccount(AccountBinding.boundAccount(bundle.get()), name) ? name : null;
            }

            @Override
            public Progress progress()
            {
                return decisions.get().progress();
            }
        };
        return new BadgeService(new BadgeRelay(http, gson, TrackerConnectionSettings.RELAY_BASE_URL), gson,
            settings, store, run, client, icons, System::currentTimeMillis, onClient);
    }
}

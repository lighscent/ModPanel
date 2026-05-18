package com.xl1te.modpanel.discord;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class DiscordBot extends ListenerAdapter {

    private final Main plugin;
    private final BestLogger logger;
    private final ExecutorService executor;
    private JDA jda;

    public DiscordBot(Main plugin, BestLogger logger, ExecutorService executor) {
        this.plugin = plugin;
        this.logger = logger;
        this.executor = executor;
    }

    public void startBot() {
        String token = plugin.getConfig().getString("discord_token", "none");
        if (token == null || token.isEmpty() || token.equals("none")) {
            logger.warning("Discord token not configured. Discord bot will not start.");
            return;
        }

        String finalToken = token;
        CompletableFuture.supplyAsync(() -> {
            try {
                JDA built = JDABuilder.createDefault(finalToken)
                        .addEventListeners(this)
                        .build();
                built.awaitReady();
                return built;
            } catch (Exception e) {
                logger.severe("Failed to start Discord bot: " + e.getMessage());
                return null;
            }
        }, executor).thenAcceptAsync(built -> {
            if (built != null) {
                this.jda = built;
                logger.info("Discord bot started successfully.");
            }
        });
    }

    public void stopBot() {
        if (jda != null) {
            jda.shutdown();
            logger.info("Discord bot stopped.");
        }
    }
}

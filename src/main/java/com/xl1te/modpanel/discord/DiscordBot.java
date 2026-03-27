package com.xl1te.modpanel.discord;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.utils.BestLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordBot extends ListenerAdapter {

    private final Main plugin;
    private final BestLogger logger;
    private JDA jda;

    public DiscordBot(Main plugin, BestLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void startBot() {
        String token = plugin.getConfig().getString("discord_token", "none");
        if (token == null || token.isEmpty() || token.equals("none")) {
            logger.warning("Discord token not configured. Discord bot will not start.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            logger.info("Discord bot started successfully.");
        } catch (Exception e) {
            logger.severe("Failed to start Discord bot: " + e.getMessage());
        }
    }

    public void stopBot() {
        if (jda != null) {
            jda.shutdown();
            logger.info("Discord bot stopped.");
        }
    }
}

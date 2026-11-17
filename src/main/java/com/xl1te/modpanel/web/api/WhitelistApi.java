package com.xl1te.modpanel.web.api;

import com.xl1te.modpanel.Main;
import com.xl1te.modpanel.database.repository.WebWhitelistRepository;
import io.javalin.http.Context;

/** Endpoint de lecture de la whitelist web. */
public class WhitelistApi {

    private final WebWhitelistRepository whitelist;
    private final ApiSupport support;

    public WhitelistApi(Main plugin, ApiSupport support) {
        this.whitelist = plugin.getDatabaseManager().getWebWhitelistRepository();
        this.support = support;
    }

    public void handleWhitelist(Context ctx) throws Exception {
        ctx.result(support.gson().toJson(whitelist.list()));
    }
}

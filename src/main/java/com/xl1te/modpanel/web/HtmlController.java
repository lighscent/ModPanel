package com.xl1te.modpanel.web;

import com.xl1te.modpanel.utils.BestLogger;
import io.javalin.http.Context;
import java.io.InputStream;

public class HtmlController {

    private final BestLogger logger;

    public HtmlController(BestLogger logger) {
        this.logger = logger;
    }

    public void serveHtml(Context ctx) {
        String page = ctx.pathParam("page");
        String fileName = page.endsWith(".html") ? page : page + ".html";
        String clientIP = getClientIP(ctx);
        logger.info("Serving HTML file '" + fileName + "' to IP: " + clientIP);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("public/html/" + fileName);
            if (is != null) {
                ctx.contentType("text/html");
                ctx.result(is);
            } else {
                ctx.status(404).result("HTML file not found: " + fileName);
            }
        } catch (Exception e) {
            logger.severe("Error serving HTML file: " + e.getMessage());
            ctx.status(500).result("Internal server error");
        }
    }

    private String getClientIP(Context ctx) {
        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return ctx.req().getRemoteAddr();
    }
}